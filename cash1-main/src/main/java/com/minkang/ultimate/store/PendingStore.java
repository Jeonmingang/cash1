package com.minkang.ultimate.store;

import com.minkang.ultimate.DonateAutomationPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PendingStore {

    public static class OrderData {
        public String orderId;
        public String player;
        public long amountWon;
        public String status;

        public OrderData(String orderId, String player, long amountWon, String status) {
            this.orderId = orderId;
            this.player = player;
            this.amountWon = amountWon;
            this.status = status;
        }
    }

    public static class GiftData {
        public String vendor;
        public String player;
        public String code;
        public String status;

        public GiftData(String vendor, String player, String code, String status) {
            this.vendor = vendor;
            this.player = player;
            this.code = code;
            this.status = status;
        }
    }

    private final DonateAutomationPlugin plugin;
    private final File file;
    private FileConfiguration yaml;

    public PendingStore(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (this.yaml == null) return;
        try {
            this.yaml.save(file);
        } catch (IOException ignored) {}
    }

    public void putOrder(String orderId, String player, long amountWon) {
        if (this.yaml == null) load();
        String path = "orders." + orderId;
        this.yaml.set(path + ".player", player);
        this.yaml.set(path + ".amount", amountWon);
        this.yaml.set(path + ".status", "PENDING");
        save();
    }

    public OrderData getOrder(String orderId) {
        if (this.yaml == null) load();
        String path = "orders." + orderId;
        if (!this.yaml.contains(path)) return null;
        String player = this.yaml.getString(path + ".player", "");
        long amount = this.yaml.getLong(path + ".amount", 0L);
        String status = this.yaml.getString(path + ".status", "PENDING");
        return new OrderData(orderId, player, amount, status);
    }

    public void markPaid(String orderId, String player, long amountWon) {
        if (this.yaml == null) load();
        String path = "orders." + orderId;
        this.yaml.set(path + ".player", player);
        this.yaml.set(path + ".amount", amountWon);
        this.yaml.set(path + ".status", "PAID");
        save();
    }

    public java.util.List<String> listOrdersOf(String player) {
        if (this.yaml == null) load();
        List<String> list = new ArrayList<>();
        if (!this.yaml.contains("orders")) return list;
        for (String k : this.yaml.getConfigurationSection("orders").getKeys(false)) {
            String p = this.yaml.getString("orders." + k + ".player", "");
            String st = this.yaml.getString("orders." + k + ".status", "");
            if (p.equalsIgnoreCase(player) && "PENDING".equalsIgnoreCase(st)) {
                list.add(k);
            }
        }
        return list;
    }

    public List<OrderData> allOrders() {
        if (this.yaml == null) load();
        List<OrderData> list = new ArrayList<>();
        if (!this.yaml.contains("orders")) return list;
        for (String k : this.yaml.getConfigurationSection("orders").getKeys(false)) {
            String p = this.yaml.getString("orders." + k + ".player", "");
            long a = this.yaml.getLong("orders." + k + ".amount", 0L);
            String s = this.yaml.getString("orders." + k + ".status", "");
            list.add(new OrderData(k, p, a, s));
        }
        return list;
    }

    public void storeGiftCode(String player, String vendor, String code) {
        if (this.yaml == null) load();
        String id = UUID.randomUUID().toString().replace("-", "");
        String path = "gift_codes." + id;
        this.yaml.set(path + ".player", player);
        this.yaml.set(path + ".vendor", vendor);
        this.yaml.set(path + ".code", code);
        this.yaml.set(path + ".status", "STORED");
        save();
    }

    public List<GiftData> allGiftCodes() {
        if (this.yaml == null) load();
        List<GiftData> list = new ArrayList<>();
        if (!this.yaml.contains("gift_codes")) return list;
        for (String k : this.yaml.getConfigurationSection("gift_codes").getKeys(false)) {
            String p = this.yaml.getString("gift_codes." + k + ".player", "");
            String v = this.yaml.getString("gift_codes." + k + ".vendor", "");
            String c = this.yaml.getString("gift_codes." + k + ".code", "");
            String s = this.yaml.getString("gift_codes." + k + ".status", "");
            list.add(new GiftData(v, p, c, s));
        }
        return list;
    }

    public void recordConsent(String orderId, String player, String uuid, String ip,
                              String type, long amount, String policyVersion,
                              java.util.List<String> summaryLines, String underageNotice,
                              String contactEmail, String contactDiscord, String contactSla,
                              String reference, long timestamp) {
        if (this.yaml == null) load();
        String path = "consents." + orderId;
        this.yaml.set(path + ".player", player);
        this.yaml.set(path + ".uuid", uuid);
        this.yaml.set(path + ".ip", ip);
        this.yaml.set(path + ".type", type);
        this.yaml.set(path + ".amount", amount);
        this.yaml.set(path + ".policyVersion", policyVersion);
        this.yaml.set(path + ".lines", summaryLines);
        this.yaml.set(path + ".underageNotice", underageNotice);
        this.yaml.set(path + ".contact.email", contactEmail);
        this.yaml.set(path + ".contact.discord", contactDiscord);
        this.yaml.set(path + ".contact.sla", contactSla);
        this.yaml.set(path + ".reference", reference);
        this.yaml.set(path + ".timestamp", timestamp);
        save();
    }

}
