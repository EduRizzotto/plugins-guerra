package com.eduardo.teams.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Representa um time de jogadores no servidor.
 */
public class Team {

    private final String name;
    private String tag;
    private NamedTextColor color;
    private UUID leader;
    private final Set<UUID> members;

    public Team(String name, String tag, NamedTextColor color, UUID leader) {
        this.name = name;
        this.tag = tag.toUpperCase();
        this.color = color != null ? color : NamedTextColor.AQUA;
        this.leader = leader;
        this.members = new LinkedHashSet<>();
        if (leader != null) {
            this.members.add(leader);
        }
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

    /**
     * Retorna o componente formatado da tag do time: [TAG] na cor escolhida.
     */
    public Component getFormattedTag() {
        return Component.text("[" + tag + "]", color);
    }
}
