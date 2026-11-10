package com.atlasglobal.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

/** Shared transport limits and retries; provider-specific schemas stay in their adapters. */
public class RateHttp {
    private static final int LIMIT = 20_000_000;
    private final RestClient http;
    private final int attempts;
    private final Duration backoff;
    private final ObjectMapper mapper;

    public RateHttp(RestClient http, ObjectMapper mapper, int attempts, Duration backoff) {
        this.http = http; this.mapper = mapper; this.attempts = attempts; this.backoff = backoff;
    }

    public static RateHttp create(ObjectMapper mapper, String accept) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RateHttp(RestClient.builder().requestFactory(factory).defaultHeader("Accept", accept)
            .defaultHeader("Accept-Encoding", "identity").defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .build(), mapper, 3, Duration.ofSeconds(2));
    }

    public JsonNode get(URI uri) {
        for (int attempt = 1; ; attempt++) {
            try {
                byte[] body = http.get().uri(uri).retrieve().body(byte[].class);
                if (body == null || body.length == 0 || body.length > LIMIT) throw new IllegalStateException("Invalid rate-provider response size");
                if (body.length > 1 && (body[0] & 255) == 31 && (body[1] & 255) == 139) {
                    try (var input = new GZIPInputStream(new ByteArrayInputStream(body))) { body = input.readNBytes(LIMIT + 1); }
                    if (body.length > LIMIT) throw new IllegalStateException("Oversized rate-provider response");
                }
                return mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
            } catch (IOException ex) { throw new IllegalStateException("Invalid rate-provider JSON", ex); }
            catch (RestClientResponseException ex) {
                if (attempt >= attempts || (ex.getStatusCode().value() != 429 && ex.getStatusCode().value() < 500)) {
                    throw new IllegalStateException("Rate-provider HTTP failure: " + ex.getStatusCode().value(), ex);
                }
                long delay = backoff.multipliedBy(attempt).toMillis();
                String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
                if (header != null) {
                    try { delay = Math.max(delay, Math.multiplyExact(Long.parseLong(header), 1000)); }
                    catch (RuntimeException invalidSeconds) {
                        try { delay = Math.max(delay, Duration.between(Instant.now(), ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()); }
                        catch (RuntimeException invalidDate) { /* Keep bounded default backoff. */ }
                    }
                }
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt >= attempts) throw new IllegalStateException("Rate-provider connection or timeout failure", ex);
                pause(backoff.multipliedBy(attempt).toMillis());
            } catch (RestClientException ex) {
                if (attempt >= attempts) throw new IllegalStateException("Rate-provider response body could not be read", ex);
                pause(backoff.multipliedBy(attempt).toMillis());
            }
        }
    }

    private static void pause(long delay) {
        if (delay > 60_000) throw new IllegalStateException("Rate provider requested a longer retry interval; retry later");
        try { Thread.sleep(Math.max(0, delay)); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Rate request interrupted", ex); }
    }
}
