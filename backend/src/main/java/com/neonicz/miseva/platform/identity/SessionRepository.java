package com.neonicz.miseva.platform.identity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

    public record Session(UUID id, UUID userId, UUID businessId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }

    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID userId, UUID businessId, String tokenHash, UUID rotatedFrom, long ttlSeconds) {
        jdbc.sql("INSERT INTO auth_sessions (id, user_id, active_business_id, refresh_token_hash, rotated_from_id, expires_at, last_used_at)"
                        + " VALUES (:id, :user, :business, :hash, :rotated, now() + make_interval(secs => :ttl), now())")
                .param("id", id).param("user", userId).param("business", businessId).param("hash", tokenHash)
                .param("rotated", rotatedFrom).param("ttl", ttlSeconds).update();
    }

    public Optional<Session> findByHash(String tokenHash) {
        return jdbc.sql("SELECT id, user_id, active_business_id, expires_at, revoked_at FROM auth_sessions WHERE refresh_token_hash = :hash")
                .param("hash", tokenHash)
                .query((rs, n) -> new Session(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("active_business_id", UUID.class), rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("revoked_at", OffsetDateTime.class)))
                .optional();
    }

    public boolean isUsable(UUID sessionId, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM auth_sessions s JOIN users u ON u.id = s.user_id"
                        + " WHERE s.id = :id AND s.user_id = :user AND s.revoked_at IS NULL AND s.expires_at > now() AND u.status = 'active'")
                .param("id", sessionId).param("user", userId).query(Integer.class).single() > 0;
    }

    public void revoke(UUID sessionId) {
        jdbc.sql("UPDATE auth_sessions SET revoked_at = now() WHERE id = :id AND revoked_at IS NULL")
                .param("id", sessionId).update();
    }

    public void revokeAllForUser(UUID userId) {
        jdbc.sql("UPDATE auth_sessions SET revoked_at = now() WHERE user_id = :user AND revoked_at IS NULL")
                .param("user", userId).update();
    }
}
