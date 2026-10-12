package edu.bridge.signaling;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles PlayFab to franchise authorization token exchange for Minecraft Bedrock/Education
 * signaling session start. Extracts and validates PMID and expiration claims.
 */
public final class McSessionAuth {
    private static final Logger LOGGER = Logger.getLogger(McSessionAuth.class.getName());

    public static final String PLAYFAB_TITLE_ID = "20CA2";
    public static final String SESSION_START_URL = "https://authorization.franchise.minecraft-services.net/api/v1.0/session/start";

    private final HttpClient httpClient;
    private final String customId;

    public record SessionClaims(String authorizationHeader, String pmid, Instant expiration) {}

    public McSessionAuth() {
        this(UUID.randomUUID().toString());
    }

    public McSessionAuth(String customId) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), customId);
    }

    public McSessionAuth(HttpClient httpClient, String customId) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.customId = Objects.requireNonNull(customId);
    }

    /**
     * Executes PlayFab LoginWithCustomID and then franchise session start to obtain Authorization header.
     */
    public SessionClaims authenticate() throws IOException, InterruptedException {
        String playfabEntityToken = fetchPlayFabEntityToken(customId);
        return fetchSessionStartToken(playfabEntityToken);
    }

    private String fetchPlayFabEntityToken(String id) throws IOException, InterruptedException {
        String url = "https://" + PLAYFAB_TITLE_ID + ".playfabapi.com/Client/LoginWithCustomID";
        String body = "{\"CreateAccount\":true,\"CustomId\":\"" + escapeJson(id) + "\",\"TitleId\":\"" + PLAYFAB_TITLE_ID + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "EduBedrockProxy/2.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("PlayFab login failed with HTTP " + resp.statusCode() + ": " + resp.body());
        }

        Matcher m = Pattern.compile("\"EntityToken\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
        if (m.find()) {
            return m.group(1);
        }
        // fallback check for SessionTicket
        Matcher st = Pattern.compile("\"SessionTicket\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
        if (st.find()) {
            return st.group(1);
        }
        throw new IOException("PlayFab login succeeded but no EntityToken found in response");
    }

    private SessionClaims fetchSessionStartToken(String playfabToken) throws IOException, InterruptedException {
        String body = "{\"device\":{\"platform\":\"Windows\"},\"titleId\":\"" + PLAYFAB_TITLE_ID + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SESSION_START_URL))
                .header("Content-Type", "application/json")
                .header("X-EntityToken", playfabToken)
                .header("User-Agent", "EduBedrockProxy/2.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Franchise session/start failed with HTTP " + resp.statusCode() + ": " + resp.body());
        }

        Matcher mAuth = Pattern.compile("\"authorizationHeader\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
        String authHeader;
        if (mAuth.find()) {
            authHeader = mAuth.group(1);
        } else {
            throw new IOException("session/start response missing authorizationHeader: " + resp.body());
        }

        // Decode JWT claims to extract pmid and exp
        String jwt = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IOException("Invalid JWT format in authorizationHeader");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Matcher pmidMatcher = Pattern.compile("\"pmid\"\\s*:\\s*\"([^\"]+)\"").matcher(payloadJson);
        String pmid = pmidMatcher.find() ? pmidMatcher.group(1) : "";

        Matcher expMatcher = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)").matcher(payloadJson);
        Instant exp = expMatcher.find() ? Instant.ofEpochSecond(Long.parseLong(expMatcher.group(1))) : Instant.now().plusSeconds(3600);

        LOGGER.info(() -> "Signaling session authenticated. pmid=" + pmid + ", expires=" + exp);
        return new SessionClaims(authHeader, pmid, exp);
    }

    /**
     * Returns a Supplier that auto-refreshes the authorization header when near expiration.
     */
    public Supplier<String> cachedSupplier() {
        return new Supplier<>() {
            private volatile SessionClaims currentClaims = null;
            private final Object lock = new Object();

            @Override
            public String get() {
                Instant now = Instant.now();
                if (currentClaims == null || now.isAfter(currentClaims.expiration().minusSeconds(60))) {
                    synchronized (lock) {
                        if (currentClaims == null || now.isAfter(currentClaims.expiration().minusSeconds(60))) {
                            try {
                                currentClaims = authenticate();
                            } catch (Exception e) {
                                if (currentClaims != null && now.isBefore(currentClaims.expiration())) {
                                    LOGGER.warning("Token refresh failed (" + e.getMessage() + "), using cached token until " + currentClaims.expiration());
                                    return currentClaims.authorizationHeader();
                                }
                                throw new RuntimeException("Failed to refresh signaling session auth", e);
                            }
                        }
                    }
                }
                return currentClaims.authorizationHeader();
            }
        };
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
