/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import java.util.List;
import org.bukkit.Material;

public enum Curse {
    SPRUNGSCHRECK("Sprungschreck", Material.CREEPER_HEAD, "Jeder Sprung zischt wie ein Creeper hinter ihm."),
    FALSCHE_BEUTE("Falsche Beute", Material.DIRT, "Abgebaute Blöcke werden unterwegs vertauscht."),
    SCHWINDEL("Schwindel", Material.SUGAR, "Beim Sprinten verreißt es ihm die Blickrichtung."),
    GRUSELKISTE("Gruselkiste", Material.CHEST, "Jede geöffnete Kiste macht ein Geräusch, das da nicht hingehört."),
    STARRENDE_KUEHE("Starrende Kühe", Material.COW_SPAWN_EGG, "Alle Kühe in der Nähe drehen sich zu ihm und sehen ihn an."),
    LAUNENHAFT("Launenhaft", Material.POTION, "Immer wieder ein zufälliger, harmloser Effekt.");

    private final String label;
    private final Material icon;
    private final String description;

    private Curse(String string2, Material material, String string3) {
        this.label = string2;
        this.icon = material;
        this.description = string3;
    }

    public String label() {
        return this.label;
    }

    public Material icon() {
        return this.icon;
    }

    public String description() {
        return this.description;
    }

    public static Curse byName(String string) {
        if (string == null) {
            return null;
        }
        for (Curse curse : Curse.values()) {
            if (!curse.name().equalsIgnoreCase(string)) continue;
            return curse;
        }
        return null;
    }

    public static List<Curse> all() {
        return List.of(Curse.values());
    }
}

