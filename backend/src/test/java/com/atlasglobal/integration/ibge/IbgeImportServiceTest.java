package com.atlasglobal.integration.ibge;

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

class IbgeImportServiceTest {
    private IbgeClient provider;
    private ImportStore store;
    private DataSource database;
    private Connection connection;
    private PreparedStatement acquire;
    private PreparedStatement release;
    private ResultSet lockResult;
    private ResultSet countryResult;
    private IbgeImportService service;

    @BeforeEach
    void setup() throws Exception {
        provider = mock(IbgeClient.class);
        store = mock(ImportStore.class);
        database = mock(DataSource.class);
        connection = mock(Connection.class);
        acquire = mock(PreparedStatement.class);
        release = mock(PreparedStatement.class);
        lockResult = mock(ResultSet.class);
        countryResult = mock(ResultSet.class);
        var country = mock(PreparedStatement.class);
        when(database.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        lenient().when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        lenient().when(connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM country WHERE iso3_code = 'BRA')")).thenReturn(country);
        lenient().when(country.executeQuery()).thenReturn(countryResult);
        lenient().when(countryResult.getBoolean(1)).thenReturn(true);
        when(acquire.executeQuery()).thenReturn(lockResult);
        when(lockResult.getBoolean(1)).thenReturn(true);
        service = new IbgeImportService(provider, store, new ImportProperties(List.of("BRA", "USA"), 2024, 2025), database);
    }

    @Test
    void importsOnlyBrazilWithSeparateDefinitionsAndAnAuditedExecution() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID(), series = UUID.randomUUID();
        when(store.ensureSource(IbgeCatalog.SOURCE)).thenReturn(source);
        when(store.start(source, "BRA;years=2024:2025")).thenReturn(execution);
        when(store.series(eq(source), any(ProviderData.Series.class), any(ProviderData.Indicator.class))).thenReturn(series);
        for (var entry : IbgeCatalog.ENTRIES) {
            var imported = new IbgeClient.ImportedSeries(entry.definition(2025), entry.metadata(2025),
                new ProviderData.Batch(List.of(new ProviderData.Observation("BRA", entry.code(), 2025, new BigDecimal("0.511"), null)), null));
            when(provider.series(entry, 2024, 2025)).thenReturn(imported);
        }
        assertThat(service.run()).isEqualTo(execution);
        verify(store, times(2)).observations(eq(execution), eq(series), any(ProviderData.Batch.class));
        verify(store).complete(execution);
        verify(store, never()).fail(any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void recordsProviderFailureAndReleasesTheProviderLock() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID();
        when(store.ensureSource(IbgeCatalog.SOURCE)).thenReturn(source);
        when(store.start(eq(source), anyString())).thenReturn(execution);
        when(provider.series(IbgeCatalog.ENTRIES.getFirst(), 2024, 2025)).thenThrow(new IbgeException("IBGE HTTP failure: 503"));
        assertThatThrownBy(service::run).isInstanceOf(IbgeException.class);
        verify(store).fail(execution);
        verify(store, never()).complete(any());
        verify(store, never()).observations(any(), any(), any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void rejectsMissingBrazilIdentityBeforeCreatingHistoryOrFetchingTheProvider() throws Exception {
        when(countryResult.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(service::run).hasMessage("Import the Brazil country catalog before IBGE data");
        verifyNoInteractions(provider, store);
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void rejectsConcurrentProviderImportsBeforeAnyHistoryOrProviderRequests() throws Exception {
        when(lockResult.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(service::run).hasMessage("IBGE import already running");
        verifyNoInteractions(provider, store);
        verify(release, never()).execute();
        verify(connection).close();
    }
}
