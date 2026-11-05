package com.neonicz.miseva.platform.rbac;

import com.neonicz.miseva.platform.rbac.RbacRepository.RoleView;
import com.neonicz.miseva.platform.security.MisevaPrincipal;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RbacRepository rbac;
    private final TenantTx tx;

    public RoleController(RbacRepository rbac, TenantTx tx) {
        this.rbac = rbac;
        this.tx = tx;
    }

    @GetMapping("/assignable")
    @PreAuthorize("hasAnyAuthority('employee.member.invite', 'rbac.role.read')")
    List<RoleView> assignable(@AuthenticationPrincipal MisevaPrincipal p) {
        return tx.inContext(p.businessId(), p.userId(), rbac::assignableRoles);
    }
}
