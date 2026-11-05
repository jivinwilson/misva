package com.neonicz.miseva.platform.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port implemented by the employee module so identity never reads membership tables directly. */
public interface MembershipDirectory {

    record BusinessRef(UUID businessId, String name, String slug) {
    }

    List<BusinessRef> activeBusinessesOf(UUID userId);

    Optional<UUID> activeMembershipId(UUID userId, UUID businessId);
}
