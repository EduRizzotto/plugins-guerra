package com.eduardo.teams.aura;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.aura.AuraManager.AuraColor;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interfaces graficas (GUI) do sistema /aura e /aura permission:
 * 1) Menu do Jogador (/aura): visualiza as cores de aura, equipa uma cor liberada ou liga/desliga a aura
 * 2) Menu de Lista de Jogadores do Servidor (/aura permission) para OPs/ADMs
 * 3) Menu de Selecao de Cores de Aura para um Jogador Especifico (ADM decide qual cor o player vai ter)
 */
public class AuraGui {

    public enum MenuType {
        PLAYER_AURA,
        ADMIN_PLAYER_LIST,
        ADMIN_PLAYER_EDITOR
    }

    public static class AuraMenuHolder implements InventoryHolder {
        private final MenuType menuType;
        private final String targetPlayerName;
        private final int page;
        private Inventory inventory;

        public AuraMenuHolder(MenuType menuType, String targetPlayerName, int page) {
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
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] COLOR_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23};

    private final AuraManager auraManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public AuraGui(TeamsPlugin plugin, AuraManager auraManager) {
        this.auraManager = auraManager;
        this.actionKey = new NamespacedKey(plugin, "aura_gui_action");
        this.targetKey = new NamespacedKey(plugin, "aura_gui_target");
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getTargetKey() {
        return targetKey;
    }

    /**
     * Abre o menu principal /aura para um jogador visualizar sua aura, trocar entre cores liberadas ou ligar/desligar.
     */
    public void openPlayerAuraMenu(Player player) {
        AuraMenuHolder holder = new AuraMenuHolder(MenuType.PLAYER_AURA, player.getName(), 0);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("✨ Auras do Servidor", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);
        fillBackground(inv);

        AuraColor activeColor = auraManager.getActiveAuraColor(player.getName());
        boolean enabled = auraManager.isAuraEnabled(player.getName());
        boolean hasAny = auraManager.hasAnyAura(player.getName());

        AuraColor[] colors = AuraColor.values();
        for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
            AuraColor color = colors[i];
            int slot = COLOR_SLOTS[i];

            boolean unlocked = auraManager.hasAuraColorPermission(player.getName(), color);
            boolean isCurrentActive = unlocked && enabled && (color == activeColor);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Efeito visual de partículas ao seu redor!", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (!unlocked) {
                lore.add(Component.text("🔒 STATUS: BLOQUEADA", NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Adquira ou peça a um ADM para liberar esta cor!", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (isCurrentActive) {
                lore.add(Component.text("✨ STATUS: EQUIPADA E ATIVA!", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Esta aura está brilhando ao seu redor.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("✅ LIBERADA — CLIQUE PARA EQUIPAR!", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Ou use: /aura " + color.getId(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            inv.setItem(slot, createMenuItem(
                    color.getIcon(),
                    Component.text("✨ " + color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    lore,
                    isCurrentActive,
                    "equip_aura",
                    color.getId()
            ));
        }

        // Botao para Ligar/Desligar a aura (slot 30 ou 31)
        if (hasAny) {
            inv.setItem(31, createMenuItem(
                    enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    enabled
                            ? Component.text("🟢 Aura: ATIVADA (Clique para Desativar)", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
                            : Component.text("🔴 Aura: DESATIVADA (Clique para Ativar)", NamedTextColor.RED, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Cor atual: " + (activeColor != null ? activeColor.getDisplayName() : "Nenhuma"), NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Clique para mostrar ou ocultar suas partículas.", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    enabled,
                    "toggle_own_aura",
                    player.getName()
            ));
        } else {
            inv.setItem(31, createMenuItem(
                    Material.BARRIER,
                    Component.text("🔒 Você ainda não possui uma Aura", NamedTextColor.RED, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("O administrador do servidor pode liberar", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("uma aura e escolher a sua cor!", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    false,
                    "none",
                    null
            ));
        }

        if (player.isOp() || player.hasPermission("teams.admin")) {
            inv.setItem(35, createMenuItem(
                    Material.COMMAND_BLOCK,
                    Component.text("⚙ Gerenciar Auras dos Jogadores (ADM)", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Clique para selecionar um jogador", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("e decidir qual cor de aura ele terá!", NamedTextColor.YELLOW)
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
     * Abre a GUI /aura permission listando todos os jogadores do servidor para o ADM escolher quem tera a aura.
     */
    public void openAdminPlayersMenu(Player admin, int page) {
        List<String> players = auraManager.getAllKnownPlayerNamesSorted();
        int pageSize = 45;
        int maxPage = Math.max(0, (players.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));

        AuraMenuHolder holder = new AuraMenuHolder(MenuType.ADMIN_PLAYER_LIST, null, safePage);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                Component.text("✨ Selecionar Jogador para Aura (Pág. " + (safePage + 1) + ")", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

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
                Material.NETHER_STAR,
                Component.text("✨ Selecione um Jogador", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Total de jogadores registrados: " + players.size(), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Clique na cabeça de um jogador para", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("escolher a cor da aura dele!", NamedTextColor.YELLOW)
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
     * Abre a GUI de um jogador especifico para o ADM escolher qual cor de aura o jogador vai ter (ou remover).
     */
    public void openPlayerAuraEditorMenu(Player admin, String targetPlayerName) {
        String displayTarget = auraManager.resolveDisplayName(targetPlayerName);
        AuraMenuHolder holder = new AuraMenuHolder(MenuType.ADMIN_PLAYER_EDITOR, displayTarget, 0);
        Inventory inv = Bukkit.createInventory(
                holder,
                45,
                Component.text("✨ Aura de: " + displayTarget, NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
        );
        holder.setInventory(inv);
        fillBackground(inv);

        // Cabeca do jogador selecionado no topo (slot 4)
        inv.setItem(4, createPlayerHeadItem(displayTarget));

        AuraColor activeColor = auraManager.getActiveAuraColor(displayTarget);
        boolean enabled = auraManager.isAuraEnabled(displayTarget);

        AuraColor[] colors = AuraColor.values();
        for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
            AuraColor color = colors[i];
            int slot = COLOR_SLOTS[i];
            boolean unlocked = auraManager.hasAuraColorPermission(displayTarget, color);
            boolean isActive = unlocked && (color == activeColor);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Comando: /aura set " + displayTarget + " " + color.getId(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (isActive) {
                lore.add(Component.text("🌟 COR ATIVA DE " + displayTarget.toUpperCase() + (enabled ? " (LIGADA)" : " (PAUSADA)"), NamedTextColor.GOLD, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique para REMOVER esta cor do jogador.", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (unlocked) {
                lore.add(Component.text("🟢 STATUS: LIBERADA PARA " + displayTarget.toUpperCase(), NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("• Clique Esquerdo: Definir como Cor Ativa", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("• Clique Direito / Shift: Remover esta cor", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("🔴 STATUS: BLOQUEADA PARA " + displayTarget.toUpperCase(), NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique para DAR e ATIVAR esta cor para " + displayTarget + "!", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }

            inv.setItem(slot, createMenuItem(
                    color.getIcon(),
                    Component.text("✨ " + color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    lore,
                    isActive || unlocked,
                    "admin_click_color",
                    color.getId()
            ));
        }

        // Botoes inferiores
        inv.setItem(36, createMenuItem(
                Material.ARROW,
                Component.text("⬅ Voltar para Lista de Jogadores", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(),
                false,
                "open_admin_players",
                "0"
        ));

        inv.setItem(39, createMenuItem(
                Material.EMERALD_BLOCK,
                Component.text("✅ Liberar TODAS as Cores de Aura", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Libera todas as 11 cores de aura", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("para " + displayTarget + " escolher!", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ),
                false,
                "unlock_all_auras",
                displayTarget
        ));

        inv.setItem(41, createMenuItem(
                Material.REDSTONE_BLOCK,
                Component.text("🔒 Remover Aura do Jogador", NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Remove todas as cores de aura", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("e desativa a aura de " + displayTarget + ".", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ),
                false,
                "lock_all_auras",
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
            Set<AuraColor> unlocked = auraManager.getUnlockedColors(playerName);
            AuraColor active = auraManager.getActiveAuraColor(playerName);
            boolean enabled = auraManager.isAuraEnabled(playerName);

            meta.displayName(Component.text("👤 " + playerName + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(isOnline ? "(Online)" : "(Offline)", isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false))
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (active != null) {
                lore.add(Component.text("Cor Ativa: ", NamedTextColor.GRAY)
                        .append(Component.text(active.getDisplayName(), active.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(enabled ? " (Ligada)" : " (Desligada)", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Cor Ativa: ", NamedTextColor.GRAY)
                        .append(Component.text("Nenhuma", NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text("Cores Liberadas (" + unlocked.size() + "/" + AuraColor.values().length + "):", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            if (unlocked.isEmpty()) {
                lore.add(Component.text(" • Sem aura liberada", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                for (AuraColor c : AuraColor.values()) {
                    if (unlocked.contains(c)) {
                        lore.add(Component.text(" ✔ " + c.getDisplayName(), c.getTextColor())
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("Clique para selecionar a cor da aura!", NamedTextColor.AQUA)
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
