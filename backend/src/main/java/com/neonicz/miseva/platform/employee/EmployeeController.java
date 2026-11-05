package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.employee.EmployeeRepository.EmployeeView;
import com.neonicz.miseva.platform.employee.InvitationService.Actor;
import com.neonicz.miseva.platform.employee.InvitationService.InviteRequest;
import com.neonicz.miseva.platform.employee.InvitationService.InviteResult;
import com.neonicz.miseva.platform.security.MisevaPrincipal;
import com.neonicz.miseva.shared.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    public record InviteBody(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 100) String jobTitle,
            @NotEmpty Set<UUID> roleIds,
            boolean allBranches,
            Set<UUID> branchIds) {
    }

    public record UpdateBody(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 100) String jobTitle,
            Set<UUID> roleIds,
            boolean allBranches,
            Set<UUID> branchIds) {
    }

    private final InvitationService invitations;
    private final TeamService team;

    public EmployeeController(InvitationService invitations, TeamService team) {
        this.invitations = invitations;
        this.team = team;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.member.read')")
    List<EmployeeView> list(@AuthenticationPrincipal MisevaPrincipal p) {
        return team.list(actor(p));
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('employee.member.invite')")
    InviteResult invite(@AuthenticationPrincipal MisevaPrincipal p, @Valid @RequestBody InviteBody b) {
        return invitations.invite(actor(p), new InviteRequest(b.firstName(), b.lastName(), b.email(), b.jobTitle(),
                b.roleIds(), b.allBranches(), b.branchIds()));
    }

    @PostMapping("/{membershipId}/resend")
    @PreAuthorize("hasAuthority('employee.member.invite')")
    InviteResult resend(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId) {
        return invitations.resend(actor(p), membershipId);
    }

    @PostMapping("/{membershipId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('employee.member.invite')")
    void revoke(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId) {
        invitations.revoke(actor(p), membershipId);
    }

    @PutMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('employee.member.update')")
    void update(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId, @Valid @RequestBody UpdateBody b) {
        team.update(actor(p), membershipId, new TeamService.UpdateRequest(b.firstName(), b.lastName(), b.jobTitle(),
                b.roleIds(), b.allBranches(), b.branchIds()));
    }

    @PostMapping("/{membershipId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('employee.member.update')")
    void suspend(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId) {
        team.suspend(actor(p), membershipId);
    }

    @PostMapping("/{membershipId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('employee.member.update')")
    void reactivate(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId) {
        team.reactivate(actor(p), membershipId);
    }

    @PostMapping("/{membershipId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('employee.member.remove')")
    void remove(@AuthenticationPrincipal MisevaPrincipal p, @PathVariable UUID membershipId) {
        team.remove(actor(p), membershipId);
    }

    private static Actor actor(MisevaPrincipal p) {
        if (p.businessId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_BUSINESS", "No business selected.");
        }
        return new Actor(p.businessId(), p.userId(), p.membershipId(), p.permissions());
    }
}
