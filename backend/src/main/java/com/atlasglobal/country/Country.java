package com.atlasglobal.country;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "country")
public class Country {
    @Id
    private UUID id;

    @Column(name = "iso2_code")
    private String iso2Code;

    @Column(name = "iso3_code")
    private String iso3Code;

    @Column(name = "name")
    private String name;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "region")
    private String region;

    @Column(name = "income_level")
    private String incomeLevel;

    @Column(name = "capital")
    private String capital;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    protected Country() {
    }

    public UUID getId() { return id; }
    public String getIso2Code() { return iso2Code; }
    public String getIso3Code() { return iso3Code; }
    public String getName() { return name; }
    public String getOriginalName() { return originalName; }
    public String getRegion() { return region; }
    public String getIncomeLevel() { return incomeLevel; }
    public String getCapital() { return capital; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
