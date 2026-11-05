package com.neonicz.miseva.platform.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessRepository {

    private final JdbcClient jdbc;

    public BusinessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> activeIndustryId(String code) {
        return jdbc.sql("SELECT id FROM industry_types WHERE code = :code AND is_active")
                .param("code", code).query(UUID.class).optional();
    }

    public boolean slugExists(String slug) {
        return jdbc.sql("SELECT business_slug_exists(:slug)")
                .param("slug", slug).query(Boolean.class).single();
    }

    public void insertBusiness(UUID id, String slug, String legalName, String displayName, UUID industryId,
                               String country, String currency, String timezone, UUID createdBy) {
        jdbc.sql("INSERT INTO businesses (id, slug, legal_name, display_name, industry_type_id, country_code,"
                        + " default_currency, timezone, status, created_by)"
                        + " VALUES (:id, :slug, :legal, :display, :industry, :country, :currency, :tz, 'active', :by)")
                .param("id", id).param("slug", slug).param("legal", legalName).param("display", displayName)
                .param("industry", industryId).param("country", country).param("currency", currency)
                .param("tz", timezone).param("by", createdBy).update();
    }

    public UUID insertHeadOffice(UUID businessId, String name, String country, String timezone, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO branches (id, business_id, code, name, country_code, timezone, is_head_office, created_by)"
                        + " VALUES (:id, :business, 'HQ', :name, :country, :tz, true, :by)")
                .param("id", id).param("business", businessId).param("name", name)
                .param("country", country).param("tz", timezone).param("by", createdBy).update();
        return id;
    }

    public record BusinessSummary(UUID id, String name, String slug, String industry, String country,
                                  String currency, String timezone, String status) {
    }

    public Optional<BusinessSummary> findSummary(UUID businessId) {
        return jdbc.sql("SELECT b.id, b.display_name, b.slug, i.code AS industry, b.country_code, b.default_currency,"
                        + " b.timezone, b.status FROM businesses b JOIN industry_types i ON i.id = b.industry_type_id"
                        + " WHERE b.id = :id")
                .param("id", businessId)
                .query((rs, n) -> new BusinessSummary(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("slug"), rs.getString("industry"), rs.getString("country_code"),
                        rs.getString("default_currency"), rs.getString("timezone"), rs.getString("status")))
                .optional();
    }

}
