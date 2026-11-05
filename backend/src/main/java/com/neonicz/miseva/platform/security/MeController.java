package com.neonicz.miseva.platform.security;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    public record Me(UUID userId, UUID businessId, Set<String> permissions) {
    }

    @GetMapping
    Me me(@AuthenticationPrincipal MisevaPrincipal principal) {
        return new Me(principal.userId(), principal.businessId(), principal.permissions());
    }
}
