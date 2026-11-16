package com.bionic.creativebox;
import org.bukkit.*; import org.bukkit.entity.Player;
import org.bukkit.WorldCreator; import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
public class SandboxWorldManager {
    private final CreativeBoxPlugin plugin; private World sandboxWorld; private Location spawnLocation;
    public SandboxWorldManager(CreativeBoxPlugin p) { this.plugin = p; }
    public boolean initializeSandbox() {
        FileConfiguration config = plugin.getConfig(); String wn=config.getString("world.name","sandbox");
        this.sandboxWorld = Bukkit.getWorld(wn);
        if (this.sandboxWorld == null) {
            WorldType type;
            try { type=WorldType.valueOf(config.getString("world.type","NORMAL")); } catch(IllegalArgumentException e) { type=WorldType.NORMAL; }
            boolean flat=config.getBoolean("world.flat",false); String dirName="sandbox";
            WorldCreator wc=new WorldCreator(dirName).type(type);
            if (flat && !config.getBoolean("world.generate-structures",true)) wc.generateStructures(false);
            this.sandboxWorld=Bukkit.createWorld(wc);
        }
        if (this.sandboxWorld==null) { plugin.getLogger().severe("Failed to create sandbox world!"); return false; }
        this.spawnLocation=createSpawnLocation(); applyGamerules();
        plugin.getLogger().info("Sandbox world initialized: "+wn); return true;
    }