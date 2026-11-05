package com.neonicz.miseva.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The only sanctioned way to touch tenant-owned tables: runs the work in a transaction
 * whose database session carries the given business and user.
 */
@Component
public class TenantTx {

    private final TransactionTemplate template;

    public TenantTx(PlatformTransactionManager tm) {
        this.template = new TransactionTemplate(tm);
    }

    public <T> T inContext(UUID businessId, UUID userId, Supplier<T> work) {
        TenantContext wanted = new TenantContext(businessId, userId);
        TenantContext previous = TenantContextHolder.get();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!previous.equals(wanted)) {
                throw new IllegalStateException("Cannot switch tenant context inside an open transaction");
            }
            return work.get();
        }
        TenantContextHolder.set(wanted);
        try {
            return template.execute(status -> work.get());
        } finally {
            TenantContextHolder.restore(previous);
        }
    }

    public void inContext(UUID businessId, UUID userId, Runnable work) {
        inContext(businessId, userId, () -> {
            work.run();
            return null;
        });
    }
}
