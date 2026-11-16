package com.eduardo.teams.quest;

import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface grafica (GUI) das 5 Quests Diarias de Equipe (/quest ou aba do /team).
 */
public class QuestGui {

    public static final int SLOT_BACK_TO_TEAM = 22;
    private static final int[] QUEST_SLOTS = {11, 12, 13, 14, 15};

    public static class QuestMenuHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final QuestManager questManager;
    private final TeamManager teamManager;

    public QuestGui(QuestManager questManager, TeamManager teamManager) {
        this.questManager = questManager;
        this.teamManager = teamManager;
    }

    public void openQuestsMenu(Player player) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        QuestMenuHolder holder = new QuestMenuHolder();
        Inventory inv = Bukkit.createInventory(
                holder,
                27,
                Component.text("Quests Diárias de Equipe", NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        // Fundo de vidro cinza
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }

        // Cabecalho informativo (Slot 4)
        inv.setItem(4, createHeaderItem(team));

        // 5 Quests Diarias (Slots 11, 12, 13, 14, 15)
        List<QuestDefinition> active = questManager.getActiveQuests();
        for (int i = 0; i < active.size() && i < QUEST_SLOTS.length; i++) {
            QuestDefinition quest = active.get(i);
            inv.setItem(QUEST_SLOTS[i], createQuestItem(team, quest, i + 1));
        }

        // Botao Voltar para o Menu do Time (Slot 22)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(
                    Component.text("Voltar ao Menu do Time (/team)", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            );
            backMeta.lore(List.of(
                    Component.text("Clique para abrir o painel principal do seu time.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            back.setItemMeta(backMeta);
        }
        inv.setItem(SLOT_BACK_TO_TEAM, back);

        player.openInventory(inv);
    }

    private ItemStack createHeaderItem(Team team) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("📜 Missões Diárias de Equipe", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> lore = new ArrayList<>();
            if (team != null) {
                int completed = questManager.getCompletedCount(team);
                lore.add(Component.text("Equipe: ", NamedTextColor.GRAY)
                        .append(Component.text(team.getName() + " ", NamedTextColor.WHITE))
                        .append(team.getFormattedTag())
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Concluídas hoje: ", NamedTextColor.GRAY)
                        .append(Component.text(completed + "/5", completed == 5 ? NamedTextColor.GREEN : NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("⚠ Você ainda não faz parte de um time!", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Entre ou crie um time em /team para", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("contabilizar o progresso das missões!", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            lore.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("O progresso é somado entre todos do time!", NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("Fácil: +10 pts │ Média: +15 pts │ Difícil: +30 pts", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("Bônus 5/5 concluídas: ", NamedTextColor.GOLD))
                    .append(Component.text("1.5x (120 pts totais!)", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("Reseta às 00:00 (faltam ", NamedTextColor.GRAY))
                    .append(Component.text(questManager.getTimeUntilMidnightFormatted(), NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createQuestItem(Team team, QuestDefinition quest, int index) {
        int current = questManager.getProgress(team, quest.getId());
        int target = quest.getTargetAmount();
        boolean done = current >= target;

        ItemStack item = new ItemStack(quest.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            QuestDifficulty diff = quest.getDifficulty();

            meta.displayName(
                    Component.text("#" + index + " [" + diff.getDisplayName() + "] ", diff.getColor(), TextDecoration.BOLD)
                            .append(Component.text(quest.getTitle(), done ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                                    .decoration(TextDecoration.BOLD, false))
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(quest.getDescription(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Recompensa: ", NamedTextColor.GRAY)
                    .append(Component.text("+" + diff.getPoints() + " pts para a equipe", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Progresso da Equipe: ", NamedTextColor.WHITE)
                    .append(Component.text(current + "/" + target, done ? NamedTextColor.GREEN : NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(buildProgressBar(current, target)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (team == null) {
                lore.add(Component.text("✖ Crie ou entre em um time (/team)", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (done) {
                lore.add(Component.text("✔ QUEST CONCLUÍDA PELA EQUIPE!", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Component.text("⏳ Em andamento — trabalhem juntos!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Component buildProgressBar(int current, int target) {
        int totalBars = 12;
        double ratio = target <= 0 ? 1.0 : Math.min(1.0, (double) current / target);
        int filled = (int) Math.round(ratio * totalBars);
        int percent = (int) Math.round(ratio * 100.0);

        StringBuilder filledStr = new StringBuilder();
        StringBuilder emptyStr = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < filled) {
                filledStr.append("█");
            } else {
                emptyStr.append("░");
            }
        }

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(filledStr.toString(), ratio >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.GOLD))
                .append(Component.text(emptyStr.toString(), NamedTextColor.DARK_GRAY))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(percent + "%", NamedTextColor.AQUA));
    }
}
