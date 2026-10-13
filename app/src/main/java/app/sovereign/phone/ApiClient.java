package app.sovereign.phone;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Transport only. Schema and authentication must be confirmed with the service owner. */
final class ApiClient {
    private final String base;
    private final DeviceIdentity identity;
    ApiClient(String base, DeviceIdentity identity) {
        URI uri = URI.create(base.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Enter an HTTPS service URL without credentials, query, or fragment.");
        this.base = base.trim().replaceAll("/+$", "");
        this.identity = identity;
    }
    String request(String route, JSONObject body) throws Exception {
        HttpURLConnection connection = connect(route, body, "application/json");
        try (InputStream input = response(connection)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 1024 * 1024) throw new IOException("Response exceeds 1 MB limit.");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        } finally { connection.disconnect(); }
    }
    void chat(JSONObject body, java.util.function.Consumer<String> delta) throws Exception {
        HttpURLConnection connection = connect("/api/omega/chat", body, "text/event-stream");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response(connection), StandardCharsets.UTF_8))) {
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream"))
                throw new IOException("Expected SSE chat stream; confirm backend protocol.");
            String line;
            StringBuilder data = new StringBuilder();
            String eventType = "message";
            int total = 0;
            while ((line = reader.readLine()) != null) {
                total += line.length();
                if (total > 1024 * 1024) throw new IOException("Chat stream exceeds limit.");
                if (line.isEmpty() && data.length() > 0) {
                    String event = data.toString(); data.setLength(0);
                    JSONObject chunk = new JSONObject(event);
                    if ("done".equals(eventType)) {
                        chunk.getString("message_id");
                        if (!"stop".equals(chunk.getString("finish_reason"))) throw new IOException("Chat stopped before normal completion.");
                        return;
                    }
                    if (!"token".equals(eventType)) throw new IOException("Unsupported chat event type.");
                    if (chunk.has("error")) throw new IOException("Chat service reported an error.");
                    delta.accept(chunk.getString("delta"));
                    eventType = "message";
                } else if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(5).replaceFirst("^ ", ""));
                }
            }
            throw new IOException("Chat stream ended without a done event.");
        } finally { connection.disconnect(); }
    }
    private HttpURLConnection connect(String route, JSONObject body, String accept) throws Exception {
        JSONObject challenge = null;
        if (!route.equals("/api/challenge") && !route.equals("/api/pair")) {
            if (identity == null) throw new IllegalStateException("Device identity unavailable.");
            challenge = new JSONObject(request("/api/challenge", null));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(base + route).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("Accept", accept);
        if (challenge != null) {
            c.setRequestProperty("X-Device-Fingerprint", identity.fingerprint);
            c.setRequestProperty("X-Challenge", challenge.getString("challenge"));
            c.setRequestProperty("X-Signature", identity.sign(challenge.getString("challenge")));
        }
        if (body != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = c.getOutputStream()) { output.write(bytes); }
        }
        return c;
    }
    private InputStream response(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("Service returned HTTP " + code + ". Operation was not confirmed.");
        return c.getInputStream();
    }
}
