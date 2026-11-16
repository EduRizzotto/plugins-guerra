package com.eduardo.teams.quest;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Dificuldades das Quests Diarias de Equipe e pontos correspondentes:
 * - Facil (2/dia): 10 pts cada
 * - Media (2/dia): 15 pts cada
 * - Dificil (1/dia): 30 pts
 * Total base: 80 pts -> Bonus 1.5x ao completar as 5 = 120 pts (+40 pts extras).
 */
public enum QuestDifficulty {
    EASY("Fácil", NamedTextColor.GREEN, 2, 10),
    MEDIUM("Média", NamedTextColor.YELLOW, 2, 15),
    HARD("Difícil", NamedTextColor.RED, 1, 30);

    public static final int ALL_FIVE_BONUS_POINTS = 40; // 80 * 1.5 - 80 = +40 pts

    private final String displayName;
    private final NamedTextColor color;
    private final int dailyCount;
    private final int points;

    QuestDifficulty(String displayName, NamedTextColor color, int dailyCount, int points) {
        this.displayName = displayName;
        this.color = color;
        this.dailyCount = dailyCount;
        this.points = points;
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

    public int getPoints() {
        return points;
    }
}
