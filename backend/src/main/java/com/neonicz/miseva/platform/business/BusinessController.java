package com.neonicz.miseva.platform.business;

import com.neonicz.miseva.platform.business.BusinessRepository.BusinessSummary;
import com.neonicz.miseva.platform.security.MisevaPrincipal;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/current")
public class BusinessController {

    private final BusinessRepository businesses;
    private final TenantTx tx;

    public BusinessController(BusinessRepository businesses, TenantTx tx) {
        this.businesses = businesses;
        this.tx = tx;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('business.business.read')")
    BusinessSummary current(@AuthenticationPrincipal MisevaPrincipal principal) {
        if (principal.businessId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_BUSINESS", "No business selected.");
        }
        return tx.inContext(principal.businessId(), principal.userId(), () -> businesses.findSummary(principal.businessId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND", "Business not found."));
    }
}
