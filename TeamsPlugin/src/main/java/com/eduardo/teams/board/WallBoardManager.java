package com.eduardo.teams.board;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WallBoardManager {

    public enum BoardType {
        CLANS("clans", "Ranking de Clãs", NamedTextColor.GOLD, "CLANS TOP"),
        PLAYERS("players", "Ranking de Players", NamedTextColor.AQUA, "TOP PVP"),
        HIGHLIGHTS("destaques", "Destaques & Resumo", NamedTextColor.LIGHT_PURPLE, "DESTAQUES"),
        ROTATING("rotativo", "Painel Dinâmico (3 em 1)", NamedTextColor.GREEN, "RANKING");

        private final String id;
        private final String displayName;
        private final NamedTextColor themeColor;
        private final String mapHeader;

        BoardType(String id, String displayName, NamedTextColor themeColor, String mapHeader) {
            this.id = id;
            this.displayName = displayName;
            this.themeColor = themeColor;
            this.mapHeader = mapHeader;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public NamedTextColor getThemeColor() {
            return themeColor;
        }

        public String getMapHeader() {
            return mapHeader;
        }

        public static BoardType fromString(String input) {
            if (input == null) return null;
            String norm = input.trim().toLowerCase(Locale.ROOT);
            return switch (norm) {
                case "clans", "clan", "clas", "cla", "times", "teams" -> CLANS;
                case "players", "player", "jogadores", "pvp", "top" -> PLAYERS;
                case "highlights", "destaques", "resumo", "geral", "server" -> HIGHLIGHTS;
                case "rotating", "rotativo", "dinamico", "auto", "all" -> ROTATING;
                default -> {
                    for (BoardType t : values()) {
                        if (t.name().equalsIgnoreCase(norm) || t.id.equalsIgnoreCase(norm)) {
                            yield t;
                        }
                    }
                    yield null;
                }
            };
        }
    }

    public enum DisplayScale {
        MOLDURA_COM_MAPA(1.05f, true, "Moldura Pixel-Art + Painel Mural (1.0x)"),
        MURAL_NORMAL(1.05f, false, "Painel Mural Limpo (1.0x)"),
        MURAL_GRANDE(1.35f, false, "Painel Mural Grande (1.35x)"),
        MURAL_GIGANTE(1.70f, false, "Telão de Lobby (1.70x)");

        private final float scale;
        private final boolean showFrameAndMap;
        private final String label;

        DisplayScale(float scale, boolean showFrameAndMap, String label) {
            this.scale = scale;
            this.showFrameAndMap = showFrameAndMap;
            this.label = label;
        }

        public float getScale() {
            return scale;
        }

        public boolean isShowFrameAndMap() {
            return showFrameAndMap;
        }

        public String getLabel() {
            return label;
        }

        public DisplayScale next() {
            DisplayScale[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    public static class PlacedBoard {
        private final String id;
        private final String worldName;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final BlockFace facing;
        private BoardType boardType;
        private BoardType activeTab;
        private DisplayScale displayScale;
        private UUID frameUuid;
        private UUID textDisplayUuid;
        private int mapId = -1;
        private String lastRenderedSignature = "";

        public PlacedBoard(String id, String worldName, int blockX, int blockY, int blockZ,
                           BlockFace facing, BoardType boardType, DisplayScale displayScale) {
            this.id = id;
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.facing = facing;
            this.boardType = boardType;
            this.activeTab = (boardType == BoardType.ROTATING) ? BoardType.CLANS : boardType;
            this.displayScale = displayScale != null ? displayScale : DisplayScale.MOLDURA_COM_MAPA;
        }

        public String getId() { return id; }
        public String getWorldName() { return worldName; }
        public int getBlockX() { return blockX; }
        public int getBlockY() { return blockY; }
        public int getBlockZ() { return blockZ; }
        public BlockFace getFacing() { return facing; }
        public BoardType getBoardType() { return boardType; }
        public void setBoardType(BoardType boardType) { this.boardType = boardType; }
        public BoardType getActiveTab() { return activeTab; }
        public void setActiveTab(BoardType activeTab) { this.activeTab = activeTab; }
        public DisplayScale getDisplayScale() { return displayScale; }
        public void setDisplayScale(DisplayScale displayScale) { this.displayScale = displayScale; }
        public UUID getFrameUuid() { return frameUuid; }
        public void setFrameUuid(UUID frameUuid) { this.frameUuid = frameUuid; }
        public UUID getTextDisplayUuid() { return textDisplayUuid; }
        public void setTextDisplayUuid(UUID textDisplayUuid) { this.textDisplayUuid = textDisplayUuid; }
        public int getMapId() { return mapId; }
        public void setMapId(int mapId) { this.mapId = mapId; }
        public String getLastRenderedSignature() { return lastRenderedSignature; }
        public void setLastRenderedSignature(String lastRenderedSignature) { this.lastRenderedSignature = lastRenderedSignature; }

        public void cycleManualTab() {
            this.activeTab = switch (this.activeTab) {
                case CLANS -> BoardType.PLAYERS;
                case PLAYERS -> BoardType.HIGHLIGHTS;
                default -> BoardType.CLANS;
            };
        }
    }

    public record ClanRankingEntry(
            Team team,
            int points,
            int kills,
            int deaths,
            double kdr,
            int balance,
            int onlineMembers,
            int totalMembers,
            String leaderName
    ) {}

    public record PlayerRankingEntry(
            UUID uuid,
            String name,
            boolean online,
            Team team,
            int kills,
            int deaths,
            double kdr,
            int balance,
            int clanKillSharePercent
    ) {}

    public record ServerSummary(
            int totalClans,
            int totalPlayersWithStats,
            int totalKills,
            int totalDeaths,
            ClanRankingEntry topClan,
            ClanRankingEntry runnerUpClan,
            PlayerRankingEntry topKiller,
            PlayerRankingEntry bestKdrPlayer,
            PlayerRankingEntry mostHuntedPlayer
    ) {}

    public static class BoardMenuHolder implements InventoryHolder {
        private final BoardType tab;
        public BoardMenuHolder(BoardType tab) {
            this.tab = tab;
        }
        public BoardType getTab() {
            return tab;
        }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final NamespacedKey boardItemKey;
    private final NamespacedKey placedBoardKey;
    private final Map<String, PlacedBoard> boardsById = new LinkedHashMap<>();
    private final Map<UUID, String> usercacheNames = new HashMap<>();
    private File boardsFile;
    private BukkitTask updateTask;
    private int tickCounter = 0;

    public WallBoardManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.boardItemKey = new NamespacedKey(plugin, "board_type");
        this.placedBoardKey = new NamespacedKey(plugin, "placed_board_id");
        reloadUsercacheNames();
        loadBoards();
        startUpdateLoop();
    }

    public NamespacedKey getBoardItemKey() {
        return boardItemKey;
    }

    public NamespacedKey getPlacedBoardKey() {
        return placedBoardKey;
    }

    public Collection<PlacedBoard> getAllBoards() {
        return Collections.unmodifiableCollection(boardsById.values());
    }

    private void startUpdateLoop() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        // Runs every 60 ticks (3 seconds); rotates ROTATING boards every 3 cycles (9 seconds)
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (boardsById.isEmpty()) {
                return;
            }
            tickCounter++;
            boolean rotatePhase = (tickCounter % 3 == 0);
            List<ClanRankingEntry> clans = collectClanRankings();
            List<PlayerRankingEntry> players = collectPlayerRankings();
            ServerSummary summary = collectServerSummary(clans, players);
            String dataDigest = buildRankingsDigest(clans, players, summary);

            for (PlacedBoard board : boardsById.values()) {
                if (board.getBoardType() == BoardType.ROTATING && rotatePhase) {
                    board.cycleManualTab();
                }
                refreshSingleBoard(board, clans, players, summary, dataDigest);
            }
        }, 60L, 60L);
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        saveBoards();
    }

    public ItemStack createBoardFrameItem(BoardType type) {
        ItemStack item = new ItemStack(Material.GLOW_ITEM_FRAME, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String icon = switch (type) {
                case CLANS -> "🏆";
                case PLAYERS -> "⚔";
                case HIGHLIGHTS -> "🌟";
                case ROTATING -> "🔄";
            };
            meta.displayName(
                    Component.text(icon + " Moldura de Placar: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text(type.getDisplayName(), type.getThemeColor(), TextDecoration.BOLD))
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Item Exclusivo de Administração", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            switch (type) {
                case CLANS -> {
                    lore.add(Component.text("Exibe na parede o ", NamedTextColor.GRAY)
                            .append(Component.text("Ranking Oficial de Clãs", NamedTextColor.GOLD))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("com Tags, Abates, Mortes, KDR, Barra de", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Domínio, Líder e Membros Online.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                case PLAYERS -> {
                    lore.add(Component.text("Exibe na parede o ", NamedTextColor.GRAY)
                            .append(Component.text("Top Jogadores PvP", NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("com Clã atual, Abates, Mortes, KDR,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Saldo e % de Participação no Clã.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                case HIGHLIGHTS -> {
                    lore.add(Component.text("Exibe na parede os ", NamedTextColor.GRAY)
                            .append(Component.text("Troféus & Destaques", NamedTextColor.LIGHT_PURPLE))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("do servidor: Clã Dominante, Ceifador,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Melhor KDR, Rivalidade e Totais Globais.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                case ROTATING -> {
                    lore.add(Component.text("Alterna automaticamente a cada ", NamedTextColor.GRAY)
                            .append(Component.text("8 segundos", NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("entre Clãs, Top Players e Destaques!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("▸ Como usar:", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" • Coloque em qualquer parede do Lobby", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" • Clique Direito: ", NamedTextColor.AQUA)
                    .append(Component.text("Alternar aba na parede", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" • Clique Esquerdo: ", NamedTextColor.AQUA)
                    .append(Component.text("Abrir menu detalhado", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" • Shift + Clique Direito: ", NamedTextColor.GOLD)
                    .append(Component.text("Mudar tamanho/estilo", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" • Shift + Clique Esquerdo: ", NamedTextColor.RED)
                    .append(Component.text("Remover da parede", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(boardItemKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public BoardType getBoardTypeFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(boardItemKey, PersistentDataType.STRING);
        return BoardType.fromString(raw);
    }

    public PlacedBoard registerPlacedBoard(ItemFrame frame, BoardType type) {
        Location loc = frame.getLocation().getBlock().getLocation();
        String worldName = loc.getWorld().getName();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        BlockFace facing = frame.getFacing();
        if (!isHorizontalWallFace(facing)) {
            facing = BlockFace.SOUTH;
        }

        // Remove any existing board at this exact block
        PlacedBoard existingAtBlock = getBoardAtBlock(worldName, bx, by, bz);
        if (existingAtBlock != null) {
            removeBoard(existingAtBlock, false);
        }

        String id = worldName + "_" + bx + "_" + by + "_" + bz + "_" + facing.name();
        PlacedBoard board = new PlacedBoard(id, worldName, bx, by, bz, facing, type, DisplayScale.MOLDURA_COM_MAPA);
        board.setFrameUuid(frame.getUniqueId());
        frame.getPersistentDataContainer().set(placedBoardKey, PersistentDataType.STRING, id);

        boardsById.put(id, board);
        refreshSingleBoard(board);
        saveBoards();
        return board;
    }

    public PlacedBoard getBoardByEntity(Entity entity) {
        if (entity == null) return null;
        String id = entity.getPersistentDataContainer().get(placedBoardKey, PersistentDataType.STRING);
        if (id != null && boardsById.containsKey(id)) {
            return boardsById.get(id);
        }
        UUID uuid = entity.getUniqueId();
        for (PlacedBoard board : boardsById.values()) {
            if (uuid.equals(board.getFrameUuid()) || uuid.equals(board.getTextDisplayUuid())) {
                return board;
            }
        }
        if (entity instanceof ItemFrame) {
            Location bLoc = entity.getLocation().getBlock().getLocation();
            return getBoardAtBlock(bLoc.getWorld().getName(), bLoc.getBlockX(), bLoc.getBlockY(), bLoc.getBlockZ());
        }
        return null;
    }

    public PlacedBoard getBoardAtBlock(String worldName, int x, int y, int z) {
        for (PlacedBoard board : boardsById.values()) {
            if (board.getWorldName().equalsIgnoreCase(worldName)
                    && board.getBlockX() == x
                    && board.getBlockY() == y
                    && board.getBlockZ() == z) {
                return board;
            }
        }
        return null;
    }

    public PlacedBoard findNearestBoard(Location origin, double maxDistance) {
        if (origin == null || origin.getWorld() == null) return null;
        PlacedBoard nearest = null;
        double bestDistSq = maxDistance * maxDistance;
        for (PlacedBoard board : boardsById.values()) {
            if (!board.getWorldName().equalsIgnoreCase(origin.getWorld().getName())) continue;
            Location bLoc = new Location(origin.getWorld(), board.getBlockX() + 0.5, board.getBlockY() + 0.5, board.getBlockZ() + 0.5);
            double dSq = origin.distanceSquared(bLoc);
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                nearest = board;
            }
        }
        return nearest;
    }

    public void removeBoard(PlacedBoard board, boolean removeFrameEntity) {
        if (board == null) return;
        boardsById.remove(board.getId());
        World world = Bukkit.getWorld(board.getWorldName());
        if (world != null) {
            if (board.getTextDisplayUuid() != null) {
                Entity td = Bukkit.getEntity(board.getTextDisplayUuid());
                if (td != null) {
                    td.remove();
                }
            }
            // Also clean up any stray TextDisplay tagged with this board ID in nearby entities
            Location center = new Location(world, board.getBlockX() + 0.5, board.getBlockY() + 0.8, board.getBlockZ() + 0.5);
            for (Entity nearby : world.getNearbyEntities(center, 2.5, 3.0, 2.5)) {
                String tagId = nearby.getPersistentDataContainer().get(placedBoardKey, PersistentDataType.STRING);
                if (board.getId().equals(tagId)) {
                    if (nearby instanceof TextDisplay || (removeFrameEntity && nearby instanceof ItemFrame)) {
                        nearby.remove();
                    }
                }
            }
            if (removeFrameEntity && board.getFrameUuid() != null) {
                Entity frame = Bukkit.getEntity(board.getFrameUuid());
                if (frame != null) {
                    frame.remove();
                }
            }
        }
        saveBoards();
    }

    public int removeAllBoards() {
        List<PlacedBoard> all = new ArrayList<>(boardsById.values());
        for (PlacedBoard b : all) {
            removeBoard(b, true);
        }
        return all.size();
    }

    public void refreshAllBoards() {
        reloadUsercacheNames();
        if (boardsById.isEmpty()) {
            return;
        }
        List<ClanRankingEntry> clans = collectClanRankings();
        List<PlayerRankingEntry> players = collectPlayerRankings();
        ServerSummary summary = collectServerSummary(clans, players);
        String dataDigest = buildRankingsDigest(clans, players, summary);
        for (PlacedBoard board : boardsById.values()) {
            board.setLastRenderedSignature("");
            refreshSingleBoard(board, clans, players, summary, dataDigest);
        }
    }

    public void refreshSingleBoard(PlacedBoard board) {
        List<ClanRankingEntry> clans = collectClanRankings();
        List<PlayerRankingEntry> players = collectPlayerRankings();
        ServerSummary summary = collectServerSummary(clans, players);
        board.setLastRenderedSignature("");
        refreshSingleBoard(board, clans, players, summary, buildRankingsDigest(clans, players, summary));
    }

    private String buildRankingsDigest(List<ClanRankingEntry> clans, List<PlayerRankingEntry> players, ServerSummary summary) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(summary.totalClans()).append(':').append(summary.totalKills()).append(':').append(summary.totalDeaths()).append('|');
        for (int i = 0; i < Math.min(6, clans.size()); i++) {
            ClanRankingEntry c = clans.get(i);
            sb.append(c.team().getName()).append(',').append(c.team().getTag()).append(',').append(c.points())
                    .append(',').append(c.kills()).append(',').append(c.deaths()).append(',').append(c.onlineMembers()).append(';');
        }
        sb.append('|');
        for (int i = 0; i < Math.min(8, players.size()); i++) {
            PlayerRankingEntry p = players.get(i);
            sb.append(p.name()).append(',').append(p.online()).append(',').append(p.kills()).append(',').append(p.deaths()).append(';');
        }
        return sb.toString();
    }

    private void refreshSingleBoard(PlacedBoard board,
                                    List<ClanRankingEntry> clans,
                                    List<PlayerRankingEntry> players,
                                    ServerSummary summary,
                                    String dataDigest) {
        World world = Bukkit.getWorld(board.getWorldName());
        if (world == null) return;
        if (!world.isChunkLoaded(board.getBlockX() >> 4, board.getBlockZ() >> 4)) {
            return;
        }

        BoardType displayTab = (board.getBoardType() == BoardType.ROTATING) ? board.getActiveTab() : board.getBoardType();
        String signature = displayTab.name() + "|" + board.getDisplayScale().name() + "|" + dataDigest;
        boolean entitiesValid = (board.getFrameUuid() != null && Bukkit.getEntity(board.getFrameUuid()) != null)
                && (board.getTextDisplayUuid() != null && Bukkit.getEntity(board.getTextDisplayUuid()) != null);
        if (entitiesValid && signature.equals(board.getLastRenderedSignature())) {
            return;
        }
        board.setLastRenderedSignature(signature);

        // 1. Ensure and update the ItemFrame on the wall
        ItemFrame frame = findOrRestoreFrame(world, board);
        if (frame != null) {
            frame.getPersistentDataContainer().set(placedBoardKey, PersistentDataType.STRING, board.getId());
            frame.setSilent(true);
            frame.setFixed(false);

            if (board.getDisplayScale().isShowFrameAndMap()) {
                frame.setVisible(true);
                ItemStack mapStack = createOrUpdateMapItem(world, board, clans, players, summary);
                frame.setItem(mapStack, false);
            } else {
                frame.setItem(null, false);
                frame.setVisible(false);
            }
        }

        // 2. Ensure and update the Wall-Mounted TextDisplay (Billboard.FIXED, flush with the wall)
        TextDisplay display = findOrSpawnTextDisplay(world, board);
        if (display != null) {
            Location targetLoc = calculateDisplayLocation(world, board);
            if (display.getLocation().distanceSquared(targetLoc) > 0.0004
                    || Math.abs(display.getLocation().getYaw() - targetLoc.getYaw()) > 0.5f) {
                display.teleport(targetLoc);
            }
            display.setRotation(targetLoc.getYaw(), 0f);
            display.setBillboard(Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(215, 12, 16, 26));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setLineWidth(360);

            float scale = board.getDisplayScale().getScale();
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            ));

            Component content = buildWallPanelText(board, clans, players, summary);
            display.text(content);
        }
    }

    private ItemFrame findOrRestoreFrame(World world, PlacedBoard board) {
        if (board.getFrameUuid() != null) {
            Entity e = Bukkit.getEntity(board.getFrameUuid());
            if (e instanceof ItemFrame itemFrame && itemFrame.isValid()) {
                return itemFrame;
            }
        }
        Location center = new Location(world, board.getBlockX() + 0.5, board.getBlockY() + 0.5, board.getBlockZ() + 0.5);
        for (Entity nearby : world.getNearbyEntities(center, 0.9, 0.9, 0.9)) {
            if (nearby instanceof ItemFrame itemFrame) {
                String tag = itemFrame.getPersistentDataContainer().get(placedBoardKey, PersistentDataType.STRING);
                if (board.getId().equals(tag) || (itemFrame.getLocation().getBlockX() == board.getBlockX()
                        && itemFrame.getLocation().getBlockY() == board.getBlockY()
                        && itemFrame.getLocation().getBlockZ() == board.getBlockZ())) {
                    board.setFrameUuid(itemFrame.getUniqueId());
                    itemFrame.getPersistentDataContainer().set(placedBoardKey, PersistentDataType.STRING, board.getId());
                    return itemFrame;
                }
            }
        }
        return null;
    }

    private TextDisplay findOrSpawnTextDisplay(World world, PlacedBoard board) {
        if (board.getTextDisplayUuid() != null) {
            Entity e = Bukkit.getEntity(board.getTextDisplayUuid());
            if (e instanceof TextDisplay td && td.isValid()) {
                return td;
            }
        }
        Location targetLoc = calculateDisplayLocation(world, board);
        for (Entity nearby : world.getNearbyEntities(targetLoc, 2.0, 2.5, 2.0)) {
            if (nearby instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(placedBoardKey, PersistentDataType.STRING);
                if (board.getId().equals(tag)) {
                    board.setTextDisplayUuid(td.getUniqueId());
                    return td;
                }
            }
        }
        TextDisplay spawned = world.spawn(targetLoc, TextDisplay.class, td -> {
            td.setPersistent(true);
            td.getPersistentDataContainer().set(placedBoardKey, PersistentDataType.STRING, board.getId());
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(215, 12, 16, 26));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setLineWidth(360);
        });
        board.setTextDisplayUuid(spawned.getUniqueId());
        return spawned;
    }

    private Location calculateDisplayLocation(World world, PlacedBoard board) {
        double bx = board.getBlockX();
        double by = board.getBlockY();
        double bz = board.getBlockZ();
        double wallOffset = 0.045; // Flush in front of the wall surface

        double x = bx + 0.5;
        double z = bz + 0.5;
        float yaw = 0f;

        switch (board.getFacing()) {
            case SOUTH -> {
                z = bz + wallOffset;
                yaw = 0f;
            }
            case NORTH -> {
                z = bz + 1.0 - wallOffset;
                yaw = 180f;
            }
            case EAST -> {
                x = bx + wallOffset;
                yaw = -90f;
            }
            case WEST -> {
                x = bx + 1.0 - wallOffset;
                yaw = 90f;
            }
            default -> {
                z = bz + wallOffset;
                yaw = 0f;
            }
        }

        // If frame + pixel-art map is visible, position the panel right above the 1x1 frame;
        // otherwise position the panel starting at the base of the block where the frame was placed.
        double y = board.getDisplayScale().isShowFrameAndMap() ? (by + 0.96) : (by + 0.08);
        return new Location(world, x, y, z, yaw, 0f);
    }

    @SuppressWarnings("deprecation")
    private ItemStack createOrUpdateMapItem(World world, PlacedBoard board,
                                            List<ClanRankingEntry> clans,
                                            List<PlayerRankingEntry> players,
                                            ServerSummary summary) {
        MapView view = null;
        if (board.getMapId() >= 0) {
            view = Bukkit.getMap(board.getMapId());
        }
        if (view == null) {
            view = Bukkit.createMap(world);
            board.setMapId(view.getId());
        }
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }
        BoardType displayTab = (board.getBoardType() == BoardType.ROTATING) ? board.getActiveTab() : board.getBoardType();
        view.addRenderer(new BoardMapRenderer(displayTab, clans, players, summary));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            mapItem.setItemMeta(meta);
        }
        return mapItem;
    }

    private Component buildWallPanelText(PlacedBoard board,
                                         List<ClanRankingEntry> clans,
                                         List<PlayerRankingEntry> players,
                                         ServerSummary summary) {
        BoardType tab = (board.getBoardType() == BoardType.ROTATING) ? board.getActiveTab() : board.getBoardType();
        TextComponent.Builder out = Component.text();

        // Top decorative bar
        out.append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
                .append(Component.newline());

        if (board.getBoardType() == BoardType.ROTATING) {
            out.append(buildRotatingTabsHeader(tab)).append(Component.newline());
        }

        switch (tab) {
            case CLANS -> appendClansPanel(out, clans, summary);
            case PLAYERS -> appendPlayersPanel(out, players, summary);
            case HIGHLIGHTS -> appendHighlightsPanel(out, clans, players, summary);
            default -> appendClansPanel(out, clans, summary);
        }

        // Bottom interactive footer
        out.append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
                .append(Component.newline())
                .append(Component.text("🖱 Clique Direito: ", NamedTextColor.GOLD)
                        .append(Component.text("Trocar Aba", NamedTextColor.WHITE))
                        .append(Component.text("  │  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("🖱 Clique Esq: ", NamedTextColor.AQUA))
                        .append(Component.text("Menu Detalhado", NamedTextColor.WHITE)));

        return out.build();
    }

    private Component buildRotatingTabsHeader(BoardType currentTab) {
        Component cTab = (currentTab == BoardType.CLANS)
                ? Component.text("【 🏆 CLÃS 】", NamedTextColor.GOLD, TextDecoration.BOLD)
                : Component.text("  🏆 Clãs  ", NamedTextColor.GRAY);
        Component pTab = (currentTab == BoardType.PLAYERS)
                ? Component.text("【 ⚔ PLAYERS 】", NamedTextColor.AQUA, TextDecoration.BOLD)
                : Component.text("  ⚔ Players  ", NamedTextColor.GRAY);
        Component hTab = (currentTab == BoardType.HIGHLIGHTS)
                ? Component.text("【 🌟 DESTAQUES 】", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                : Component.text("  🌟 Destaques  ", NamedTextColor.GRAY);

        return Component.empty()
                .append(cTab)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(pTab)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(hTab);
    }

    private void appendClansPanel(TextComponent.Builder out, List<ClanRankingEntry> clans, ServerSummary summary) {
        int totalClanPoints = clans.stream().mapToInt(ClanRankingEntry::points).sum();
        int totalClanKills = clans.stream().mapToInt(ClanRankingEntry::kills).sum();

        out.append(Component.text("🏆 ", NamedTextColor.GOLD))
                .append(Component.text("PLACAR OFICIAL DE CLÃS", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" 🏆", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Clãs Ativos: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(summary.totalClans()), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("  •  Pontos: ", NamedTextColor.GRAY))
                .append(Component.text(totalClanPoints + " ⭐", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("  •  Abates: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(totalClanKills), NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline());

        if (clans.isEmpty()) {
            out.append(Component.text("Nenhum clã foi criado ainda.", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Use /team create <nome> <tag> para fundar um clã!", NamedTextColor.GRAY))
                    .append(Component.newline());
            return;
        }

        int maxPoints = Math.max(1, clans.stream().mapToInt(ClanRankingEntry::points).max().orElse(1));
        int limit = Math.min(6, clans.size());

        for (int i = 0; i < limit; i++) {
            ClanRankingEntry entry = clans.get(i);
            Team team = entry.team();
            Component medal = formatRankPrefix(i + 1);

            out.append(medal)
                    .append(Component.text(" "))
                    .append(team.getFormattedTag())
                    .append(Component.text(" " + team.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("  ⭐ ", NamedTextColor.GOLD))
                    .append(Component.text(entry.points() + " pts ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(buildBar(entry.points(), maxPoints, 8, team.getColor()))
                    .append(Component.newline())
                    .append(Component.text("   ⚔ ", NamedTextColor.GREEN))
                    .append(Component.text(entry.kills() + " Kills", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("  ☠ ", NamedTextColor.RED))
                    .append(Component.text(entry.deaths() + " Mortes", NamedTextColor.RED))
                    .append(Component.text("  🎯 KDR: ", NamedTextColor.GRAY))
                    .append(Component.text(formatKdr(entry.kdr()), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("  (", NamedTextColor.DARK_GRAY))
                    .append(formatBalance(entry.balance()))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY))
                    .append(Component.newline())
                    .append(Component.text("   👑 Líder: ", NamedTextColor.GRAY))
                    .append(Component.text(entry.leaderName(), NamedTextColor.GOLD))
                    .append(Component.text("  •  👥 Membros: ", NamedTextColor.GRAY))
                    .append(Component.text(entry.onlineMembers() + "/" + entry.totalMembers() + " online",
                            entry.onlineMembers() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                    .append(Component.newline());

            if (i < limit - 1) {
                out.append(Component.newline());
            }
        }
    }

    private void appendPlayersPanel(TextComponent.Builder out, List<PlayerRankingEntry> players, ServerSummary summary) {
        out.append(Component.text("⚔ ", NamedTextColor.AQUA))
                .append(Component.text("RANKING INDIVIDUAL DE PLAYERS", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" ⚔", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("Combatentes: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(summary.totalPlayersWithStats()), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("  •  Total PvP: ", NamedTextColor.GRAY))
                .append(Component.text(summary.totalKills() + " ⚔", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(summary.totalDeaths() + " ☠", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline());

        if (players.isEmpty()) {
            out.append(Component.text("Nenhum combate registrado ainda!", NamedTextColor.GRAY))
                    .append(Component.newline());
            return;
        }

        int limit = Math.min(8, players.size());
        for (int i = 0; i < limit; i++) {
            PlayerRankingEntry p = players.get(i);
            Component medal = formatRankPrefix(i + 1);
            Component statusDot = p.online()
                    ? Component.text("● ", NamedTextColor.GREEN)
                    : Component.text("● ", NamedTextColor.DARK_GRAY);
            Component clanTag = p.team() != null
                    ? p.team().getFormattedTag().append(Component.text(" "))
                    : Component.text("[---] ", NamedTextColor.DARK_GRAY);

            out.append(medal)
                    .append(Component.text(" "))
                    .append(statusDot)
                    .append(clanTag)
                    .append(Component.text(p.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("  ⚔ ", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(p.kills()), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" ☠ ", NamedTextColor.RED))
                    .append(Component.text(String.valueOf(p.deaths()), NamedTextColor.RED))
                    .append(Component.text("  KDR: ", NamedTextColor.GRAY))
                    .append(Component.text(formatKdr(p.kdr()), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(formatBalance(p.balance()))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY));

            if (p.team() != null && p.clanKillSharePercent() > 0) {
                out.append(Component.text(" [" + p.clanKillSharePercent() + "% clã]", NamedTextColor.AQUA));
            }
            out.append(Component.newline());
        }
    }

    private void appendHighlightsPanel(TextComponent.Builder out,
                                       List<ClanRankingEntry> clans,
                                       List<PlayerRankingEntry> players,
                                       ServerSummary summary) {
        out.append(Component.text("🌟 ", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("DESTAQUES & TROFÉUS DO SERVIDOR", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" 🌟", NamedTextColor.LIGHT_PURPLE))
                .append(Component.newline())
                .append(Component.text("Clãs: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(summary.totalClans()), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("  •  Players: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(summary.totalPlayersWithStats()), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("  •  Abates Globais: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(summary.totalKills()), NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline());

        // 1. Dominant Clan
        out.append(Component.text("👑 CLÃ DOMINANTE: ", NamedTextColor.GOLD, TextDecoration.BOLD));
        if (summary.topClan() != null) {
            ClanRankingEntry top = summary.topClan();
            out.append(top.team().getFormattedTag())
                    .append(Component.text(" " + top.team().getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" (⭐ " + top.points() + " pts • " + top.kills() + "⚔ / " + top.deaths() + "☠ • KDR " + formatKdr(top.kdr()) + ")", NamedTextColor.YELLOW));
        } else {
            out.append(Component.text("Nenhum", NamedTextColor.GRAY));
        }
        out.append(Component.newline());

        // 2. Top Killer (Ceifador)
        out.append(Component.text("💀 CEIFADOR (TOP KILLER): ", NamedTextColor.RED, TextDecoration.BOLD));
        if (summary.topKiller() != null && summary.topKiller().kills() > 0) {
            PlayerRankingEntry tk = summary.topKiller();
            if (tk.team() != null) {
                out.append(tk.team().getFormattedTag()).append(Component.text(" "));
            }
            out.append(Component.text(tk.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" com " + tk.kills() + " abates!", NamedTextColor.GREEN));
        } else {
            out.append(Component.text("Sem abates registrados", NamedTextColor.GRAY));
        }
        out.append(Component.newline());

        // 3. Best KDR (Imortal)
        out.append(Component.text("🎯 EFICIÊNCIA LETAL (KDR): ", NamedTextColor.AQUA, TextDecoration.BOLD));
        if (summary.bestKdrPlayer() != null && summary.bestKdrPlayer().kills() > 0) {
            PlayerRankingEntry bk = summary.bestKdrPlayer();
            if (bk.team() != null) {
                out.append(bk.team().getFormattedTag()).append(Component.text(" "));
            }
            out.append(Component.text(bk.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" (KDR " + formatKdr(bk.kdr()) + " • " + bk.kills() + "/" + bk.deaths() + ")", NamedTextColor.YELLOW));
        } else {
            out.append(Component.text("Em disputa", NamedTextColor.GRAY));
        }
        out.append(Component.newline());

        // 4. Most Hunted (Alvo Mais Caçado)
        out.append(Component.text("🩸 ALVO MAIS CAÇADO: ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        if (summary.mostHuntedPlayer() != null && summary.mostHuntedPlayer().deaths() > 0) {
            PlayerRankingEntry mh = summary.mostHuntedPlayer();
            if (mh.team() != null) {
                out.append(mh.team().getFormattedTag()).append(Component.text(" "));
            }
            out.append(Component.text(mh.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" (" + mh.deaths() + " mortes em combate)", NamedTextColor.RED));
        } else {
            out.append(Component.text("Ninguém", NamedTextColor.GRAY));
        }
        out.append(Component.newline());

        // 5. Direct Clan Rivalry Bar if at least 2 clans exist
        if (summary.topClan() != null && summary.runnerUpClan() != null) {
            ClanRankingEntry c1 = summary.topClan();
            ClanRankingEntry c2 = summary.runnerUpClan();
            int totalRivalryPts = Math.max(1, c1.points() + c2.points());
            int c1Blocks = (c1.points() + c2.points() == 0)
                    ? 6
                    : (int) Math.round((c1.points() * 12.0) / totalRivalryPts);
            c1Blocks = Math.max(1, Math.min(11, c1Blocks));
            int c2Blocks = 12 - c1Blocks;

            out.append(Component.newline())
                    .append(Component.text("🔥 RIVALIDADE PRINCIPAL DE CLÃS 🔥", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(c1.team().getFormattedTag())
                    .append(Component.text(" " + c1.points() + "⭐ (" + c1.kills() + "⚔) ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("■".repeat(c1Blocks), c1.team().getColor()))
                    .append(Component.text("■".repeat(c2Blocks), c2.team().getColor()))
                    .append(Component.text(" " + c2.points() + "⭐ (" + c2.kills() + "⚔) ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(c2.team().getFormattedTag())
                    .append(Component.newline());
        }
    }

    private Component formatRankPrefix(int rank) {
        return switch (rank) {
            case 1 -> Component.text("🥇 1º", NamedTextColor.GOLD, TextDecoration.BOLD);
            case 2 -> Component.text("🥈 2º", NamedTextColor.GRAY, TextDecoration.BOLD);
            case 3 -> Component.text("🥉 3º", TextColor.color(205, 127, 50), TextDecoration.BOLD);
            default -> Component.text("   " + rank + "º", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
        };
    }

    private Component formatBalance(int balance) {
        if (balance > 0) {
            return Component.text("+" + balance, NamedTextColor.GREEN);
        } else if (balance < 0) {
            return Component.text(String.valueOf(balance), NamedTextColor.RED);
        }
        return Component.text("0", NamedTextColor.GRAY);
    }

    private Component buildBar(int value, int max, int length, NamedTextColor activeColor) {
        int filled = max <= 0 ? 0 : (int) Math.round((value * (double) length) / max);
        filled = Math.max(0, Math.min(length, filled));
        int empty = length - filled;
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("■".repeat(filled), activeColor))
                .append(Component.text("□".repeat(empty), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    public static String formatKdr(double kdr) {
        return String.format(Locale.US, "%.2f", kdr);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, TeamManager.PlayerStats> getInternalPlayerStatsMap() {
        try {
            Field f = TeamManager.class.getDeclaredField("playerStatsMap");
            f.setAccessible(true);
            Object val = f.get(teamManager);
            if (val instanceof Map<?, ?> map) {
                return (Map<UUID, TeamManager.PlayerStats>) map;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    public void reloadUsercacheNames() {
        File usercache = new File("usercache.json");
        if (!usercache.exists()) return;
        try (FileReader reader = new FileReader(usercache, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        if (obj.has("uuid") && obj.has("name")) {
                            try {
                                UUID u = UUID.fromString(obj.get("uuid").getAsString());
                                String n = obj.get("name").getAsString();
                                // Keep the first occurrence in usercache.json (most recent entry)
                                usercacheNames.putIfAbsent(u, n);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public String resolvePlayerName(UUID uuid) {
        if (uuid == null) return "Desconhecido";
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            usercacheNames.put(uuid, online.getName());
            return online.getName();
        }
        String cached = usercacheNames.get(uuid);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName();
        if (name != null && !name.isBlank()) {
            usercacheNames.put(uuid, name);
            return name;
        }
        String fallback = uuid.toString().substring(0, 8);
        usercacheNames.put(uuid, fallback);
        return fallback;
    }

    public List<ClanRankingEntry> collectClanRankings() {
        List<ClanRankingEntry> list = new ArrayList<>();
        for (Team team : teamManager.getAllTeams()) {
            int points = teamManager.getTeamPoints(team);
            int kills = teamManager.getTeamTotalKills(team);
            int deaths = teamManager.getTeamTotalDeaths(team);
            double kdr = deaths == 0 ? kills : (double) kills / deaths;
            int balance = kills - deaths;

            int onlineCount = 0;
            for (UUID memberId : team.getMembers()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline()) {
                    onlineCount++;
                }
            }
            String leaderName = resolvePlayerName(team.getLeader());
            list.add(new ClanRankingEntry(
                    team,
                    points,
                    kills,
                    deaths,
                    kdr,
                    balance,
                    onlineCount,
                    team.getMembers().size(),
                    leaderName
            ));
        }

        list.sort(Comparator
                .comparingInt(ClanRankingEntry::points).reversed()
                .thenComparing(Comparator.comparingInt(ClanRankingEntry::kills).reversed())
                .thenComparing(Comparator.comparingDouble(ClanRankingEntry::kdr).reversed())
                .thenComparingInt(ClanRankingEntry::deaths)
                .thenComparing(e -> e.team().getName().toLowerCase(Locale.ROOT)));
        return list;
    }

    public List<PlayerRankingEntry> collectPlayerRankings() {
        Set<UUID> candidateUuids = new LinkedHashSet<>();
        for (Team team : teamManager.getAllTeams()) {
            candidateUuids.addAll(team.getMembers());
        }
        candidateUuids.addAll(getInternalPlayerStatsMap().keySet());
        for (Player p : Bukkit.getOnlinePlayers()) {
            candidateUuids.add(p.getUniqueId());
        }

        // Deduplicate by lowercase player name, preferring UUIDs that are in a team or currently online
        Map<String, UUID> canonicalByName = new LinkedHashMap<>();
        for (UUID uuid : candidateUuids) {
            String name = resolvePlayerName(uuid);
            String key = name.toLowerCase(Locale.ROOT);
            UUID existing = canonicalByName.get(key);
            if (existing == null) {
                canonicalByName.put(key, uuid);
            } else {
                boolean newInTeam = teamManager.getPlayerTeam(uuid) != null;
                boolean oldInTeam = teamManager.getPlayerTeam(existing) != null;
                boolean newOnline = Bukkit.getPlayer(uuid) != null;
                boolean oldOnline = Bukkit.getPlayer(existing) != null;
                if ((newInTeam && !oldInTeam) || (newOnline && !oldOnline)) {
                    canonicalByName.put(key, uuid);
                }
            }
        }

        List<PlayerRankingEntry> list = new ArrayList<>();
        for (UUID uuid : canonicalByName.values()) {
            String name = resolvePlayerName(uuid);
            boolean online = Bukkit.getPlayer(uuid) != null;
            Team team = teamManager.getPlayerTeam(uuid);
            TeamManager.PlayerStats stats = teamManager.getPlayerStats(uuid);
            int kills = stats != null ? stats.kills() : 0;
            int deaths = stats != null ? stats.deaths() : 0;
            double kdr = deaths == 0 ? kills : (double) kills / deaths;
            int balance = kills - deaths;

            int share = 0;
            if (team != null) {
                int teamKills = teamManager.getTeamTotalKills(team);
                if (teamKills > 0) {
                    share = (int) Math.round((kills * 100.0) / teamKills);
                }
            }
            list.add(new PlayerRankingEntry(uuid, name, online, team, kills, deaths, kdr, balance, share));
        }

        list.sort(Comparator
                .comparingInt(PlayerRankingEntry::kills).reversed()
                .thenComparing(Comparator.comparingDouble(PlayerRankingEntry::kdr).reversed())
                .thenComparingInt(PlayerRankingEntry::deaths)
                .thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    public ServerSummary collectServerSummary(List<ClanRankingEntry> clans, List<PlayerRankingEntry> players) {
        int totalKills = players.stream().mapToInt(PlayerRankingEntry::kills).sum();
        int totalDeaths = players.stream().mapToInt(PlayerRankingEntry::deaths).sum();

        ClanRankingEntry topClan = clans.size() >= 1 ? clans.get(0) : null;
        ClanRankingEntry runnerUp = clans.size() >= 2 ? clans.get(1) : null;

        PlayerRankingEntry topKiller = players.isEmpty() ? null : players.get(0);
        PlayerRankingEntry bestKdr = players.stream()
                .filter(p -> p.kills() > 0)
                .max(Comparator.comparingDouble(PlayerRankingEntry::kdr)
                        .thenComparingInt(PlayerRankingEntry::kills))
                .orElse(topKiller);
        PlayerRankingEntry mostHunted = players.stream()
                .filter(p -> p.deaths() > 0)
                .max(Comparator.comparingInt(PlayerRankingEntry::deaths)
                        .thenComparingInt(PlayerRankingEntry::kills))
                .orElse(null);

        return new ServerSummary(
                clans.size(),
                players.size(),
                totalKills,
                totalDeaths,
                topClan,
                runnerUp,
                topKiller,
                bestKdr,
                mostHunted
        );
    }

    public void openInspectorGui(Player viewer, BoardType initialTab) {
        BoardType tab = (initialTab == null || initialTab == BoardType.ROTATING) ? BoardType.CLANS : initialTab;
        String title = switch (tab) {
            case CLANS -> "🏆 Placar Oficial • Clãs";
            case PLAYERS -> "⚔ Placar Oficial • Players";
            default -> "🌟 Placar Oficial • Destaques";
        };

        Inventory inv = Bukkit.createInventory(new BoardMenuHolder(tab), 54,
                Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));

        // Decorative border
        ItemStack borderPane = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, borderPane);
            }
        }

        List<ClanRankingEntry> clans = collectClanRankings();
        List<PlayerRankingEntry> players = collectPlayerRankings();
        ServerSummary summary = collectServerSummary(clans, players);

        if (tab == BoardType.CLANS) {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            for (int i = 0; i < Math.min(clans.size(), slots.length); i++) {
                ClanRankingEntry c = clans.get(i);
                Material bannerMat = getBannerForColor(c.team().getColor());
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Posição no Ranking: #" + (i + 1), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("⭐ Pontos do Clã: ", NamedTextColor.GOLD)
                        .append(Component.text(c.points() + " pts", NamedTextColor.YELLOW, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("⚔ Abates do Clã: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(c.kills()), NamedTextColor.GREEN, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("☠ Mortes do Clã: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(c.deaths()), NamedTextColor.RED, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("🎯 KDR do Clã: ", NamedTextColor.GRAY)
                        .append(Component.text(formatKdr(c.kdr()), NamedTextColor.YELLOW, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("📊 Saldo Geral: ", NamedTextColor.GRAY)
                        .append(formatBalance(c.balance())).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("👑 Líder: ", NamedTextColor.GRAY)
                        .append(Component.text(c.leaderName(), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("👥 Membros (" + c.onlineMembers() + "/" + c.totalMembers() + " online):", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                for (UUID mId : c.team().getMembers()) {
                    TeamManager.PlayerStats ps = teamManager.getPlayerStats(mId);
                    int mk = ps != null ? ps.kills() : 0;
                    int md = ps != null ? ps.deaths() : 0;
                    boolean on = Bukkit.getPlayer(mId) != null;
                    lore.add(Component.text(" " + (on ? "● " : "○ "), on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                            .append(Component.text(resolvePlayerName(mId), NamedTextColor.WHITE))
                            .append(Component.text(" [" + mk + "/" + md + "]", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false));
                }

                Component name = formatRankPrefix(i + 1)
                        .append(Component.text(" "))
                        .append(c.team().getFormattedTag())
                        .append(Component.text(" " + c.team().getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false);
                inv.setItem(slots[i], createItemWithLore(bannerMat, name, lore));
            }
        } else if (tab == BoardType.PLAYERS) {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < Math.min(players.size(), slots.length); i++) {
                PlayerRankingEntry p = players.get(i);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                if (sm != null) {
                    try {
                        sm.setOwningPlayer(Bukkit.getOfflinePlayer(p.uuid()));
                    } catch (Exception ignored) {
                    }
                    Component name = formatRankPrefix(i + 1)
                            .append(Component.text(" "))
                            .append(p.team() != null ? p.team().getFormattedTag().append(Component.text(" ")) : Component.empty())
                            .append(Component.text(p.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                            .decoration(TextDecoration.ITALIC, false);
                    sm.displayName(name);

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                            .append(Component.text(p.online() ? "Online" : "Offline", p.online() ? NamedTextColor.GREEN : NamedTextColor.RED))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Clã: ", NamedTextColor.GRAY)
                            .append(p.team() != null
                                    ? Component.text(p.team().getName() + " ", NamedTextColor.WHITE).append(p.team().getFormattedTag())
                                    : Component.text("Sem Clã", NamedTextColor.DARK_GRAY))
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.empty());
                    lore.add(Component.text("⚔ Abates: ", NamedTextColor.GRAY)
                            .append(Component.text(String.valueOf(p.kills()), NamedTextColor.GREEN, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("☠ Mortes: ", NamedTextColor.GRAY)
                            .append(Component.text(String.valueOf(p.deaths()), NamedTextColor.RED, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("🎯 KDR: ", NamedTextColor.GRAY)
                            .append(Component.text(formatKdr(p.kdr()), NamedTextColor.YELLOW, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("📊 Saldo: ", NamedTextColor.GRAY)
                            .append(formatBalance(p.balance())).decoration(TextDecoration.ITALIC, false));
                    if (p.team() != null) {
                        lore.add(Component.text("🏆 Contribuição no Clã: ", NamedTextColor.GRAY)
                                .append(Component.text(p.clanKillSharePercent() + "% dos abates", NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
                    }
                    sm.lore(lore);
                    head.setItemMeta(sm);
                }
                inv.setItem(slots[i], head);
            }
        } else {
            // Highlights tab
            inv.setItem(20, createHighlightItem(Material.NETHER_STAR,
                    Component.text("👑 Clã Dominante", NamedTextColor.GOLD, TextDecoration.BOLD),
                    summary.topClan() != null
                            ? List.of(
                            Component.text("Clã: ", NamedTextColor.GRAY).append(summary.topClan().team().getFormattedTag()).append(Component.text(" " + summary.topClan().team().getName(), NamedTextColor.WHITE)),
                            Component.text("Pontos: " + summary.topClan().points() + " ⭐", NamedTextColor.GOLD),
                            Component.text("Abates: " + summary.topClan().kills() + " ⚔", NamedTextColor.GREEN),
                            Component.text("Mortes: " + summary.topClan().deaths() + " ☠", NamedTextColor.RED),
                            Component.text("KDR: " + formatKdr(summary.topClan().kdr()), NamedTextColor.YELLOW),
                            Component.text("Líder: " + summary.topClan().leaderName(), NamedTextColor.GOLD)
                    )
                            : List.of(Component.text("Nenhum clã registrado", NamedTextColor.GRAY))
            ));

            inv.setItem(22, createHighlightItem(Material.NETHERITE_SWORD,
                    Component.text("💀 Ceifador Supremo (Top Killer)", NamedTextColor.RED, TextDecoration.BOLD),
                    summary.topKiller() != null
                            ? List.of(
                            Component.text("Jogador: " + summary.topKiller().name(), NamedTextColor.WHITE),
                            Component.text("Abates: " + summary.topKiller().kills() + " ⚔", NamedTextColor.GREEN),
                            Component.text("Mortes: " + summary.topKiller().deaths() + " ☠", NamedTextColor.RED),
                            Component.text("KDR: " + formatKdr(summary.topKiller().kdr()), NamedTextColor.YELLOW)
                    )
                            : List.of(Component.text("Sem combates ainda", NamedTextColor.GRAY))
            ));

            inv.setItem(24, createHighlightItem(Material.TOTEM_OF_UNDYING,
                    Component.text("🎯 Eficiência Letal (Melhor KDR)", NamedTextColor.AQUA, TextDecoration.BOLD),
                    summary.bestKdrPlayer() != null
                            ? List.of(
                            Component.text("Jogador: " + summary.bestKdrPlayer().name(), NamedTextColor.WHITE),
                            Component.text("KDR: " + formatKdr(summary.bestKdrPlayer().kdr()), NamedTextColor.YELLOW),
                            Component.text("Histórico: " + summary.bestKdrPlayer().kills() + "⚔ / " + summary.bestKdrPlayer().deaths() + "☠", NamedTextColor.GRAY)
                    )
                            : List.of(Component.text("Sem combates ainda", NamedTextColor.GRAY))
            ));

            inv.setItem(31, createHighlightItem(Material.SKELETON_SKULL,
                    Component.text("🩸 Alvo Mais Caçado", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    summary.mostHuntedPlayer() != null
                            ? List.of(
                            Component.text("Jogador: " + summary.mostHuntedPlayer().name(), NamedTextColor.WHITE),
                            Component.text("Mortes em PvP: " + summary.mostHuntedPlayer().deaths() + " ☠", NamedTextColor.RED),
                            Component.text("Abates: " + summary.mostHuntedPlayer().kills() + " ⚔", NamedTextColor.GREEN)
                    )
                            : List.of(Component.text("Nenhuma baixa registrada", NamedTextColor.GRAY))
            ));
        }

        // Bottom navigation tabs (slots 47, 49, 51)
        inv.setItem(47, createHighlightItem(Material.GOLD_INGOT,
                Component.text("🏆 Ranking de Clãs", tab == BoardType.CLANS ? NamedTextColor.GOLD : NamedTextColor.GRAY, TextDecoration.BOLD),
                List.of(Component.text("Clique para ver o ranking dos clãs!", NamedTextColor.YELLOW))));
        inv.setItem(49, createHighlightItem(Material.DIAMOND_SWORD,
                Component.text("⚔ Ranking de Players", tab == BoardType.PLAYERS ? NamedTextColor.AQUA : NamedTextColor.GRAY, TextDecoration.BOLD),
                List.of(Component.text("Clique para ver o ranking individual!", NamedTextColor.YELLOW))));
        inv.setItem(51, createHighlightItem(Material.BEACON,
                Component.text("🌟 Destaques do Servidor", tab == BoardType.HIGHLIGHTS ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY, TextDecoration.BOLD),
                List.of(Component.text("Clique para ver os troféus e recordes!", NamedTextColor.YELLOW))));

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private ItemStack createSimpleItem(Material mat, Component name) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createHighlightItem(Material mat, Component name, List<Component> rawLore) {
        List<Component> cleanLore = rawLore.stream()
                .map(c -> c.decoration(TextDecoration.ITALIC, false))
                .toList();
        return createItemWithLore(mat, name.decoration(TextDecoration.ITALIC, false), cleanLore);
    }

    private ItemStack createItemWithLore(Material mat, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material getBannerForColor(NamedTextColor color) {
        if (color == NamedTextColor.RED || color == NamedTextColor.DARK_RED) return Material.RED_BANNER;
        if (color == NamedTextColor.AQUA || color == NamedTextColor.DARK_AQUA) return Material.LIGHT_BLUE_BANNER;
        if (color == NamedTextColor.BLUE || color == NamedTextColor.DARK_BLUE) return Material.BLUE_BANNER;
        if (color == NamedTextColor.GREEN || color == NamedTextColor.DARK_GREEN) return Material.LIME_BANNER;
        if (color == NamedTextColor.YELLOW) return Material.YELLOW_BANNER;
        if (color == NamedTextColor.GOLD) return Material.ORANGE_BANNER;
        if (color == NamedTextColor.LIGHT_PURPLE) return Material.MAGENTA_BANNER;
        if (color == NamedTextColor.DARK_PURPLE) return Material.PURPLE_BANNER;
        if (color == NamedTextColor.WHITE) return Material.WHITE_BANNER;
        return Material.CYAN_BANNER;
    }

    public static boolean isHorizontalWallFace(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    public void loadBoards() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        boardsFile = new File(plugin.getDataFolder(), "boards.yml");
        if (!boardsFile.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(boardsFile);
        boardsById.clear();
        ConfigurationSection sec = cfg.getConfigurationSection("boards");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection bSec = sec.getConfigurationSection(id);
            if (bSec == null) continue;
            String worldName = bSec.getString("world", "world");
            int x = bSec.getInt("x");
            int y = bSec.getInt("y");
            int z = bSec.getInt("z");
            BlockFace facing = BlockFace.SOUTH;
            try {
                facing = BlockFace.valueOf(bSec.getString("facing", "SOUTH"));
            } catch (IllegalArgumentException ignored) {
            }
            BoardType type = BoardType.fromString(bSec.getString("type", "ROTATING"));
            if (type == null) type = BoardType.ROTATING;

            DisplayScale scale = DisplayScale.MOLDURA_COM_MAPA;
            try {
                scale = DisplayScale.valueOf(bSec.getString("scale", "MOLDURA_COM_MAPA"));
            } catch (IllegalArgumentException ignored) {
            }

            PlacedBoard board = new PlacedBoard(id, worldName, x, y, z, facing, type, scale);
            String fUuid = bSec.getString("frameUuid");
            if (fUuid != null) {
                try { board.setFrameUuid(UUID.fromString(fUuid)); } catch (IllegalArgumentException ignored) {}
            }
            String tdUuid = bSec.getString("textDisplayUuid");
            if (tdUuid != null) {
                try { board.setTextDisplayUuid(UUID.fromString(tdUuid)); } catch (IllegalArgumentException ignored) {}
            }
            board.setMapId(bSec.getInt("mapId", -1));
            boardsById.put(id, board);
        }
    }

    public void saveBoards() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (boardsFile == null) {
            boardsFile = new File(plugin.getDataFolder(), "boards.yml");
        }
        FileConfiguration cfg = new YamlConfiguration();
        for (PlacedBoard board : boardsById.values()) {
            String path = "boards." + board.getId();
            cfg.set(path + ".world", board.getWorldName());
            cfg.set(path + ".x", board.getBlockX());
            cfg.set(path + ".y", board.getBlockY());
            cfg.set(path + ".z", board.getBlockZ());
            cfg.set(path + ".facing", board.getFacing().name());
            cfg.set(path + ".type", board.getBoardType().name());
            cfg.set(path + ".scale", board.getDisplayScale().name());
            if (board.getFrameUuid() != null) {
                cfg.set(path + ".frameUuid", board.getFrameUuid().toString());
            }
            if (board.getTextDisplayUuid() != null) {
                cfg.set(path + ".textDisplayUuid", board.getTextDisplayUuid().toString());
            }
            cfg.set(path + ".mapId", board.getMapId());
        }
        try {
            cfg.save(boardsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar boards.yml: " + e.getMessage());
        }
    }

    /**
     * Custom 128x128 Pixel-Art Map Renderer for the Wall Scoreboard Frame.
     */
    private static class BoardMapRenderer extends MapRenderer {
        private final BoardType tab;
        private final List<ClanRankingEntry> clans;
        private final List<PlayerRankingEntry> players;
        private final ServerSummary summary;
        private boolean rendered = false;

        public BoardMapRenderer(BoardType tab,
                                List<ClanRankingEntry> clans,
                                List<PlayerRankingEntry> players,
                                ServerSummary summary) {
            super(false);
            this.tab = tab;
            this.clans = clans;
            this.players = players;
            this.summary = summary;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) return;
            rendered = true;

            java.awt.Color bgDark = new java.awt.Color(16, 20, 32);
            java.awt.Color bgHeader = new java.awt.Color(28, 36, 56);
            java.awt.Color gold = new java.awt.Color(255, 200, 40);
            java.awt.Color cyan = new java.awt.Color(60, 220, 255);
            java.awt.Color green = new java.awt.Color(80, 235, 100);
            java.awt.Color barBg = new java.awt.Color(38, 44, 62);

            // Fill background
            fillRect(canvas, 0, 0, 128, 128, bgDark);
            // Header banner
            fillRect(canvas, 3, 3, 122, 22, bgHeader);

            // Outer Gold Frame Border
            drawBorder(canvas, 0, 0, 128, 128, gold);
            drawBorder(canvas, 2, 2, 124, 124, cyan);

            // Title text in header
            String title = sanitizeAscii(tab.getMapHeader());
            canvas.drawText(10, 10, MinecraftFont.Font, title);

            if (tab == BoardType.CLANS) {
                int maxPoints = Math.max(1, clans.stream().mapToInt(ClanRankingEntry::points).max().orElse(1));
                int y = 32;
                int limit = Math.min(4, clans.size());
                for (int i = 0; i < limit; i++) {
                    ClanRankingEntry c = clans.get(i);
                    String line = (i + 1) + ".[" + sanitizeAscii(c.team().getTag()) + "] " + c.points() + "P " + c.kills() + "K";
                    canvas.drawText(6, y, MinecraftFont.Font, line);

                    // Draw horizontal bar
                    fillRect(canvas, 6, y + 10, 114, 6, barBg);
                    int barW = Math.max(4, (int) Math.round((c.points() * 114.0) / maxPoints));
                    java.awt.Color barColor = matchAdventureColor(c.team().getColor(), gold);
                    fillRect(canvas, 6, y + 10, Math.min(114, barW), 6, barColor);
                    y += 22;
                }
            } else if (tab == BoardType.PLAYERS) {
                int maxKills = Math.max(1, players.stream().mapToInt(PlayerRankingEntry::kills).max().orElse(1));
                int y = 30;
                int limit = Math.min(5, players.size());
                for (int i = 0; i < limit; i++) {
                    PlayerRankingEntry p = players.get(i);
                    String shortName = sanitizeAscii(p.name());
                    if (shortName.length() > 10) shortName = shortName.substring(0, 10);
                    String line = (i + 1) + "." + shortName + " " + p.kills() + "/" + p.deaths();
                    canvas.drawText(6, y, MinecraftFont.Font, line);

                    fillRect(canvas, 6, y + 9, 114, 5, barBg);
                    int barW = Math.max(4, (int) Math.round((p.kills() * 114.0) / maxKills));
                    java.awt.Color barColor = p.team() != null ? matchAdventureColor(p.team().getColor(), cyan) : green;
                    fillRect(canvas, 6, y + 9, Math.min(114, barW), 5, barColor);
                    y += 18;
                }
            } else {
                canvas.drawText(6, 34, MinecraftFont.Font, "CLAS ATIVOS: " + summary.totalClans());
                canvas.drawText(6, 48, MinecraftFont.Font, "PLAYERS PVP: " + summary.totalPlayersWithStats());
                canvas.drawText(6, 62, MinecraftFont.Font, "ABATES: " + summary.totalKills());
                if (summary.topClan() != null) {
                    canvas.drawText(6, 80, MinecraftFont.Font, "TOP CLA: [" + sanitizeAscii(summary.topClan().team().getTag()) + "]");
                }
                if (summary.topKiller() != null) {
                    String tk = sanitizeAscii(summary.topKiller().name());
                    if (tk.length() > 11) tk = tk.substring(0, 11);
                    canvas.drawText(6, 96, MinecraftFont.Font, "MVP: " + tk);
                }
                fillRect(canvas, 6, 112, 116, 6, gold);
            }
        }

        private java.awt.Color matchAdventureColor(NamedTextColor c, java.awt.Color fallback) {
            if (c == null) return fallback;
            return new java.awt.Color(c.red(), c.green(), c.blue());
        }

        private void fillRect(MapCanvas canvas, int x, int y, int w, int h, java.awt.Color color) {
            for (int dx = 0; dx < w; dx++) {
                for (int dy = 0; dy < h; dy++) {
                    int px = x + dx;
                    int py = y + dy;
                    if (px >= 0 && px < 128 && py >= 0 && py < 128) {
                        canvas.setPixelColor(px, py, color);
                    }
                }
            }
        }

        private void drawBorder(MapCanvas canvas, int x, int y, int w, int h, java.awt.Color color) {
            fillRect(canvas, x, y, w, 1, color);
            fillRect(canvas, x, y + h - 1, w, 1, color);
            fillRect(canvas, x, y, 1, h, color);
            fillRect(canvas, x + w - 1, y, 1, h, color);
        }

        private String sanitizeAscii(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= 32 && c <= 126) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
