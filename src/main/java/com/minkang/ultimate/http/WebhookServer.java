package com.minkang.ultimate.http;

import com.minkang.ultimate.DonateAutomationPlugin;
import com.minkang.ultimate.util.Hmac;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class WebhookServer {

    private final DonateAutomationPlugin plugin;
    private HttpServer server;

    public WebhookServer(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        boolean enabled = plugin.getConfig().getBoolean("webhook.enabled", true);
        if (!enabled) return;

        String bind = plugin.getConfig().getString("webhook.bind", "127.0.0.1");
        int port = plugin.getConfig().getInt("webhook.port", 27111);
        String pathDeposit = plugin.getConfig().getString("webhook.pathDeposit", "/webhook/deposit");
        String pathGift = plugin.getConfig().getString("webhook.pathGift", "/webhook/gift");

        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.server.createContext(pathDeposit, new DepositHandler());
        this.server.createContext(pathGift, new GiftHandler());
        this.server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        this.server.start();
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    private byte[] readBody(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
    return baos.toByteArray();
}

class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, 405, "method_not_allowed");
                return;
            }
            String secret = plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME");
            String body = new String(readBody(exchange.getRequestBody()), StandardCharsets.UTF_8);
            String sign = exchange.getRequestHeaders().getFirst("X-Signature");
            if (sign == null) {
                write(exchange, 401, "no_signature");
                return;
            }
            String calc = Hmac.hmacSha256Hex(secret, body);
            if (!sign.equalsIgnoreCase(calc)) {
                write(exchange, 401, "bad_signature");
                return;
            }
            Map<String, String> form = parseQueryLike(body);
            String event = form.getOrDefault("event", "");
            String orderId = form.getOrDefault("orderId", "");
            String player = form.getOrDefault("player", "");
            String amountStr = form.getOrDefault("amount", "0");
            long amount = 0L;
            try { amount = Long.parseLong(amountStr); } catch (Exception ignored) {}

            if (!"deposit.paid".equalsIgnoreCase(event)) {
                write(exchange, 400, "bad_event");
                return;
            }
            if (orderId.isEmpty() || player.isEmpty() || amount <= 0) {
                write(exchange, 400, "bad_fields");
                return;
            }

            final long amountFinal = amount;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPendingStore().markPaid(orderId, player, amountFinal);
                plugin.giveRewardNow(player, amountFinal, "bank:" + orderId);
            });
            write(exchange, 200, "ok");
        }
    }

    class GiftHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, 405, "method_not_allowed");
                return;
            }
            String secret = plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME");
            String body = new String(readBody(exchange.getRequestBody()), StandardCharsets.UTF_8);
            String sign = exchange.getRequestHeaders().getFirst("X-Signature");
            if (sign == null) {
                write(exchange, 401, "no_signature");
                return;
            }
            String calc = Hmac.hmacSha256Hex(secret, body);
            if (!sign.equalsIgnoreCase(calc)) {
                write(exchange, 401, "bad_signature");
                return;
            }
            Map<String, String> form = parseQueryLike(body);
            String event = form.getOrDefault("event", "");
            String vendor = form.getOrDefault("vendor", "cultureland");
            String player = form.getOrDefault("player", "");
            String valueStr = form.getOrDefault("value", "0");
            long value = 0L;
            try { value = Long.parseLong(valueStr); } catch (Exception ignored) {}

            if (!"gift.redeemed".equalsIgnoreCase(event) && !"gift.valid".equalsIgnoreCase(event)) {
                write(exchange, 400, "bad_event");
                return;
            }
            if (player.isEmpty() || value <= 0) {
                write(exchange, 400, "bad_fields");
                return;
            }

            final long amountFinal = value;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.giveRewardNow(player, amountFinal, "gift:" + vendor);
            });
            write(exchange, 200, "ok");
        }
    }

    private void write(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryLike(String s) {
        Map<String, String> map = new LinkedHashMap<>();
        if (s == null) return map;
        String[] parts = s.split("&");
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String[] kv = p.split("=", 2);
            String k = kv[0];
            String v = kv.length >= 2 ? kv[1] : "";
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {}
            map.put(k, v);
        }
        return map;
    }
}
