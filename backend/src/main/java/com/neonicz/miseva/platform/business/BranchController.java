package com.neonicz.miseva.platform.business;

import com.neonicz.miseva.platform.business.BranchRepository.BranchData;
import com.neonicz.miseva.platform.business.BranchRepository.BranchView;
import com.neonicz.miseva.platform.business.BranchService.Actor;
import com.neonicz.miseva.platform.business.BranchService.BranchList;
import com.neonicz.miseva.platform.security.MisevaPrincipal;
import com.neonicz.miseva.shared.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    public record CreateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,11}", message = "Use 2-12 letters, digits or hyphens") String code,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @Size(max = 64) String timezone) {

        BranchData data() {
            return new BranchData(name, addressLine1, addressLine2, city, region, postalCode, countryCode, timezone);
        }
    }

    public record UpdateRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @Size(max = 64) String timezone) {

        BranchData data() {
            return new BranchData(name, addressLine1, addressLine2, city, region, postalCode, countryCode, timezone);
        }
    }

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('business.branch.read')")
    BranchList list(@AuthenticationPrincipal MisevaPrincipal p) {
        return service.list(actor(p));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('business.branch.read')")
    BranchView get(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID id) {
        return service.get(actor(p), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('business.branch.create')")
    BranchView create(@AuthenticationPrincipal MisevaPrincipal p, @Valid @RequestBody CreateRequest req) {
        return service.create(actor(p), req.code(), req.data());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('business.branch.update')")
    BranchView update(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID id,
                      @Valid @RequestBody UpdateRequest req) {
        return service.update(actor(p), id, req.version(), req.data());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('business.branch.update')")
    BranchView deactivate(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID id) {
        return service.setActive(actor(p), id, false);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('business.branch.update')")
    BranchView activate(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID id) {
        return service.setActive(actor(p), id, true);
    }

    private static Actor actor(MisevaPrincipal p) {
        if (p.businessId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_BUSINESS", "No business selected.");
        }
        return new Actor(p.businessId(), p.userId(), p.membershipId());
    }
}
