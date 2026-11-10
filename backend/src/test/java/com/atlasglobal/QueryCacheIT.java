package com.atlasglobal;

import com.atlasglobal.country.CountryQueryService;
import com.atlasglobal.country.CountryRepository;
import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.shared.api.PageParameters;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "atlas.cache.enabled=true", "atlas.import.schedule-enabled=false", "atlas.policy-rates.schedule-enabled=false",
    "atlas.additional-import.schedule-enabled=false"})
@DirtiesContext
class QueryCacheIT {
    @Autowired CountryQueryService countries;
    @Autowired CacheManager caches;
    @Autowired ImportStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource database;
    @MockitoSpyBean CountryRepository repository;
    private UUID source, execution;

    @BeforeEach
    void prepare() {
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        store.countries(List.of(new ProviderData.Country("Q1", "TQC", "Cache catalog original", "Cache test", null, null, null, null, null)));
        source = store.ensureSource(new ProviderData.Source("TEST_CACHE", "Cache test provider", "https://example.test/", "Test", "https://example.test/docs"));
        execution = store.start(source, "cache-invalidation-test");
        clearInvocations(repository);
    }

    @AfterEach
    void cleanup() {
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        jdbc.update("DELETE FROM data_import WHERE data_source_id=?", source);
        jdbc.update("DELETE FROM data_source WHERE id=?", source);
        jdbc.update("DELETE FROM country WHERE iso3_code='TQC'");
    }

    @Test
    void reusesCatalogReadsAndInvalidatesAfterAnImportCompletesOnAnotherConnection() throws Exception {
        assertThat(countries.getCountry("TQC").name()).isEqualTo("Cache catalog original");
        assertThat(countries.getCountry("TQC").name()).isEqualTo("Cache catalog original");
        verify(repository, times(1)).findByIso3Code("TQC");
        // This connection represents a separate operational import process, not an in-process eviction event.
        try (var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement("UPDATE country SET name='Cache catalog revised' WHERE iso3_code='TQC'");
                 var complete = connection.prepareStatement("UPDATE data_import SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?")) {
                update.executeUpdate(); complete.setObject(1, execution); complete.executeUpdate(); connection.commit();
            }
        }
        assertThat(countries.getCountry("TQC").name()).isEqualTo("Cache catalog revised");
        verify(repository, times(2)).findByIso3Code("TQC");
    }

    @Test
    void aFailedImportAlsoInvalidatesPreviouslyCommittedMetadata() {
        countries.getCountry("TQC");
        jdbc.update("UPDATE country SET name='Cache partial revision' WHERE iso3_code='TQC'");
        store.fail(execution);
        assertThat(countries.getCountry("TQC").name()).isEqualTo("Cache partial revision");
        verify(repository, times(2)).findByIso3Code("TQC");
    }

    @Test
    void paginationKeysCaptureValuesInsteadOfRetainingMutableParameterObjects() {
        var page = new PageParameters();
        assertThat(countries.search("Cache catalog", null, null, page).content()).hasSize(1);
        countries.search("Cache catalog", null, null, page);
        verify(repository, times(1)).search(any(), any(), any(), any());
        page.setPage(1);
        assertThat(countries.search("Cache catalog", null, null, page).content()).isEmpty();
        page.setPage(0);
        assertThat(countries.search("Cache catalog", null, null, page).content()).hasSize(1);
        verify(repository, times(2)).search(any(), any(), any(), any());
        var nativeCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>)caches.getCache("countries").getNativeCache();
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(256);
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(java.time.Duration.ofSeconds(60));
    }
}
