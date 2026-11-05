package com.neonicz.miseva.platform.identity;

import com.neonicz.miseva.platform.identity.MembershipDirectory.BusinessRef;
import com.neonicz.miseva.platform.identity.TokenService.TokenPair;
import com.neonicz.miseva.platform.identity.UserRepository.UserAccount;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final MembershipDirectory memberships;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final TenantTx tx;
    private final String dummyHash;

    public AuthService(UserRepository users, MembershipDirectory memberships, TokenService tokens,
                       PasswordEncoder encoder, TenantTx tx) {
        this.users = users;
        this.memberships = memberships;
        this.tokens = tokens;
        this.encoder = encoder;
        this.tx = tx;
        this.dummyHash = encoder.encode("miseva-timing-equaliser");
    }

    public TokenPair login(String email, String password, UUID requestedBusinessId) {
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Too many failed attempts. Try again later.");
        }
        if (!encoder.matches(password, user.passwordHash())) {
            users.recordFailedLogin(user.id());
            throw invalidCredentials();
        }
        if (!"active".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "This account is not active.");
        }
        users.recordSuccessfulLogin(user.id());

        List<BusinessRef> businesses = tx.inContext(null, user.id(), () -> memberships.activeBusinessesOf(user.id()));
        BusinessRef chosen;
        if (requestedBusinessId != null) {
            chosen = businesses.stream().filter(b -> b.businessId().equals(requestedBusinessId)).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_ACCESS_TO_BUSINESS",
                            "You do not have access to that business."));
        } else if (businesses.size() == 1) {
            chosen = businesses.get(0);
        } else if (businesses.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_BUSINESS", "You do not belong to any active business.");
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "BUSINESS_SELECTION_REQUIRED", "Choose a business to sign in to.")
                    .with("businesses", businesses);
        }
        return tokens.openSession(user.id(), chosen.businessId());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
}
