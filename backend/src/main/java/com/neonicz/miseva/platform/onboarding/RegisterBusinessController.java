package com.neonicz.miseva.platform.onboarding;

import com.neonicz.miseva.platform.onboarding.RegisterBusinessService.Registration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class RegisterBusinessController {

    public record Request(
            @NotBlank @Size(max = 150) String businessName,
            @Size(max = 200) String legalName,
            @NotBlank @Pattern(regexp = "restaurant|hotel|salon|retail|cafe|gym|clinic") String industry,
            @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 150) String headOfficeName,
            @NotBlank @Size(max = 100) String ownerFirstName,
            @NotBlank @Size(max = 100) String ownerLastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }

    private final RegisterBusinessService service;

    public RegisterBusinessController(RegisterBusinessService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Registration register(@Valid @RequestBody Request request) {
        return service.register(request);
    }
}
