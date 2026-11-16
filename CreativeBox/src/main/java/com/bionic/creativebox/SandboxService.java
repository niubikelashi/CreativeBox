package com.bionic.creativebox;
import org.bukkit.*; import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player; import org.bukkit.inventory.ItemStack;
import java.util.UUID; import java.util.concurrent.ConcurrentHashMap;
public class SandboxService {
    private final CreativeBoxPlugin plugin; private final SandboxWorldManager worldManager;
    private final ConcurrentHashMap<UUID,String> enteringPlayers=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID,String> exitingPlayers=new ConcurrentHashMap<>();
    private final StateStore stateStore;
    public SandboxService(CreativeBoxPlugin plugin) { this.plugin=plugin; this.worldManager=new SandboxWorldManager(plugin); this.stateStore=new StateStore(plugin.getDataFolder()); }
    public SandboxWorldManager getWorldManager() { return worldManager; }
    public boolean isEntering(Player player) { return enteringPlayers.containsKey(player.getUniqueId()); }
    public boolean isExiting(Player player) { return exitingPlayers.containsKey(player.getUniqueId()); }
    public boolean isInSandbox(Player player) { PlayerSnapshot snap=stateStore.load(player.getUniqueId()); return snap!=null&&snap.state==PlayerSnapshot.State.IN_SANDBOX; }
    public void enter(Player player) {
        UUID uuid=player.getUniqueId(); PlayerSnapshot snap=stateStore.load(uuid);
        if (snap!=null&&snap.state==PlayerSnapshot.State.IN_SANDBOX) return;
        snap=PlayerSnapshot.fromPlayer(player); snap.state=PlayerSnapshot.State.IN_SANDBOX; stateStore.save(snap); enteringPlayers.put(uuid,"entering");
        player.getInventory().clear(); player.getEnderChest().setContents(new ItemStack[0]);
        for (org.bukkit.potion.PotionEffect pe:player.getActivePotionEffects()) { player.removePotionEffect(pe.getType()); }
        World sandboxWorld=worldManager.getSandboxWorld();
        if (sandboxWorld==null) { if(!worldManager.initializeSandbox()){enteringPlayers.remove(uuid);return;} }
        else { try{sandboxWorld.getChunkAt(player.getLocation()).load();}catch(Exception e){} }
        Location spawn=worldManager.getSpawnLocation();
        if (spawn!=null) player.teleport(spawn);
        else { Location ms=Bukkit.getWorlds().isEmpty()?null:Bukkit.getWorld(Bukkit.getWorlds().get(0).getName()).getSpawnLocation(); if(ms!=null)player.teleport(ms); }
        player.setGameMode(GameMode.CREATIVE); player.setAllowFlight(true); player.setFlying(true);
        player.setHealth(player.getMaxHealth()); player.setFoodLevel(20); player.setSaturation(100);
        enteringPlayers.remove(uuid);
        FileConfiguration config=plugin.getConfig();
        String prefix=config.getString("messages.prefix","&8[&b沙盒&8] "); String msg=config.getString("messages.enter","&a已进入创意沙盒世界");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',prefix+msg));
    }
    public void exit(Player player) {
        UUID uuid=player.getUniqueId(); PlayerSnapshot snap=stateStore.load(uuid);
        if (snap==null||snap.state!=PlayerSnapshot.State.IN_SANDBOX) return;
        snap=PlayerSnapshot.fromPlayer(player); snap.state=PlayerSnapshot.State.NONE; stateStore.save(snap); exitingPlayers.put(uuid,"exiting");
        player.getInventory().clear(); player.getEnderChest().setContents(new ItemStack[0]);
        for (org.bukkit.potion.PotionEffect pe:player.getActivePotionEffects()) { player.removePotionEffect(pe.getType()); }
        player.setGameMode(GameMode.SURVIVAL); player.setAllowFlight(false); player.setFlying(false);
        ItemStack[] inv=stateStore.deserializeItemsFromBase64(snap.inventoryBase64); player.getInventory().setContents(inv);
        ItemStack[] ec=stateStore.deserializeItemsFromBase64(snap.enderChestBase64); player.getEnderChest().setContents(ec);
        player.setLevel(snap.level); player.setExp(Math.max(0,Math.min(1,snap.exp)));
        player.setHealth(Math.min(snap.health,player.getMaxHealth())); player.setFoodLevel(Math.max(0,snap.foodLevel)); player.setSaturation(Math.max(0,snap.saturation));
        player.setFireTicks(snap.fireTicks);
        for (org.bukkit.potion.PotionEffect pe:player.getActivePotionEffects()) { player.removePotionEffect(pe.getType()); }
        for (PlayerSnapshot.PotionEffectData ped:snap.potionEffects) { try{org.bukkit.potion.PotionEffect e=ped.toPotionEffect(); if(e!=null)player.addPotionEffect(e);}catch(Exception ignored){} }
        exitingPlayers.remove(uuid);
        FileConfiguration config=plugin.getConfig();
        String prefix=config.getString("messages.prefix","&8[&b沙盒&8] "); String msg=config.getString("messages.exit","&a已退出创意沙盒世界，回到主世界");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',prefix+msg));
    }
    public StateStore getStateStore() { return stateStore; }
}