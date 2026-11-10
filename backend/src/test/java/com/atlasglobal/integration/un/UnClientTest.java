package com.atlasglobal.integration.un;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UnClientTest {
    private static final String DOCUMENTATION = UnCatalog.DOCUMENTATION.toString();
    private static final String PATH = "/sites/default/files/2025_HDR/HDR25_Composite_indices_complete_time_series.csv";
    private static final String DOWNLOAD = "https://hdr.undp.org" + PATH + "?download=1";
    private static final String HTML = "<a href=\"" + PATH + "\">All composite indices and components time series (1990–2023)</a>";

    @Test
    void discoversOnlyThePublishedCompositeFileAndRejectsMissingOrInconsistentEditions() {
        var download = UnClient.discover(HTML);
        assertThat(download.edition()).isEqualTo(2025);
        assertThat(download.uri().toString()).isEqualTo(DOWNLOAD);
        assertThat(UnClient.discover(HTML + HTML).uri()).isEqualTo(download.uri());
        assertThatThrownBy(() -> UnClient.discover("<html>No published download</html>"))
            .hasMessageContaining("not found");
        assertThatThrownBy(() -> UnClient.discover(HTML.replace("HDR25_", "HDR24_"))).hasMessageContaining("edition");
    }

    @Test
    void decodesTheOfficialWindows1252DownloadWithoutTreatingResourceDatesAsStatisticalUpdates() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DOCUMENTATION)).andRespond(withSuccess(HTML, MediaType.TEXT_HTML));
        String fixture;
        try (var input = getClass().getResourceAsStream("/un/hdr25-indices.csv")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        server.expect(requestTo(DOWNLOAD)).andRespond(withSuccess(fixture.getBytes(Charset.forName("windows-1252")), MediaType.parseMediaType("text/csv"))
            .header("Last-Modified", "Mon, 05 May 2025 22:39:12 GMT"));
        var snapshot = new UnClient(builder.build(), 1, Duration.ZERO).load();
        assertThat(snapshot.edition()).isEqualTo(2025);
        assertThat(snapshot.csv()).contains("Côte d'Ivoire").isEqualTo(fixture);
        server.verify();
    }

    @Test
    void retriesTemporaryFailuresAndDoesNotRetryPermanentAccessErrors() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DOCUMENTATION)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(DOCUMENTATION)).andRespond(withSuccess(HTML, MediaType.TEXT_HTML));
        server.expect(requestTo(DOWNLOAD)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private provider detail"));
        assertThatThrownBy(() -> new UnClient(builder.build(), 3, Duration.ZERO).load())
            .hasMessage("UNDP HTTP failure: 403");
        server.verify();
    }

    @Test
    void refusesExcessiveRetryAfterAndOversizedDownloads() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DOCUMENTATION)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "61"));
        var delayedClient = new UnClient(builder.build(), 2, Duration.ZERO);
        assertThatThrownBy(delayedClient::load).hasMessageContaining("retry later");
        server.verify();
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DOCUMENTATION)).andRespond(withSuccess("x".repeat(5_000_001), MediaType.TEXT_HTML));
        var oversizedClient = new UnClient(builder.build(), 1, Duration.ZERO);
        assertThatThrownBy(oversizedClient::load).hasMessageContaining("response size");
        server.verify();
    }
}
