package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.audit.AuditService;
import com.neonicz.miseva.platform.employee.EmployeeRepository.EmployeeView;
import com.neonicz.miseva.platform.employee.EmployeeRepository.MemberDetail;
import com.neonicz.miseva.platform.employee.InvitationService.Actor;
import com.neonicz.miseva.platform.rbac.RbacRepository;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TeamService {

    public record UpdateRequest(String firstName, String lastName, String jobTitle, Set<UUID> roleIds,
                                boolean allBranches, Set<UUID> branchIds) {
    }

    private final TenantTx tx;
    private final EmployeeRepository employees;
    private final MembershipRepository memberships;
    private final RbacRepository rbac;
    private final GrantValidator grants;
    private final AuditService audit;

    public TeamService(TenantTx tx, EmployeeRepository employees, MembershipRepository memberships, RbacRepository rbac,
                       GrantValidator grants, AuditService audit) {
        this.tx = tx;
        this.employees = employees;
        this.memberships = memberships;
        this.rbac = rbac;
        this.grants = grants;
        this.audit = audit;
    }

    public List<EmployeeView> list(Actor actor) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> employees.list(actor.businessId()).stream()
                .map(e -> e.withFlags(e.membershipId().equals(actor.membershipId()), isManageable(actor, e)))
                .toList());
    }

    public void update(Actor actor, UUID id, UpdateRequest req) {
        tx.inContext(actor.businessId(), actor.userId(), () -> {
            employees.lockBusiness(actor.businessId());
            MemberDetail target = manageable(actor, id, "active", "suspended");
            if (target.owner() && req.roleIds() != null && !req.roleIds().isEmpty()
                    && !req.roleIds().equals(target.roleIds())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ROLE_IMMUTABLE", "The owner's role cannot be changed here.");
            }
            Set<UUID> roleIds = target.owner() ? target.roleIds() : grants.roles(actor, req.roleIds());
            Set<UUID> branchIds = grants.branches(actor, req.allBranches(), req.branchIds());

            employees.saveProfile(actor.businessId(), id, req.firstName().trim(), req.lastName().trim(),
                    blankToNull(req.jobTitle()), actor.userId());
            if (!target.owner()) {
                employees.replaceRoles(actor.businessId(), id, roleIds, actor.userId());
            }
            employees.setAllBranches(actor.businessId(), id, req.allBranches(), actor.userId());
            employees.replaceBranchAccess(actor.businessId(), id, req.allBranches() ? Set.of() : branchIds, actor.userId());
            audit.record(actor.businessId(), null, actor.userId(), "employee.updated", "membership", id);
        });
    }

    public void suspend(Actor actor, UUID id) {
        change(actor, id, List.of("active"), "suspended", "employee.suspended");
    }

    public void reactivate(Actor actor, UUID id) {
        change(actor, id, List.of("suspended"), "active", "employee.reactivated");
    }

    public void remove(Actor actor, UUID id) {
        change(actor, id, List.of("active", "suspended"), "removed", "employee.removed");
    }

    private void change(Actor actor, UUID id, List<String> from, String to, String auditAction) {
        tx.inContext(actor.businessId(), actor.userId(), () -> {
            employees.lockBusiness(actor.businessId());
            manageable(actor, id, from.toArray(String[]::new));
            if (!employees.transitionStatus(actor.businessId(), id, from, to, actor.userId())) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This change is not possible in the member's current state.");
            }
            audit.record(actor.businessId(), null, actor.userId(), auditAction, "membership", id);
        });
    }

    private MemberDetail manageable(Actor actor, UUID id, String... allowedStatuses) {
        MemberDetail target = employees.findDetail(actor.businessId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Team member not found."));
        if (!List.of(allowedStatuses).contains(target.status())) {
            if (List.of("invited", "removed").contains(target.status())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Team member not found.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This change is not possible in the member's current state.");
        }
        if (id.equals(actor.membershipId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_MODIFY_SELF", "You cannot change your own access or status.");
        }
        if (!actor.permissions().containsAll(rbac.permissionsOf(id))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TARGET_EXCEEDS_YOUR_ACCESS",
                    "You cannot change someone who has more access than you.");
        }
        Optional<Set<UUID>> scope = memberships.restrictedBranchIds(actor.membershipId());
        if (scope.isPresent() && (target.allBranches() || !scope.get().containsAll(target.branchIds()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_EXCEEDED",
                    "This person has access to branches you do not manage.");
        }
        return target;
    }

    private boolean isManageable(Actor actor, EmployeeView e) {
        if (!List.of("active", "suspended").contains(e.status()) || e.membershipId().equals(actor.membershipId())) {
            return false;
        }
        if (!actor.permissions().containsAll(rbac.permissionsOf(e.membershipId()))) {
            return false;
        }
        Optional<Set<UUID>> scope = memberships.restrictedBranchIds(actor.membershipId());
        return scope.isEmpty() || !e.allBranches() && scope.get().containsAll(e.branchIds());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
