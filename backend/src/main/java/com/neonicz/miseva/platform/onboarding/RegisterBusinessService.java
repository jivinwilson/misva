package com.neonicz.miseva.platform.onboarding;

import com.neonicz.miseva.platform.audit.AuditService;
import com.neonicz.miseva.platform.business.BusinessRepository;
import com.neonicz.miseva.platform.employee.MembershipRepository;
import com.neonicz.miseva.platform.identity.TokenService;
import com.neonicz.miseva.platform.identity.TokenService.TokenPair;
import com.neonicz.miseva.platform.identity.UserRepository;
import com.neonicz.miseva.platform.rbac.RbacRepository;
import com.neonicz.miseva.platform.subscription.SubscriptionRepository;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterBusinessService {

    public record Registration(UUID businessId, String slug, UUID headOfficeBranchId, TokenPair tokens) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantTx tx;
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final RbacRepository rbac;
    private final SubscriptionRepository subscriptions;
    private final AuditService audit;
    private final TokenService tokens;
    private final PasswordEncoder encoder;

    public RegisterBusinessService(TenantTx tx, UserRepository users, BusinessRepository businesses,
                                   MembershipRepository memberships, RbacRepository rbac,
                                   SubscriptionRepository subscriptions, AuditService audit, TokenService tokens,
                                   PasswordEncoder encoder) {
        this.tx = tx;
        this.users = users;
        this.businesses = businesses;
        this.memberships = memberships;
        this.rbac = rbac;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    public Registration register(RegisterBusinessController.Request req) {
        validateReferenceData(req);
        UUID businessId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String passwordHash = encoder.encode(req.password());
        String fullName = req.ownerFirstName().trim() + " " + req.ownerLastName().trim();

        Registration partial = tx.inContext(businessId, userId, () -> {
            UUID industryId = businesses.activeIndustryId(req.industry())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_INDUSTRY", "Unknown industry."));
            try {
                users.insert(userId, req.email().trim(), passwordHash, fullName);
            } catch (DuplicateKeyException e) {
                if (String.valueOf(e.getMessage()).contains("uq_users_email")) {
                    throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists.");
                }
                throw e;
            }
            String slug = uniqueSlug(req.businessName());
            String legalName = req.legalName() == null || req.legalName().isBlank() ? req.businessName().trim() : req.legalName().trim();
            businesses.insertBusiness(businessId, slug, legalName, req.businessName().trim(), industryId,
                    req.countryCode(), req.currency(), req.timezone(), userId);
            String branchName = req.headOfficeName() == null || req.headOfficeName().isBlank() ? "Head Office" : req.headOfficeName().trim();
            UUID branchId = businesses.insertHeadOffice(businessId, branchName, req.countryCode(), req.timezone(), userId);
            UUID membershipId = memberships.insertActiveOwner(businessId, userId, req.ownerFirstName().trim(), req.ownerLastName().trim());
            rbac.assignSystemRole(businessId, membershipId, "owner");
            subscriptions.startTrial(businessId, "starter", userId);
            audit.record(businessId, branchId, userId, "business.registered", "business", businessId);
            return new Registration(businessId, slug, branchId, null);
        });

        TokenPair pair = tokens.openSession(userId, businessId);
        return new Registration(partial.businessId(), partial.slug(), partial.headOfficeBranchId(), pair);
    }

    private static void validateReferenceData(RegisterBusinessController.Request req) {
        if (!java.util.Arrays.asList(Locale.getISOCountries()).contains(req.countryCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY", "Unknown country code.");
        }
        try {
            Currency.getInstance(req.currency());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "Unknown currency code.");
        }
        if (!ZoneId.getAvailableZoneIds().contains(req.timezone())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Unknown timezone.");
        }
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > 48) {
            base = base.substring(0, 48).replaceAll("-+$", "");
        }
        if (base.length() < 2) {
            base = "business";
        }
        String slug = base;
        while (businesses.slugExists(slug)) {
            byte[] suffix = new byte[2];
            RANDOM.nextBytes(suffix);
            slug = base + "-" + HexFormat.of().formatHex(suffix);
        }
        return slug;
    }
}
