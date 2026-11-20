package me.vorty.vortyclan.model;

import java.util.UUID;

public final class Member {
    public final UUID id;
    public volatile String name;
    public volatile Role role;

    public Member(UUID id, String name, Role role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }
}
