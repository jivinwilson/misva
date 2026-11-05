package com.neonicz.miseva.platform.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BranchRepository {

    public record BranchView(UUID id, String code, String name, String status, boolean headOffice,
                             String addressLine1, String addressLine2, String city, String region,
                             String postalCode, String countryCode, String timezone, long version) {
    }

    public record BranchData(String name, String addressLine1, String addressLine2, String city, String region,
                             String postalCode, String countryCode, String timezone) {
    }

    private static final String COLUMNS = "id, code, name, status, is_head_office, address_line1, address_line2,"
            + " city, region, postal_code, country_code, timezone, version";

    private final JdbcClient jdbc;

    public BranchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void lockBusiness(UUID businessId) {
        jdbc.sql("SELECT id FROM businesses WHERE id = :id FOR UPDATE").param("id", businessId).query(UUID.class).single();
    }

    public long count(UUID businessId) {
        return jdbc.sql("SELECT count(*) FROM branches WHERE business_id = :business")
                .param("business", businessId).query(Long.class).single();
    }

    public List<BranchView> list(UUID businessId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM branches WHERE business_id = :business ORDER BY is_head_office DESC, code")
                .param("business", businessId).query(this::map).list();
    }

    public Optional<BranchView> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM branches WHERE business_id = :business AND id = :id")
                .param("business", businessId).param("id", id).query(this::map).optional();
    }

    public UUID insert(UUID businessId, String code, BranchData d, UUID by) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO branches (id, business_id, code, name, address_line1, address_line2, city, region,"
                        + " postal_code, country_code, timezone, created_by, updated_by)"
                        + " VALUES (:id, :business, :code, :name, :a1, :a2, :city, :region, :postal, :country, :tz, :by, :by)")
                .param("id", id).param("business", businessId).param("code", code).param("name", d.name())
                .param("a1", d.addressLine1()).param("a2", d.addressLine2()).param("city", d.city())
                .param("region", d.region()).param("postal", d.postalCode()).param("country", d.countryCode())
                .param("tz", d.timezone()).param("by", by).update();
        return id;
    }

    public boolean update(UUID businessId, UUID id, long version, BranchData d, UUID by) {
        return jdbc.sql("UPDATE branches SET name = :name, address_line1 = :a1, address_line2 = :a2, city = :city,"
                        + " region = :region, postal_code = :postal, country_code = :country, timezone = :tz, updated_by = :by"
                        + " WHERE id = :id AND business_id = :business AND version = :version")
                .param("name", d.name()).param("a1", d.addressLine1()).param("a2", d.addressLine2())
                .param("city", d.city()).param("region", d.region()).param("postal", d.postalCode())
                .param("country", d.countryCode()).param("tz", d.timezone()).param("by", by)
                .param("id", id).param("business", businessId).param("version", version).update() == 1;
    }

    public void setStatus(UUID businessId, UUID id, String status, UUID by) {
        jdbc.sql("UPDATE branches SET status = :status, updated_by = :by WHERE id = :id AND business_id = :business")
                .param("status", status).param("by", by).param("id", id).param("business", businessId).update();
    }

    private BranchView map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new BranchView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("is_head_office"), rs.getString("address_line1"),
                rs.getString("address_line2"), rs.getString("city"), rs.getString("region"),
                rs.getString("postal_code"), rs.getString("country_code"), rs.getString("timezone"), rs.getLong("version"));
    }
}
