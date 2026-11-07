package com.atlasglobal.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "data_import")
public class DataImport {
    @Id
    private UUID id;

    @Column(name = "data_source_id")
    private UUID dataSourceId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status")
    private String status;

    @Column(name = "records_imported")
    private Long recordsImported;

    @Column(name = "scope", columnDefinition = "text")
    private String scope;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected DataImport() {
    }

    public UUID getId() { return id; }
    public UUID getDataSourceId() { return dataSourceId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getStatus() { return status; }
    public Long getRecordsImported() { return recordsImported; }
    public String getScope() { return scope; }
    public String getErrorMessage() { return errorMessage; }
}
