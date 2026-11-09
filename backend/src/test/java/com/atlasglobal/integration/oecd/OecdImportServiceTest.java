package com.atlasglobal.integration.oecd;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OecdImportServiceTest {
    private OecdClient provider;
    private ImportStore store;
    private DataSource database;
    private Connection connection;
    private PreparedStatement acquire;
    private PreparedStatement release;
    private ResultSet lockResult;
    private OecdImportService service;

    @BeforeEach
    void setup() throws Exception {
        provider = mock(OecdClient.class);
        store = mock(ImportStore.class);
        database = mock(DataSource.class);
        connection = mock(Connection.class);
        acquire = mock(PreparedStatement.class);
        release = mock(PreparedStatement.class);
        lockResult = mock(ResultSet.class);
        when(database.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        lenient().when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(lockResult);
        when(lockResult.getBoolean(1)).thenReturn(true);
        service = new OecdImportService(provider, store,
            new ImportProperties(List.of("USA"), 2024, 2024), database);
    }

    @Test
    void importsDefinitionsAndBatchesUnderOneRecordedExecution() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID(), series = UUID.randomUUID();
        when(store.ensureSource(OecdCatalog.SOURCE)).thenReturn(source);
        when(store.start(eq(source), anyString())).thenReturn(execution);
        when(store.series(eq(source), any(ProviderData.Series.class), any(ProviderData.Indicator.class))).thenReturn(series);
        for (var entry : OecdCatalog.ENTRIES) {
            var data = new OecdClient.ImportedSeries(entry.definition("2025"), entry.metadata("2025"),
                new ProviderData.Batch(List.of(new ProviderData.Observation("USA", entry.code(), 2024, new BigDecimal("123.45"), "A")), null));
            when(provider.series(entry, List.of("USA"), 2024, 2024)).thenReturn(List.of(data));
        }
        assertThat(service.run()).isEqualTo(execution);
        verify(store, times(3)).observations(eq(execution), eq(series), any(ProviderData.Batch.class));
        verify(store).complete(execution);
        verify(store, never()).fail(any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void recordsProviderFailureAndReleasesTheLock() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID();
        when(store.ensureSource(OecdCatalog.SOURCE)).thenReturn(source);
        when(store.start(eq(source), anyString())).thenReturn(execution);
        when(provider.series(OecdCatalog.ENTRIES.getFirst(), List.of("USA"), 2024, 2024))
            .thenThrow(new OecdException("OECD HTTP failure: 503"));
        assertThatThrownBy(service::run).isInstanceOf(OecdException.class);
        verify(store).fail(execution);
        verify(store, never()).complete(any());
        verify(store, never()).observations(any(), any(), any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void rejectsConcurrentImportBeforeCreatingHistoryOrRequestingData() throws Exception {
        when(lockResult.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(service::run).isInstanceOf(IllegalStateException.class).hasMessageContaining("already running");
        verifyNoInteractions(provider, store);
        verify(release, never()).execute();
        verify(connection).close();
    }
}
