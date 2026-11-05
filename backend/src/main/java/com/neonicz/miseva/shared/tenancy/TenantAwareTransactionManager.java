package com.neonicz.miseva.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies the current tenant context to every new transaction with SET LOCAL semantics
 * (set_config(..., true)), so the values vanish at commit/rollback and can never leak
 * to another request through the connection pool.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(getDataSource());
        if (holder == null) {
            throw new CannotCreateTransactionException("Cannot apply tenant context: no JDBC connection bound");
        }
        TenantContext ctx = TenantContextHolder.get();
        Connection con = holder.getConnection();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT set_config('app.business_id', ?, true), set_config('app.user_id', ?, true)")) {
            ps.setString(1, ctx.businessId() == null ? "" : ctx.businessId().toString());
            ps.setString(2, ctx.userId() == null ? "" : ctx.userId().toString());
            ps.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not apply tenant context", e);
        }
    }
}
