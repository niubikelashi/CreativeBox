package com.bionic.creativebox.listener;

import com.bionic.creativebox.CreativeBoxPlugin;
import com.bionic.creativebox.SandboxService;
import com.bionic.creativebox.SandboxWorldManager;
import com.bionic.creativebox.util.Text;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * 传送拦截：沙盒世界不与主世界互相穿越，除非是插件自己发起的进出传送。
 * entering/exiting 标记用于放行插件自身的传送，避免「拦截 -> 递归传送」死循环。
 */
public final class TeleportListener implements Listener {

    private final CreativeBoxPlugin plugin;

    public TeleportListener(CreativeBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();
        if (service == null || worlds == null || !worlds.isReady()) {
            return;
        }

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (service.isEntering(id) || service.isExiting(id)) {
            return;
        }

        World from = event.getFrom().getWorld();
        World to = event.getTo() == null ? from : event.getTo().getWorld();
        String sandboxName = worlds.getWorldName();
        if (from == null || to == null || from.getName().equals(to.getName())) {
            return;
        }
        if (!from.getName().equals(sandboxName) && !to.getName().equals(sandboxName)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("isolation.block-cross-world-teleport", true)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Text.color(
                plugin.getConfig().getString("messages.teleport-blocked", "&c沙盒内不允许跨世界传送，用 /sb exit 退出")));
    }
}
