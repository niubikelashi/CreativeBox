package com.bionic.creativebox;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * 沙盒世界管理：创建/加载沙盒世界、应用游戏规则、世界边界、自动铺平台、重置世界。
 *
 * 注意 API 兼容性（已在 paper-api 1.20.1 上实测签名）：
 * - 卸载世界用 Bukkit.unloadWorld(...)，World 本身没有 unload()。
 * - 世界类型用枚举 org.bukkit.WorldType（World.Type 不存在）。
 * - 现代游戏规则用 org.bukkit.GameRule<Boolean> 常量；"pvp" 等未收录的键走 setGameRuleValue(String,String)。
 */
public final class SandboxWorldManager {

    private final CreativeBoxPlugin plugin;
    private World sandboxWorld;
    private Location spawnLocation;
    private String worldName = "sandbox";

    public SandboxWorldManager(CreativeBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isReady() {
        return sandboxWorld != null && spawnLocation != null;
    }

    public World getWorld() {
        return sandboxWorld;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public String getWorldName() {
        return worldName;
    }

    /** 创建（或复用）沙盒世界并应用配置。返回 false 表示世界不可用。 */
    public boolean initializeSandbox() {
        ConfigurationSection cfg = plugin.getConfig();
        worldName = cfg.getString("world.name", "sandbox");

        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            sandboxWorld = existing;
        } else {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(parseEnvironment(cfg.getString("world.environment", "NORMAL")));
            WorldType type = WorldType.getByName(cfg.getString("world.type", "FLAT"));
            creator.type(type == null ? WorldType.NORMAL : type);
            creator.seed(cfg.getLong("world.seed", 0L));
            creator.generateStructures(cfg.getBoolean("world.generate-structures", false));
            String settings = cfg.getString("world.generator-settings", "");
            if (!settings.isEmpty()) {
                creator.generatorSettings(settings);
            }
            sandboxWorld = Bukkit.createWorld(creator);
            if (sandboxWorld == null) {
                plugin.getLogger().warning("无法创建沙盒世界: " + worldName);
                return false;
            }
        }

        applySpawn(cfg);
        applyGamerules(cfg);
        applyWorldBorder(cfg);
        if (cfg.getBoolean("world.auto-build-platform", true)) {
            buildAutoPlatform();
        }
        plugin.getLogger().info("沙盒世界已就绪: " + worldName);
        return true;
    }

    /** 配置热更新后重新应用出生点、游戏规则与边界。 */
    public boolean refreshWorldSettings() {
        if (sandboxWorld == null) {
            return initializeSandbox();
        }
        ConfigurationSection cfg = plugin.getConfig();
        applySpawn(cfg);
        applyGamerules(cfg);
        applyWorldBorder(cfg);
        if (cfg.getBoolean("world.auto-build-platform", true)) {
            buildAutoPlatform();
        }
        return true;
    }

    private void applySpawn(ConfigurationSection cfg) {
        double x = cfg.getDouble("spawn.x", 0.5);
        double y = cfg.getDouble("spawn.y", 65.0);
        double z = cfg.getDouble("spawn.z", 0.5);
        float yaw = (float) cfg.getDouble("spawn.yaw", 0.0);
        float pitch = (float) cfg.getDouble("spawn.pitch", 0.0);
        spawnLocation = new Location(sandboxWorld, x, y, z, yaw, pitch);
        // 用 Location 重载，避免把 double 硬塞进 int 重载（javac 会报精度损失）。
        sandboxWorld.setSpawnLocation(spawnLocation);
    }

    private void applyGamerules(ConfigurationSection cfg) {
        ConfigurationSection rules = cfg.getConfigurationSection("gamerules");
        if (rules == null) {
            return;
        }
        for (String key : rules.getKeys(false)) {
            boolean value = rules.getBoolean(key, true);
            GameRule<Boolean> typed = matchGameRule(key);
            if (typed != null) {
                sandboxWorld.setGameRule(typed, value);
            } else {
                // 兼容未收录进 GameRule 常量的键（例如 "pvp"）。
                sandboxWorld.setGameRuleValue(key, String.valueOf(value));
            }
        }
    }

    private static GameRule<Boolean> matchGameRule(String key) {
        switch (key.toLowerCase()) {
            case "domobspawning": return GameRule.DO_MOB_SPAWNING;
            case "dodaylightcycle": return GameRule.DO_DAYLIGHT_CYCLE;
            case "doweathercycle": return GameRule.DO_WEATHER_CYCLE;
            case "mobgriefing": return GameRule.MOB_GRIEFING;
            case "keepinventory": return GameRule.KEEP_INVENTORY;
            default: return null;
        }
    }

    private void applyWorldBorder(ConfigurationSection cfg) {
        if (!cfg.getBoolean("world-border.enabled", false)) {
            return;
        }
        double diameter = cfg.getDouble("world-border.diameter", 500.0);
        if (diameter <= 0) {
            return;
        }
        org.bukkit.WorldBorder border = sandboxWorld.getWorldBorder();
        border.setSize(diameter);
        if (spawnLocation != null) {
            border.setCenter(spawnLocation.getX(), spawnLocation.getZ());
        }
        plugin.getLogger().info("沙盒世界边界已设置，直径 " + diameter + " 格（世界 " + sandboxWorld.getName() + "）");
    }

    /**
     * 在出生点下方铺一层基岩 + 石头，防止玩家一进场就掉进虚空。
     * 修复说明：原版代码把同一个 Location 对象反复 add()，导致方块坐标累积错位；
     * 这里改为按整数坐标逐个放置，表面（石头）位于出生点脚下方格。
     */
    private void buildAutoPlatform() {
        if (spawnLocation == null) {
            return;
        }
        int baseX = (int) Math.floor(spawnLocation.getX());
        int baseZ = (int) Math.floor(spawnLocation.getZ());
        int surfaceY = (int) Math.floor(spawnLocation.getY()) - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block stone = sandboxWorld.getBlockAt(baseX + dx, surfaceY, baseZ + dz);
                stone.setType(org.bukkit.Material.STONE);
                Block bedrock = sandboxWorld.getBlockAt(baseX + dx, surfaceY - 1, baseZ + dz);
                bedrock.setType(org.bukkit.Material.BEDROCK);
            }
        }
    }

    /** 把沙盒内的玩家送回主世界出生点。 */
    private void evacuatePlayers() {
        World home = firstLoadedWorld();
        if (home == null) {
            plugin.getLogger().warning("找不到可返回的世界，玩家仍留在沙盒中");
            return;
        }
        for (Player player : sandboxWorld.getPlayers()) {
            player.teleport(home.getSpawnLocation());
        }
    }

    private World firstLoadedWorld() {
        List<World> worlds = Bukkit.getWorlds();
        for (World world : worlds) {
            if (!world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    /**
     * 重置沙盒：把玩家送回主世界，卸载并删除世界目录，再按当前配置重建。
     * 修复说明：原版代码删除目录后不再重建，之后进入沙盒会拿到 null 世界。
     */
    public boolean resetSandboxWorld(SandboxService service) {
        if (sandboxWorld == null) {
            return initializeSandbox();
        }
        evacuatePlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isInSandbox(player)) {
                service.exit(player);
            }
        }

        boolean unloaded = Bukkit.unloadWorld(sandboxWorld, false);
        if (!unloaded) {
            plugin.getLogger().warning("世界卸载失败，跳过删除与重建");
            return false;
        }
        sandboxWorld = null;
        spawnLocation = null;

        File dir = worldDirectory(worldName);
        if (dir != null) {
            deleteRecursively(dir);
        } else {
            plugin.getLogger().info("没有发现世界目录，直接重建");
        }
        return initializeSandbox();
    }

    /** 世界目录可能带 .db 后缀（单人存档风格），也可能不带（区域目录直接在外层）。 */
    private File worldDirectory(String name) {
        File container = Bukkit.getWorldContainer();
        File withSuffix = new File(container, name + ".db");
        if (withSuffix.isDirectory()) {
            return withSuffix;
        }
        File plain = new File(container, name);
        if (plain.isDirectory()) {
            return plain;
        }
        return null;
    }

    private void deleteRecursively(File dir) {
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            plugin.getLogger().log(Level.WARNING, "删除失败: " + path, e);
                        }
                    });
            plugin.getLogger().info("已删除沙盒世界目录: " + dir.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "删除世界目录失败: " + dir, e);
        }
    }

    private static World.Environment parseEnvironment(String raw) {
        try {
            return World.Environment.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return World.Environment.NORMAL;
        }
    }
}
