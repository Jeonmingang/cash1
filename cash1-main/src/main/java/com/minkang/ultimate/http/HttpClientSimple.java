package com.minkang.ultimate.http;

import com.minkang.ultimate.util.Hmac;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpClientSimple {

    public static String postForm(String urlStr, Map<String, String> form, String apiKey, String hmacSecret) throws Exception {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (!first) body.append("&");
            first = false;
            body.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            body.append("=");
            String v = e.getValue() == null ? "" : e.getValue();
            body.append(URLEncoder.encode(v, "UTF-8"));
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("X-API-KEY", apiKey);
        String signature = Hmac.hmacSha256Hex(hmacSecret, body.toString());
        conn.setRequestProperty("X-Signature", signature);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.write(bytes);
            out.flush();
        }
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
