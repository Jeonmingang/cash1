package com.minkang.ultimate.commands;

import com.minkang.ultimate.DonateAutomationPlugin;
import com.minkang.ultimate.http.HttpClientSimple;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DonateCommand implements CommandExecutor, TabCompleter {

    private final DonateAutomationPlugin plugin;

    public DonateCommand(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "도움말".equalsIgnoreCase(args[0]) || "help".equalsIgnoreCase(args[0])) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // 계좌: 동의 플로우
        if (sub.equals("계좌") || sub.equals("계좌이체") || sub.equals("bank")) {
            if (!(sender instanceof Player)) { sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(plugin.color("&c사용법: /" + label + " 계좌 <금액>")); return true; }
            long amount;
            try { amount = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage(plugin.color("&c금액은 숫자로 입력하세요.")); return true; }
            long min = plugin.getConfig().getInt("rewards.minAmountWon", 1000);
            if (amount < min) { sender.sendMessage(plugin.color("&c최소 금액은 " + min + "원 입니다.")); return true; }

            if (plugin.getConfig().getBoolean("legal.enabled", true) && plugin.getConfig().getBoolean("legal.requireConsentFor.bank", true)) {
                showLegalSummaryForBank((Player) sender, amount);
                return true;
            }
            handleBank((Player) sender, String.valueOf(amount));
            return true;
        }

        // 동의 (계좌용)
        if (sub.equals("동의")) {
            if (!(sender instanceof Player)) { sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(plugin.color("&c사용법: /" + label + " 동의 <금액>")); return true; }
            String msgAccepted = plugin.getConfig().getString("messages.legalAccepted", "&a동의가 확인되었습니다. 주문을 생성합니다...");
            sender.sendMessage(plugin.color(msgAccepted));
            handleBank((Player) sender, args[1]);
            return true;
        }

        // 문화상품권
        if (sub.equals("코드") || sub.equals("문상") || sub.equals("gift") || sub.equals("code")) {
            if (!(sender instanceof Player)) { sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다.")); return true; }
            if (args.length < 2) {
                sender.sendMessage(plugin.color("&c사용법: /" + label + " 코드 <코드> [벤더(cultureland|happymoney|booknlife)]"));
                return true;
            }
            String code = args[1];
            String vendor = (args.length >= 3 ? args[2] : "cultureland");
            if (plugin.getConfig().getBoolean("legal.enabled", true) && plugin.getConfig().getBoolean("legal.requireConsentFor.gift", false)) {
                showLegalSummaryForGift((Player) sender, code, vendor);
                return true;
            }
            handleGiftCode((Player) sender, code, vendor);
            return true;
        }

        // 동의코드 (문화상품권용)
        if (sub.equals("동의코드")) {
            if (!(sender instanceof Player)) { sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(plugin.color("&c사용법: /" + label + " 동의코드 <코드> [벤더]")); return true; }
            String code = args[1];
            String vendor = (args.length >= 3 ? args[2] : "cultureland");
            String msgAccepted = plugin.getConfig().getString("messages.legalAccepted", "&a동의가 확인되었습니다. 계속 진행합니다...");
            sender.sendMessage(plugin.color(msgAccepted));
            handleGiftCode((Player) sender, code, vendor);
            return true;
        }

        // 상태
        if (sub.equals("상태") || sub.equals("status")) {
            if (!(sender instanceof Player)) { sender.sendMessage(plugin.color("&c플레이어만 사용할 수 있습니다.")); return true; }
            List<String> list = plugin.getPendingStore().listOrdersOf(((Player) sender).getName());
            if (list.isEmpty()) {
                sender.sendMessage(plugin.color("&7보류 중인 주문이 없습니다."));
            } else {
                sender.sendMessage(plugin.color("&e보류 주문ID: &f" + String.join(", ", list)));
            }
            return true;
        }

        sender.sendMessage(plugin.color("&c알 수 없는 서브커맨드."));
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(plugin.color("&6/" + label + " 도움말 &7- 이 도움말을 표시합니다."));
        sender.sendMessage(plugin.color("&6/" + label + " 계좌 <금액> &7- 입금 계좌/가상계좌 안내"));
        sender.sendMessage(plugin.color("&6/" + label + " 코드 <핀> [벤더] &7- 문화상품권 코드 등록/검증"));
        sender.sendMessage(plugin.color("&6/" + label + " 상태 &7- 보류 주문ID 확인"));
    }

    private void showLegalSummaryForBank(Player player, long amount) {
        if (!plugin.getConfig().getBoolean("legal.enabled", true)) return;
        List<String> lines = plugin.getConfig().getStringList("legal.summaryLines");
        String header = plugin.getConfig().getString("messages.legalHeader", "&c&l[중요 안내]&r");
        player.sendMessage(plugin.color(header));
        // 동적 배수/환산 안내
        double mb = plugin.getConfig().getDouble("rewards.multipliers.bank", 1.0);
        double mg = plugin.getConfig().getDouble("rewards.multipliers.gift", 1.0);
        int wpc = plugin.getConfig().getInt("rewards.wonPerCash", 100);
        player.sendMessage(plugin.color("&e현재 배수: &f계좌 " + mb + "배 &7/ 문상 " + mg + "배 &7| 환산: &f" + wpc + "원=캐시1"));
        for (String ln : lines) player.sendMessage(plugin.color("&7- " + ln));
        String under = plugin.getConfig().getString("legal.underageNotice", "");
        if (under != null && !under.isEmpty()) player.sendMessage(plugin.color("&6" + under));
        String contactEmail = plugin.getConfig().getString("legal.contact.email", "");
        String contactDiscord = plugin.getConfig().getString("legal.contact.discord", "");
        String sla = plugin.getConfig().getString("legal.contact.sla", "");
        String ref = plugin.getConfig().getString("legal.reference", "");
        if (!contactEmail.isEmpty() || !contactDiscord.isEmpty() || !sla.isEmpty()) {
            player.sendMessage(plugin.color("&7문의: &f" + contactEmail + " &7/ " + contactDiscord + " &7/ " + sla));
        }
        if (!ref.isEmpty()) player.sendMessage(plugin.color("&7자세히: &f" + ref));
        String prompt = plugin.getConfig().getString("messages.legalPromptBank", "&e'/후원 동의 {amount}' 를 입력하면 계속 진행됩니다.");
        prompt = prompt.replace("{amount}", String.valueOf(amount));
        player.sendMessage(plugin.color(prompt));
    }

    private void showLegalSummaryForGift(Player player, String code, String vendor) {
        if (!plugin.getConfig().getBoolean("legal.enabled", true)) return;
        List<String> lines = plugin.getConfig().getStringList("legal.summaryLines");
        String header = plugin.getConfig().getString("messages.legalHeader", "&c&l[중요 안내]&r");
        player.sendMessage(plugin.color(header));
        double mb = plugin.getConfig().getDouble("rewards.multipliers.bank", 1.0);
        double mg = plugin.getConfig().getDouble("rewards.multipliers.gift", 1.0);
        int wpc = plugin.getConfig().getInt("rewards.wonPerCash", 100);
        player.sendMessage(plugin.color("&e현재 배수: &f계좌 " + mb + "배 &7/ 문상 " + mg + "배 &7| 환산: &f" + wpc + "원=캐시1"));
        for (String ln : lines) player.sendMessage(plugin.color("&7- " + ln));
        String under = plugin.getConfig().getString("legal.underageNotice", "");
        if (under != null && !under.isEmpty()) player.sendMessage(plugin.color("&6" + under));
        String contactEmail = plugin.getConfig().getString("legal.contact.email", "");
        String contactDiscord = plugin.getConfig().getString("legal.contact.discord", "");
        String sla = plugin.getConfig().getString("legal.contact.sla", "");
        String ref = plugin.getConfig().getString("legal.reference", "");
        if (!contactEmail.isEmpty() || !contactDiscord.isEmpty() || !sla.isEmpty()) {
            player.sendMessage(plugin.color("&7문의: &f" + contactEmail + " &7/ " + contactDiscord + " &7/ " + sla));
        }
        if (!ref.isEmpty()) player.sendMessage(plugin.color("&7자세히: &f" + ref));
        String prompt = plugin.getConfig().getString("messages.legalPromptGift", "&e'/후원 동의코드 {code} {vendor}' 를 입력하면 계속 진행됩니다.");
        prompt = prompt.replace("{code}", code).replace("{vendor}", vendor);
        player.sendMessage(plugin.color(prompt));
    }

    private void handleBank(Player player, String amountStr) {
        long min = plugin.getConfig().getInt("rewards.minAmountWon", 1000);
        long amount;
        try { amount = Long.parseLong(amountStr); } catch (Exception e) {
            player.sendMessage(plugin.color("&c금액은 숫자로 입력하세요. 예) /후원 계좌 5000")); return;
        }
        if (amount < min) { player.sendMessage(plugin.color("&c최소 금액은 " + min + "원 입니다.")); return; }

        String baseUrl = plugin.getConfig().getString("microservice.baseUrl", "http://127.0.0.1:3100");
        String createEndpoint = plugin.getConfig().getString("bank.createEndpoint", "/bank/create");
        String apiKey = plugin.getConfig().getString("microservice.apiKey", "CHANGE_ME");
        String hmac = plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME");
        String serverId = plugin.getConfig().getString("microservice.serverId", "Server");

        Map<String,String> form = new LinkedHashMap<String, String>();
        form.put("player", player.getName());
        form.put("amount", String.valueOf(amount));
        form.put("serverId", serverId);
        form.put("mode", plugin.getConfig().getString("bank.mode","VIRTUAL_ACCOUNT"));

        if (plugin.getConfig().getBoolean("legal.enabled", true)) {
            form.put("consent", "true");
            form.put("consentAt", String.valueOf(System.currentTimeMillis()));
            form.put("policyVersion", plugin.getConfig().getString("legal.policyVersion", ""));
            form.put("consentText", buildConsentText());
            try { form.put("playerUUID", player.getUniqueId().toString()); } catch (Throwable ignored) {}
            try { java.net.InetSocketAddress a = player.getAddress(); if (a!=null && a.getAddress()!=null) form.put("playerIP", a.getAddress().getHostAddress()); } catch (Throwable ignored) {}
        }

        final long amountWon = amount;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    String resp = HttpClientSimple.postForm(baseUrl + createEndpoint, form, apiKey, hmac);
                    if (resp == null) throw new Exception("empty response");
                    if (resp.startsWith("error=")) { player.sendMessage(plugin.color("&c요청 실패: " + resp)); return; }
                    Map<String,String> map = parseQueryString(resp);
                    String orderId = safe(map.get("orderId"));
                    String bank = safe(map.get("bank"));
                    String account = safe(map.get("account"));
                    String expires = safe(map.get("expires"));
                    if (orderId.isEmpty() || bank.isEmpty() || account.isEmpty()) {
                        player.sendMessage(plugin.color("&c요청 실패: 응답 형식 오류")); return;
                    }
                    plugin.getPendingStore().putOrder(orderId, player.getName(), amountWon);
                    try {
                        List<String> lines = plugin.getConfig().getStringList("legal.summaryLines");
                        String under = plugin.getConfig().getString("legal.underageNotice", "");
                        String email = plugin.getConfig().getString("legal.contact.email", "");
                        String discord = plugin.getConfig().getString("legal.contact.discord", "");
                        String sla = plugin.getConfig().getString("legal.contact.sla", "");
                        String ref = plugin.getConfig().getString("legal.reference", "");
                        String policyV = plugin.getConfig().getString("legal.policyVersion", "");
                        String uuid = player.getUniqueId().toString();
                        String ip=""; try { java.net.InetSocketAddress a = player.getAddress(); if (a!=null && a.getAddress()!=null) ip=a.getAddress().getHostAddress(); } catch (Throwable ignored) {}
                        plugin.getPendingStore().recordConsent(orderId, player.getName(), uuid, ip, "bank", amountWon, policyV, lines, under, email, discord, sla, ref, System.currentTimeMillis());
                    } catch (Throwable ignored) {}

                    String createdMsg = plugin.getConfig().getString("messages.createdVA", "&a입금 계좌: &e{bank} {account} &7(주문ID {orderId})");
                    createdMsg = createdMsg.replace("{bank}", bank).replace("{account}", account).replace("{expires}", expires).replace("{orderId}", orderId);
                    String pendingMsg = plugin.getConfig().getString("messages.pending", "&7입금 확인 후 지급됩니다. 이체 메모에 &e{orderId}&7 를 꼭 넣어주세요.").replace("{orderId}", orderId);
                    player.sendMessage(plugin.color(createdMsg));
                    player.sendMessage(plugin.color(pendingMsg));
                } catch (Exception ex) {
                    player.sendMessage(plugin.color("&c요청 오류: " + ex.getMessage()));
                }
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
        String hmac = plugin.getConfig().getString("security.hmacSecret", "CHANGE_ME");

        Map<String,String> form = new LinkedHashMap<String, String>();
        form.put("player", player.getName());
        form.put("code", code);
        form.put("vendor", vendor);
        if (plugin.getConfig().getBoolean("legal.enabled", true)) {
            form.put("consent", "true");
            form.put("consentAt", String.valueOf(System.currentTimeMillis()));
            form.put("policyVersion", plugin.getConfig().getString("legal.policyVersion", ""));
            form.put("consentText", buildConsentText());
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    String resp = HttpClientSimple.postForm(baseUrl + endpoint, form, apiKey, hmac);
                    if (resp == null) throw new Exception("empty response");
                    if (resp.startsWith("error=")) { player.sendMessage(plugin.color("&c요청 실패: " + resp)); return; }
                    Map<String,String> map = parseQueryString(resp);
                    String status = safe(map.get("status"));
                    String valueStr = safe(map.get("value"));
                    if ("OK".equalsIgnoreCase(status) || "REDEEMED".equalsIgnoreCase(status) || "VALID".equalsIgnoreCase(status)) {
                        long value = 0L; try { value = Long.parseLong(valueStr); } catch (Exception ignored) {}
                        final long vFinal = value;
                        plugin.giveRewardAsync(player.getName(), vFinal, "gift:" + vendor);
                        String msg = plugin.getConfig().getString("messages.redeemedGift", "&a문화상품권 &e{vendor}&a {value}원 확인되었습니다.");
                        msg = msg.replace("{vendor}", vendor).replace("{value}", String.valueOf(vFinal));
                        player.sendMessage(plugin.color(msg));
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
            }
        });
    }

    private Map<String,String> parseQueryString(String qs) {
        Map<String,String> map = new HashMap<String, String>();
        String[] parts = qs.split("&");
        for (String p : parts) {
            int i = p.indexOf('=');
            if (i <= 0) continue;
            String k = p.substring(0, i);
            String v = p.substring(i+1);
            try { v = URLDecoder.decode(v, "UTF-8"); } catch (Exception ignored) {}
            map.put(k, v);
        }
        return map;
    }

    private String buildConsentText() {
        List<String> lines = plugin.getConfig().getStringList("legal.summaryLines");
        String under = plugin.getConfig().getString("legal.underageNotice", "");
        String ref = plugin.getConfig().getString("legal.reference", "");
        String contactEmail = plugin.getConfig().getString("legal.contact.email", "");
        String contactDiscord = plugin.getConfig().getString("legal.contact.discord", "");
        String sla = plugin.getConfig().getString("legal.contact.sla", "");

        StringBuilder sb = new StringBuilder();
        for (String ln : lines) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(ln);
        }
        if (!under.isEmpty()) { if (sb.length()>0) sb.append(" | "); sb.append(under); }
        if (!ref.isEmpty()) { if (sb.length()>0) sb.append(" | "); sb.append(ref); }
        if (!contactEmail.isEmpty() || !contactDiscord.isEmpty() || !sla.isEmpty()) {
            if (sb.length()>0) sb.append(" | ");
            sb.append("문의: ").append(contactEmail).append(" / ").append(contactDiscord).append(" / ").append(sla);
        }
        return sb.toString();
    }

    private String safe(String s) { return s==null ? "" : s; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            out.add("도움말"); out.add("계좌"); out.add("계좌이체"); out.add("코드"); out.add("문상"); out.add("상태");
        } else if (args.length == 2 && ("계좌".equals(args[0]) || "계좌이체".equals(args[0]) || "bank".equalsIgnoreCase(args[0]))) {
            out.add("5000"); out.add("10000"); out.add("20000");
        } else if (args.length == 3 && ("코드".equals(args[0]) || "문상".equals(args[0]) || "gift".equalsIgnoreCase(args[0]) || "code".equalsIgnoreCase(args[0]))) {
            out.add("cultureland"); out.add("happymoney"); out.add("booknlife");
        }
        return out;
    }
}
