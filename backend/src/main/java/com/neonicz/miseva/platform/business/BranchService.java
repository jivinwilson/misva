package com.neonicz.miseva.platform.business;

import com.neonicz.miseva.platform.audit.AuditService;
import com.neonicz.miseva.platform.business.BranchRepository.BranchData;
import com.neonicz.miseva.platform.business.BranchRepository.BranchView;
import com.neonicz.miseva.platform.subscription.EntitlementRepository;
import com.neonicz.miseva.platform.subscription.EntitlementRepository.Entitlement;
import com.neonicz.miseva.shared.error.ApiException;
import com.neonicz.miseva.shared.tenancy.TenantTx;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BranchService {

    public record Actor(UUID businessId, UUID userId, UUID membershipId) {
    }

    public record BranchList(List<BranchView> items, Integer maxBranches, long used, boolean restricted) {
    }

    private static final String MAX_BRANCHES = "max_branches";

    private final TenantTx tx;
    private final BranchRepository branches;
    private final BusinessRepository businesses;
    private final EntitlementRepository entitlements;
    private final BranchScopeResolver scopes;
    private final AuditService audit;

    public BranchService(TenantTx tx, BranchRepository branches, BusinessRepository businesses,
                         EntitlementRepository entitlements, BranchScopeResolver scopes, AuditService audit) {
        this.tx = tx;
        this.branches = branches;
        this.businesses = businesses;
        this.entitlements = entitlements;
        this.scopes = scopes;
        this.audit = audit;
    }

    public BranchList list(Actor actor) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            Optional<Set<UUID>> scope = scopes.restrictedBranchIds(actor.membershipId());
            List<BranchView> items = branches.list(actor.businessId()).stream()
                    .filter(b -> scope.map(ids -> ids.contains(b.id())).orElse(true)).toList();
            Entitlement ent = entitlements.resolve(actor.businessId(), MAX_BRANCHES);
            return new BranchList(items, ent.limit(), branches.count(actor.businessId()), scope.isPresent());
        });
    }

    public BranchView get(Actor actor, UUID id) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> visible(actor, id));
    }

    public BranchView create(Actor actor, String code, BranchData data) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            if (scopes.restrictedBranchIds(actor.membershipId()).isPresent()) {
                throw denied();
            }
            branches.lockBusiness(actor.businessId());
            Entitlement ent = entitlements.resolve(actor.businessId(), MAX_BRANCHES);
            long used = branches.count(actor.businessId());
            if (!ent.allows(used)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_LIMIT_REACHED",
                        "Your plan allows " + ent.limit() + " branch(es). Upgrade to add more.")
                        .with("maxBranches", ent.limit());
            }
            var business = businesses.findSummary(actor.businessId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND", "Business not found."));
            BranchData resolved = withDefaults(data, business.country(), business.timezone());
            UUID id;
            try {
                id = branches.insert(actor.businessId(), normalizedCode, resolved, actor.userId());
            } catch (DuplicateKeyException e) {
                throw new ApiException(HttpStatus.CONFLICT, "BRANCH_CODE_TAKEN", "A branch with this code already exists.");
            }
            audit.record(actor.businessId(), id, actor.userId(), "branch.created", "branch", id);
            return branches.find(actor.businessId(), id).orElseThrow();
        });
    }

    public BranchView update(Actor actor, UUID id, long version, BranchData data) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            BranchView existing = visible(actor, id);
            BranchData resolved = withDefaults(data, existing.countryCode(), existing.timezone());
            if (!branches.update(actor.businessId(), id, version, resolved, actor.userId())) {
                throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION",
                        "This branch was changed by someone else. Reload and try again.");
            }
            audit.record(actor.businessId(), id, actor.userId(), "branch.updated", "branch", id);
            return branches.find(actor.businessId(), id).orElseThrow();
        });
    }

    public BranchView setActive(Actor actor, UUID id, boolean active) {
        return tx.inContext(actor.businessId(), actor.userId(), () -> {
            BranchView existing = visible(actor, id);
            if (!active && existing.headOffice()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HEAD_OFFICE_CANNOT_BE_DEACTIVATED",
                        "The head office cannot be deactivated.");
            }
            String status = active ? "active" : "inactive";
            if (!existing.status().equals(status)) {
                branches.setStatus(actor.businessId(), id, status, actor.userId());
                audit.record(actor.businessId(), id, actor.userId(),
                        active ? "branch.activated" : "branch.deactivated", "branch", id);
            }
            return branches.find(actor.businessId(), id).orElseThrow();
        });
    }

    private BranchView visible(Actor actor, UUID id) {
        BranchView branch = branches.find(actor.businessId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND", "Branch not found."));
        Optional<Set<UUID>> scope = scopes.restrictedBranchIds(actor.membershipId());
        if (scope.isPresent() && !scope.get().contains(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND", "Branch not found.");
        }
        return branch;
    }

    private static BranchData withDefaults(BranchData d, String country, String timezone) {
        String c = blank(d.countryCode()) ? country : d.countryCode();
        String tz = blank(d.timezone()) ? timezone : d.timezone();
        if (!java.util.Arrays.asList(Locale.getISOCountries()).contains(c)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUNTRY", "Unknown country code.");
        }
        if (!ZoneId.getAvailableZoneIds().contains(tz)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Unknown timezone.");
        }
        return new BranchData(d.name().trim(), nullIfBlank(d.addressLine1()), nullIfBlank(d.addressLine2()),
                nullIfBlank(d.city()), nullIfBlank(d.region()), nullIfBlank(d.postalCode()), c, tz);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullIfBlank(String s) {
        return blank(s) ? null : s.trim();
    }

    private static ApiException denied() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to do this.");
    }
}
