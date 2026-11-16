package com.eduardo.teams.quest;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Dificuldades das Quests Diarias de Equipe.
 */
public enum QuestDifficulty {
    EASY("Fácil", NamedTextColor.GREEN, 2),
    MEDIUM("Média", NamedTextColor.YELLOW, 2),
    HARD("Difícil", NamedTextColor.RED, 1);

    private final String displayName;
    private final NamedTextColor color;
    private final int dailyCount;

    QuestDifficulty(String displayName, NamedTextColor color, int dailyCount) {
        this.displayName = displayName;
        this.color = color;
        this.dailyCount = dailyCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public int getDailyCount() {
        return dailyCount;
    }
}
