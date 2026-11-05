package com.neonicz.miseva.platform.subscription;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {

    private static final int TRIAL_DAYS = 14;

    private final JdbcClient jdbc;

    public SubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void startTrial(UUID businessId, String planCode, UUID createdBy) {
        int rows = jdbc.sql("INSERT INTO subscriptions (business_id, plan_id, status, current_period_start, current_period_end,"
                        + " trial_ends_at, created_by)"
                        + " SELECT :business, id, 'trialing', now(), now() + make_interval(days => :days),"
                        + " now() + make_interval(days => :days), :by FROM plans WHERE code = :plan AND is_active")
                .param("business", businessId).param("days", TRIAL_DAYS).param("plan", planCode).param("by", createdBy)
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Plan not found: " + planCode);
        }
    }
}
