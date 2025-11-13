package com.example.threeblue;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public class ThreeBlueHearts extends JavaPlugin implements Listener {

    private File dataFile;
    private FileConfiguration dataConfig;

    private final int START_LIVES = 3;      // start with 3 blue hearts
    private final long INVULNERABLE_TICKS = 60L; // 3 seconds (20 ticks = 1s)

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        setupDataFile();

        for (Player p : Bukkit.getOnlinePlayers()) {
            ensurePlayerEntry(p.getUniqueId());
            if (isInOverworld(p)) updatePlayerAbsorption(p);
        }

        getLogger().info("ThreeBlueHearts enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("ThreeBlueHearts disabled.");
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create data.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    private void ensurePlayerEntry(UUID uuid) {
        String key = uuid.toString();
        if (!dataConfig.contains(key)) {
            dataConfig.set(key, START_LIVES);
            saveData();
        }
    }

    private int getLives(UUID uuid) {
        String key = uuid.toString();
        if (!dataConfig.contains(key)) {
            dataConfig.set(key, START_LIVES);
            saveData();
            return START_LIVES;
        }
        return dataConfig.getInt(key, START_LIVES);
    }

    private void setLives(UUID uuid, int lives) {
        if (lives < 0) lives = 0;
        dataConfig.set(uuid.toString(), lives);
        saveData();
    }

    private void decrementLives(Player victim) {
        UUID id = victim.getUniqueId();
        int cur = getLives(id);
        cur -= 1;
        setLives(id, cur);

        if (cur <= 0) {
            String reason = "Du hast alle Blauen Herzen verloren.";
            Bukkit.getBanList(BanList.Type.NAME).addBan(victim.getName(), reason, null, "ThreeBlueHearts");
            victim.kickPlayer("Du hast alle Blauen Herzen verloren und wurdest gebannt.");
            getLogger().info(victim.getName() + " wurde gebannt (keine blauen Herzen mehr).");
        } else {
            World world = victim.getWorld();
            Location spawn = world.getSpawnLocation();
            victim.teleport(spawn);
            double maxHealth = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            victim.setHealth(Math.max(1.0, Math.min(maxHealth, maxHealth)));
            victim.setFireTicks(0);
            victim.setNoDamageTicks((int) INVULNERABLE_TICKS);
            victim.sendMessage("§cDu hast ein blaues Herz verloren! §eVerbleibende: §b" + cur);
            victim.sendTitle("§cVerloren!", "§eBlaues Herz verloren. Verbleibend: §b" + cur, 10, 60, 10);
            updatePlayerAbsorption(victim);
        }
    }

    private void updatePlayerAbsorption(Player p) {
        int lives = getLives(p.getUniqueId());
        double absorptionPoints = lives * 2.0;
        p.setAbsorptionAmount(absorptionPoints);
        sendActionBarLives(p, lives);
    }

    private void sendActionBarLives(Player p, int lives) {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < lives; i++) hearts.append("❤");
        for (int i = lives; i < START_LIVES; i++) hearts.append("♡");
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§bBlaue Herzen: " + hearts.toString()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        ensurePlayerEntry(p.getUniqueId());
        if (isInOverworld(p)) updatePlayerAbsorption(p);
        // set resource pack if configured
        String pack = getConfig().getString("resource-pack", "");
        boolean require = getConfig().getBoolean("require-resource-pack", true);
        if (pack != null && !pack.isEmpty()) {
            p.setResourcePack(pack, "");
            if (require) getServer().dispatchCommand(Bukkit.getConsoleSender(), "resourcepack send " + p.getName() + " " + pack);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        Entity ent = e.getEntity();
        if (!(ent instanceof Player)) return;
        Player victim = (Player) ent;

        if (!isInOverworld(victim)) return;

        Player damager = null;
        Entity dam = e.getDamager();
        if (dam instanceof Player) damager = (Player) dam;
        else if (dam instanceof Projectile) {
            Projectile proj = (Projectile) dam;
            if (proj.getShooter() instanceof Player) damager = (Player) proj.getShooter();
        }

        if (damager == null) return;

        double finalDamage = e.getFinalDamage();
        double health = victim.getHealth();
        if (finalDamage >= health) {
            e.setCancelled(true);
            decrementLives(victim);
            if (getLives(victim.getUniqueId()) > 0) updatePlayerAbsorption(victim);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        // intentionally left blank for non-PvP
    }

    private boolean isInOverworld(Player p) {
        return p.getWorld().getEnvironment() == World.Environment.NORMAL;
    }
}
