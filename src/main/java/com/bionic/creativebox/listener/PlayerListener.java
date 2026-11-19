package com.bionic.creativebox.listener;

import com.bionic.creativebox.CreativeBoxPlugin;
import com.bionic.creativebox.SandboxService;
import com.bionic.creativebox.SandboxWorldManager;
import com.bionic.creativebox.util.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 玩家事件：重连回到沙盒、重生回沙盒出生点、掉出虚空时拉回出生点。
 *
 * 虚空保护按「绝对 Y」判断（threshold 是高度阈值，例如 50 表示低于 50 触发），
 * 并做节流，避免 PlayerMoveEvent 高频写日志。
 */
public final class PlayerListener implements Listener {

    private final CreativeBoxPlugin plugin;
    private final java.util.Map<java.util.UUID, Long> lastVoidCheck =
            new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerListener(CreativeBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();
        Player player = event.getPlayer();
        if (service.isInSandbox(player) && worlds.isReady()) {
            player.teleport(worlds.getSpawnLocation());
            plugin.getLogger().info("玩家 " + player.getName() + " 重连，已送回沙盒");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();
        if (service.isInSandbox(event.getPlayer()) && worlds.isReady()) {
            event.setRespawnLocation(worlds.getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();
        if (!plugin.getConfig().getBoolean("void-protection.enabled", true)) {
            return;
        }
        if (!service.isInSandbox(event.getPlayer()) || !worlds.isReady()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        World sandbox = worlds.getWorld();
        if (sandbox == null || !to.getWorld().getName().equals(sandbox.getName())) {
            return;
        }

        double threshold = plugin.getConfig().getDouble("void-protection.threshold", 50.0);
        // 只在明显向下掉且低于阈值时才处理，水平移动不触发。
        if (to.getY() >= threshold || to.getY() > from.getY()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastVoidCheck.get(event.getPlayer().getUniqueId());
        if (last != null && now - last < 1000L) {
            return;
        }
        lastVoidCheck.put(event.getPlayer().getUniqueId(), now);

        Location spawn = worlds.getSpawnLocation();
        event.getPlayer().teleport(spawn);
        event.getPlayer().sendMessage(Text.color(
                plugin.getConfig().getString("messages.void-rescued", "&c你掉出了沙盒，已拉回出生点")));
    }
}
