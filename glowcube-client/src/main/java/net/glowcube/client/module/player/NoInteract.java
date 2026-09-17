package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code NoInteract}.
 *
 * <p>Das Original ist deutlich weiter als BleachHacks gleichnamiges Modul
 * (das nur eine Blockliste gegen Betten kennt): es trennt vier Wege - Block
 * schlagen, Block anklicken, Wesen schlagen, Wesen anklicken - und gibt
 * jedem eine eigene Liste mit eigener Betriebsart. Diese Aufteilung, die
 * Reihenfolge der Pruefungen und die Bedeutung von Schwarz- und Weissliste
 * sind uebernommen.
 *
 * <p>Der praktische Fall ist derselbe geblieben: Betten und Respawn-Anker in
 * die Klickliste, und man sprengt sich im Nether nicht mehr selbst in die
 * Luft, wenn man danebenklickt.
 *
 * <p><b>Nicht uebernommen:</b> Meteors Freundesliste - die haengt an einem
 * eigenen System, das GlowCube (noch) nicht hat. Babys und benannte Wesen
 * sind da.
 */
public final class NoInteract extends Module {
    // --------------------------------------------------------------- Bloecke
    private final BlockListSetting abbauListe = register(new BlockListSetting("Nicht abbauen",
            "Diese Bloecke nicht anschlagen"));
    private final ModeSetting abbauArt = register(new ModeSetting("Abbau-Liste",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste", "Aus"));

    private final BlockListSetting klickListe = register(new BlockListSetting("Nicht anklicken",
            "Diese Bloecke nicht benutzen",
            "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed",
            "lime_bed", "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed",
            "purple_bed", "blue_bed", "brown_bed", "green_bed", "red_bed", "black_bed",
            "respawn_anchor"));
    private final ModeSetting klickArt = register(new ModeSetting("Klick-Liste",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste", "Aus"));
    private final ModeSetting klickHand = register(new ModeSetting("Klick-Hand",
            "Eine ganze Hand sperren, unabhaengig vom Block",
            "Keine", "Keine", "Haupthand", "Nebenhand", "Beide"));

    // ---------------------------------------------------------------- Wesen
    private final BlockListSetting schlagListe = register(new BlockListSetting("Nicht schlagen",
            "Diese Wesen nicht angreifen").fuerWesen());
    private final ModeSetting schlagArt = register(new ModeSetting("Schlag-Liste",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste", "Aus"));

    private final BlockListSetting wesenKlickListe = register(new BlockListSetting("Wesen nicht anklicken",
            "Diese Wesen nicht benutzen").fuerWesen());
    private final ModeSetting wesenKlickArt = register(new ModeSetting("Wesen-Klick-Liste",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste", "Aus"));
    private final ModeSetting wesenKlickHand = register(new ModeSetting("Wesen-Klick-Hand",
            "Eine ganze Hand sperren, unabhaengig vom Wesen",
            "Keine", "Keine", "Haupthand", "Nebenhand", "Beide"));

    private final ModeSetting babys = register(new ModeSetting("Babys",
            "Jungtiere verschonen", "Keine", "Keine", "Schlagen", "Anklicken", "Beides"));
    private final ModeSetting benannt = register(new ModeSetting("Benannte",
            "Wesen mit Namensschild verschonen",
            "Keine", "Keine", "Schlagen", "Anklicken", "Beides"));

    public NoInteract() {
        super("NoInteract", "Sperrt einzelne Arten von Klicks und Schlaegen", Category.PLAYER);
    }

    // ---------------------------------------------------------------- Haken

    @Override
    public boolean onBlockBreak(BlockPos pos) {
        return !darfAbbauen(pos);
    }

    @Override
    public boolean onBlockUse(BlockHitResult treffer, InteractionHand hand) {
        return !darfKlicken(treffer, hand);
    }

    @Override
    public boolean onEntityAttack(Entity ziel) {
        return !darfSchlagen(ziel);
    }

    @Override
    public boolean onEntityUse(Entity ziel, InteractionHand hand) {
        return !darfWesenKlicken(ziel, hand);
    }

    // ------------------------------------------------------------ Regelwerk

    /**
     * Die Doppelabfrage aus dem Original: bei einer Weissliste faellt alles
     * durch, was <em>auf</em> der Liste steht; bei einer Schwarzliste alles,
     * was <em>nicht</em> darauf steht. Genau so herum, damit "Weissliste"
     * dasselbe bedeutet wie dort.
     */
    private boolean listeErlaubt(ModeSetting art, boolean drauf) {
        if (art.is("Aus")) {
            return true;
        }
        return art.is("Schwarzliste") ? !drauf : drauf;
    }

    private boolean handGesperrt(ModeSetting art, InteractionHand hand) {
        return art.is("Beide")
                || (art.is("Haupthand") && hand == InteractionHand.MAIN_HAND)
                || (art.is("Nebenhand") && hand == InteractionHand.OFF_HAND);
    }

    private boolean darfAbbauen(BlockPos pos) {
        String id = BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos).getBlock()).toString();
        return listeErlaubt(abbauArt, abbauListe.contains(id));
    }

    private boolean darfKlicken(BlockHitResult treffer, InteractionHand hand) {
        if (handGesperrt(klickHand, hand)) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(
                level().getBlockState(treffer.getBlockPos()).getBlock()).toString();
        return listeErlaubt(klickArt, klickListe.contains(id));
    }

    private boolean darfSchlagen(Entity ziel) {
        if (babyGesperrt(babys, ziel, true) || benanntGesperrt(ziel, true)) {
            return false;
        }
        return listeErlaubt(schlagArt, schlagListe.contains(typId(ziel)));
    }

    private boolean darfWesenKlicken(Entity ziel, InteractionHand hand) {
        if (handGesperrt(wesenKlickHand, hand)) {
            return false;
        }
        if (babyGesperrt(babys, ziel, false) || benanntGesperrt(ziel, false)) {
            return false;
        }
        return listeErlaubt(wesenKlickArt, wesenKlickListe.contains(typId(ziel)));
    }

    private boolean babyGesperrt(ModeSetting art, Entity ziel, boolean schlag) {
        boolean gilt = art.is("Beides") || art.is(schlag ? "Schlagen" : "Anklicken");
        return gilt && ziel instanceof AgeableMob tier && tier.isBaby();
    }

    private boolean benanntGesperrt(Entity ziel, boolean schlag) {
        boolean gilt = benannt.is("Beides") || benannt.is(schlag ? "Schlagen" : "Anklicken");
        return gilt && ziel.hasCustomName();
    }

    private static String typId(Entity ziel) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(ziel.getType()).toString();
    }
}
