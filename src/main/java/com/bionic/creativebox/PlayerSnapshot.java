package com.bionic.creativebox;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家进入沙盒前的状态快照。
 * 背包与末影箱以 Base64 字符串保存（序列化由 {@link PlayerStateStore} 完成）。
 */
public final class PlayerSnapshot {

    public enum State { NONE, IN_SANDBOX }

    public final UUID playerId;
    public String playerName;

    public String originWorld;
    public double x, y, z;
    public float yaw, pitch;

    public GameMode gameMode = GameMode.SURVIVAL;
    public boolean allowFlight;
    public boolean flying;

    public int level;
    public float exp;

    public double health;
    public int foodLevel;
    public float saturation;
    public int fireTicks;
    public int freezeTicks;

    public List<PotionEffect> potionEffects = new ArrayList<>();

    public String inventoryBase64 = "";
    public String enderChestBase64 = "";

    public State state = State.NONE;
    public long savedAt = System.currentTimeMillis();

    public static PlayerSnapshot fromPlayer(Player player, Location origin) {
        PlayerSnapshot snap = new PlayerSnapshot(player.getUniqueId());
        snap.playerName = player.getName();

        if (origin == null) {
            origin = player.getLocation();
        }
        World world = origin.getWorld();
        snap.originWorld = world == null ? "" : world.getName();
        snap.x = origin.getX();
        snap.y = origin.getY();
        snap.z = origin.getZ();
        snap.yaw = origin.getYaw();
        snap.pitch = origin.getPitch();

        snap.gameMode = player.getGameMode();
        snap.allowFlight = player.getAllowFlight();
        snap.flying = player.isFlying();

        snap.level = player.getLevel();
        snap.exp = player.getExp();

        snap.health = player.getHealth();
        snap.foodLevel = player.getFoodLevel();
        snap.saturation = player.getSaturation();
        snap.fireTicks = player.getFireTicks();
        snap.freezeTicks = player.getFreezeTicks();

        snap.potionEffects = new ArrayList<>(player.getActivePotionEffects());

        snap.inventoryBase64 = PlayerStateStore.serializeItems(player.getInventory().getContents());
        snap.enderChestBase64 = PlayerStateStore.serializeItems(player.getEnderChest().getContents());
        return snap;
    }

    private PlayerSnapshot(UUID playerId) {
        this.playerId = playerId;
    }

    /** 从磁盘状态重建快照（不含玩家实时数据）。 */
    public static PlayerSnapshot forId(UUID playerId) {
        return new PlayerSnapshot(playerId);
    }
}
