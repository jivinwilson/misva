package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.audit.AuditService;
import com.neonicz.miseva.platform.employee.EmployeeRepository.EmployeeView;
import com.neonicz.miseva.platform.employee.EmployeeRepository.MembershipRef;
import com.neonicz.miseva.platform.identity.UserRepository;
import com.neonicz.miseva.platform.identity.UserRepository.UserAccount;
import com.neonicz.miseva.platform.rbac.RbacRepository;
import com.neonicz.miseva.platform.rbac.RbacRepository.RoleView;
import com.neonicz.miseva.platform.subscription.EntitlementRepository;
import com.neonicz.miseva.platform.subscription.EntitlementRepository.Entitlement;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class InvitationService {

    public record Actor(UUID businessId, UUID userId, UUID membershipId, Set<String> permissions) {
    }

    public record InviteRequest(String firstName, String lastName, String email, String jobTitle,
                                Set<UUID> roleIds, boolean allBranches, Set<UUID> branchIds) {
    }

    public record InviteResult(UUID membershipId, String email, OffsetDateTime expiresAt, String inviteUrl) {
    }

    static final int INVITE_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantTx tx;
    private final EmployeeRepository employees;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final RbacRepository rbac;
    private final EntitlementRepository entitlements;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final boolean exposeLink;
    private final String acceptUrlBase;

    public InvitationService(TenantTx tx, EmployeeRepository employees, MembershipRepository memberships,
                             UserRepository users, RbacRepository rbac, EntitlementRepository entitlements,
                             AuditService audit, PasswordEncoder encoder,
                             @Value("${miseva.invitations.expose-link:false}") boolean exposeLink,
                             @Value("${miseva.invitations.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.tx = tx;
        this.employees = employees;
        this.memberships = memberships;
        this.users = users;
        this.rbac = rbac;
        this.entitlements = entitlements;
        this.audit = audit;
        this.encoder = encoder;
        this.exposeLink = exposeLink;
        this.acceptUrlBase = frontendUrl.replaceAll("/+$", "") + "/accept-invite?token=";
    }

    public List<EmployeeView> list(Actor actor) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> employees.list(actor.businessId()));
    }

    public InviteResult invite(Actor actor, InviteRequest req) {
        String email = req.email().trim();
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            employees.lockBusiness(actor.businessId());
            Set<UUID> roleIds = validateRoles(actor, req.roleIds());
            Set<UUID> branchIds = validateBranches(actor, req);

            UserAccount user = users.findByEmail(email).orElse(null);
            UUID userId = user != null ? user.id() : UUID.randomUUID();
            Optional<MembershipRef> existing = user == null ? Optional.empty() : employees.findMembership(actor.businessId(), userId);
            if (existing.isPresent() && !List.of("invited", "removed").contains(existing.get().status())) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "This person is already part of your business.");
            }
            boolean addsMember = existing.isEmpty() || "removed".equals(existing.get().status());
            if (addsMember) {
                Entitlement ent = entitlements.resolve(actor.businessId(), "max_employees");
                if (!ent.allows(employees.countMembers(actor.businessId()))) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_LIMIT_REACHED",
                            "Your plan allows " + ent.limit() + " team member(s). Upgrade to add more.")
                            .with("maxEmployees", ent.limit());
                }
            }
            if (user == null) {
                users.insertPending(userId, email, encoder.encode(randomSecret()),
                        req.firstName().trim() + " " + req.lastName().trim());
            }

            String token = actor.businessId() + "." + randomSecret();
            OffsetDateTime expires = OffsetDateTime.now().plusDays(INVITE_DAYS);
            UUID membershipId;
            if (existing.isPresent()) {
                membershipId = existing.get().id();
                employees.reissue(actor.businessId(), membershipId, req.allBranches(), actor.userId(), sha256(token), expires);
            } else {
                membershipId = employees.insertInvited(actor.businessId(), userId, req.allBranches(), actor.userId(),
                        sha256(token), expires);
            }
            employees.saveProfile(actor.businessId(), membershipId, req.firstName().trim(), req.lastName().trim(),
                    blankToNull(req.jobTitle()), actor.userId());
            employees.replaceRoles(actor.businessId(), membershipId, roleIds, actor.userId());
            employees.replaceBranchAccess(actor.businessId(), membershipId, req.allBranches() ? Set.of() : branchIds, actor.userId());
            audit.record(actor.businessId(), null, actor.userId(), "employee.invited", "membership", membershipId);
            return new InviteResult(membershipId, email, expires, exposeLink ? acceptUrlBase + token : null);
        });
    }

    public InviteResult resend(Actor actor, UUID membershipId) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            MembershipRef ref = employees.findMembershipById(actor.businessId(), membershipId)
                    .filter(m -> "invited".equals(m.status())).orElseThrow(InvitationService::notFound);
            String token = actor.businessId() + "." + randomSecret();
            OffsetDateTime expires = OffsetDateTime.now().plusDays(INVITE_DAYS);
            employees.replaceToken(actor.businessId(), ref.id(), actor.userId(), sha256(token), expires);
            audit.record(actor.businessId(), null, actor.userId(), "employee.invitation_resent", "membership", ref.id());
            String email = employees.emailOf(actor.businessId(), ref.id()).orElseThrow();
            return new InviteResult(ref.id(), email, expires, exposeLink ? acceptUrlBase + token : null);
        });
    }

    public void revoke(Actor actor, UUID membershipId) {
        tx.inContext(actor.businessId(), actor.userId(), () -> {
            if (!employees.revokeInvitation(actor.businessId(), membershipId, actor.userId())) {
                throw notFound();
            }
            audit.record(actor.businessId(), null, actor.userId(), "employee.invitation_revoked", "membership", membershipId);
        });
    }

    private Set<UUID> validateRoles(Actor actor, Set<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_REQUIRED", "Choose at least one role.");
        }
        Set<UUID> assignable = rbac.assignableRoles().stream().map(RoleView::id).collect(Collectors.toSet());
        for (UUID roleId : requested) {
            if (!assignable.contains(roleId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ASSIGNABLE", "That role cannot be assigned.");
            }
            if (!actor.permissions().containsAll(rbac.permissionsOfRole(roleId))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_EXCEEDS_YOUR_ACCESS",
                        "You cannot grant a role with more access than you have.");
            }
        }
        return new LinkedHashSet<>(requested);
    }

    private Set<UUID> validateBranches(Actor actor, InviteRequest req) {
        Optional<Set<UUID>> mine = memberships.restrictedBranchIds(actor.membershipId());
        if (req.allBranches()) {
            if (mine.isPresent()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_EXCEEDED",
                        "You can only grant access to branches you have access to.");
            }
            return Set.of();
        }
        Set<UUID> ids = req.branchIds() == null ? Set.of() : new HashSet<>(req.branchIds());
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_REQUIRED", "Choose at least one branch, or all branches.");
        }
        if (mine.isPresent() && !mine.get().containsAll(ids)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_EXCEEDED",
                    "You can only grant access to branches you have access to.");
        }
        if (employees.countExistingBranches(actor.businessId(), ids) != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_BRANCH", "One of the branches does not exist or is inactive.");
        }
        return ids;
    }

    static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitation not found.");
    }
}
