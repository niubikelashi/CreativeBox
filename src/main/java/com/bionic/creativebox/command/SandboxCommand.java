package com.bionic.creativebox.command;

import com.bionic.creativebox.CreativeBoxPlugin;
import com.bionic.creativebox.SandboxService;
import com.bionic.creativebox.SandboxWorldManager;
import com.bionic.creativebox.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /sb 命令：help | enter | exit | status | setspawn | reload | reset | world */
public final class SandboxCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("help", "enter", "exit", "status", "setspawn", "reload", "reset", "world");

    private final CreativeBoxPlugin plugin;

    public SandboxCommand(CreativeBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;

            case "enter":
            case "exit": {
                Player player = asPlayer(sender);
                if (player == null) {
                    sender.sendMessage(Text.color("&c控制台无法进出沙盒，请用 /sb status 或 /sb reload"));
                    return true;
                }
                if (!player.hasPermission("creativebox.use")) {
                    sender.sendMessage(Text.color("&c你没有权限使用沙盒"));
                    return true;
                }
                if (sub.equals("enter")) {
                    service.enter(player);
                } else {
                    service.exit(player);
                }
                return true;
            }

            case "status": {
                sender.sendMessage(Text.color("&7沙盒世界: &f" + worlds.getWorldName()
                        + (worlds.isReady() ? " &a(已就绪)" : " &c(不可用)")));
                sender.sendMessage(Text.color("&7沙盒内玩家: &f" + service.activeCount()));
                Player player = asPlayer(sender);
                if (player != null) {
                    sender.sendMessage(Text.color("&7你的状态: "
                            + (service.isInSandbox(player) ? "&b在沙盒中" : "&7不在沙盒")));
                }
                return true;
            }

            case "setspawn": {
                if (!sender.hasPermission("creativebox.admin")) {
                    sender.sendMessage(Text.color("&c需要 creativebox.admin 权限"));
                    return true;
                }
                Player player = asPlayer(sender);
                if (player == null) {
                    sender.sendMessage(Text.color("&c需要玩家执行 /sb setspawn"));
                    return true;
                }
                if (!worlds.isReady()) {
                    sender.sendMessage(Text.color("&c沙盒世界不可用"));
                    return true;
                }
                worlds.initializeSandbox();
                worlds.refreshWorldSettings();
                org.bukkit.Location spawn = worlds.getSpawnLocation();
                player.teleport(spawn);
                sender.sendMessage(Text.color("&a沙盒出生点已设为 "
                        + String.format(Locale.ROOT, "%.1f %.1f %.1f", spawn.getX(), spawn.getY(), spawn.getZ())));
                return true;
            }

            case "reload": {
                if (!sender.hasPermission("creativebox.admin")) {
                    sender.sendMessage(Text.color("&c需要 creativebox.admin 权限"));
                    return true;
                }
                boolean ok = plugin.reloadWorldSettings();
                sender.sendMessage(Text.color(ok ? "&a配置已重载，沙盒世界已更新" : "&c重载后沙盒世界仍不可用"));
                return true;
            }

            case "reset": {
                if (!sender.hasPermission("creativebox.admin")) {
                    sender.sendMessage(Text.color("&c需要 creativebox.admin 权限"));
                    return true;
                }
                sender.sendMessage(Text.color("&e正在重置沙盒世界（玩家会被送回主世界）…"));
                boolean ok = worlds.resetSandboxWorld(service);
                sender.sendMessage(Text.color(ok ? "&a沙盒世界已重建" : "&c重置失败，见服务端日志"));
                return true;
            }

            case "world": {
                if (!sender.hasPermission("creativebox.admin")) {
                    sender.sendMessage(Text.color("&c需要 creativebox.admin 权限"));
                    return true;
                }
                org.bukkit.World world = worlds.getWorld();
                sender.sendMessage(Text.color("&7当前世界数: &f" + plugin.getServer().getWorlds().size()));
                if (world != null) {
                    sender.sendMessage(Text.color("&7沙盒: &f" + world.getName()
                            + " &7环境=" + world.getEnvironment()
                            + " &7玩家=" + world.getPlayers().size()));
                } else {
                    sender.sendMessage(Text.color("&c沙盒世界未加载"));
                }
                return true;
            }

            default:
                sender.sendMessage(Text.color("&c未知子命令: &f" + sub + "&c，用 /sb help"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String name : SUBCOMMANDS) {
                if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("reset"))) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.color("&b/sb help &7- 查看帮助"));
        sender.sendMessage(Text.color("&b/sb enter &7- 进入沙盒（保留你的状态）"));
        sender.sendMessage(Text.color("&b/sb exit &7- 退出沙盒并恢复状态"));
        sender.sendMessage(Text.color("&b/sb status &7- 查看沙盒状态"));
        sender.sendMessage(Text.color("&b/sb setspawn &7- 设置沙盒出生点 [admin]"));
        sender.sendMessage(Text.color("&b/sb reload &7- 重载配置并应用到沙盒 [admin]"));
        sender.sendMessage(Text.color("&b/sb reset &7- 删除并重建沙盒世界 [admin]"));
        sender.sendMessage(Text.color("&b/sb world &7- 查看世界信息 [admin]"));
    }

    private static Player asPlayer(CommandSender sender) {
        return (sender instanceof Player) ? (Player) sender : null;
    }
}
