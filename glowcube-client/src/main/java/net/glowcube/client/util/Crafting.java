package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Craften ueber direktes Legen der Zutaten ins Raster.
 *
 * <p>Warum nicht ueber das Rezeptbuch: seit 1.21.2 sind Rezepte
 * clientseitig nur noch als "Display" da, das alte {@code handlePlaceRecipe}
 * ist weg. Der stabile Weg ist deshalb der, den ein Mensch auch geht -
 * Zutat aufnehmen, in die richtigen Felder legen, Ergebnis herausnehmen -
 * nur ueber dieselben Klick-Pakete, die {@link InvUtils} schon benutzt.
 *
 * <p>Die Muster sind fest und von Hand hinterlegt. Das ist Absicht: eine
 * feste Zwei-mal-zwei- oder Drei-mal-drei-Anordnung haengt an nichts, was
 * sich zwischen den Fassungen verschiebt, und das Ergebnis ist immer
 * dasselbe.
 *
 * <p><b>Menuegebunden.</b> Zwei-mal-zwei geht im eigenen Rucksack jederzeit
 * (InventoryMenu). Drei-mal-drei - also alle Werkzeuge - verlangt eine
 * offene Werkbank (CraftingMenu). Ist das falsche oder gar kein Menue offen,
 * gibt {@link #craften} das ehrlich als {@link Ergebnis} zurueck, statt
 * blind zu klicken.
 */
public final class Crafting {
    /** Wie es ausging - AutoPlay steuert danach, was es als Naechstes tut. */
    public enum Ergebnis {
        FERTIG,
        BRAUCHT_WERKBANK,
        ZU_WENIG_ZUTATEN,
        KEIN_ZIEL
    }

    private Crafting() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------ Zutaten

    /** Ein Feld im Raster braucht diese Zutat - oder ist leer (null). */
    public interface Zutat extends Predicate<ItemStack> {
    }

    public static final Zutat HOLZ = stack -> pfad(stack).endsWith("_log");
    public static final Zutat PLANKEN = stack -> pfad(stack).endsWith("_planks");
    public static final Zutat STIEL = stack -> pfad(stack).equals("stick");
    public static final Zutat STEIN = stack -> {
        String p = pfad(stack);
        return p.equals("cobblestone") || p.equals("cobbled_deepslate");
    };

    private static String pfad(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    // ------------------------------------------------------------ Rezepte

    /**
     * Ein Rezept: die Kantenlaenge des Rasters und je Feld die Zutat (oder
     * null fuer leer). {@code ergebnis} ist der Item-Pfad, den das Ergebnis
     * haben muss - nur zur Kontrolle.
     */
    public record Rezept(String name, int breite, Zutat[] felder, String ergebnis) {
    }

    public static Rezept PLANKEN_AUS_HOLZ = new Rezept("Planken", 2,
            new Zutat[]{HOLZ, null, null, null}, "_planks");
    public static Rezept STIELE = new Rezept("Stiele", 2,
            new Zutat[]{PLANKEN, null, PLANKEN, null}, "stick");
    public static Rezept WERKBANK = new Rezept("Werkbank", 2,
            new Zutat[]{PLANKEN, PLANKEN, PLANKEN, PLANKEN}, "crafting_table");

    // Drei-mal-drei (Werkbank noetig). Reihenweise: 0 1 2 / 3 4 5 / 6 7 8.
    public static Rezept holzHacke = werkzeug3x3(PLANKEN, "wooden_pickaxe", true);
    public static Rezept holzSchwert = schwert3x3(PLANKEN, "wooden_sword");
    public static Rezept holzAxt = axt3x3(PLANKEN, "wooden_axe");
    public static Rezept steinHacke = werkzeug3x3(STEIN, "stone_pickaxe", true);
    public static Rezept steinSchwert = schwert3x3(STEIN, "stone_sword");
    public static Rezept steinAxt = axt3x3(STEIN, "stone_axe");

    /** Spitzhacke: obere Reihe voll, Stiele in der Mitte darunter. */
    private static Rezept werkzeug3x3(Zutat kopf, String ergebnis, boolean hacke) {
        return new Rezept(ergebnis, 3, new Zutat[]{
                kopf, kopf, kopf,
                null, STIEL, null,
                null, STIEL, null}, ergebnis);
    }

    /** Schwert: zwei Kopf-Teile senkrecht, Stiel darunter. */
    private static Rezept schwert3x3(Zutat kopf, String ergebnis) {
        return new Rezept(ergebnis, 3, new Zutat[]{
                null, kopf, null,
                null, kopf, null,
                null, STIEL, null}, ergebnis);
    }

    /** Axt: L-Form aus Kopf-Teilen links oben, Stiele in der Mitte. */
    private static Rezept axt3x3(Zutat kopf, String ergebnis) {
        return new Rezept(ergebnis, 3, new Zutat[]{
                kopf, kopf, null,
                kopf, STIEL, null,
                null, STIEL, null}, ergebnis);
    }

    // ------------------------------------------------------------ Ausfuehrung

    /**
     * Versucht, das Rezept einmal zu craften. Klickt die Zutaten ins Raster
     * des offenen Menues und nimmt das Ergebnis heraus.
     */
    public static Ergebnis craften(Rezept rezept) {
        if (mc().player == null) {
            return Ergebnis.KEIN_ZIEL;
        }
        AbstractContainerMenu menu = mc().player.containerMenu;

        boolean istWerkbank = menu instanceof CraftingMenu;
        boolean istRucksack = menu instanceof InventoryMenu;
        if (rezept.breite() == 3 && !istWerkbank) {
            return Ergebnis.BRAUCHT_WERKBANK;
        }
        if (rezept.breite() == 2 && !istRucksack && !istWerkbank) {
            return Ergebnis.BRAUCHT_WERKBANK;
        }

        int rasterAnfang = 1;              // Feld 0 ist immer das Ergebnis.
        int felderImMenu = rezept.breite() == 3 ? 9 : (istWerkbank ? 9 : 4);
        // Bei einer offenen Werkbank hat das Raster 3x3; ein 2x2-Rezept legt
        // dann nur die obere linke Ecke.
        int menuBreite = felderImMenu == 9 ? 3 : 2;

        if (!genugZutaten(menu, rezept)) {
            return Ergebnis.ZU_WENIG_ZUTATEN;
        }

        // Raster vorher leerraeumen, damit keine Reste stoeren.
        for (int i = 0; i < felderImMenu; i++) {
            if (!menu.getSlot(rasterAnfang + i).getItem().isEmpty()) {
                klick(menu, rasterAnfang + i, ClickType.QUICK_MOVE, 0);
            }
        }

        // Je Feld die Zutat legen.
        for (int feld = 0; feld < rezept.felder().length; feld++) {
            Zutat zutat = rezept.felder()[feld];
            if (zutat == null) {
                continue;
            }
            int reihe = feld / rezept.breite();
            int spalte = feld % rezept.breite();
            int zielSlot = rasterAnfang + reihe * menuBreite + spalte;

            int quelle = findeZutat(menu, zutat, felderImMenu);
            if (quelle < 0) {
                raeumeZeigerAuf(menu);
                return Ergebnis.ZU_WENIG_ZUTATEN;
            }
            // Aufnehmen, ein Stueck ablegen, Rest zuruecklegen.
            klick(menu, quelle, ClickType.PICKUP, 0);
            klick(menu, zielSlot, ClickType.PICKUP, 1);
            klick(menu, quelle, ClickType.PICKUP, 0);
        }

        // Ergebnis herausnehmen.
        klick(menu, 0, ClickType.QUICK_MOVE, 0);
        return Ergebnis.FERTIG;
    }

    private static boolean genugZutaten(AbstractContainerMenu menu, Rezept rezept) {
        // Fuer jede Zutatart zaehlen, wie oft sie im Rezept vorkommt, und ob
        // im Rucksack (ausserhalb des Rasters) so viele liegen.
        int rasterFelder = rezept.breite() == 3 ? 9 : 4;
        for (Zutat zutat : eindeutig(rezept)) {
            int gebraucht = 0;
            for (Zutat z : rezept.felder()) {
                if (z == zutat) {
                    gebraucht++;
                }
            }
            int da = 0;
            for (int i = rasterFelder + 1; i < menu.slots.size(); i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (zutat.test(stack)) {
                    da += stack.getCount();
                }
            }
            if (da < gebraucht) {
                return false;
            }
        }
        return true;
    }

    private static java.util.List<Zutat> eindeutig(Rezept rezept) {
        java.util.List<Zutat> liste = new java.util.ArrayList<>();
        for (Zutat z : rezept.felder()) {
            if (z != null && !liste.contains(z)) {
                liste.add(z);
            }
        }
        return liste;
    }

    /** Ein Slot ausserhalb des Rasters, in dem diese Zutat liegt. */
    private static int findeZutat(AbstractContainerMenu menu, Zutat zutat, int rasterFelder) {
        for (int i = rasterFelder + 1; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (zutat.test(slot.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private static void raeumeZeigerAuf(AbstractContainerMenu menu) {
        if (!menu.getCarried().isEmpty()) {
            // Was noch am Zeiger haengt, irgendwo ablegen.
            for (int i = 10; i < menu.slots.size(); i++) {
                if (menu.getSlot(i).getItem().isEmpty()) {
                    klick(menu, i, ClickType.PICKUP, 0);
                    return;
                }
            }
        }
    }

    private static void klick(AbstractContainerMenu menu, int slot, ClickType art, int knopf) {
        mc().gameMode.handleInventoryMouseClick(menu.containerId, slot, knopf, art, mc().player);
    }
}
