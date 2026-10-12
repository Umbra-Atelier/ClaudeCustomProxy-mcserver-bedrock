package edu.bridge.signaling;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles interactive Microsoft Entra ID / Microsoft 365 Device Code flow login.
 * Caches refresh tokens locally in gitignored .auth_cache.json so the user only logs in once.
 */
public final class MicrosoftDeviceAuth {
    private static final Logger LOGGER = Logger.getLogger(MicrosoftDeviceAuth.class.getName());

    public static final String DEFAULT_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46"; // Standard public client
    public static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/organizations/oauth2/v2.0/devicecode";
    public static final String TOKEN_URL = "https://login.microsoftonline.com/organizations/oauth2/v2.0/token";
    public static final String CACHE_FILE_NAME = ".auth_cache.json";

    private final HttpClient httpClient;
    private final String clientId;
    private final File cacheFile;

    public record AuthResult(String accessToken, String refreshToken, String pmid, Instant expiration) {}

    public MicrosoftDeviceAuth() {
        this(DEFAULT_CLIENT_ID, new File(CACHE_FILE_NAME));
    }

    public MicrosoftDeviceAuth(String clientId, File cacheFile) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.clientId = (clientId != null && !clientId.isBlank()) ? clientId : DEFAULT_CLIENT_ID;
        this.cacheFile = Objects.requireNonNull(cacheFile);
    }

    /**
     * Authenticates the user, checking the local cache first or prompting via device-code flow.
     */
    public AuthResult loginOrRefresh() throws IOException, InterruptedException {
        // 1. Try local cache
        if (cacheFile.exists()) {
            try {
                String cachedJson = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
                String refreshToken = extractJsonField(cachedJson, "refresh_token");
                if (!refreshToken.isBlank()) {
                    LOGGER.info("Found cached login in " + cacheFile.getName() + ". Checking session...");
                    AuthResult refreshed = refreshAccessToken(refreshToken);
                    if (refreshed != null && isMultiplayerToken(refreshed.accessToken())) {
                        saveCache(refreshed);
                        LOGGER.info("Session successfully refreshed with Minecraft multiplayer credentials.");
                        return refreshed;
                    } else {
                        LOGGER.info("Cached token was not for Minecraft multiplayer audience. Requesting new device code...");
                        cacheFile.delete();
                    }
                }
            } catch (Exception ex) {
                LOGGER.fine("Cached session expired or invalid: " + ex.getMessage());
            }
        }

        // 2. Perform interactive device code flow
        return performDeviceCodeFlow();
    }

    public static final String MINECRAFT_MULTIPLAYER_SCOPE = "api://auth-minecraft-services/multiplayer/.default%20offline_access";

    private AuthResult performDeviceCodeFlow() throws IOException, InterruptedException {
        String body = "client_id=" + clientId + "&scope=" + MINECRAFT_MULTIPLAYER_SCOPE;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(DEVICE_CODE_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("Failed to initiate Microsoft device code flow (HTTP " + resp.statusCode() + "): " + resp.body());
        }

        String json = resp.body();
        String userCode = extractJsonField(json, "user_code");
        String deviceCode = extractJsonField(json, "device_code");
        String verificationUri = extractJsonField(json, "verification_uri");
        if (verificationUri.isBlank()) verificationUri = "https://microsoft.com/devicelogin";

        int interval = 5;
        try {
            Matcher mInt = Pattern.compile("\"interval\"\\s*:\\s*(\\d+)").matcher(json);
            if (mInt.find()) interval = Integer.parseInt(mInt.group(1));
        } catch (Exception ignored) {}

        int expiresIn = 900;
        try {
            Matcher mExp = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)").matcher(json);
            if (mExp.find()) expiresIn = Integer.parseInt(mExp.group(1));
        } catch (Exception ignored) {}

        // Print prominent banner to console
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("                   MICROSOFT 365 SIGN-IN REQUIRED");
        System.out.println("================================================================================");
        System.out.println(" 1. Open your browser and go to: " + verificationUri);
        System.out.println(" 2. Enter this code:             " + userCode);
        System.out.println(" 3. Sign in with your school Microsoft 365 Education account.");
        System.out.println("================================================================================");
        System.out.println("Waiting for approval in browser...");
        System.out.println();

        Instant deadline = Instant.now().plusSeconds(expiresIn);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(interval * 1000L);

            String pollBody = "client_id=" + clientId
                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&device_code=" + deviceCode;

            HttpRequest pollReq = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(pollBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> pollResp = httpClient.send(pollReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String pollJson = pollResp.body();

            if (pollResp.statusCode() == 200) {
                System.out.println(">> Microsoft authentication successful!");
                String accessToken = extractJsonField(pollJson, "access_token");
                String refreshToken = extractJsonField(pollJson, "refresh_token");

                int tokenExpiresIn = 3600;
                Matcher mTokenExp = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)").matcher(pollJson);
                if (mTokenExp.find()) {
                    tokenExpiresIn = Integer.parseInt(mTokenExp.group(1));
                }
                Instant exp = Instant.now().plusSeconds(tokenExpiresIn);

                // Extract or synthesize pmid from token claims
                String pmid = extractPmidFromJwt(accessToken);
                AuthResult result = new AuthResult(accessToken, refreshToken, pmid, exp);
                saveCache(result);
                return result;
            }

            if (pollJson.contains("authorization_pending")) {
                // Still waiting for student/teacher to sign in
                continue;
            }

            if (pollJson.contains("slow_down")) {
                interval += 5;
                continue;
            }

            if (pollJson.contains("code_expired") || pollJson.contains("bad_verification_code")) {
                throw new IOException("Device code expired or cancelled. Please re-run the proxy.");
            }

            throw new IOException("Authentication error from Microsoft: " + pollJson);
        }

        throw new IOException("Device code timed out. Please re-run the proxy.");
    }

    private AuthResult refreshAccessToken(String refreshToken) {
        try {
            String body = "client_id=" + clientId
                    + "&grant_type=refresh_token"
                    + "&refresh_token=" + refreshToken
                    + "&scope=" + MINECRAFT_MULTIPLAYER_SCOPE;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String json = resp.body();
                String accessToken = extractJsonField(json, "access_token");
                String newRefresh = extractJsonField(json, "refresh_token");
                if (newRefresh.isBlank()) newRefresh = refreshToken;

                int expiresIn = 3600;
                Matcher m = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)").matcher(json);
                if (m.find()) expiresIn = Integer.parseInt(m.group(1));

                String pmid = extractPmidFromJwt(accessToken);
                return new AuthResult(accessToken, newRefresh, pmid, Instant.now().plusSeconds(expiresIn));
            }
        } catch (Exception ex) {
            LOGGER.fine("Failed to refresh token: " + ex.getMessage());
        }
        return null;
    }

    private static boolean isMultiplayerToken(String jwt) {
        if (jwt == null || jwt.isBlank()) return false;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return payload.contains("api://auth-minecraft-services/multiplayer") || payload.contains("minecraft");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void saveCache(AuthResult result) {
        try {
            String cacheJson = "{\n"
                    + "  \"access_token\": \"" + escapeJson(result.accessToken()) + "\",\n"
                    + "  \"refresh_token\": \"" + escapeJson(result.refreshToken()) + "\",\n"
                    + "  \"pmid\": \"" + escapeJson(result.pmid()) + "\",\n"
                    + "  \"expires_at\": \"" + result.expiration().toString() + "\"\n"
                    + "}\n";
            Files.writeString(cacheFile.toPath(), cacheJson, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warning("Could not write auth cache: " + e.getMessage());
        }
    }

    private static String extractPmidFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                // Look for pmid or oid (object ID)
                Matcher mPmid = Pattern.compile("\"pmid\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
                if (mPmid.find()) return mPmid.group(1);

                Matcher mOid = Pattern.compile("\"oid\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
                if (mOid.find()) return mOid.group(1);

                Matcher mSub = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
                if (mSub.find()) return mSub.group(1);
            }
        } catch (Exception ignored) {}
        return java.util.UUID.randomUUID().toString();
    }

    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
