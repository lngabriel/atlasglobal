package com.atlasglobal.integration.un;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Fetches the official published download without API credentials or arbitrary upstream URLs. */
@Component
class UnClient {
    private static final int LIMIT = 5_000_000;
    private static final Pattern DOWNLOAD = Pattern.compile("/sites/default/files/(20[0-9]{2})_HDR/HDR([0-9]{2})_Composite_indices_complete_time_series\\.csv");
    private final RestClient http;
    private final int attempts;
    private final Duration backoff;

    @Autowired UnClient() { this(create(), 3, Duration.ofSeconds(2)); }
    UnClient(RestClient http, int attempts, Duration backoff) {
        this.http = http; this.attempts = attempts; this.backoff = backoff;
    }
    record Snapshot(int edition, String csv) {}
    record Download(int edition, URI uri) {}

    Snapshot load() {
        Download download = discover(get(UnCatalog.DOCUMENTATION, StandardCharsets.UTF_8));
        return new Snapshot(download.edition(), get(download.uri(), Charset.forName("windows-1252")));
    }

    static Download discover(String html) {
        var links = new HashMap<Integer, URI>();
        var matcher = DOWNLOAD.matcher(html);
        while (matcher.find()) {
            int edition = Integer.parseInt(matcher.group(1));
            if (!matcher.group(1).endsWith(matcher.group(2)) || edition > Year.now(ZoneOffset.UTC).getValue()) {
                throw new IllegalStateException("Invalid UNDP report edition");
            }
            links.put(edition, URI.create("https://hdr.undp.org" + matcher.group() + "?download=1"));
        }
        int edition = links.keySet().stream().max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException("UNDP composite download was not found in official documentation"));
        return new Download(edition, links.get(edition));
    }

    private String get(URI uri, Charset charset) {
        for (int attempt = 1; ; attempt++) {
            try {
                byte[] bytes = http.get().uri(uri).retrieve().body(byte[].class);
                if (bytes == null || bytes.length == 0 || bytes.length > LIMIT) throw new IllegalStateException("Invalid UNDP response size");
                if (bytes.length > 1 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139) {
                    try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = gzip.readNBytes(LIMIT + 1); }
                    if (bytes.length > LIMIT) throw new IllegalStateException("Oversized UNDP response");
                }
                return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (IOException ex) { throw new IllegalStateException("Invalid UNDP response encoding", ex); }
            catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= attempts || (status != 429 && status < 500)) throw new IllegalStateException("UNDP HTTP failure: " + status, ex);
                long wait = backoff.multipliedBy(attempt).toMillis();
                String retry = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
                if (retry != null) {
                    try { wait = Math.max(wait, Math.multiplyExact(Long.parseLong(retry), 1000)); }
                    catch (RuntimeException invalidSeconds) {
                        try { wait = Math.max(wait, Duration.between(Instant.now(), ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()); }
                        catch (RuntimeException invalidDate) { /* Keep the bounded default delay. */ }
                    }
                }
                pause(wait);
            } catch (RestClientException ex) {
                if (attempt >= attempts) throw new IllegalStateException("UNDP connection or response failure", ex);
                pause(backoff.multipliedBy(attempt).toMillis());
            }
        }
    }

    private static void pause(long millis) {
        if (millis > 60_000) throw new IllegalStateException("UNDP requested a longer retry interval; retry later");
        try { Thread.sleep(Math.max(0, millis)); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("UNDP request interrupted", ex); }
    }
    private static RestClient create() {
        var request = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build());
        request.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder().requestFactory(request)
            .defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .defaultHeader("Accept", "text/html,text/csv").defaultHeader("Accept-Encoding", "identity").build();
    }
}
