package com.neonicz.miseva.platform.security;

import java.util.Set;
import java.util.UUID;

public record MisevaPrincipal(UUID userId, UUID businessId, UUID sessionId, UUID membershipId, Set<String> permissions) {
}
