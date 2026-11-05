package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.audit.AuditService;
import com.neonicz.miseva.platform.employee.EmployeeRepository.InvitationRow;
import com.neonicz.miseva.platform.identity.TokenService;
import com.neonicz.miseva.platform.identity.TokenService.TokenPair;
import com.neonicz.miseva.platform.identity.UserRepository;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AcceptInvitationService {

    public record Preview(String businessName, String email, String firstName, boolean existingAccount) {
    }

    private record Outcome(UUID acceptedUserId, UUID failedUserId) {
    }

    private final TenantTx tx;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AuditService audit;

    public AcceptInvitationService(TenantTx tx, EmployeeRepository employees, UserRepository users,
                                   PasswordEncoder encoder, TokenService tokens, AuditService audit) {
        this.tx = tx;
        this.employees = employees;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.audit = audit;
    }

    public Preview preview(String token) {
        UUID businessId = businessOf(token);
        String hash = InvitationService.sha256(token);
        InvitationRow row = tx.inContext(businessId, null, () -> employees.findInvitationByHash(businessId, hash))
                .orElseThrow(AcceptInvitationService::invalid);
        return new Preview(row.businessName(), row.email(), row.firstName(), !"pending".equals(row.userStatus()));
    }

    public TokenPair accept(String token, String password) {
        UUID businessId = businessOf(token);
        String hash = InvitationService.sha256(token);

        Outcome outcome = tx.inContext(businessId, null, () -> {
            InvitationRow row = employees.findInvitationByHash(businessId, hash).orElseThrow(AcceptInvitationService::invalid);
            if ("pending".equals(row.userStatus())) {
                if (password == null || password.length() < 10 || password.length() > 72) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "Use 10 to 72 characters for your password.");
                }
                users.activatePending(row.userId(), encoder.encode(password));
            } else {
                var user = users.findById(row.userId()).orElseThrow(AcceptInvitationService::invalid);
                if (!"active".equals(user.status()) || user.lockedUntil() != null && user.lockedUntil().isAfter(java.time.OffsetDateTime.now())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "This account cannot accept invitations right now.");
                }
                if (password == null || !encoder.matches(password, user.passwordHash())) {
                    return new Outcome(null, row.userId());
                }
            }
            employees.activateMembership(businessId, row.membershipId(), row.userId());
            audit.record(businessId, null, row.userId(), "employee.invitation_accepted", "membership", row.membershipId());
            return new Outcome(row.userId(), null);
        });

        if (outcome.failedUserId() != null) {
            users.recordFailedLogin(outcome.failedUserId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Password is incorrect.");
        }
        return tokens.openSession(outcome.acceptedUserId(), businessId);
    }

    private static UUID businessOf(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot < 0) {
            throw invalid();
        }
        try {
            return UUID.fromString(token.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITATION", "This invitation is invalid or has expired.");
    }
}
