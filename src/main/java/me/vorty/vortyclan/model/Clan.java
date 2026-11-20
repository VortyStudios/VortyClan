package me.vorty.vortyclan.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Clan {
    public final UUID id;
    public final long created;
    public volatile String name;
    public volatile String tag;
    public volatile char color = 'b';
    public volatile String description = "";
    public volatile boolean open = false;
    public volatile boolean friendlyFire = false;
    public volatile int score = 0;
    public volatile ClanHome home;

    public final Map<UUID, Member> members = new ConcurrentHashMap<>();
    public final Map<UUID, String> banned = new ConcurrentHashMap<>();

    public Clan(UUID id, String name, String tag, long created) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.created = created;
    }

    public Member owner() {
        for (Member m : members.values()) {
            if (m.role == Role.OWNER) return m;
        }
        return null;
    }

    public Member member(UUID id) {
        return members.get(id);
    }

    public Member memberByName(String name) {
        for (Member m : members.values()) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        return null;
    }
}
