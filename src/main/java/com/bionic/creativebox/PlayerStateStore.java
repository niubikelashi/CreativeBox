package com.bionic.creativebox;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 玩家状态持久化：每个玩家一个 YAML 文件。
 * 用 Bukkit 的 {@link YamlConfiguration} 读写（自动转义，避免手写解析出错），
 * 写入采用「临时文件 + 原子 rename」，避免崩溃时留下半截文件。
 * 物品序列化用 java.util.Base64 + BukkitObjectOutputStream（不依赖 snakeyaml 内部类）。
 */
public final class PlayerStateStore {

    private static final java.util.logging.Logger STATIC_LOG =
            java.util.logging.Logger.getLogger("CreativeBox");

    private final File dataDir;
    private final java.util.logging.Logger logger;

    public PlayerStateStore(File dataDir, java.util.logging.Logger logger) {
        this.dataDir = dataDir;
        this.logger = logger;
        if (!dataDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataDir.mkdirs();
        }
    }

    public static String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(items.length);
                for (ItemStack item : items) {
                    out.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            STATIC_LOG.log(Level.WARNING, "物品序列化失败", e);
            return "";
        }
    }

    public static ItemStack[] deserializeItems(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int count = in.readInt();
                if (count < 0) {
                    return new ItemStack[0];
                }
                ItemStack[] items = new ItemStack[count];
                for (int i = 0; i < count; i++) {
                    Object o = in.readObject();
                    items[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
                }
                return items;
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            STATIC_LOG.warning("物品反序列化失败: " + e.getMessage());
            return new ItemStack[0];
        }
    }

    private File fileFor(UUID playerId) {
        return new File(dataDir, playerId.toString() + ".yml");
    }

    public void save(PlayerSnapshot snap) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("player-name", snap.playerName);
        yml.set("state", snap.state.name());
        yml.set("saved-at", snap.savedAt);

        yml.set("origin.world", snap.originWorld);
        yml.set("origin.x", snap.x);
        yml.set("origin.y", snap.y);
        yml.set("origin.z", snap.z);
        yml.set("origin.yaw", snap.yaw);
        yml.set("origin.pitch", snap.pitch);

        yml.set("player.game-mode", snap.gameMode.name());
        yml.set("player.allow-flight", snap.allowFlight);
        yml.set("player.flying", snap.flying);
        yml.set("player.level", snap.level);
        yml.set("player.exp", snap.exp);
        yml.set("player.health", snap.health);
        yml.set("player.food-level", snap.foodLevel);
        yml.set("player.saturation", snap.saturation);
        yml.set("player.fire-ticks", snap.fireTicks);
        yml.set("player.freeze-ticks", snap.freezeTicks);
        yml.set("player.inventory", snap.inventoryBase64);
        yml.set("player.ender-chest", snap.enderChestBase64);

        List<Map<String, Object>> potions = new ArrayList<>();
        for (PotionEffect effect : snap.potionEffects) {
            Map<String, Object> entry = new HashMap<>();
            PotionEffectType type = effect.getType();
            entry.put("type", type == null ? "" : type.getName());
            entry.put("duration", effect.getDuration());
            entry.put("amplifier", effect.getAmplifier());
            entry.put("ambient", effect.isAmbient());
            potions.add(entry);
        }
        yml.set("player.potion-effects", potions);

        File target = fileFor(snap.playerId);
        File tmp = new File(dataDir, target.getName() + ".tmp");
        try {
            Files.write(tmp.toPath(), yml.saveToString().getBytes(StandardCharsets.UTF_8));
            Path targetPath = target.toPath();
            try {
                Files.move(tmp.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException renameFailed) {
                // 某些文件系统不支持跨目录 rename 之外的原子替换，退回直接写入。
                Files.write(targetPath, yml.saveToString().getBytes(StandardCharsets.UTF_8));
                Files.deleteIfExists(tmp.toPath());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "保存玩家状态失败: " + snap.playerId, e);
        }
    }

    public PlayerSnapshot load(UUID playerId) {
        File file = fileFor(playerId);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        PlayerSnapshot snap = PlayerSnapshot.forId(playerId);
        snap.playerName = yml.getString("player-name", playerId.toString());
        snap.state = parseState(yml.getString("state", "NONE"));
        snap.savedAt = yml.getLong("saved-at", 0L);

        snap.originWorld = yml.getString("origin.world", "");
        snap.x = yml.getDouble("origin.x", 0.0);
        snap.y = yml.getDouble("origin.y", 64.0);
        snap.z = yml.getDouble("origin.z", 0.0);
        snap.yaw = (float) yml.getDouble("origin.yaw", 0.0);
        snap.pitch = (float) yml.getDouble("origin.pitch", 0.0);

        snap.gameMode = parseGameMode(yml.getString("player.game-mode", "SURVIVAL"));
        snap.allowFlight = yml.getBoolean("player.allow-flight", false);
        snap.flying = yml.getBoolean("player.flying", false);
        snap.level = yml.getInt("player.level", 0);
        snap.exp = (float) yml.getDouble("player.exp", 0.0);
        snap.health = yml.getDouble("player.health", 20.0);
        snap.foodLevel = yml.getInt("player.food-level", 20);
        snap.saturation = (float) yml.getDouble("player.saturation", 5.0);
        snap.fireTicks = yml.getInt("player.fire-ticks", 0);
        snap.freezeTicks = yml.getInt("player.freeze-ticks", 0);
        snap.inventoryBase64 = yml.getString("player.inventory", "");
        snap.enderChestBase64 = yml.getString("player.ender-chest", "");

        List<PotionEffect> effects = new ArrayList<>();
        for (Object raw : yml.getList("player.potion-effects", new ArrayList<>())) {
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) raw;
            String typeName = String.valueOf(entry.get("type"));
            PotionEffectType type = PotionEffectType.getByName(typeName);
            if (type == null) {
                logger.warning("未知的药水类型，已跳过: " + typeName);
                continue;
            }
            // 1.20.1 的 PotionEffect 没有 isVisible()/显示开关持久化，这里只保存类型、时长、等级、ambient。
            effects.add(new PotionEffect(type,
                    asInt(entry.get("duration"), 0),
                    asInt(entry.get("amplifier"), 0),
                    asBoolean(entry.get("ambient"), false)));
        }
        snap.potionEffects = effects;
        return snap;
    }

    /** 读取所有仍处于沙盒中的玩家状态（服务器重启后的崩溃恢复）。 */
    public Map<UUID, PlayerSnapshot> loadInSandbox() {
        Map<UUID, PlayerSnapshot> result = new HashMap<>();
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String name = file.getName();
            UUID uuid = parseUuid(name.substring(0, name.length() - 4));
            if (uuid == null) {
                continue;
            }
            PlayerSnapshot snap = load(uuid);
            if (snap != null && snap.state == PlayerSnapshot.State.IN_SANDBOX) {
                result.put(uuid, snap);
            }
        }
        return result;
    }

    public World originWorld(PlayerSnapshot snap) {
        if (snap.originWorld == null || snap.originWorld.isEmpty()) {
            return null;
        }
        return Bukkit.getWorld(snap.originWorld);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int asInt(Object value, int fallback) {
        return (value instanceof Number) ? ((Number) value).intValue() : fallback;
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        return (value instanceof Boolean) ? (Boolean) value : fallback;
    }

    private static PlayerSnapshot.State parseState(String raw) {
        try {
            return PlayerSnapshot.State.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return PlayerSnapshot.State.NONE;
        }
    }

    private static GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }
}
