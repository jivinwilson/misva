package com.neonicz.miseva.platform.business;

import com.neonicz.miseva.platform.business.BusinessRepository.Branch;
import com.neonicz.miseva.platform.security.MisevaPrincipal;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BusinessRepository businesses;
    private final TenantTx tx;

    public BranchController(BusinessRepository businesses, TenantTx tx) {
        this.businesses = businesses;
        this.tx = tx;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('business.branch.read')")
    List<Branch> list(@AuthenticationPrincipal MisevaPrincipal principal) {
        if (principal.businessId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_BUSINESS", "No business selected.");
        }
        return tx.inContext(principal.businessId(), principal.userId(), () -> businesses.listBranches(principal.businessId()));
    }
}
