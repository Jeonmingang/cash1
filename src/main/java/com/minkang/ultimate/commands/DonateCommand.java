package com.minkang.ultimate.commands;

import com.minkang.ultimate.DonateAutomationPlugin;
import com.minkang.ultimate.http.HttpClientSimple;
import com.minkang.ultimate.store.PendingStore;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DonateCommand implements CommandExecutor, TabCompleter {

    private final DonateAutomationPlugin plugin;

    public DonateCommand(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("도움말")) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("계좌") || sub.equals("계좌이체") || sub.equals("bank")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다."));
                return true;
            }
            String amountStr = (args.length >= 2 ? args[1] : "");
            handleBank((Player) sender, amountStr);
            return true;
        }
        if (sub.equals("코드") || sub.equals("문상") || sub.equals("gift") || sub.equals("code")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.color("&c사용법: /" + label + " 코드 <코드> [벤더(cultureland|happymoney|booknlife)]"));
                return true;
            }
            String code = args[1];
            String vendor = (args.length >= 3 ? args[2] : "cultureland");
            handleGiftCode((Player) sender, code, vendor);
            return true;
        }
        if (sub.equals("상태") || sub.equals("status")) {
            String orderId = (args.length >= 2 ? args[1] : "");
            handleStatus(sender, orderId);
            return true;
        }

        help(sender, label);
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(plugin.color("&6==== &e후원 안내 &6===="));
        sender.sendMessage(plugin.color("&e/" + label + " 계좌 <금액> &7: 가상계좌 발급"));
        sender.sendMessage(plugin.color("&e/" + label + " 코드 <코드> [벤더] &7: 문화상품권 등록/검사/저장"));
        sender.sendMessage(plugin.color("&e/" + label + " 상태 [주문ID] &7: 입금/코드 상태 보기"));
    }

    private void handleBank(Player player, String amountStr) {
        long min = plugin.getConfig().getInt("rewards.minAmountWon", 1000);
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (Exception e) {
            player.sendMessage(plugin.color("&c금액은 숫자로 입력하세요. 예) /후원 계좌 5000"));
            return;
        }
        if (amount < min) {
            player.sendMessage(plugin.color("&c최소 금액은 " + min + "원 입니다."));
            return;
        }

        String baseUrl = plugin.getConfig().getString("microservice.baseUrl", "http://127.0.0.1:3100");
        String createEndpoint = plugin.getConfig().getString("bank.createEndpoint", "/bank/create");
        String apiKey = plugin.getConfig().getString("microservice.apiKey", "CHANGE_ME");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("player", player.getName());
        form.put("amount", String.valueOf(amount));
        form.put("serverId", plugin.getConfig().getString("microservice.serverId", "Server"));
        form.put("mode", plugin.getConfig().getString("bank.mode", "VIRTUAL_ACCOUNT"));

        // 안전한 순차 시도 (boolean 체이닝 금지 요구 준수)
        boolean ok = baseUrl != null;
        if (!ok) {
            player.sendMessage(plugin.color("&c설정 오류: baseUrl을 확인하세요."));
            return;
        }
        ok = createEndpoint != null;
        if (!ok) {
            player.sendMessage(plugin.color("&c설정 오류: bank.createEndpoint를 확인하세요."));
            return;
        }

        player.sendMessage(plugin.color("&7가상계좌 발급을 요청했습니다. 잠시만 기다려주세요..."));

        CompletableFuture.runAsync(() -> {
            try {
                String url = baseUrl + createEndpoint;
                String resp = HttpClientSimple.postForm(url, form, apiKey, plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME"));
                // 예상 응답: key=value&key2=value2 ... (URL-encoded)
                Map<String, String> parsed = parseQueryLike(resp);
                String orderId = parsed.getOrDefault("orderId", "");
                String bank = parsed.getOrDefault("bank", "BANK");
                String account = parsed.getOrDefault("account", "000-0000-0000");
                String expires = parsed.getOrDefault("expires", "N/A");

                if (orderId.isEmpty()) {
                    player.sendMessage(plugin.color("&c가상계좌 발급 실패: " + resp));
                    return;
                }

                plugin.getPendingStore().putOrder(orderId, player.getName(), amount);
                String msg = plugin.getConfig().getString("messages.createdVA",
                        "&a가상계좌가 발급되었습니다: &e{bank} {account} &7(만료 {expires}) &7주문ID {orderId}");
                msg = msg.replace("{bank}", bank)
                        .replace("{account}", account)
                        .replace("{expires}", expires)
                        .replace("{orderId}", orderId);
                player.sendMessage(plugin.color(msg));

                String pendingMsg = plugin.getConfig().getString("messages.pending",
                        "&7입금 확인 중입니다. 입금 후 자동으로 지급됩니다. &7주문ID {orderId}");
                pendingMsg = pendingMsg.replace("{orderId}", orderId);
                player.sendMessage(plugin.color(pendingMsg));
            } catch (Exception ex) {
                player.sendMessage(plugin.color("&c가상계좌 발급 요청 중 오류: " + ex.getMessage()));
            }
        });
    }

    private void handleGiftCode(Player player, String code, String vendor) {
        String mode = plugin.getConfig().getString("gift.mode", "STORE_ONLY");
        if (mode.equalsIgnoreCase("STORE_ONLY")) {
            plugin.getPendingStore().storeGiftCode(player.getName(), vendor, code);
            String msg = plugin.getConfig().getString("messages.storedGift", "&e문화상품권 코드가 안전하게 저장되었습니다. 관리자 확인 후 지급됩니다.");
            player.sendMessage(plugin.color(msg));
            return;
        }

        String baseUrl = plugin.getConfig().getString("microservice.baseUrl", "http://127.0.0.1:3100");
        String endpoint = mode.equalsIgnoreCase("AUTO_REDEEM") ? plugin.getConfig().getString("gift.redeemEndpoint", "/gift/redeem") : plugin.getConfig().getString("gift.verifyEndpoint", "/gift/verify");
        String apiKey = plugin.getConfig().getString("microservice.apiKey", "CHANGE_ME");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("player", player.getName());
        form.put("vendor", vendor);
        form.put("code", code);
        form.put("mode", mode);

        player.sendMessage(plugin.color("&7문화상품권 코드를 확인 중입니다..."));

        CompletableFuture.runAsync(() -> {
            try {
                String url = baseUrl + endpoint;
                String resp = HttpClientSimple.postForm(url, form, apiKey, plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME"));
                Map<String, String> parsed = parseQueryLike(resp);
                String status = parsed.getOrDefault("status", "ERROR");
                String valueStr = parsed.getOrDefault("value", "0");
                long value = 0;
                try { value = Long.parseLong(valueStr); } catch (Exception ignored) {}

                if ("VALID".equalsIgnoreCase(status) || "REDEEMED".equalsIgnoreCase(status)) {
                    int wonPerCoin = plugin.getConfig().getInt("rewards.wonPerCoin", 100);
                    int coins = (int) (value / wonPerCoin);
                    String msg = plugin.getConfig().getString("messages.redeemedGift",
                            "&a문화상품권 &e{vendor}&a {value}원 확인되었습니다. &b{coins} 코인 지급!");
                    msg = msg.replace("{vendor}", vendor).replace("{value}", String.valueOf(value)).replace("{coins}", String.valueOf(coins));
                    player.sendMessage(plugin.color(msg));

                    final long vFinal = value;
                    plugin.giveRewardAsync(player.getName(), vFinal, "gift:" + vendor);
                } else if ("STORED".equalsIgnoreCase(status)) {
                    String msg = plugin.getConfig().getString("messages.storedGift", "&e문화상품권 코드가 안전하게 저장되었습니다. 관리자 확인 후 지급됩니다.");
                    player.sendMessage(plugin.color(msg));
                } else {
                    String msg = plugin.getConfig().getString("messages.invalidGift", "&c코드가 유효하지 않거나 사용되었습니다.");
                    player.sendMessage(plugin.color(msg));
                }
            } catch (Exception ex) {
                player.sendMessage(plugin.color("&c코드 확인 중 오류: " + ex.getMessage()));
            }
        });
    }

    private void handleStatus(CommandSender sender, String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            java.util.List<String> list = plugin.getPendingStore().listOrdersOf(sender.getName());
            if (list.isEmpty()) {
                sender.sendMessage(plugin.color("&7현재 보류 중인 주문이 없습니다."));
                return;
            }
            sender.sendMessage(plugin.color("&6보류 주문 목록:"));
            for (String s : list) {
                sender.sendMessage(plugin.color("&7- " + s));
            }
            return;
        }
        PendingStore.OrderData od = plugin.getPendingStore().getOrder(orderId);
        if (od == null) {
            sender.sendMessage(plugin.color("&c주문을 찾을 수 없습니다: " + orderId));
            return;
        }
        sender.sendMessage(plugin.color("&e주문ID: " + orderId + " &7플레이어: " + od.player + " 금액: " + od.amountWon + "원 상태: " + od.status));
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
                k = java.net.URLDecoder.decode(k, "UTF-8");
                v = java.net.URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {}
            map.put(k, v);
        }
        return map;
    }

    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            if (args.length == 1) {
                out.add("도움말"); out.add("계좌"); out.add("계좌이체"); out.add("코드"); out.add("문상"); out.add("상태");
            } else if (args.length == 2 && ("계좌".equals(args[0]) || "계좌이체".equals(args[0]) || "bank".equalsIgnoreCase(args[0]))) {
                out.add("5000"); out.add("10000"); out.add("20000");
            } else if (args.length == 3 && ("코드".equals(args[0]) || "문상".equals(args[0]) || "gift".equalsIgnoreCase(args[0]) || "code".equalsIgnoreCase(args[0]))) {
                out.add("cultureland"); out.add("happymoney"); out.add("booknlife");
            }
        } catch (Throwable ignored) {}
        return out;
    }
}