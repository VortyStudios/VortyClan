package me.vorty.vortyclan.model;

public enum Role {
    MEMBER(0, "member"),
    ADMIN(1, "admin"),
    OWNER(2, "owner");

    private final int level;
    private final String key;

    Role(int level, String key) {
        this.level = level;
        this.key = key;
    }

    public int level() {
        return level;
    }

    public String key() {
        return key;
    }

    public boolean atLeast(Role other) {
        return level >= other.level;
    }

    public boolean higherThan(Role other) {
        return level > other.level;
    }

    public static Role fromString(String s) {
        if (s == null) return MEMBER;
        try {
            return valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MEMBER;
        }
    }
}
