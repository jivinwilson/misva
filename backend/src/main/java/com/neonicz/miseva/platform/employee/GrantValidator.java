package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.employee.InvitationService.Actor;
import com.neonicz.miseva.platform.rbac.RbacRepository;
import com.neonicz.miseva.platform.rbac.RbacRepository.RoleView;
import com.neonicz.miseva.shared.error.ApiException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Rules for what an actor may hand out: roles within their own access, branches within their own scope. */
@Component
class GrantValidator {

    private final RbacRepository rbac;
    private final MembershipRepository memberships;
    private final EmployeeRepository employees;

    GrantValidator(RbacRepository rbac, MembershipRepository memberships, EmployeeRepository employees) {
        this.rbac = rbac;
        this.memberships = memberships;
        this.employees = employees;
    }

    Set<UUID> roles(Actor actor, Set<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_REQUIRED", "Choose at least one role.");
        }
        Set<UUID> assignable = rbac.assignableRoles().stream().map(RoleView::id).collect(Collectors.toSet());
        for (UUID roleId : requested) {
            if (!assignable.contains(roleId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ASSIGNABLE", "That role cannot be assigned.");
            }
            if (!actor.permissions().containsAll(rbac.permissionsOfRole(roleId))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_EXCEEDS_YOUR_ACCESS",
                        "You cannot grant a role with more access than you have.");
            }
        }
        return new LinkedHashSet<>(requested);
    }

    Set<UUID> branches(Actor actor, boolean allBranches, Set<UUID> requested) {
        Optional<Set<UUID>> mine = memberships.restrictedBranchIds(actor.membershipId());
        if (allBranches) {
            if (mine.isPresent()) {
                throw scopeExceeded();
            }
            return Set.of();
        }
        Set<UUID> ids = requested == null ? Set.of() : new HashSet<>(requested);
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_REQUIRED", "Choose at least one branch, or all branches.");
        }
        if (mine.isPresent() && !mine.get().containsAll(ids)) {
            throw scopeExceeded();
        }
        if (employees.countExistingBranches(actor.businessId(), ids) != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_BRANCH", "One of the branches does not exist or is inactive.");
        }
        return ids;
    }

    private static ApiException scopeExceeded() {
        return new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_EXCEEDED",
                "You can only grant access to branches you have access to.");
    }
}
