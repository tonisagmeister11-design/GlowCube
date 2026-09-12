/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

public enum AdminRole {
    OWNER("Owner", "<gradient:#ffd166:#ff8a00>", "<gold>"),
    ADMIN("Admin", "<gradient:#5ad1ff:#a06bff>", "<aqua>");

    private final String label;
    private final String gradient;
    private final String color;

    private AdminRole(String string2, String string3, String string4) {
        this.label = string2;
        this.gradient = string3;
        this.color = string4;
    }

    public String label() {
        return this.label;
    }

    public String gradient() {
        return this.gradient;
    }

    public String color() {
        return this.color;
    }

    public boolean isOwner() {
        return this == OWNER;
    }

    public static AdminRole byName(String string) {
        if (string == null) {
            return null;
        }
        for (AdminRole adminRole : AdminRole.values()) {
            if (!adminRole.name().equalsIgnoreCase(string)) continue;
            return adminRole;
        }
        return null;
    }
}

