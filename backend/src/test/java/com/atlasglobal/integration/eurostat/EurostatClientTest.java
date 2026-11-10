package com.atlasglobal.integration.eurostat;

import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EurostatClientTest {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final EurostatCatalog.Entry debt = EurostatCatalog.ENTRIES.get(0), renewable = EurostatCatalog.ENTRIES.get(1);
    private final Map<String, String> countries = Map.of("FR", "FRA", "ES", "ESP", "EL", "GRC", "UK", "GBR", "BR", "BRA");

    private JsonNode fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/eurostat/" + name + ".json")) { return mapper.readTree(input); }
    }

    @Test
    void returnsAnEmptyHistoryWhenTheRequestedYearHasNotBeenPublished() throws Exception {
        var empty = fixture("renewables-empty");
        assertThat(EurostatClient.decode(empty, renewable, countries, 2026, 2026).observations()).isEmpty();
        ((ObjectNode)empty.path("value")).put("0", 10);
        assertThatThrownBy(() -> EurostatClient.decode(empty, renewable, countries, 2026, 2026)).hasMessageContaining("outside cube");
    }

    @Test
    void preservesTheOfficialAnnualDebtDefinitionAndCountryScope() throws Exception {
        var data = EurostatClient.decode(fixture("debt"), debt, countries, 2023, 2025);
        assertThat(data.observations()).hasSize(9).allMatch(row -> row.indicatorCode().equals("EUROSTAT.GOVERNMENT.DEBT.GDP"));
        var france = data.observations().stream().filter(row -> row.countryCode().equals("FRA") && row.year() == 2025).findFirst().orElseThrow();
        assertThat(france.value()).isEqualByComparingTo("115.7");
        assertThat(data.observations()).noneMatch(row -> row.countryCode().equals("BRA") || row.countryCode().equals("GBR"));
        assertThat(data.sourceUpdatedAt()).isNull();
        assertThat(debt.definition().unit()).isEqualTo("PERCENT_GDP");
        assertThat(debt.metadata().methodology()).contains("ESA 2010", "AF.2", "year-end");
    }

    @Test
    void retainsRenewablePrecisionAndProvisionalFlagsWithoutUsingDatasetDatesAsRevisionDates() throws Exception {
        var data = EurostatClient.decode(fixture("renewables"), renewable, countries, 2023, 2025);
        var france = data.observations().stream().filter(row -> row.countryCode().equals("FRA")).toList();
        assertThat(france.getFirst().value()).isEqualByComparingTo("22.462");
        assertThat(france.getLast().providerStatus()).isEqualTo("p");
        assertThat(data.sourceUpdatedAt()).isNull();
        assertThat(renewable.definition().unit()).isEqualTo("PERCENT_GROSS_FINAL_ENERGY");
        assertThat(renewable.metadata().methodology()).contains("grid losses", "not the renewable share of electricity");
    }

    @Test
    void keepsZeroNegativeAndExplicitMissingCellsButDoesNotManufactureAbsentRows() throws Exception {
        var root = fixture("renewables");
        var values = (ObjectNode) root.path("value");
        values.set("0", mapper.readTree("0"));
        values.set("1", mapper.readTree("-0.1234567890123456789"));
        values.remove("2"); // The existing p status still identifies this source cell explicitly.
        values.remove("3"); // No value or status exists for this cell: do not create a row.
        var data = EurostatClient.decode(root, renewable, countries, 2023, 2025);
        assertThat(data.observations()).hasSize(8);
        var greece = data.observations().stream().filter(row -> row.countryCode().equals("GRC")).toList();
        assertThat(greece.getFirst().value()).isZero();
        assertThat(greece.get(1).value()).isEqualByComparingTo("-0.1234567890123456789");
        assertThat(greece.getLast().value()).isNull();
        assertThat(greece.getLast().providerStatus()).isEqualTo("p");
        assertThat(data.observations()).noneMatch(row -> row.countryCode().equals("ESP") && row.year() == 2023);
    }

    @Test
    void supportsDenseValueAndStatusArraysWithoutTurningNullStatusSlotsIntoMissingObservations() throws Exception {
        var root = (ObjectNode) fixture("debt");
        var values = mapper.createArrayNode(); var flags = mapper.createArrayNode();
        for (int index = 0; index < 18; index++) {
            values.add(root.path("value").get(Integer.toString(index)));
            if (index == 0) flags.add("p"); else flags.addNull();
        }
        values.set(1, mapper.nullNode()); root.set("value", values); root.set("status", flags);
        var data = EurostatClient.decode(root, debt, countries, 2023, 2025);
        assertThat(data.observations()).hasSize(9);
        assertThat(data.observations().getFirst().providerStatus()).isEqualTo("p");
        assertThat(data.observations().get(1).value()).isNull();
        var sparse = mapper.createObjectNode(); sparse.set("0", values.get(0)); root.set("value", sparse);
        assertThat(EurostatClient.decode(root, debt, countries, 2023, 2025).observations()).hasSize(1);
    }

    @Test
    void resolvesCubeCoordinatesFromDeclaredDimensionOrderRatherThanAssumingGeoThenTime() throws Exception {
        var root = (ObjectNode) fixture("debt");
        var ids = (ArrayNode) root.path("id"); ids.set(4, mapper.getNodeFactory().textNode("time")); ids.set(5, mapper.getNodeFactory().textNode("geo"));
        var sizes = (ArrayNode) root.path("size"); sizes.set(4, mapper.getNodeFactory().numberNode(3)); sizes.set(5, mapper.getNodeFactory().numberNode(6));
        var values = mapper.createObjectNode();
        for (var fields = root.path("value").fields(); fields.hasNext();) {
            var cell = fields.next(); int index = Integer.parseInt(cell.getKey());
            values.set(Integer.toString(index % 3 * 6 + index / 3), cell.getValue());
        }
        root.set("value", values);
        var france2025 = EurostatClient.decode(root, debt, countries, 2023, 2025).observations().stream()
            .filter(row -> row.countryCode().equals("FRA") && row.year() == 2025).findFirst().orElseThrow();
        assertThat(france2025.value()).isEqualByComparingTo("115.7");
    }

    @Test
    void rejectsChangedUnitsFrequencyDatasetGeographyAndDates() throws Exception {
        var wrongUnit = fixture("debt");
        ((ObjectNode)wrongUnit.path("dimension").path("unit").path("category").path("index")).remove("PC_GDP");
        ((ObjectNode)wrongUnit.path("dimension").path("unit").path("category").path("index")).put("MIO_EUR", 0);
        assertThatThrownBy(() -> EurostatClient.decode(wrongUnit, debt, countries, 2023, 2025)).hasMessageContaining("unit");
        var wrongFrequency = fixture("debt");
        ((ObjectNode)wrongFrequency.path("dimension").path("freq").path("category").path("index")).remove("A");
        ((ObjectNode)wrongFrequency.path("dimension").path("freq").path("category").path("index")).put("Q", 0);
        assertThatThrownBy(() -> EurostatClient.decode(wrongFrequency, debt, countries, 2023, 2025)).hasMessageContaining("freq");
        var wrongDataset = fixture("debt"); ((ObjectNode)wrongDataset.path("extension")).put("id", "OTHER");
        assertThatThrownBy(() -> EurostatClient.decode(wrongDataset, debt, countries, 2023, 2025)).hasMessageContaining("dataset");
        var aggregate = fixture("debt");
        ((ObjectNode)aggregate.path("dimension").path("geo").path("category").path("index")).remove("EL");
        ((ObjectNode)aggregate.path("dimension").path("geo").path("category").path("index")).put("EU27_2020", 0);
        assertThatThrownBy(() -> EurostatClient.decode(aggregate, debt, countries, 2023, 2025)).hasMessageContaining("geography");
        assertThatThrownBy(() -> EurostatClient.decode(fixture("debt"), debt, countries, 2024, 2025)).hasMessageContaining("period");
    }

    @Test
    void rejectsInvalidPositionsNonNumericValuesAndProviderWarningsInsteadOfImportingPartialData() throws Exception {
        var wrongPosition = fixture("debt"); ((ObjectNode)wrongPosition.path("value")).put("18", 1);
        assertThatThrownBy(() -> EurostatClient.decode(wrongPosition, debt, countries, 2023, 2025)).hasMessageContaining("outside cube");
        var repeatedPosition = fixture("debt");
        ((ObjectNode)repeatedPosition.path("dimension").path("geo").path("category").path("index")).put("ES", 0);
        assertThatThrownBy(() -> EurostatClient.decode(repeatedPosition, debt, countries, 2023, 2025)).hasMessageContaining("duplicate");
        var wrongValue = fixture("debt"); ((ObjectNode)wrongValue.path("value")).put("0", "not available");
        assertThatThrownBy(() -> EurostatClient.decode(wrongValue, debt, countries, 2023, 2025)).hasMessageContaining("numeric");
        assertThatThrownBy(() -> EurostatClient.decode(mapper.readTree("{\"warning\":{\"status\":413}}"), debt, countries, 2023, 2025)).hasMessageContaining("dataset");
    }

    @Test
    void usesDocumentedCountryLevelAndTimeBoundsAndRetriesTransientFailures() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var query = allOf(containsString("/gov_10dd_edpt1?lang=EN&geoLevel=country"), containsString("sinceTimePeriod=2023&untilTimePeriod=2025"),
            containsString("freq=A"), containsString("na_item=GD"), containsString("sector=S13"), containsString("unit=PC_GDP"));
        server.expect(requestTo(query)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(query)).andRespond(withSuccess(fixture("debt").toString(), MediaType.APPLICATION_JSON));
        var client = new EurostatClient(new RateHttp(builder.build(), mapper, 2, Duration.ZERO));
        assertThat(client.load(debt, countries, 2023, 2025).observations()).hasSize(9);
        server.verify();
    }

    @Test
    void mapsOnlyTheExplicitEurostatCountryCodeExceptions() {
        assertThat(EurostatProvider.referenceArea("GR")).isEqualTo("EL");
        assertThat(EurostatProvider.referenceArea("GB")).isEqualTo("UK");
        assertThat(EurostatProvider.referenceArea("FR")).isEqualTo("FR");
    }
}
