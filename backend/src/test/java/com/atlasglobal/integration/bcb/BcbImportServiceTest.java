package com.atlasglobal.integration.bcb;

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

class BcbImportServiceTest {
    private BcbClient provider;
    private ImportStore store;
    private DataSource database;
    private Connection connection;
    private PreparedStatement acquire;
    private PreparedStatement release;
    private ResultSet lockResult;
    private PreparedStatement countryCheck;
    private ResultSet countryResult;
    private BcbImportService service;

    @BeforeEach
    void setup() throws Exception {
        provider = mock(BcbClient.class);
        store = mock(ImportStore.class);
        database = mock(DataSource.class);
        connection = mock(Connection.class);
        acquire = mock(PreparedStatement.class);
        release = mock(PreparedStatement.class);
        lockResult = mock(ResultSet.class);
        countryCheck = mock(PreparedStatement.class);
        countryResult = mock(ResultSet.class);
        lenient().when(database.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        lenient().when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        lenient().when(acquire.executeQuery()).thenReturn(lockResult);
        lenient().when(lockResult.getBoolean(1)).thenReturn(true);
        lenient().when(connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM country WHERE iso3_code = 'BRA')"))
            .thenReturn(countryCheck);
        lenient().when(countryCheck.executeQuery()).thenReturn(countryResult);
        lenient().when(countryResult.getBoolean(1)).thenReturn(true);
        service = new BcbImportService(provider, store,
            new ImportProperties(List.of("BRA", "USA"), 2024, 2025), database);
    }

    @Test
    void recordsBrazilianDefinitionAndDecemberObservationsInOneExecution() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID(), series = UUID.randomUUID();
        when(store.ensureSource(BcbCatalog.SOURCE)).thenReturn(source);
        when(store.start(eq(source), contains("month=12;completed-years-only"))).thenReturn(execution);
        when(store.series(source, BcbCatalog.DEFINITION, BcbCatalog.METADATA)).thenReturn(series);
        var batch = new ProviderData.Batch(List.of(new ProviderData.Observation("BRA", BcbCatalog.DEFINITION.code(),
            2025, new BigDecimal("49.70"), null)), null);
        when(provider.series(2024, 2025)).thenReturn(batch);
        assertThat(service.run()).isEqualTo(execution);
        verify(acquire).setLong(1, 728301952150L);
        verify(store).observations(execution, series, batch);
        verify(store).complete(execution);
        verify(store, never()).fail(any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void recordsFailureAndReleasesItsOwnLockWithoutPublishingABatch() throws Exception {
        UUID source = UUID.randomUUID(), execution = UUID.randomUUID();
        when(store.ensureSource(BcbCatalog.SOURCE)).thenReturn(source);
        when(store.start(eq(source), anyString())).thenReturn(execution);
        when(provider.series(2024, 2025)).thenThrow(new BcbException("BCB HTTP failure: 503"));
        assertThatThrownBy(service::run).isInstanceOf(BcbException.class);
        verify(store).fail(execution);
        verify(store, never()).complete(any());
        verify(store, never()).observations(any(), any(), any());
        verify(release).execute();
        verify(connection).close();
    }

    @Test
    void rejectsConcurrentImportsBeforeCreatingHistoryOrContactingTheProvider() throws Exception {
        when(lockResult.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(service::run).isInstanceOf(IllegalStateException.class).hasMessageContaining("already running");
        verifyNoInteractions(provider, store);
        verify(release, never()).execute();
        verify(connection).close();
    }

    @Test
    void rejectsScopeWithoutBrazilBeforeOpeningADatabaseConnection() {
        service = new BcbImportService(provider, store,
            new ImportProperties(List.of("USA"), 2024, 2025), database);
        assertThatThrownBy(service::run).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BRA");
        verifyNoInteractions(provider, store, database);
    }

    @Test
    void rejectsMissingCountryCatalogBeforeCreatingHistoryOrFetchingDataAndReleasesItsLock() throws Exception {
        when(countryResult.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(service::run).isInstanceOf(IllegalStateException.class)
            .hasMessage("Import the Brazil country catalog before BCB data");
        verifyNoInteractions(provider, store);
        verify(release).execute();
        verify(connection).close();
    }
}
