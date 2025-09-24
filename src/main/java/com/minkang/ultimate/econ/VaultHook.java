package com.minkang.ultimate.econ;

import com.minkang.ultimate.DonateAutomationPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public class VaultHook {

    private final DonateAutomationPlugin plugin;
    private Object economyProvider; // Reflection-based
    private boolean ready = false;

    public VaultHook(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean tryHook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                this.ready = false;
                return false;
            }
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> rspClass = Class.forName("org.bukkit.plugin.RegisteredServiceProvider");
            Method getRegistration = org.bukkit.Server.class.getMethod("getServicesManager").getReturnType()
                    .getMethod("getRegistration", Class.class);
            Object rsp = plugin.getServer().getServicesManager().getRegistration(ecoClass);
            if (rsp == null) {
                this.ready = false;
                return false;
            }
            Method getProvider = rsp.getClass().getMethod("getProvider");
            Object provider = getProvider.invoke(rsp);
            if (provider == null) {
                this.ready = false;
                return false;
            }
            this.economyProvider = provider;
            this.ready = true;
            return true;
        } catch (Throwable t) {
            this.ready = false;
            return false;
        }
    }

    public boolean isReady() {
        return ready && economyProvider != null;
    }

    public boolean deposit(String playerName, double amount) {
        if (!isReady()) return false;
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> respClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Method depositPlayer = ecoClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Method transactionSuccess = respClass.getMethod("transactionSuccess");
            OfflinePlayer offline = plugin.getServer().getOfflinePlayer(playerName);
            Object resp = depositPlayer.invoke(economyProvider, offline, amount);
            Boolean ok = (Boolean) transactionSuccess.invoke(resp);
            return ok != null && ok;
        } catch (Throwable t) {
            return false;
        }
    }
}
