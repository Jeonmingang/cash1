package com.minkang.ultimate.commands;

import com.minkang.ultimate.DonateAutomationPlugin;
import com.minkang.ultimate.store.PendingStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DonateAdminCommand implements CommandExecutor, TabCompleter {

    private final DonateAutomationPlugin plugin;

    public DonateAdminCommand(DonateAutomationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ultimate.donate.admin")) {
            sender.sendMessage(plugin.color("&c권한이 없습니다."));
            sender.sendMessage(plugin.color("&e/" + label + " 배수 <유형(bank|gift)> <배수> &7: 배수 설정"));sender.sendMessage(plugin.color("&e/" + label + " 배수보기 &7: 현재 배수 확인"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.color("&6==== &e후원 관리자 &6===="));
            sender.sendMessage(plugin.color("&e/" + label + " 리로드 &7: 설정 리로드"));
            sender.sendMessage(plugin.color("&e/" + label + " 목록 주문|코드 &7: 보류 목록"));
            sender.sendMessage(plugin.color("&e/" + label + " 지급 <플레이어> <원화> &7: 강제 지급 테스트"));
            sender.sendMessage(plugin.color("&e/" + label + " 배수 <유형(bank|gift)> <배수> &7: 배수 설정"));sender.sendMessage(plugin.color("&e/" + label + " 배수보기 &7: 현재 배수 확인"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("리로드") || sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.reloaded", "&a설정이 리로드되었습니다.")));
            sender.sendMessage(plugin.color("&e/" + label + " 배수 <유형(bank|gift)> <배수> &7: 배수 설정"));sender.sendMessage(plugin.color("&e/" + label + " 배수보기 &7: 현재 배수 확인"));
            return true;
        }

        if (sub.equals("목록") || sub.equals("list")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.color("&c사용법: /" + label + " list orders|codes"));
                return true;
            }
            if (args[1].equalsIgnoreCase("orders")) {
                for (PendingStore.OrderData od : plugin.getPendingStore().allOrders()) {
                    sender.sendMessage(plugin.color("&7- " + od.orderId + " / " + od.player + " / " + od.amountWon + "원 / " + od.status));
                }
                return true;
            }
            if (args[1].equalsIgnoreCase("codes")) {
                for (PendingStore.GiftData gd : plugin.getPendingStore().allGiftCodes()) {
                    sender.sendMessage(plugin.color("&7- " + gd.vendor + " / ****" + mask(gd.code) + " / " + gd.player + " / " + gd.status));
                }
                return true;
            }
            sender.sendMessage(plugin.color("&corders 또는 codes 중 하나를 선택하세요."));
            sender.sendMessage(plugin.color("&e/" + label + " 배수 <유형(bank|gift)> <배수> &7: 배수 설정"));sender.sendMessage(plugin.color("&e/" + label + " 배수보기 &7: 현재 배수 확인"));
            return true;
        }

        if (sub.equals("지급") || sub.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.color("&c사용법: /" + label + " give <player> <won>"));
                return true;
            }
            String player = args[1];
            long won;
            try {
                won = Long.parseLong(args[2]);
            } catch (Exception e) {
                sender.sendMessage(plugin.color("&c금액은 숫자여야 합니다."));
                return true;
            }
            plugin.giveRewardNow(player, won, "admin");
            sender.sendMessage(plugin.color("&a지급 완료."));
            sender.sendMessage(plugin.color("&e/" + label + " 배수 <유형(bank|gift)> <배수> &7: 배수 설정"));sender.sendMessage(plugin.color("&e/" + label + " 배수보기 &7: 현재 배수 확인"));
            return true;
        }

        
        if (sub.equals("배수") || sub.equals("mult")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.color("&c사용법: /" + label + " 배수 <유형(bank|gift)> <배수>"));
                return true;
            }
            String type = args[1].toLowerCase(Locale.ROOT);
            double val;
            try { val = Double.parseDouble(args[2]); } catch (Exception e) {
                sender.sendMessage(plugin.color("&c배수는 숫자여야 합니다."));
                return true;
            }
            if (val <= 0) {
                sender.sendMessage(plugin.color("&c배수는 0보다 커야 합니다."));
                return true;
            }
            if ("bank".equals(type) || "계좌".equals(type) || "계좌이체".equals(type)) {
                plugin.getConfig().set("rewards.multipliers.bank", val);
            } else if ("gift".equals(type) || "문상".equals(type) || "문화상품권".equals(type)) {
                plugin.getConfig().set("rewards.multipliers.gift", val);
            } else {
                sender.sendMessage(plugin.color("&cbank|gift 중에서 선택하세요."));
                return true;
            }
            plugin.saveConfig();
            sender.sendMessage(plugin.color("&a배수가 설정되었습니다. " + type + " = " + val));
            return true;
        }

        if (sub.equals("배수보기")) {
            double mb = plugin.getConfig().getDouble("rewards.multipliers.bank", 1.0);
            double mg = plugin.getConfig().getDouble("rewards.multipliers.gift", 1.0);
            sender.sendMessage(plugin.color("&e현재 배수 - 계좌이체(bank): &b" + mb + " &7/ 문화상품권(gift): &b" + mg));
            return true;
        }
sender.sendMessage(plugin.color("&c알 수 없는 서브커맨드."));
        return true;
    }

    private String mask(String code) {
        if (code == null || code.length() < 4) return "****";
        return code.substring(code.length()-4);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "list", "give");
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            return Arrays.asList("orders", "codes");
        }
        return new ArrayList<>();
    }
}
