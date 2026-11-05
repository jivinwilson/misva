package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.employee.AcceptInvitationService.Preview;
import com.neonicz.miseva.platform.identity.TokenService.TokenPair;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: the single-use invitation token is the credential. */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    public record AcceptBody(@NotBlank String token, String password) {
    }

    private final AcceptInvitationService service;

    public InvitationController(AcceptInvitationService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    Preview preview(@RequestParam String token) {
        return service.preview(token);
    }

    @PostMapping("/accept")
    TokenPair accept(@Valid @RequestBody AcceptBody body) {
        return service.accept(body.token(), body.password());
    }
}
