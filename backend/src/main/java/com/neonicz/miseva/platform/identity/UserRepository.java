package com.neonicz.miseva.platform.identity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public record UserAccount(UUID id, String email, String passwordHash, String fullName, String status,
                              OffsetDateTime lockedUntil) {
    }

    private static final int MAX_FAILED_LOGINS = 5;

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String email, String passwordHash, String fullName) {
        jdbc.sql("INSERT INTO users (id, email, password_hash, full_name, status) VALUES (:id, :email, :hash, :name, 'active')")
                .param("id", id).param("email", email).param("hash", passwordHash).param("name", fullName)
                .update();
    }

    public void insertPending(UUID id, String email, String unusablePasswordHash, String fullName) {
        jdbc.sql("INSERT INTO users (id, email, password_hash, full_name, status) VALUES (:id, :email, :hash, :name, 'pending')")
                .param("id", id).param("email", email).param("hash", unusablePasswordHash).param("name", fullName)
                .update();
    }

    public void activatePending(UUID id, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :hash, status = 'active', email_verified_at = now()"
                        + " WHERE id = :id AND status = 'pending'")
                .param("hash", passwordHash).param("id", id).update();
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.sql("SELECT id, email, password_hash, full_name, status, locked_until FROM users WHERE email = :email")
                .param("email", email).query(this::map).optional();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbc.sql("SELECT id, email, password_hash, full_name, status, locked_until FROM users WHERE id = :id")
                .param("id", id).query(this::map).optional();
    }

    public void recordFailedLogin(UUID id) {
        jdbc.sql("UPDATE users SET failed_login_count = failed_login_count + 1,"
                        + " locked_until = CASE WHEN failed_login_count + 1 >= :max THEN now() + interval '15 minutes' ELSE locked_until END"
                        + " WHERE id = :id")
                .param("max", MAX_FAILED_LOGINS).param("id", id).update();
    }

    public void recordSuccessfulLogin(UUID id) {
        jdbc.sql("UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = now() WHERE id = :id")
                .param("id", id).update();
    }

    private UserAccount map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                rs.getString("full_name"), rs.getString("status"), rs.getObject("locked_until", OffsetDateTime.class));
    }
}
