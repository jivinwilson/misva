package com.neonicz.miseva.platform.identity;

import com.neonicz.miseva.platform.identity.SessionRepository.Session;
import com.neonicz.miseva.platform.identity.UserRepository.UserAccount;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final MembershipDirectory memberships;
    private final TenantTx tx;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public TokenService(JwtEncoder encoder, SessionRepository sessions, UserRepository users,
                        MembershipDirectory memberships, TenantTx tx,
                        @Value("${miseva.security.access-token-ttl:PT15M}") Duration accessTtl,
                        @Value("${miseva.security.refresh-token-ttl:P30D}") Duration refreshTtl) {
        this.encoder = encoder;
        this.sessions = sessions;
        this.users = users;
        this.memberships = memberships;
        this.tx = tx;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public TokenPair openSession(UUID userId, UUID businessId) {
        return create(userId, businessId, null);
    }

    public TokenPair refresh(String refreshToken) {
        UUID userId = parseUserId(refreshToken);
        String hash = sha256(refreshToken);
        boolean[] reuseDetected = {false};
        TokenPair pair = tx.inContext(null, userId, () -> {
            Session old = sessions.findByHash(hash).orElse(null);
            if (old == null) {
                return null;
            }
            if (old.revokedAt() != null) {
                reuseDetected[0] = true;
                return null;
            }
            if (old.expiresAt().toInstant().isBefore(Instant.now())) {
                return null;
            }
            Optional<UserAccount> user = users.findById(userId);
            if (user.isEmpty() || !"active".equals(user.get().status())) {
                return null;
            }
            sessions.revoke(old.id());
            if (old.businessId() != null && memberships.activeMembershipId(userId, old.businessId()).isEmpty()) {
                return null;
            }
            return create(userId, old.businessId(), old.id());
        });
        if (reuseDetected[0]) {
            tx.inContext(null, userId, () -> sessions.revokeAllForUser(userId));
        }
        if (pair == null) {
            throw invalid();
        }
        return pair;
    }

    public void revoke(String refreshToken) {
        UUID userId;
        try {
            userId = parseUserId(refreshToken);
        } catch (ApiException e) {
            return;
        }
        String hash = sha256(refreshToken);
        tx.inContext(null, userId, () -> sessions.findByHash(hash).ifPresent(s -> sessions.revoke(s.id())));
    }

    private TokenPair create(UUID userId, UUID businessId, UUID rotatedFrom) {
        UUID sessionId = UUID.randomUUID();
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String refreshToken = userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        tx.inContext(null, userId, () -> sessions.insert(sessionId, userId, businessId, sha256(refreshToken),
                rotatedFrom, refreshTtl.toSeconds()));

        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("miseva").subject(userId.toString()).issuedAt(now).expiresAt(now.plus(accessTtl))
                .claim("sid", sessionId.toString());
        if (businessId != null) {
            claims.claim("bid", businessId.toString());
        }
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return new TokenPair(access, refreshToken, accessTtl.toSeconds());
    }

    private static UUID parseUserId(String refreshToken) {
        int dot = refreshToken == null ? -1 : refreshToken.indexOf('.');
        if (dot < 0) {
            throw invalid();
        }
        try {
            return UUID.fromString(refreshToken.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "The refresh token is invalid or expired.");
    }
}
