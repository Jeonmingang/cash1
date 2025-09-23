package com.minkang.ultimate;

import com.minkang.ultimate.commands.DonateAdminCommand;
import com.minkang.ultimate.commands.DonateCommand;
import com.minkang.ultimate.http.WebhookServer;
import com.minkang.ultimate.store.PendingStore;
import com.minkang.ultimate.econ.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DonateAutomationPlugin extends JavaPlugin {

    private static DonateAutomationPlugin instance;
    private PendingStore pendingStore;
    private WebhookServer webhookServer;
    private VaultHook vaultHook;
    private String prefix;

    public static DonateAutomationPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.prefix = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("messages.prefix", "&6[후원]&r "));

        this.pendingStore = new PendingStore(this);
        this.pendingStore.load();

        // Vault (optional) with NPE guard
        this.vaultHook = new VaultHook(this);
        boolean okVault = this.vaultHook.tryHook();
        if (!okVault) {
            log("&eVault/경제 플러그인을 찾지 못했습니다. 명령어 보상만 사용됩니다.");
        }

        // Commands
        DonateCommand donate = new DonateCommand(this);
        DonateAdminCommand donateAdmin = new DonateAdminCommand(this);

        PluginCommand donateCmd = this.getCommand("donate");
        if (donateCmd != null) {
            donateCmd.setExecutor(donate);
            donateCmd.setTabCompleter(donate);
        }

        PluginCommand donateAdminCmd = this.getCommand("donateadmin");
        if (donateAdminCmd != null) {
            donateAdminCmd.setExecutor(donateAdmin);
            donateAdminCmd.setTabCompleter(donateAdmin);
        }

        // Webhook server (local)
        boolean enabled = getConfig().getBoolean("webhook.enabled", true);
        if (enabled) {
            this.webhookServer = new WebhookServer(this);
            try {
                this.webhookServer.start();
                log("&a로컬 웹훅 서버가 시작되었습니다.");
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "웹훅 서버 시작 실패", ex);
                log("&c웹훅 서버 시작에 실패했습니다. config.yml을 확인하세요.");
            }
        }

        log("&aUltimateDonateAutomation v1.0.0 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
        if (pendingStore != null) {
            pendingStore.save();
        }
        log("&7비활성화 및 데이터 저장 완료.");
    }

    public void log(String message) {
        String msg = ChatColor.translateAlternateColorCodes('&', prefix + message);
        getServer().getConsoleSender().sendMessage(msg);
    }

    public PendingStore getPendingStore() {
        return pendingStore;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public void giveRewardAsync(final String playerName, final long amountWon, final String reason) {
        // 안전하게 메인 스레드에서 지급
        new BukkitRunnable() {
            @Override
            public void run() {
                giveRewardNow(playerName, amountWon, reason);
            }
        }.runTask(this);
    }

    
    public void giveRewardNow(String playerName, long amountWon, String reason) {
        // 배수 결정 (bank/gift 구분)
        double mult = 1.0;
        if (reason != null) {
            if (reason.startsWith("bank:")) {
                mult = getConfig().getDouble("rewards.multipliers.bank", 1.0);
            } else if (reason.startsWith("gift:")) {
                mult = getConfig().getDouble("rewards.multipliers.gift", 1.0);
            }
        }
        if (mult <= 0) mult = 1.0;

        int wonPerCash = getConfig().getInt("rewards.wonPerCash", 100);
        if (wonPerCash <= 0) wonPerCash = 100;

        long adjustedWon = Math.round(amountWon * mult);
        int cash = (int) (adjustedWon / wonPerCash);
        if (cash <= 0) cash = 1;

        String fmt = getConfig().getString("rewards.cashCommand", "캐시 지급 {player} {cash}");
        String cmd = fmt.replace("{player}", playerName)
                .replace("{cash}", String.valueOf(cash))
                .replace("{won}", String.valueOf(amountWon))
                .replace("{mult}", String.valueOf(mult));
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);

        String rewardMsg = getConfig().getString("messages.rewardGiven",
                "&a{cash} 캐시가 지급되었습니다.");
        rewardMsg = rewardMsg.replace("{cash}", String.valueOf(cash));
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(playerName);
        if (target != null && target.isOnline()) {
            target.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + rewardMsg));
        }
        log("&a보상 지급 완료: " + playerName + " / 원화 " + amountWon + " (배수 " + mult + ") / 캐시 " + cash + " / 사유 " + reason);
    }
    
