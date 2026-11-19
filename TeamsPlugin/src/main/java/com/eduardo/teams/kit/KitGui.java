package com.eduardo.teams.kit;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.kit.KitManager.KitType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interfaces graficas (GUI) do sistema /kit e /kit permission:
 * 1) Menu de Resgate de Kits (/kit) para jogadores
 * 2) Menu de Lista de Jogadores do Servidor (/kit permission) para OPs/ADMs
 * 3) Menu de Liberacao/Bloqueio dos 6 Kits para um Jogador Especifico
 */
public class KitGui {

    public enum MenuType {
        PLAYER_KITS,
        ADMIN_PLAYER_LIST,
        ADMIN_PLAYER_EDITOR
    }

    public static class KitMenuHolder implements InventoryHolder {
        private final MenuType menuType;
        private final String targetPlayerName;
        private final int page;
        private Inventory inventory;

        public KitMenuHolder(MenuType menuType, String targetPlayerName, int page) {
            this.menuType = menuType;
            this.targetPlayerName = targetPlayerName;
            this.page = page;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        public String getTargetPlayerName() {
            return targetPlayerName;
        }

        public int getPage() {
            return page;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] SIX_KIT_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] SIX_STATUS_SLOTS = {19, 20, 21, 23, 24, 25};

    private final KitManager kitManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public KitGui(TeamsPlugin plugin, KitManager kitManager) {
        this.kitManager = kitManager;
        this.actionKey = new NamespacedKey(plugin, "kit_gui_action");
        this.targetKey = new NamespacedKey(plugin, "kit_gui_target");
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getTargetKey() {
        return targetKey;
    }

    /**
     * Abre o menu principal /kit para um jogador visualizar e resgatar seus kits liberados.
     */
    public void openPlayerKitsMenu(Player player) {
        KitMenuHolder holder = new KitMenuHolder(MenuType.PLAYER_KITS, player.getName(), 0);
        Inventory inv = Bukkit.createInventory(
                holder,
                27,
                Component.text("🎁 Kits Oficiais do Servidor", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);
        fillBackground(inv);

        KitType[] kits = KitType.values();
        for (int i = 0; i < kits.length && i < SIX_KIT_SLOTS.length; i++) {
            KitType kit = kits[i];
            int slot = SIX_KIT_SLOTS[i];

            boolean unlocked = kitManager.hasKitPermission(player.getName(), kit);
            boolean claimedToday = unlocked && kitManager.hasClaimedToday(player.getName(), kit);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Valor oficial: " + kit.getPriceLabel(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Itens inclusos:", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            for (String desc : kit.getItemDescriptions()) {
                lore.add(Component.text(" • " + desc, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());

            if (!unlocked) {
                lore.add(Component.text("🔒 STATUS: BLOQUEADO", NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Adquira este kit com um ADM para liberar!", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (claimedToday) {
                lore.add(Component.text("⏳ JÁ RESGATADO HOJE", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Próximo resgate em: " + kitManager.getRemainingTimeUntilMidnight(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("✅ LIBERADO — CLIQUE PARA RESGATAR!", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Ou use: /kit " + kit.getId(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            ItemStack icon = createMenuItem(
                    kit.getIcon(),
                    Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    lore,
                    unlocked && !claimedToday,
                    "claim_kit",
                    kit.getId()
            );
            inv.setItem(slot, icon);
        }

        if (player.isOp() || player.hasPermission("teams.admin")) {
            inv.setItem(22, createMenuItem(
                    Material.COMMAND_BLOCK,
                    Component.text("⚙ Gerenciar Permissões (/kit permission)", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Clique para abrir a lista de jogadores", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("e liberar/remover kits de qualquer pessoa!", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    true,
                    "open_admin_players",
                    "0"
            ));
        }

        player.openInventory(inv);
    }

    /**
     * Abre a GUI /kit permission listando todos os jogadores que ja estiveram online no servidor.
     */
    public void openPermissionPlayersMenu(Player admin, int page) {
        List<String> players = kitManager.getAllKnownPlayerNamesSorted();
        int pageSize = 45;
        int maxPage = Math.max(0, (players.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));

        KitMenuHolder holder = new KitMenuHolder(MenuType.ADMIN_PLAYER_LIST, null, safePage);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                Component.text("⚙ Permissões de Kits (Pág. " + (safePage + 1) + ")", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Barra inferior (slots 45..53)
        ItemStack filler = createFillerPane();
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        int startIdx = safePage * pageSize;
        int endIdx = Math.min(players.size(), startIdx + pageSize);
        for (int i = startIdx; i < endIdx; i++) {
            String playerName = players.get(i);
            int slot = i - startIdx;
            inv.setItem(slot, createPlayerHeadItem(playerName));
        }

        if (safePage > 0) {
            inv.setItem(45, createMenuItem(
                    Material.ARROW,
                    Component.text("⬅ Página Anterior", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(),
                    false,
                    "open_admin_players",
                    String.valueOf(safePage - 1)
            ));
        }

        inv.setItem(49, createMenuItem(
                Material.BOOK,
                Component.text("📋 Selecione um Jogador", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Total de jogadores registrados: " + players.size(), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Clique na cabeça de um jogador para", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("liberar ou remover os kits dele!", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)
                ),
                false,
                "none",
                null
        ));

        if (safePage < maxPage) {
            inv.setItem(53, createMenuItem(
                    Material.ARROW,
                    Component.text("Próxima Página ➡", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(),
                    false,
                    "open_admin_players",
                    String.valueOf(safePage + 1)
            ));
        }

        admin.openInventory(inv);
    }

    /**
     * Abre a GUI de um jogador especifico para o ADM clicar em um ou mais kits e liberar/bloquear.
     */
    public void openPlayerPermissionEditorMenu(Player admin, String targetPlayerName) {
        String displayTarget = kitManager.resolveDisplayName(targetPlayerName);
        KitMenuHolder holder = new KitMenuHolder(MenuType.ADMIN_PLAYER_EDITOR, displayTarget, 0);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("⚙ Kits de: " + displayTarget, NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);
        fillBackground(inv);

        // Cabeca do jogador selecionado no topo (slot 4)
        inv.setItem(4, createPlayerHeadItem(displayTarget));

        KitType[] kits = KitType.values();
        for (int i = 0; i < kits.length && i < SIX_KIT_SLOTS.length; i++) {
            KitType kit = kits[i];
            int kitSlot = SIX_KIT_SLOTS[i];
            int statusSlot = SIX_STATUS_SLOTS[i];
            boolean unlocked = kitManager.hasKitPermission(displayTarget, kit);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Comando: /kit " + kit.getId(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Itens do Kit:", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            for (String desc : kit.getItemDescriptions()) {
                lore.add(Component.text(" • " + desc, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (unlocked) {
                lore.add(Component.text("🟢 STATUS: LIBERADO PARA " + displayTarget.toUpperCase(), NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique para BLOQUEAR este kit.", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("🔴 STATUS: BLOQUEADO PARA " + displayTarget.toUpperCase(), NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique para LIBERAR este kit!", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }

            inv.setItem(kitSlot, createMenuItem(
                    kit.getIcon(),
                    Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    lore,
                    unlocked,
                    "toggle_player_kit",
                    kit.getId()
            ));

            inv.setItem(statusSlot, createMenuItem(
                    unlocked ? Material.LIME_DYE : Material.GRAY_DYE,
                    unlocked
                            ? Component.text("✅ LIBERADO: " + kit.getDisplayName(), NamedTextColor.GREEN, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
                            : Component.text("🔒 BLOQUEADO: " + kit.getDisplayName(), NamedTextColor.RED, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text(unlocked ? "Clique para remover a permissão" : "Clique para aprovar/liberar o kit", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    unlocked,
                    "toggle_player_kit",
                    kit.getId()
            ));
        }

        // Botoes inferiores de acao rapida
        inv.setItem(27, createMenuItem(
                Material.ARROW,
                Component.text("⬅ Voltar para Lista de Jogadores", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(),
                false,
                "open_admin_players",
                "0"
        ));

        inv.setItem(30, createMenuItem(
                Material.EMERALD_BLOCK,
                Component.text("✅ Liberar TODOS os 6 Kits", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Libera todos os 6 kits de uma vez", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("para " + displayTarget + "!", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ),
                false,
                "unlock_all_kits",
                displayTarget
        ));

        inv.setItem(32, createMenuItem(
                Material.REDSTONE_BLOCK,
                Component.text("🔒 Bloquear TODOS os Kits", NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Remove a permissão de todos os kits", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("de " + displayTarget + ".", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ),
                false,
                "lock_all_kits",
                displayTarget
        ));

        admin.openInventory(inv);
    }

    private ItemStack createPlayerHeadItem(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player online = Bukkit.getPlayerExact(playerName);
            OfflinePlayer offline = (online != null) ? online : Bukkit.getOfflinePlayer(playerName);
            meta.setOwningPlayer(offline);

            boolean isOnline = (online != null && online.isOnline());
            Set<KitType> unlocked = kitManager.getUnlockedKits(playerName);

            meta.displayName(Component.text("👤 " + playerName + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(isOnline ? "(Online)" : "(Offline)", isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false))
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Kits Liberados (" + unlocked.size() + "/6):", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            if (unlocked.isEmpty()) {
                lore.add(Component.text(" • Nenhum kit liberado", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                for (KitType k : KitType.values()) {
                    if (unlocked.contains(k)) {
                        lore.add(Component.text(" ✔ " + k.getDisplayName(), NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("Clique para gerenciar os kits!", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(actionKey, PersistentDataType.STRING, "select_player");
            pdc.set(targetKey, PersistentDataType.STRING, playerName);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createMenuItem(Material material, Component title, List<Component> lore, boolean glint, String action, String target) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            if (glint) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(actionKey, PersistentDataType.STRING, action);
            if (target != null) {
                pdc.set(targetKey, PersistentDataType.STRING, target);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createFillerPane() {
        return createMenuItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "),
                List.of(),
                false,
                "none",
                null
        );
    }

    private void fillBackground(Inventory inv) {
        ItemStack filler = createFillerPane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }
}
