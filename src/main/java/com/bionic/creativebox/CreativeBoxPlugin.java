package com.bionic.creativebox;

import com.bionic.creativebox.command.SandboxCommand;
import com.bionic.creativebox.listener.PortalListener;
import com.bionic.creativebox.listener.PlayerListener;
import com.bionic.creativebox.listener.TeleportListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * CreativeBox：一个隔离的沙盒世界，玩家进去玩，退出时状态恢复。
 *
 * 世界管理见 {@link SandboxWorldManager}，进出逻辑见 {@link SandboxService}，
 * 状态持久化见 {@link PlayerStateStore}。
 */
public final class CreativeBoxPlugin extends JavaPlugin {

    private SandboxWorldManager worldManager;
    private PlayerStateStore stateStore;
    private SandboxService sandboxService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        worldManager = new SandboxWorldManager(this);
        if (!worldManager.initializeSandbox()) {
            getLogger().warning("沙盒世界初始化失败，/sb enter 会提示世界不可用；修好配置后用 /sb reload");
        }

        stateStore = new PlayerStateStore(new File(getDataFolder(), "states"), getLogger());
        sandboxService = new SandboxService(this, stateStore, worldManager);
        sandboxService.restoreFromDisk();

        PluginCommand command = getCommand("sb");
        if (command == null) {
            getLogger().severe("命令 sb 未注册，检查 plugin.yml");
            return;
        }
        SandboxCommand handler = new SandboxCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        scheduleDailyReset();
        getLogger().info("CreativeBox 已启用，沙盒世界: " + worldManager.getWorldName());
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("CreativeBox 已关闭（沙盒世界保持加载，状态已落盘）");
    }

    /** 重新读取配置并应用到沙盒世界。 */
    public boolean reloadWorldSettings() {
        reloadConfig();
        return worldManager.refreshWorldSettings();
    }

    public SandboxWorldManager getWorldManager() {
        return worldManager;
    }

    public SandboxService getSandboxService() {
        return sandboxService;
    }

    public PlayerStateStore getStateStore() {
        return stateStore;
    }

    /** 若 reset.enabled 为 true，按 reset.time（服务器本地时间 HH:mm）每天重置一次。 */
    private void scheduleDailyReset() {
        if (!getConfig().getBoolean("reset.enabled", false)) {
            return;
        }
        LocalTime at = parseTime(getConfig().getString("reset.time", "04:00"));
        long nowMillis = System.currentTimeMillis();
        long nextMillis = nextOccurrence(nowMillis, at);
        long ticks = Math.max(1L, (nextMillis - nowMillis) / 50L);

        getServer().getScheduler().runTaskLater(this, () -> {
            boolean ok = worldManager.resetSandboxWorld(sandboxService);
            getLogger().info("定时重置沙盒世界：" + (ok ? "完成" : "失败"));
            scheduleDailyReset();
        }, ticks);

        getLogger().info("已安排定时重置，首次于 " + at + "（服务器本地时间，约 " + ticks + " tick 后）");
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return LocalTime.of(4, 0);
        }
    }

    private static long nextOccurrence(long nowMillis, LocalTime at) {
        java.time.Instant now = java.time.Instant.ofEpochMilli(nowMillis);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime today = java.time.LocalDateTime.ofInstant(now, zone).toLocalDate().atTime(at);
        if (today.compareTo(java.time.LocalDateTime.ofInstant(now, zone)) <= 0) {
            today = today.plus(1, ChronoUnit.DAYS);
        }
        return today.atZone(zone).toInstant().toEpochMilli();
    }
}
