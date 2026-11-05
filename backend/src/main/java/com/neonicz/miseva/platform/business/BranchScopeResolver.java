package com.neonicz.miseva.platform.business;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Port implemented by the employee module. Empty result means the member may use every branch. */
public interface BranchScopeResolver {

    Optional<Set<UUID>> restrictedBranchIds(UUID membershipId);
}
