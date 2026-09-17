package net.glowcube.client.module.misc;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.player.AutoEat;
import net.glowcube.client.module.player.AutoTool;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

/**
 * Ein Ueberlebens-Autopilot.
 *
 * <p><b>Was dieses Modul ehrlich ist - und was nicht.</b> Es gibt in keinem
 * echten Client (Meteor, BleachHack) ein Modul, das Minecraft von selbst
 * durchspielt: quer zur naechsten Ressource laufen, sich hochcraften, in den
 * Nether, ins End, den Drachen toeten. Das verlangt ein
 * Pathfinding-Verfahren auf dem Niveau von Baritone (zehntausende Zeilen),
 * und selbst damit "spielt" es das Spiel nicht durch. Ich baue hier deshalb
 * nicht die Attrappe eines solchen Bots, sondern das, was aus echten
 * Bausteinen wirklich und zuverlaessig geht.
 *
 * <p>AutoPlay steuert die schon vorhandenen Module so, dass man nicht mehr
 * daneben sitzen muss:
 *
 * <ul>
 *   <li><b>Verteidigung.</b> Sobald ein Monster oder ein fremder Spieler in
 *       Reichweite ist, wird KillAura eingeschaltet - das waehlt von selbst
 *       das beste Schwert oder die Axt. Ist die Luft wieder rein, geht es
 *       aus.</li>
 *   <li><b>Essen.</b> AutoEat laeuft mit; bei Hunger wird von selbst
 *       gegessen und zurueckgetauscht.</li>
 *   <li><b>Werkzeug.</b> AutoTool waehlt beim Abbauen das passende
 *       Werkzeug.</li>
 *   <li><b>Sammeln.</b> Auf Wunsch: was von der Liste (Holz, Erze) in
 *       Reichweite ist, wird abgebaut - der naechste Block zuerst.</li>
 * </ul>
 *
 * <p>Die Module, die AutoPlay einschaltet, merkt es sich und stellt sie beim
 * Ausschalten wieder auf den vorherigen Stand - wer KillAura selbst an hatte,
 * behaelt es.
 */
public final class AutoPlay extends Module {
    private final BooleanSetting verteidigen = register(new BooleanSetting("Verteidigen",
            "Bei Angriff KillAura einschalten und aufs Schwert wechseln", true));
    private final NumberSetting radius = register(new NumberSetting("Wach-Radius",
            "In welcher Naehe eine Bedrohung zaehlt", 8, 3, 16, 1));
    private final NumberSetting nachlauf = register(new NumberSetting("Nachlauf",
            "Ticks, die KillAura nach der letzten Bedrohung noch anbleibt", 40, 0, 200, 5));
    private final BooleanSetting spielerAlsBedrohung = register(new BooleanSetting("Spieler zaehlen",
            "Auch fremde Spieler in Nahkampfnaehe als Bedrohung werten", true));
    private final BooleanSetting essen = register(new BooleanSetting("Essen",
            "AutoEat mitlaufen lassen", true));
    private final BooleanSetting werkzeug = register(new BooleanSetting("Werkzeug",
            "AutoTool mitlaufen lassen", true));
    private final BooleanSetting sammeln = register(new BooleanSetting("Sammeln",
            "Bloecke der Liste in Reichweite abbauen", false));
    private final BlockListSetting sammelListe = register(new BlockListSetting("Sammel-Bloecke",
            "Was gesammelt wird, wenn es in Reichweite liegt",
            "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log",
            "dark_oak_log", "mangrove_log", "cherry_log",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "diamond_ore", "deepslate_diamond_ore"));

    /** Ob AutoPlay das jeweilige Modul selbst eingeschaltet hat. */
    private boolean killAuraVonUns;
    private boolean autoEatVonUns;
    private boolean autoToolVonUns;
    private int bedrohungGesehen;

    public AutoPlay() {
        super("AutoPlay", "Haelt dich am Leben und verteidigt dich von selbst",
                Category.MISC);
    }

    @Override
    public void onEnable() {
        killAuraVonUns = false;
        autoEatVonUns = false;
        autoToolVonUns = false;
        bedrohungGesehen = 0;
    }

    @Override
    public void onDisable() {
        // Alles zuruecknehmen, was wir eingeschaltet haben.
        setzeModul(KillAura.class, false, killAuraVonUns);
        setzeModul(AutoEat.class, false, autoEatVonUns);
        setzeModul(AutoTool.class, false, autoToolVonUns);
        killAuraVonUns = autoEatVonUns = autoToolVonUns = false;
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            return;
        }

        // Essen und Werkzeug laufen einfach mit, solange AutoPlay an ist.
        autoEatVonUns = halteAn(AutoEat.class, essen.get(), autoEatVonUns);
        autoToolVonUns = halteAn(AutoTool.class, werkzeug.get(), autoToolVonUns);

        if (verteidigen.get()) {
            verteidigung();
        } else if (killAuraVonUns) {
            setzeModul(KillAura.class, false, true);
            killAuraVonUns = false;
        }

        if (sammeln.get()) {
            sammelSchritt();
        }
    }

    // ------------------------------------------------------------ Verteidigung

    private void verteidigung() {
        if (bedrohungInDerNaehe()) {
            bedrohungGesehen = nachlauf.getInt();
        } else if (bedrohungGesehen > 0) {
            bedrohungGesehen--;
        }

        boolean sollAn = bedrohungGesehen > 0;
        KillAura killAura = GlowCubeClient.modules().get(KillAura.class);
        if (sollAn && !killAura.isEnabled()) {
            killAura.setEnabled(true);
            killAuraVonUns = true;
        } else if (!sollAn && killAuraVonUns && killAura.isEnabled()) {
            killAura.setEnabled(false);
            killAuraVonUns = false;
        }
    }

    private boolean bedrohungInDerNaehe() {
        double wach = radius.get();
        double nahkampf = 4.5;
        for (Entity wesen : level().entitiesForRendering()) {
            if (wesen == player() || !wesen.isAlive()) {
                continue;
            }
            // Monster gelten immer als Bedrohung.
            if (wesen instanceof Enemy && wesen.distanceTo(player()) <= wach) {
                return true;
            }
            // Fremde Spieler nur in Nahkampfnaehe - wer nah genug ist, um zu
            // schlagen, ist selbst schuld.
            if (spielerAlsBedrohung.get() && wesen instanceof Player
                    && wesen.distanceTo(player()) <= nahkampf) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Sammeln

    private void sammelSchritt() {
        BlockPos mitte = player().blockPosition();
        int r = 5;
        BlockPos bester = null;
        double naechste = Double.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    probe.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    BlockState zustand = level().getBlockState(probe);
                    if (zustand.isAir() || !gesucht(zustand)) {
                        continue;
                    }
                    // In Reichweite und nicht durch die Wand.
                    double abstand = player().getEyePosition().distanceToSqr(
                            probe.getX() + 0.5, probe.getY() + 0.5, probe.getZ() + 0.5);
                    if (abstand > 20.25 || abstand >= naechste) {
                        continue;
                    }
                    if (!BlockUtils.kannAbbauen(probe)) {
                        continue;
                    }
                    naechste = abstand;
                    bester = probe.immutable();
                }
            }
        }

        if (bester != null) {
            BlockPos ziel = bester;
            Rotations.rotate(Rotations.getYaw(ziel), Rotations.getPitch(ziel), 30,
                    () -> BlockUtils.abbauen(ziel, true));
        }
    }

    private boolean gesucht(BlockState zustand) {
        return sammelListe.contains(BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString());
    }

    // --------------------------------------------------------------- Helfer

    /**
     * Haelt ein Modul an, solange gewuenscht. Liefert zurueck, ob AutoPlay es
     * gerade selbst anhaelt - damit wir es beim Abschalten nur dann
     * zuruecknehmen, wenn es nicht vorher schon der Nutzer anhatte.
     */
    private <T extends Module> boolean halteAn(Class<T> art, boolean gewuenscht, boolean vonUns) {
        T modul = GlowCubeClient.modules().get(art);
        if (gewuenscht) {
            if (!modul.isEnabled()) {
                modul.setEnabled(true);
                return true;
            }
            return vonUns;
        }
        if (vonUns && modul.isEnabled()) {
            modul.setEnabled(false);
        }
        return false;
    }

    private <T extends Module> void setzeModul(Class<T> art, boolean an, boolean nurWennVonUns) {
        if (!nurWennVonUns) {
            return;
        }
        T modul = GlowCubeClient.modules().get(art);
        if (modul.isEnabled() != an) {
            modul.setEnabled(an);
        }
    }

    @Override
    public String hudSuffix() {
        List<String> teile = new java.util.ArrayList<>();
        if (bedrohungGesehen > 0) {
            teile.add("Kampf");
        }
        if (essen.get()) {
            teile.add("Essen");
        }
        return teile.isEmpty() ? "wacht" : String.join(",", teile);
    }
}
