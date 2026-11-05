package com.neonicz.miseva.shared.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String env;

    public HealthController(@Value("${APP_ENV:local}") String env) {
        this.env = env;
    }

    @GetMapping
    Map<String, String> health() {
        return Map.of("status", "UP", "service", "miseva", "env", env);
    }
}
