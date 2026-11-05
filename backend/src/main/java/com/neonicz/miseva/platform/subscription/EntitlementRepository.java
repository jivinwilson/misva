package com.neonicz.miseva.platform.subscription;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementRepository {

    /** limit == null means unlimited; included == false means the feature is not part of the business's plan. */
    public record Entitlement(boolean included, Integer limit) {

        public boolean allows(long used) {
            return included && (limit == null || used < limit);
        }
    }

    private final JdbcClient jdbc;

    public EntitlementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Entitlement resolve(UUID businessId, String featureKey) {
        List<Integer> overrides = jdbc.sql("SELECT limit_value FROM business_entitlement_overrides"
                        + " WHERE business_id = :business AND feature_key = :key AND (expires_at IS NULL OR expires_at > now())")
                .param("business", businessId).param("key", featureKey)
                .query((rs, n) -> rs.getObject("limit_value", Integer.class)).list();
        if (!overrides.isEmpty()) {
            return new Entitlement(true, overrides.get(0));
        }
        List<Integer> fromPlan = jdbc.sql("SELECT pe.limit_value FROM subscriptions s"
                        + " JOIN plan_entitlements pe ON pe.plan_id = s.plan_id"
                        + " WHERE s.business_id = :business AND s.status IN ('trialing', 'active', 'past_due')"
                        + " AND pe.feature_key = :key")
                .param("business", businessId).param("key", featureKey)
                .query((rs, n) -> rs.getObject("limit_value", Integer.class)).list();
        return fromPlan.isEmpty() ? new Entitlement(false, 0) : new Entitlement(true, fromPlan.get(0));
    }
}
