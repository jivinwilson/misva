package com.neonicz.miseva.platform.security;

import com.neonicz.miseva.platform.identity.MembershipDirectory;
import com.neonicz.miseva.platform.identity.SessionRepository;
import com.neonicz.miseva.platform.rbac.RbacRepository;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into a principal. Tenant identity and permissions come from the database
 * (live session, live membership), never from client-supplied values.
 */
@Component
public class MisevaAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TenantTx tx;
    private final SessionRepository sessions;
    private final MembershipDirectory memberships;
    private final RbacRepository rbac;

    public MisevaAuthenticationConverter(TenantTx tx, SessionRepository sessions, MembershipDirectory memberships,
                                         RbacRepository rbac) {
        this.tx = tx;
        this.sessions = sessions;
        this.memberships = memberships;
        this.rbac = rbac;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        UUID sessionId;
        UUID businessId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            String bid = jwt.getClaimAsString("bid");
            businessId = bid == null ? null : UUID.fromString(bid);
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Malformed token claims");
        }

        MisevaPrincipal principal = tx.inContext(businessId, userId, () -> {
            if (!sessions.isUsable(sessionId, userId)) {
                throw new InvalidBearerTokenException("Session is no longer valid");
            }
            if (businessId == null) {
                return new MisevaPrincipal(userId, null, sessionId, null, Set.of());
            }
            Optional<UUID> membership = memberships.activeMembershipId(userId, businessId);
            if (membership.isEmpty()) {
                throw new InvalidBearerTokenException("Membership is no longer active");
            }
            return new MisevaPrincipal(userId, businessId, sessionId, membership.get(), rbac.permissionsOf(membership.get()));
        });

        var authorities = principal.permissions().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
