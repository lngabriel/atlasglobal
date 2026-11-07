package com.atlasglobal.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "data_source")
public class DataSource {
    @Id
    private UUID id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "base_url", columnDefinition = "text")
    private String baseUrl;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "documentation_url", columnDefinition = "text")
    private String documentationUrl;

    protected DataSource() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getDescription() { return description; }
    public String getDocumentationUrl() { return documentationUrl; }
}
