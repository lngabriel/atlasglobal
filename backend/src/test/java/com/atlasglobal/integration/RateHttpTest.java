package com.atlasglobal.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RateHttpTest {
    private static final URI URI = java.net.URI.create("https://example.test/data");
    @Test
    void rejectsPermanentErrorsWithoutRetryingOrExposingProviderPayloads() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private provider detail"));
        assertThatThrownBy(() -> new RateHttp(builder.build(), new ObjectMapper(), 3, Duration.ZERO).get(URI))
            .hasMessage("Rate-provider HTTP failure: 403");
        server.verify();
    }
    @Test
    void rejectsAmbiguousDuplicateJsonKeys() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"rate\":1,\"rate\":2}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new RateHttp(builder.build(), new ObjectMapper(), 2, Duration.ZERO).get(URI))
            .hasMessage("Invalid rate-provider JSON");
        server.verify();
    }
    @Test
    void refusesExcessiveRetryAfterInsteadOfHoldingAnImportIndefinitely() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "61"));
        assertThatThrownBy(() -> new RateHttp(builder.build(), new ObjectMapper(), 2, Duration.ZERO).get(URI))
            .hasMessageContaining("retry later");
        server.verify();
    }
}
