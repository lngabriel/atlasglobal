package com.atlasglobal.ingestion;

import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import com.atlasglobal.integration.worldbank.WorldBankProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorldBankImportServiceTest {
    @Test
    void importsPublishedWorldSeriesUnderTheSameRunWithoutTreatingTheWorldAsACountry() throws Exception {
        var provider = mock(WorldBankProvider.class);
        var store = mock(ImportStore.class);
        var overview = mock(OverviewImportStore.class);
        var database = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var lock = mock(ResultSet.class);
        when(database.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(lock);
        when(lock.next()).thenReturn(true);
        when(lock.getBoolean(1)).thenReturn(true);
        UUID source = UUID.randomUUID(), run = UUID.randomUUID();
        when(store.ensureSource()).thenReturn(source);
        when(store.start(eq(source), anyString())).thenReturn(run);
        when(store.series(eq(source), any(WorldBankCatalog.Entry.class), any(ProviderData.Indicator.class)))
            .thenReturn(UUID.randomUUID());
        when(provider.countries()).thenReturn(List.of(new ProviderData.Country("BR", "BRA", "Brasil", "Brazil",
            null, null, null, null, null)));
        when(provider.indicator(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return new ProviderData.Indicator(code, code, "Original methodology", "Original organization");
        });
        doAnswer(invocation -> {
            List<String> countries = invocation.getArgument(0);
            String code = invocation.getArgument(1);
            Consumer<ProviderData.Batch> consumer = invocation.getArgument(4);
            consumer.accept(new ProviderData.Batch(countries.stream().map(country ->
                new ProviderData.Observation(country, code, 2024, BigDecimal.ONE, "")).toList(),
                LocalDate.of(2026, 10, 8)));
            return null;
        }).when(provider).observations(anyList(), anyString(), anyInt(), anyInt(), any());
        var importer = new WorldBankImportService(provider, store, overview,
            new ImportProperties(List.of("BRA"), 2020, 2024), database);

        assertThat(importer.run()).isEqualTo(run);

        verify(provider, times(WorldBankCatalog.ENTRIES.size())).observations(eq(List.of("BRA")), anyString(), eq(2020), eq(2024), any());
        for (String code : WorldBankCatalog.WORLD_INDICATORS) {
            verify(provider).observations(eq(List.of("WLD")), eq(code), eq(2020), eq(2024), any());
        }
        verify(provider, times(4)).observations(eq(List.of("WLD")), anyString(), anyInt(), anyInt(), any());
        verify(overview, times(4)).observations(eq(run), any(), argThat(batch ->
            batch.observations().stream().allMatch(value -> value.countryCode().equals("WLD"))));
        verify(store, times(WorldBankCatalog.ENTRIES.size())).observations(eq(run), any(), argThat(batch ->
            batch.observations().stream().allMatch(value -> value.countryCode().equals("BRA"))));
        verify(store).complete(run);
        verify(store, never()).fail(any());
        verify(store).countries(argThat(countries -> countries.size() == 1 && countries.getFirst().iso3().equals("BRA")));
        verify(connection).close();
    }
}
