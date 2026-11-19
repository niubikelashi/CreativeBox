package com.bionic.creativebox;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进入/退出沙盒：保存快照、按配置隔离状态、传送、恢复。
 *
 * 修复说明（原实现里这些都没真正生效）：
 * - 末影箱在 paper-api 1.20.1 上是 HumanEntity.getEnderChest() 返回 Inventory，没有 EnderChest 接口。
 * - 原代码把背包写成字面量 "null"，退出后物品丢失；现在真正 Base64 序列化。
 * - 传送时用 entering/exiting 标记，避免 TeleportListener 把自家传送拦掉造成递归。
 */
public final class SandboxService {

    private final CreativeBoxPlugin plugin;
    private final PlayerStateStore store;
    private final SandboxWorldManager worldManager;

    private final Map<UUID, PlayerSnapshot> active = new ConcurrentHashMap<>();
    private final Set<UUID> entering = ConcurrentHashMap.newKeySet();
    private final Set<UUID> exiting = ConcurrentHashMap.newKeySet();

    public SandboxService(CreativeBoxPlugin plugin, PlayerStateStore store, SandboxWorldManager worldManager) {
        this.plugin = plugin;
        this.store = store;
        this.worldManager = worldManager;
    }

    /** 服务器重启后，把磁盘上仍处于沙盒状态的玩家恢复进内存。 */
    public void restoreFromDisk() {
        Map<UUID, PlayerSnapshot> recovered = store.loadInSandbox();
        active.putAll(recovered);
        if (!recovered.isEmpty()) {
            plugin.getLogger().info("从磁盘恢复 " + recovered.size() + " 个沙盒玩家状态");
        }
    }

    public boolean isInSandbox(Player player) {
        return isInSandbox(player.getUniqueId());
    }

    public boolean isInSandbox(UUID playerId) {
        return active.containsKey(playerId);
    }

    public int activeCount() {
        return active.size();
    }

    public boolean isEntering(UUID playerId) {
        return entering.contains(playerId);
    }

    public boolean isExiting(UUID playerId) {
        return exiting.contains(playerId);
    }

    public PlayerSnapshot snapshot(UUID playerId) {
        PlayerSnapshot snap = active.get(playerId);
        return snap != null ? snap : store.load(playerId);
    }

    public boolean enter(Player player) {
        UUID id = player.getUniqueId();
        if (active.containsKey(id)) {
            message(player, "already-in-sandbox", "&c你已经在沙盒里了");
            return false;
        }
        if (!worldManager.isReady()) {
            message(player, "world-not-ready", "&c沙盒世界不可用，请先执行 /sb reload");
            return false;
        }

        Location origin = player.getLocation();
        PlayerSnapshot snap = PlayerSnapshot.fromPlayer(player, origin);
        snap.state = PlayerSnapshot.State.IN_SANDBOX;
        store.save(snap);
        active.put(id, snap);

        entering.add(id);
        try {
            applyIsolation(player);
            Location spawn = worldManager.getSpawnLocation();
            if (spawn == null) {
                World fallback = plugin.getServer().getWorlds().get(0);
                spawn = fallback.getSpawnLocation();
            }
            if (!player.teleport(spawn)) {
                plugin.getLogger().warning("传送进沙盒失败: " + player.getName());
            }
        } finally {
            entering.remove(id);
        }
        message(player, "entered-sandbox", "&a已进入沙盒世界");
        return true;
    }

    public boolean exit(Player player) {
        UUID id = player.getUniqueId();
        PlayerSnapshot snap = snapshot(id);
        if (snap == null || snap.state != PlayerSnapshot.State.IN_SANDBOX) {
            message(player, "not-in-sandbox", "&c你不在沙盒里");
            return false;
        }

        exiting.add(id);
        try {
            World originWorld = store.originWorld(snap);
            Location target;
            if (originWorld == null) {
                plugin.getLogger().warning("原世界 " + snap.originWorld + " 已不存在，传送到主世界出生点");
                World fallback = plugin.getServer().getWorlds().get(0);
                target = fallback.getSpawnLocation();
            } else {
                target = new Location(originWorld, snap.x, snap.y, snap.z, snap.yaw, snap.pitch);
            }
            if (!player.teleport(target)) {
                plugin.getLogger().warning("传送回原世界失败: " + player.getName());
            }
            restore(player, snap);
            snap.state = PlayerSnapshot.State.NONE;
            store.save(snap);
            active.remove(id);
        } finally {
            exiting.remove(id);
        }
        message(player, "exited-sandbox", "&a已退出沙盒，状态已恢复");
        return true;
    }

    private void applyIsolation(Player player) {
        ConfigurationSection cfg = plugin.getConfig();
        ConfigurationSection iso = cfg.getConfigurationSection("isolation");
        boolean all = iso == null || iso.getBoolean("all", false);

        GameMode mode = parseGameMode(cfg.getString("sandbox.game-mode", "CREATIVE"));
        player.setGameMode(mode);
        player.setAllowFlight(cfg.getBoolean("sandbox.allow-flight", true));
        if (player.getAllowFlight()) {
            player.setFlying(cfg.getBoolean("sandbox.flying", false));
        }

        if (flag(iso, "inventory", all)) {
            player.getInventory().clear();
        }
        if (flag(iso, "ender-chest", all)) {
            player.getEnderChest().clear();
        }
        if (flag(iso, "potion-effects", all)) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        }
        if (flag(iso, "experience", all)) {
            player.setLevel(0);
            player.setExp(0f);
        }
        if (flag(iso, "health-and-food", all)) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(5f);
            player.setFireTicks(0);
        }
    }

    private void restore(Player player, PlayerSnapshot snap) {
        ConfigurationSection cfg = plugin.getConfig();
        ConfigurationSection iso = cfg.getConfigurationSection("isolation");
        boolean all = iso == null || iso.getBoolean("all", false);

        player.setGameMode(snap.gameMode);
        player.setAllowFlight(snap.allowFlight);
        if (snap.allowFlight && snap.flying) {
            player.setFlying(true);
        }

        if (flag(iso, "inventory", all)) {
            player.getInventory().setContents(PlayerStateStore.deserializeItems(snap.inventoryBase64));
        }
        if (flag(iso, "ender-chest", all)) {
            player.getEnderChest().setContents(PlayerStateStore.deserializeItems(snap.enderChestBase64));
        }
        if (flag(iso, "potion-effects", all)) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            for (PotionEffect effect : snap.potionEffects) {
                player.addPotionEffect(effect);
            }
        }
        if (flag(iso, "experience", all)) {
            player.setLevel(snap.level);
            player.setExp(snap.exp);
        }
        if (flag(iso, "health-and-food", all)) {
            double max = player.getMaxHealth();
            player.setHealth(Math.min(Math.max(snap.health, 1.0), max));
            player.setFoodLevel(snap.foodLevel);
            player.setSaturation(snap.saturation);
            player.setFireTicks(snap.fireTicks);
            player.setFreezeTicks(snap.freezeTicks);
        }
    }

    private static boolean flag(ConfigurationSection iso, String key, boolean all) {
        return all || (iso != null && iso.getBoolean(key, false));
    }

    private static GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameMode.CREATIVE;
        }
    }

    public void message(Player player, String key, String fallback) {
        String text = plugin.getConfig().getString("messages." + key, fallback);
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&b沙盒&8] ");
        player.sendMessage(com.bionic.creativebox.util.Text.color(prefix + text));
    }
}
