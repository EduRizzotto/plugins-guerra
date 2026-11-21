package com.eduardo.teams.base;

import com.eduardo.teams.TeamsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gerencia as bases dos times (/base criar e /base remover).
 * Cada time pode ter no maximo 1 base (30 blocos para cada lado a partir do centro).
 * Dentro da base:
 * - Proibido colocar lava
 * - Cada time pode colocar e explodir no maximo 5 TNTs
 * - Qualquer outro bloco pode ser colocado e quebrado livremente
 */
public class BaseManager {

    public static final int BASE_RADIUS = 30;
    public static final int MAX_TNT_PER_TEAM = 5;
    public static final long TNT_CYCLE_DURATION_MS = 3L * 24L * 60L * 60L * 1000L; // 3 dias (72 horas)

    public static class TntQuota {
        private int count;
        private long cycleStartMs;

        public TntQuota(int count, long cycleStartMs) {
            this.count = count;
            this.cycleStartMs = cycleStartMs;
        }

        public int getCount() {
            return count;
        }

        public long getCycleStartMs() {
            return cycleStartMs;
        }

        public boolean isExpired(long nowMs) {
            return count <= 0 || (nowMs - cycleStartMs) >= TNT_CYCLE_DURATION_MS;
        }

        public long getRemainingMs(long nowMs) {
            return Math.max(0L, TNT_CYCLE_DURATION_MS - (nowMs - cycleStartMs));
        }
    }

    public static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    public static class TeamBase {
        private final String teamKey;
        private final String teamDisplayName;
        private final String worldName;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private final Map<String, TntQuota> tntQuotasByTeam = new LinkedHashMap<>();

        public TeamBase(String teamKey, String teamDisplayName, String worldName, int centerX, int centerY, int centerZ) {
            this.teamKey = teamKey.toLowerCase(Locale.ROOT);
            this.teamDisplayName = teamDisplayName;
            this.worldName = worldName;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
        }

        public String getTeamKey() {
            return teamKey;
        }

        public String getTeamDisplayName() {
            return teamDisplayName;
        }

        public String getWorldName() {
            return worldName;
        }

        public int getCenterX() {
            return centerX;
        }

        public int getCenterY() {
            return centerY;
        }

        public int getCenterZ() {
            return centerZ;
        }

        public int getMinX() {
            return centerX - BASE_RADIUS;
        }

        public int getMaxX() {
            return centerX + BASE_RADIUS;
        }

        public int getMinZ() {
            return centerZ - BASE_RADIUS;
        }

        public int getMaxZ() {
            return centerZ + BASE_RADIUS;
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
            int z = loc.getBlockZ();
            return x >= getMinX() && x <= getMaxX()
                    && z >= getMinZ() && z <= getMaxZ();
        }

        public boolean overlaps(String otherWorld, int otherCenterX, int otherCenterZ) {
            if (otherWorld == null || !otherWorld.equalsIgnoreCase(worldName)) {
                return false;
            }
            int otherMinX = otherCenterX - BASE_RADIUS;
            int otherMaxX = otherCenterX + BASE_RADIUS;
            int otherMinZ = otherCenterZ - BASE_RADIUS;
            int otherMaxZ = otherCenterZ + BASE_RADIUS;
            return getMinX() <= otherMaxX && getMaxX() >= otherMinX
                    && getMinZ() <= otherMaxZ && getMaxZ() >= otherMinZ;
        }

        public int getTntUsed(String attackingTeamName) {
            if (attackingTeamName == null) {
                return 0;
            }
            String key = attackingTeamName.toLowerCase(Locale.ROOT);
            TntQuota quota = tntQuotasByTeam.get(key);
            if (quota == null) {
                return 0;
            }
            long now = System.currentTimeMillis();
            if (quota.isExpired(now)) {
                tntQuotasByTeam.remove(key);
                return 0;
            }
            return quota.getCount();
        }

        public long getRemainingResetMs(String attackingTeamName) {
            if (attackingTeamName == null) {
                return 0L;
            }
            String key = attackingTeamName.toLowerCase(Locale.ROOT);
            TntQuota quota = tntQuotasByTeam.get(key);
            if (quota == null) {
                return 0L;
            }
            long now = System.currentTimeMillis();
            if (quota.isExpired(now)) {
                tntQuotasByTeam.remove(key);
                return 0L;
            }
            return quota.getRemainingMs(now);
        }

        public int incrementTntUsed(String attackingTeamName) {
            String key = attackingTeamName.toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            TntQuota quota = tntQuotasByTeam.get(key);
            if (quota == null || quota.isExpired(now)) {
                quota = new TntQuota(1, now);
                tntQuotasByTeam.put(key, quota);
                return 1;
            }
            quota.count++;
            return quota.count;
        }

        public void decrementTntUsed(String attackingTeamName) {
            if (attackingTeamName == null) {
                return;
            }
            String key = attackingTeamName.toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            TntQuota quota = tntQuotasByTeam.get(key);
            if (quota == null || quota.isExpired(now) || quota.count <= 1) {
                tntQuotasByTeam.remove(key);
            } else {
                quota.count--;
            }
        }

        public Map<String, TntQuota> getActiveTntQuotas() {
            long now = System.currentTimeMillis();
            tntQuotasByTeam.entrySet().removeIf(e -> e.getValue().isExpired(now));
            return Collections.unmodifiableMap(tntQuotasByTeam);
        }

        public void setTntQuotaRaw(String teamKey, int count, long cycleStartMs) {
            if (count > 0) {
                TntQuota quota = new TntQuota(count, cycleStartMs > 0 ? cycleStartMs : System.currentTimeMillis());
                if (!quota.isExpired(System.currentTimeMillis())) {
                    tntQuotasByTeam.put(teamKey.toLowerCase(Locale.ROOT), quota);
                }
            }
        }
    }

    private final TeamsPlugin plugin;
    private final File basesFile;
    private final Map<String, TeamBase> basesByTeam = new LinkedHashMap<>();
    // Rastreia blocos de TNT colocados (ainda nao explodidos) -> teamKey que colocou
    private final Map<Location, String> placedUnignitedTnt = new HashMap<>();

    public BaseManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.basesFile = new File(plugin.getDataFolder(), "bases.yml");
        loadBases();
    }

    public TeamBase getBaseByTeam(String teamName) {
        if (teamName == null) {
            return null;
        }
        return basesByTeam.get(teamName.toLowerCase(Locale.ROOT));
    }

    public TeamBase getBaseAt(Location loc) {
        if (loc == null || basesByTeam.isEmpty()) {
            return null;
        }
        for (TeamBase base : basesByTeam.values()) {
            if (base.contains(loc)) {
                return base;
            }
        }
        return null;
    }

    public TeamBase findOverlappingBase(String worldName, int centerX, int centerZ, String ignoreTeamName) {
        String ignoreKey = ignoreTeamName != null ? ignoreTeamName.toLowerCase(Locale.ROOT) : null;
        for (TeamBase base : basesByTeam.values()) {
            if (ignoreKey != null && base.getTeamKey().equals(ignoreKey)) {
                continue;
            }
            if (base.overlaps(worldName, centerX, centerZ)) {
                return base;
            }
        }
        return null;
    }

    public TeamBase createBase(String teamName, Location centerLoc) {
        String key = teamName.toLowerCase(Locale.ROOT);
        TeamBase base = new TeamBase(
                key,
                teamName,
                centerLoc.getWorld().getName(),
                centerLoc.getBlockX(),
                centerLoc.getBlockY(),
                centerLoc.getBlockZ()
        );
        basesByTeam.put(key, base);
        saveBases();
        return base;
    }

    public TeamBase removeBase(String teamName) {
        if (teamName == null) {
            return null;
        }
        TeamBase removed = basesByTeam.remove(teamName.toLowerCase(Locale.ROOT));
        if (removed != null) {
            placedUnignitedTnt.entrySet().removeIf(entry -> removed.contains(entry.getKey()));
            saveBases();
        }
        return removed;
    }

    public Collection<TeamBase> getAllBases() {
        return Collections.unmodifiableCollection(basesByTeam.values());
    }

    private Location normalizeBlockLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void registerPlacedTntBlock(Location loc, String teamKey) {
        Location norm = normalizeBlockLoc(loc);
        if (norm != null && teamKey != null) {
            placedUnignitedTnt.put(norm, teamKey.toLowerCase(Locale.ROOT));
        }
    }

    public String consumePlacedTntBlock(Location loc) {
        Location norm = normalizeBlockLoc(loc);
        return norm != null ? placedUnignitedTnt.remove(norm) : null;
    }

    public void loadBases() {
        basesByTeam.clear();
        if (!basesFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(basesFile);
        ConfigurationSection sec = cfg.getConfigurationSection("bases");
        if (sec == null) {
            return;
        }
        for (String teamKey : sec.getKeys(false)) {
            ConfigurationSection bSec = sec.getConfigurationSection(teamKey);
            if (bSec == null) {
                continue;
            }
            String displayName = bSec.getString("teamName", teamKey);
            String worldName = bSec.getString("world");
            if (worldName == null || worldName.isBlank()) {
                continue;
            }
            int cx = bSec.getInt("centerX");
            int cy = bSec.getInt("centerY");
            int cz = bSec.getInt("centerZ");
            TeamBase base = new TeamBase(teamKey, displayName, worldName, cx, cy, cz);

            ConfigurationSection tntSec = bSec.getConfigurationSection("tntUsed");
            if (tntSec != null) {
                for (String atkTeam : tntSec.getKeys(false)) {
                    if (tntSec.isConfigurationSection(atkTeam)) {
                        ConfigurationSection qSec = tntSec.getConfigurationSection(atkTeam);
                        if (qSec != null) {
                            base.setTntQuotaRaw(atkTeam, qSec.getInt("count", 0), qSec.getLong("startMs", System.currentTimeMillis()));
                        }
                    } else {
                        base.setTntQuotaRaw(atkTeam, tntSec.getInt(atkTeam, 0), System.currentTimeMillis());
                    }
                }
            }
            basesByTeam.put(teamKey.toLowerCase(Locale.ROOT), base);
        }
        if (!basesByTeam.isEmpty()) {
            plugin.getLogger().info("[Bases] Carregadas " + basesByTeam.size() + " base(s) de times de bases.yml.");
        }
    }

    public void saveBases() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (TeamBase base : basesByTeam.values()) {
            String path = "bases." + base.getTeamKey();
            cfg.set(path + ".teamName", base.getTeamDisplayName());
            cfg.set(path + ".world", base.getWorldName());
            cfg.set(path + ".centerX", base.getCenterX());
            cfg.set(path + ".centerY", base.getCenterY());
            cfg.set(path + ".centerZ", base.getCenterZ());
            for (Map.Entry<String, TntQuota> entry : base.getActiveTntQuotas().entrySet()) {
                cfg.set(path + ".tntUsed." + entry.getKey() + ".count", entry.getValue().getCount());
                cfg.set(path + ".tntUsed." + entry.getKey() + ".startMs", entry.getValue().getCycleStartMs());
            }
        }
        try {
            cfg.save(basesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Bases] Falha ao salvar bases.yml: " + e.getMessage());
        }
    }
}
