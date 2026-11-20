package com.eduardo.teams.zone;

import com.eduardo.teams.TeamsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gerencia areas protegidas criadas por coordenadas (/proteger e /safezone),
 * com persistencia automatica em plugins/TeamsPlugin/zones.yml.
 */
public class ZoneManager {

    public static class ProtectedZone {
        private final String name;
        private final String worldName;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final boolean blockPvp;

        public ProtectedZone(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2, boolean blockPvp) {
            this.name = name;
            this.worldName = worldName;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
            this.blockPvp = blockPvp;
        }

        public String getName() {
            return name;
        }

        public String getWorldName() {
            return worldName;
        }

        public int getMinX() {
            return minX;
        }

        public int getMinY() {
            return minY;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxY() {
            return maxY;
        }

        public int getMaxZ() {
            return maxZ;
        }

        public boolean isBlockPvp() {
            return blockPvp;
        }

        public boolean contains(Location loc) {
            if (loc == null) {
                return false;
            }
            World world = loc.getWorld();
            if (world == null || !world.getName().equalsIgnoreCase(worldName)) {
                return false;
            }
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        public long getVolume() {
            return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        }
    }

    private final TeamsPlugin plugin;
    private final File zonesFile;
    private final Map<String, ProtectedZone> zones = new LinkedHashMap<>();

    public ZoneManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        loadZones();
    }

    public boolean isBuildProtected(Location loc) {
        if (loc == null || zones.isEmpty()) {
            return false;
        }
        for (ProtectedZone zone : zones.values()) {
            if (zone.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPvpBlocked(Location loc) {
        if (loc == null || zones.isEmpty()) {
            return false;
        }
        for (ProtectedZone zone : zones.values()) {
            if (zone.isBlockPvp() && zone.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    public ProtectedZone getZone(String name) {
        if (name == null) {
            return null;
        }
        return zones.get(name.toLowerCase(Locale.ROOT));
    }

    public String generateNextName(boolean blockPvp) {
        String prefix = blockPvp ? "safezone-" : "zona-";
        int idx = 1;
        while (zones.containsKey((prefix + idx).toLowerCase(Locale.ROOT))) {
            idx++;
        }
        return prefix + idx;
    }

    public void addZone(ProtectedZone zone) {
        zones.put(zone.getName().toLowerCase(Locale.ROOT), zone);
        saveZones();
    }

    public ProtectedZone removeZone(String name) {
        if (name == null) {
            return null;
        }
        ProtectedZone removed = zones.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveZones();
        }
        return removed;
    }

    public int clearZonesByType(boolean blockPvp) {
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, ProtectedZone> entry : zones.entrySet()) {
            if (entry.getValue().isBlockPvp() == blockPvp) {
                keysToRemove.add(entry.getKey());
            }
        }
        for (String key : keysToRemove) {
            zones.remove(key);
        }
        if (!keysToRemove.isEmpty()) {
            saveZones();
        }
        return keysToRemove.size();
    }

    public Collection<ProtectedZone> getZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public void loadZones() {
        zones.clear();
        if (!zonesFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(zonesFile);
        ConfigurationSection section = cfg.getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection zSec = section.getConfigurationSection(key);
            if (zSec == null) {
                continue;
            }
            String name = zSec.getString("name", key);
            String world = zSec.getString("world");
            if (world == null || world.isBlank()) {
                continue;
            }
            int minX = zSec.getInt("minX");
            int minY = zSec.getInt("minY");
            int minZ = zSec.getInt("minZ");
            int maxX = zSec.getInt("maxX");
            int maxY = zSec.getInt("maxY");
            int maxZ = zSec.getInt("maxZ");
            boolean blockPvp = zSec.getBoolean("blockPvp", false);

            ProtectedZone zone = new ProtectedZone(name, world, minX, minY, minZ, maxX, maxY, maxZ, blockPvp);
            zones.put(name.toLowerCase(Locale.ROOT), zone);
        }
        if (!zones.isEmpty()) {
            plugin.getLogger().info("[Zonas] Carregadas " + zones.size() + " area(s) protegida(s) de zones.yml.");
        }
    }

    public void saveZones() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (ProtectedZone zone : zones.values()) {
            String path = "zones." + zone.getName().toLowerCase(Locale.ROOT);
            cfg.set(path + ".name", zone.getName());
            cfg.set(path + ".world", zone.getWorldName());
            cfg.set(path + ".minX", zone.getMinX());
            cfg.set(path + ".minY", zone.getMinY());
            cfg.set(path + ".minZ", zone.getMinZ());
            cfg.set(path + ".maxX", zone.getMaxX());
            cfg.set(path + ".maxY", zone.getMaxY());
            cfg.set(path + ".maxZ", zone.getMaxZ());
            cfg.set(path + ".blockPvp", zone.isBlockPvp());
        }
        try {
            cfg.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Zonas] Falha ao salvar zones.yml: " + e.getMessage());
        }
    }
}
