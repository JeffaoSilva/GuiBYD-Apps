package com.guibyd.apps;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SupabaseApi {

    private final String baseUrl;
    private final String apiKey;

    public SupabaseApi(String baseUrl, String apiKey) {
        this.baseUrl = trimSlash(baseUrl == null ? "" : baseUrl.trim());
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty();
    }

    public JSONObject activateLicense(
            String code,
            String installationId,
            String publicKey,
            int versionCode,
            String versionName,
            String deviceLabel
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_code", code);
        body.put("p_installation_id", installationId);
        body.put("p_public_key", publicKey);
        body.put("p_app_version_code", versionCode);
        body.put("p_app_version_name", versionName);
        body.put("p_device_label", deviceLabel);
        return firstObject(rpc("activate_license", body));
    }

    public JSONObject validateLicense(
            String installationId,
            String publicKey,
            int versionCode,
            String versionName
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_installation_id", installationId);
        body.put("p_public_key", publicKey);
        body.put("p_app_version_code", versionCode);
        body.put("p_app_version_name", versionName);
        return firstObject(rpc("validate_license", body));
    }

    public JSONObject getRelease(String appKey) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_app_key", appKey);
        String raw = rpc("get_release", body);
        if (raw == null || raw.trim().isEmpty()) return null;
        String t = raw.trim();
        if (t.startsWith("[")) {
            JSONArray a = new JSONArray(t);
            return a.length() == 0 ? null : a.getJSONObject(0);
        }
        if (t.startsWith("{")) return new JSONObject(t);
        return null;
    }

    public void recordUsage(
            String installationId,
            String publicKey,
            String eventName,
            int versionCode,
            String versionName
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_installation_id", installationId);
        body.put("p_public_key", publicKey);
        body.put("p_event_name", eventName);
        body.put("p_app_version_code", versionCode);
        body.put("p_app_version_name", versionName);
        rpc("record_usage_event", body);
    }

    public String storageDownloadUrl(String filePath) {
        String clean = filePath == null ? "" : filePath.replace(" ", "%20");
        return baseUrl + "/storage/v1/object/authenticated/releases/" + clean;
    }

    public void addAuthHeaders(DownloadHeaderTarget target) {
        target.add("apikey", apiKey);
        target.add("Authorization", "Bearer " + apiKey);
    }

    private String rpc(String function, JSONObject body) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Supabase não configurado");

        URL url = new URL(baseUrl + "/rest/v1/rpc/" + function);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("apikey", apiKey);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }

        int status = c.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? c.getInputStream()
                : c.getErrorStream();
        String text = readAll(stream);
        c.disconnect();

        if (status < 200 || status >= 300) {
            String message = text;
            try {
                JSONObject err = new JSONObject(text);
                message = err.optString("message", text);
            } catch (Exception ignored) {}
            throw new IllegalStateException(message == null || message.isEmpty()
                    ? "Erro HTTP " + status
                    : message);
        }
        return text;
    }

    private static JSONObject firstObject(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        String t = raw.trim();
        if (t.startsWith("[")) {
            JSONArray a = new JSONArray(t);
            if (a.length() == 0) return new JSONObject();
            Object first = a.get(0);
            if (first instanceof JSONObject) return (JSONObject) first;
            if (first instanceof String) return new JSONObject((String) first);
        }
        if (t.startsWith("{")) return new JSONObject(t);
        return new JSONObject().put("value", t);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public interface DownloadHeaderTarget {
        void add(String name, String value);
    }
}
