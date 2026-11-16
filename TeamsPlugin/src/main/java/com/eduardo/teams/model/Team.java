package com.eduardo.teams.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Representa um time de jogadores no servidor, incluindo pontos e estatisticas permanentes da equipe.
 */
public class Team {

    private final String name;
    private String tag;
    private NamedTextColor color;
    private UUID leader;
    private final Set<UUID> members;

    // Pontos e abates/mortes permanentes da equipe (nao sao perdidos se um membro sair do time)
    private int points;
    private int teamKills;
    private int teamDeaths;

    public Team(String name, String tag, NamedTextColor color, UUID leader) {
        this.name = name;
        this.tag = tag.toUpperCase();
        this.color = color != null ? color : NamedTextColor.AQUA;
        this.leader = leader;
        this.members = new LinkedHashSet<>();
        if (leader != null) {
            this.members.add(leader);
        }
        this.points = 0;
        this.teamKills = 0;
        this.teamDeaths = 0;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag.toUpperCase();
    }

    public NamedTextColor getColor() {
        return color;
    }

    public void setColor(NamedTextColor color) {
        this.color = color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public boolean isLeader(UUID playerId) {
        return leader != null && leader.equals(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int amount) {
        this.points = Math.max(0, this.points + amount);
    }

    public int getTeamKills() {
        return teamKills;
    }

    public void setTeamKills(int teamKills) {
        this.teamKills = Math.max(0, teamKills);
    }

    public void incrementTeamKills() {
        this.teamKills++;
    }

    public int getTeamDeaths() {
        return teamDeaths;
    }

    public void setTeamDeaths(int teamDeaths) {
        this.teamDeaths = Math.max(0, teamDeaths);
    }

    public void incrementTeamDeaths() {
        this.teamDeaths++;
    }

    /**
     * Retorna o componente formatado da tag do time: [TAG] na cor escolhida.
     */
    public Component getFormattedTag() {
        return Component.text("[" + tag + "]", color);
    }
}
