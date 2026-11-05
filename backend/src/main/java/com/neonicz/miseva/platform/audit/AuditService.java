package com.neonicz.miseva.platform.audit;

import com.neonicz.miseva.shared.web.TraceIdFilter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Must be called inside a TenantTx whose context matches businessId (or with a null businessId). */
    public void record(UUID businessId, UUID branchId, UUID actorUserId, String action, String entityType, UUID entityId) {
        jdbc.sql("INSERT INTO audit_events (business_id, branch_id, actor_user_id, action, entity_type, entity_id, trace_id)"
                        + " VALUES (:business, :branch, :actor, :action, :type, :entity, :trace)")
                .param("business", businessId).param("branch", branchId).param("actor", actorUserId)
                .param("action", action).param("type", entityType).param("entity", entityId)
                .param("trace", MDC.get(TraceIdFilter.MDC_KEY)).update();
    }
}
