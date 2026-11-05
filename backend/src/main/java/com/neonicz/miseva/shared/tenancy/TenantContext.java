package com.neonicz.miseva.shared.tenancy;

import java.util.UUID;

public record TenantContext(UUID businessId, UUID userId) {

    public static final TenantContext NONE = new TenantContext(null, null);
}
