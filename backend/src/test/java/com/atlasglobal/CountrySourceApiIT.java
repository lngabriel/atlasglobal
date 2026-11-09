package com.atlasglobal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CountrySourceApiIT {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UUID countryId;
    private UUID sourceId;

    @BeforeEach
    void catalog() {
        countryId = insertCountry("X1", "XAA", "API Brasil", "API Brazil", "API Região", "API Renda");
        insertCountry("X2", "XAB", "API Brasil", "API Twin", "API Região", "API Outra renda");
        insertCountry("X3", "XAC", "API 100%_Literal", "API Literal", "API Outra", "API Renda");
        insertCountry("X4", "XAD", "API 100XXLiteral", "API Plain", "API Outra", "API Outra renda");
        insertCountry("X5", "XAE", "API 100%XLiteral", "API Percent", "API Outra", "API Outra renda");
        sourceId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO data_source (id, code, name, base_url, description, documentation_url)
            VALUES (?, ?, ?, ?, ?, ?)
            """, sourceId, "API_TEST_PROVIDER", "API Test Source", "https://api.example.test/",
            "API source description", "https://example.test/docs");
    }

    @Test
    void searchesLocalizedNameOriginalNameIsoCodesAndRegion() throws Exception {
        mvc.perform(get("/api/v1/countries").param("q", "API BRASIL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
        for (String query : List.of("api brazil", "xaa", "x1")) {
            mvc.perform(get("/api/v1/countries").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].iso3Code").value("XAA"));
        }
        mvc.perform(get("/api/v1/countries").param("q", "API REGIÃO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinesRegionAndIncomeFiltersWithoutCaseSensitivity() throws Exception {
        mvc.perform(get("/api/v1/countries")
                .param("region", " api região ").param("incomeLevel", "API RENDA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].iso3Code").value("XAA"));
        mvc.perform(get("/api/v1/countries").param("q", "api brasil")
                .param("region", "API Outra").param("incomeLevel", "API Renda"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void treatsPercentAndUnderscoreAsLiteralSearchCharacters() throws Exception {
        mvc.perform(get("/api/v1/countries").param("q", "api 100%"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/countries").param("q", "api 100%_"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].iso3Code").value("XAC"));
    }

    @Test
    void paginatesWithStableTiesAndHandlesPagesBeyondTheResult() throws Exception {
        mvc.perform(get("/api/v1/countries").param("q", "api brasil").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[0].iso3Code").value("XAA"));
        mvc.perform(get("/api/v1/countries").param("q", "api brasil").param("size", "1").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].iso3Code").value("XAB"));
        mvc.perform(get("/api/v1/countries").param("q", "api brasil").param("size", "1").param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/countries").param("q", "api")
                .param("sort", "iso3Code").param("direction", "desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].iso3Code").value("XAE"));
    }

    @Test
    void returnsCountryMetadataWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/countries/xaa"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("WWW-Authenticate"))
            .andExpect(jsonPath("$.id").value(countryId.toString()))
            .andExpect(jsonPath("$.iso2Code").value("X1"))
            .andExpect(jsonPath("$.iso3Code").value("XAA"))
            .andExpect(jsonPath("$.name").value("API Brasil"))
            .andExpect(jsonPath("$.originalName").value("API Brazil"))
            .andExpect(jsonPath("$.region").value("API Região"))
            .andExpect(jsonPath("$.incomeLevel").value("API Renda"))
            .andExpect(jsonPath("$.capital").value("API Capital"))
            .andExpect(jsonPath("$.latitude").value(-10.25))
            .andExpect(jsonPath("$.longitude").value(-40.5));
    }

    @Test
    void searchesSourcesByNameAndCodeAndReturnsPublicMetadata() throws Exception {
        for (String query : List.of("api test source", "api_test_provider")) {
            mvc.perform(get("/api/v1/sources").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("API_TEST_PROVIDER"));
        }
        mvc.perform(get("/api/v1/sources/api_test_provider"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("WWW-Authenticate"))
            .andExpect(jsonPath("$.id").value(sourceId.toString()))
            .andExpect(jsonPath("$.name").value("API Test Source"))
            .andExpect(jsonPath("$.baseUrl").value("https://api.example.test/"))
            .andExpect(jsonPath("$.description").value("API source description"))
            .andExpect(jsonPath("$.documentationUrl").value("https://example.test/docs"))
            .andExpect(jsonPath("$.errorMessage").doesNotExist());
        mvc.perform(get("/api/v1/sources").param("q", "api test source")
                .param("size", "1").param("page", "1").param("sort", "code").param("direction", "desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsInvalidPaginationSortingAndFiltersWithProblemDetails() throws Exception {
        var invalidParameters = List.of(
            new String[]{"page", "abc"}, new String[]{"page", "-1"}, new String[]{"page", "10001"},
            new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"direction", "sideways"},
            new String[]{"sort", "name;DROP TABLE country"}, new String[]{"q", "a".repeat(101)},
            new String[]{"region", "a".repeat(201)}, new String[]{"incomeLevel", "a".repeat(201)});
        for (String[] parameter : invalidParameters) {
            mvc.perform(get("/api/v1/countries").param(parameter[0], parameter[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/v1/sources").param("sort", "baseUrl"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void distinguishesInvalidIdentifiersFromMissingResources() throws Exception {
        for (String path : List.of("/api/v1/countries/TOOLONG", "/api/v1/countries/---",
                "/api/v1/sources/invalid-code")) {
            mvc.perform(get(path)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        for (String path : List.of("/api/v1/countries/ZZZ", "/api/v1/sources/API_SOURCE_NOT_FOUND")) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void rejectsWriteMethodsOnPublicCatalogs() throws Exception {
        for (String path : List.of("/api/v1/countries", "/api/v1/sources")) {
            mvc.perform(post(path)).andExpect(status().isMethodNotAllowed());
            mvc.perform(put(path)).andExpect(status().isMethodNotAllowed());
            mvc.perform(patch(path)).andExpect(status().isMethodNotAllowed());
            mvc.perform(delete(path)).andExpect(status().isMethodNotAllowed());
        }
    }

    private UUID insertCountry(String iso2, String iso3, String name, String originalName,
                               String region, String incomeLevel) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO country (id, iso2_code, iso3_code, name, original_name, region, income_level,
                capital, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, iso2, iso3, name, originalName, region, incomeLevel, "API Capital",
            new BigDecimal("-10.25"), new BigDecimal("-40.5"));
        return id;
    }
}
