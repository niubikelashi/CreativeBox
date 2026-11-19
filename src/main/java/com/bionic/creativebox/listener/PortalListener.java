package com.bionic.creativebox.listener;

import com.bionic.creativebox.CreativeBoxPlugin;
import com.bionic.creativebox.SandboxService;
import com.bionic.creativebox.SandboxWorldManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 阻止沙盒里的玩家（以及末影珍珠等投射物）通过下界/末地传送门穿到主世界。
 * 插件自己发起的进出传送不经过实体传送门事件，所以不需要放行标记。
 */
public final class PortalListener implements Listener {

    private final CreativeBoxPlugin plugin;

    public PortalListener(CreativeBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        SandboxService service = plugin.getSandboxService();
        SandboxWorldManager worlds = plugin.getWorldManager();
        if (service == null || worlds == null || !worlds.isReady()) {
            return;
        }

        String sandboxName = worlds.getWorldName();
        if (event.getEntity() == null || event.getEntity().getWorld() == null
                || !event.getEntity().getWorld().getName().equals(sandboxName)) {
            return;
        }

        boolean sandboxInside = false;
        if (event.getEntity() instanceof Player) {
            sandboxInside = service.isInSandbox((Player) event.getEntity());
        } else if (event.getEntity() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getEntity()).getShooter();
            if (shooter instanceof Player) {
                sandboxInside = service.isInSandbox((Player) shooter);
            }
        }
        if (!sandboxInside) {
            return;
        }

        if (!plugin.getConfig().getBoolean("isolation.block-cross-world-teleport", true)) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Player) {
            plugin.getSandboxService().message((Player) event.getEntity(), "portal-blocked",
                    "&c沙盒内不能通过传送门穿越世界");
        }
    }
}
