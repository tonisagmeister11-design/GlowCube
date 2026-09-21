package net.glowcube.client.module.misc;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.player.AutoEat;
import net.glowcube.client.module.player.AutoTool;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.Crafting;
import net.glowcube.client.util.Navigation;
import net.glowcube.client.util.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;


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
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Wie nah der Angreifer noch sein muss, um zurueckgeschlagen zu werden", 6, 3, 16, 1));
    private final NumberSetting nachlauf = register(new NumberSetting("Nachlauf",
            "Ticks, die nach dem letzten Treffer noch zurueckgeschlagen wird", 60, 0, 200, 5));
    private final BooleanSetting essen = register(new BooleanSetting("Essen",
            "AutoEat mitlaufen lassen", true));
    private final BooleanSetting werkzeug = register(new BooleanSetting("Werkzeug",
            "AutoTool mitlaufen lassen", true));
    private final BooleanSetting sammeln = register(new BooleanSetting("Sammeln",
            "Nur das in Reichweite abbauen, was laut Bedarf fehlt", true));
    private final BooleanSetting laufen = register(new BooleanSetting("Laufen",
            "Zum fehlenden Rohstoff hinlaufen (experimentell, offenes Gelaende)", false));
    private final NumberSetting suchweite = register(new NumberSetting("Suchweite",
            "Wie weit nach dem Rohstoff gesucht wird, um hinzulaufen", 20, 8, 48, 1));
    private final BooleanSetting craften = register(new BooleanSetting("Craften",
            "Aus dem Gesammelten Planken, Stiele, Werkbank und Werkzeug herstellen", true));
    private final BooleanSetting werkbankStellen = register(new BooleanSetting("Werkbank aufstellen",
            "Werkbank selbst hinsetzen und oeffnen (experimentell, nur auf festem Boden)", true));
    private final ModeSetting zielStufe = register(new ModeSetting("Zielstufe",
            "Bis zu welcher Werkzeugstufe von selbst gesammelt wird",
            "Stein", "Holz", "Stein"));
    private final NumberSetting minEssen = register(new NumberSetting("Essen-Vorrat",
            "Wie viel Essen dabei sein soll", 8, 0, 64, 1));
    private final NumberSetting minHolz = register(new NumberSetting("Holz-Vorrat",
            "Wie viele Holzbloecke dabei sein sollen", 8, 0, 64, 1));
    private final NumberSetting minStein = register(new NumberSetting("Stein-Vorrat",
            "Wie viel Pflasterstein dabei sein soll", 16, 0, 64, 1));

    /** Ob AutoPlay das jeweilige Modul selbst eingeschaltet hat. */
    private boolean killAuraVonUns;
    private boolean autoEatVonUns;
    private boolean autoToolVonUns;
    private LivingEntity aktiverAngreifer;

    // Was der letzte Bedarfs-Check ergeben hat.
    private boolean holzSammeln;
    private boolean steinSammeln;
    private java.util.List<String> fehltListe = java.util.List.of();
    private int bHacke, bSchwert, bAxt, bHolz, bPlanken, bStiele, bStein, bZiel = 3;
    private boolean hatWerkbank;
    private int craftPause;
    private BlockPos laufZiel;
    private int suchPause;
    /** Ob AutoPlay gerade selbst die Bewegungstasten haelt. */
    private boolean wirLaufen;

    public AutoPlay() {
        super("AutoPlay", "Haelt dich am Leben und verteidigt dich von selbst",
                Category.MISC);
    }

    @Override
    public void onEnable() {
        killAuraVonUns = false;
        autoEatVonUns = false;
        autoToolVonUns = false;
        aktiverAngreifer = null;
        if (inGame()) {
            bedarfErmitteln();
            String was = fehltListe.isEmpty()
                    ? "Nichts fehlt - alles beisammen."
                    : "Es fehlt: " + String.join(", ", fehltListe) + ".";
            player().displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[AutoPlay] Stand: " + werkzeugStufe() + "-Werkzeug. " + was
                            + " Planken/Stiele/Werkbank craftet es selbst; fuer Werkzeug"
                            + " eine Werkbank aufstellen und anklicken."), false);
        }
    }

    @Override
    public void onDisable() {
        // Alles zuruecknehmen, was wir eingeschaltet haben.
        Navigation.stopp();
        KillAura.nurZiel(null);
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
            KillAura.nurZiel(null);
            setzeModul(KillAura.class, false, true);
            killAuraVonUns = false;
        }

        // Erst schauen, was fehlt - dann nur danach sammeln.
        bedarfErmitteln();
        if (sammeln.get() && (holzSammeln || steinSammeln)) {
            BlockPos inReich = naechsterInReichweite();
            if (inReich != null) {
                laufenBeenden();
                sammleBei(inReich);
            } else if (laufen.get()) {
                zumRohstoffLaufen();
                wirLaufen = true;
            } else {
                laufenBeenden();
            }
        } else {
            laufenBeenden();
        }
        if (craften.get()) {
            craftSchritt();
        }
    }

    // ------------------------------------------------------------ Verteidigung

    private void verteidigung() {
        LivingEntity angreifer = angreiferErmitteln();

        KillAura killAura = GlowCubeClient.modules().get(KillAura.class);
        if (angreifer != null) {
            // Genau diesen einen zurueckschlagen - KillAura bekommt ihn als
            // festes Ziel und laesst alles andere in Ruhe.
            KillAura.nurZiel(angreifer);
            if (!killAura.isEnabled()) {
                killAura.setEnabled(true);
                killAuraVonUns = true;
            }
            aktiverAngreifer = angreifer;
        } else {
            KillAura.nurZiel(null);
            if (killAuraVonUns && killAura.isEnabled()) {
                killAura.setEnabled(false);
                killAuraVonUns = false;
            }
            aktiverAngreifer = null;
        }
    }

    /**
     * Wer den Spieler zuletzt getroffen hat - Minecraft merkt sich das selbst.
     * Gilt nur, wenn der Treffer nicht zu lange her ist, der Angreifer noch
     * lebt und in Reichweite steht. Das ist reine Notwehr: kein Vorbeilaufen
     * loest etwas aus, nur ein echter Schlag.
     */
    private LivingEntity angreiferErmitteln() {
        LivingEntity letzter = player().getLastHurtByMob();
        if (letzter == null || !letzter.isAlive() || letzter == player()) {
            return null;
        }
        int her = player().tickCount - player().getLastHurtByMobTimestamp();
        if (her < 0 || her > nachlauf.getInt()) {
            return null;
        }
        if (letzter.distanceTo(player()) > reichweite.get()) {
            return null;
        }
        return letzter;
    }

    // ---------------------------------------------------------------- Sammeln

    /** Den bereits gefundenen Block anschauen und abbauen. */
    private void sammleBei(BlockPos ziel) {
        Rotations.rotate(Rotations.getYaw(ziel), Rotations.getPitch(ziel), 30,
                () -> BlockUtils.abbauen(ziel, true));
    }

    /**
     * Die Bewegung freigeben - aber nur, wenn AutoPlay sie ueberhaupt
     * uebernommen hatte. Sonst wuerde jeder Tick die W-/Leertaste des Nutzers
     * loslassen, und man koennte mit eingeschaltetem AutoPlay nicht mehr
     * selbst laufen.
     */
    private void laufenBeenden() {
        if (wirLaufen) {
            Navigation.stopp();
            wirLaufen = false;
        }
    }

    /**
     * Der naechste gesuchte Block in Abbau-Reichweite, oder null. Ein
     * einziger Suchlauf fuer beides: die Entscheidung "ist was da?" und das
     * Ziel zum Abbauen - kein doppeltes Absuchen desselben Wuerfels mehr.
     */
    private BlockPos naechsterInReichweite() {
        BlockPos mitte = player().blockPosition();
        BlockPos bester = null;
        double naechste = Double.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    probe.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    BlockState zustand = level().getBlockState(probe);
                    if (zustand.isAir() || !gesucht(zustand)) {
                        continue;
                    }
                    double abstand = player().getEyePosition().distanceToSqr(
                            probe.getX() + 0.5, probe.getY() + 0.5, probe.getZ() + 0.5);
                    if (abstand <= 20.25 && abstand < naechste && BlockUtils.kannAbbauen(probe)) {
                        naechste = abstand;
                        bester = probe.immutable();
                    }
                }
            }
        }
        return bester;
    }

    /**
     * Zum naechsten gesuchten Block laufen. Das Ziel wird nur ab und zu neu
     * gesucht (die Suche ueber ein grosses Feld ist teuer) und dann
     * angelaufen, bis es erreicht oder verschwunden ist.
     */
    private void zumRohstoffLaufen() {
        // Ziel noch gueltig?
        if (laufZiel != null) {
            BlockState z = level().getBlockState(laufZiel);
            if (z.isAir() || !gesucht(z)) {
                laufZiel = null;
            }
        }
        if (laufZiel == null && suchPause <= 0) {
            laufZiel = naechsterRohstoff();
            suchPause = 20;
        }
        if (suchPause > 0) {
            suchPause--;
        }
        if (laufZiel == null) {
            Navigation.stopp();
            return;
        }
        Navigation.laufeZu(laufZiel);
    }

    /** Der naechste gesuchte Block im weiteren Umkreis - oder null. */
    private BlockPos naechsterRohstoff() {
        BlockPos mitte = player().blockPosition();
        int w = suchweite.getInt();
        int h = Math.min(w, 12);
        BlockPos bester = null;
        double naechste = Double.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -w; dz <= w; dz++) {
                for (int dy = -h; dy <= h; dy++) {
                    probe.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    BlockState zustand = level().getBlockState(probe);
                    if (zustand.isAir() || !gesucht(zustand)) {
                        continue;
                    }
                    // Nur was auch abbaubar ist - sonst laeuft es ewig auf
                    // einen Block zu, den es nie erreichen kann.
                    if (!BlockUtils.kannAbbauen(probe)) {
                        continue;
                    }
                    double abstand = probe.distToCenterSqr(
                            player().getX(), player().getY(), player().getZ());
                    if (abstand < naechste) {
                        naechste = abstand;
                        bester = probe.immutable();
                    }
                }
            }
        }
        return bester;
    }

    private boolean gesucht(BlockState zustand) {
        String id = BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).getPath();
        if (holzSammeln && id.endsWith("_log")) {
            return true;
        }
        return steinSammeln && STEIN.contains(id);
    }

    /** Bloecke, aus denen Pflasterstein und Erze kommen. */
    private static final java.util.Set<String> STEIN = java.util.Set.of(
            "stone", "cobblestone", "deepslate", "cobbled_deepslate", "tuff",
            "andesite", "diorite", "granite",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore");

    // ---------------------------------------------------------------- Craften

    /**
     * Ein Craft-Schritt je Aufruf, gedrosselt. Reihenfolge: erst die
     * Zwei-mal-zwei-Sachen im Rucksack (Planken, Stiele, Werkbank), die
     * jederzeit gehen; die Werkzeuge nur, wenn eine Werkbank offen ist.
     *
     * <p>Das ehrliche Bild: das Craften selbst ist echt und laeuft von
     * selbst. Die Werkbank aufstellen und anklicken macht AutoPlay noch
     * nicht - das ist der eine Handgriff, der bleibt: Werkbank setzen,
     * rechtsklick, und AutoPlay fuellt sie und nimmt das Werkzeug heraus.
     */
    private void craftSchritt() {
        if (craftPause > 0) {
            craftPause--;
            return;
        }
        boolean werkzeugFehlt = bHacke < bZiel || bSchwert < bZiel || bAxt < bZiel;

        // Werkbank offen -> das naechste fehlende Werkzeug bauen.
        if (player().containerMenu instanceof net.minecraft.world.inventory.CraftingMenu) {
            Crafting.Rezept ziel = naechstesWerkzeug();
            if (ziel != null) {
                Crafting.craften(ziel);
                craftPause = 10;
            } else {
                // Alles gebaut - Werkbank wieder zumachen.
                player().closeContainer();
            }
            return;
        }

        if (!werkzeugFehlt) {
            return;
        }

        // Zwei-mal-zwei im Rucksack - in sinnvoller Reihenfolge, eins je Schritt.
        if (bPlanken < 4 && bHolz >= 1) {
            Crafting.craften(Crafting.PLANKEN_AUS_HOLZ);
            craftPause = 10;
            return;
        }
        if (bStiele < 2 && bPlanken >= 2) {
            Crafting.craften(Crafting.STIELE);
            craftPause = 10;
            return;
        }
        if (!hatWerkbank && bPlanken >= 4) {
            Crafting.craften(Crafting.WERKBANK);
            craftPause = 10;
            return;
        }
        // Werkzeug faellt an, Stiele und (falls Steinstufe) Stein sind da:
        // Werkbank selbst hinstellen und oeffnen, damit der Rest von selbst
        // laeuft.
        if (werkbankStellen.get() && hatWerkbank && bStiele >= 2) {
            werkbankBereitstellen();
        }
    }

    /**
     * Stellt eine Werkbank hin und oeffnet sie - der Handgriff, der bisher
     * dir blieb.
     *
     * <p><b>Experimentell und ehrlich begrenzt:</b> es setzt die Werkbank auf
     * den Boden direkt vor die Fuesse. Steht dort kein fester Halt (Abgrund,
     * Wasser, schraege Wand), klappt es nicht - dafuer braeuchte es
     * Wegfindung. Auf normalem Boden geht es.
     */
    private void werkbankBereitstellen() {
        // Steht schon eine Werkbank neben mir? Dann die oeffnen.
        BlockPos vorhanden = werkbankInDerNaehe();
        if (vorhanden != null) {
            oeffneWerkbank(vorhanden);
            craftPause = 10;
            return;
        }
        // Sonst eine hinsetzen. Platz: ein Feld vor den Fuessen, wenn frei
        // und mit festem Block darunter.
        net.minecraft.core.Direction blick = player().getDirection();
        BlockPos davor = player().blockPosition().relative(blick);
        if (!level().getBlockState(davor).canBeReplaced()) {
            return;
        }
        if (level().getBlockState(davor.below()).isAir()) {
            return;
        }
        net.glowcube.client.util.FindItemResult fund =
                net.glowcube.client.util.InvUtils.findeInHotbar(
                        stack -> stack.is(net.minecraft.world.item.Items.CRAFTING_TABLE));
        if (!fund.found()) {
            return;
        }
        BlockUtils.setzen(davor, fund, true, 50, true, true);
        craftPause = 10;
    }

    private BlockPos werkbankInDerNaehe() {
        BlockPos mitte = player().blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = mitte.offset(dx, dy, dz);
                    if (level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                            && player().getEyePosition().distanceToSqr(
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 20.25) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void oeffneWerkbank(BlockPos pos) {
        net.minecraft.world.phys.Vec3 mitte = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        net.minecraft.world.phys.BlockHitResult treffer = new net.minecraft.world.phys.BlockHitResult(
                mitte, net.minecraft.core.Direction.UP, pos, false);
        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 30,
                () -> BlockUtils.benutzen(treffer, net.minecraft.world.InteractionHand.MAIN_HAND, true));
    }

    /** Das erste Werkzeug unter der Zielstufe, mit passendem Rezept. */
    private Crafting.Rezept naechstesWerkzeug() {
        boolean stein = bZiel >= 3;
        if (bHacke < bZiel) {
            return stein && bHacke >= 1 ? Crafting.steinHacke : Crafting.holzHacke;
        }
        if (bSchwert < bZiel) {
            return stein && bSchwert >= 1 ? Crafting.steinSchwert : Crafting.holzSchwert;
        }
        if (bAxt < bZiel) {
            return stein && bAxt >= 1 ? Crafting.steinAxt : Crafting.holzAxt;
        }
        return null;
    }

    // ------------------------------------------------------- Bedarfs-Check

    /**
     * Schaut in den Rucksack und entscheidet, was fehlt. Das ist der Kern des
     * Wunsches "farmt nur, was ihm fehlt": hat man schon Steinwerkzeug und
     * die Vorraete, wird gar nichts gesammelt - erst wenn etwas fehlt, geht
     * die passende Kategorie an.
     *
     * <p>Ehrlich bleibt: erkennen und das Fehlende in Reichweite abbauen
     * geht. Hinlaufen und daraus Werkzeug craften nicht - dafuer fehlt die
     * Wegfindung. Fehlt also die Steinhacke, sammelt AutoPlay den Stein dazu;
     * die Hacke daraus musst du (noch) selbst craften.
     */
    private void bedarfErmitteln() {
        int ziel = zielStufe.is("Holz") ? 1 : 3;

        int hacke = 0;
        int schwert = 0;
        int axt = 0;
        int holz = 0;
        int planken = 0;
        int stiele = 0;
        int stein = 0;
        int essenZahl = 0;
        hatWerkbank = false;

        for (int i = 0; i < player().getInventory().getContainerSize(); i++) {
            ItemStack stack = player().getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int menge = stack.getCount();
            int rang = stufeAus(id);
            if (id.endsWith("_pickaxe")) {
                hacke = Math.max(hacke, rang);
            } else if (id.endsWith("_sword")) {
                schwert = Math.max(schwert, rang);
            } else if (id.endsWith("_axe")) {
                axt = Math.max(axt, rang);
            } else if (id.endsWith("_log")) {
                holz += menge;
            } else if (id.endsWith("_planks")) {
                planken += menge;
            } else if (id.equals("stick")) {
                stiele += menge;
            } else if (id.equals("cobblestone") || id.equals("cobbled_deepslate")) {
                stein += menge;
            } else if (id.equals("crafting_table")) {
                hatWerkbank = true;
            }
            if (stack.get(net.minecraft.core.component.DataComponents.FOOD) != null) {
                essenZahl += menge;
            }
        }

        // Fuer den Craft-Schritt merken.
        bHacke = hacke; bSchwert = schwert; bAxt = axt;
        bHolz = holz; bPlanken = planken; bStiele = stiele; bStein = stein;
        bZiel = ziel;

        java.util.List<String> fehlt = new java.util.ArrayList<>();
        holzSammeln = false;
        steinSammeln = false;

        // Werkzeuge unter der Zielstufe.
        pruefeWerkzeug("Spitzhacke", hacke, ziel, fehlt);
        pruefeWerkzeug("Schwert", schwert, ziel, fehlt);
        pruefeWerkzeug("Axt", axt, ziel, fehlt);

        // Fuer jedes Werkzeug braucht es Stiele, fuer Stiele Holz.
        boolean werkzeugFehlt = hacke < ziel || schwert < ziel || axt < ziel;
        if (werkzeugFehlt && holz == 0 && planken == 0 && stiele == 0) {
            holzSammeln = true;
        }

        // Vorraete.
        if (holz < minHolz.getInt()) {
            fehlt.add("Holz (" + (minHolz.getInt() - holz) + " fehlen)");
            holzSammeln = true;
        }
        if (ziel >= 3 && stein < minStein.getInt()) {
            fehlt.add("Stein (" + (minStein.getInt() - stein) + " fehlen)");
            steinSammeln = true;
        }
        if (essenZahl < minEssen.getInt()) {
            // Essen laesst sich nicht aus Bloecken abbauen - nur melden.
            fehlt.add("Essen (" + (minEssen.getInt() - essenZahl) + " fehlen)");
        }

        fehltListe = fehlt;
    }

    private void pruefeWerkzeug(String name, int stufe, int ziel, java.util.List<String> fehlt) {
        if (stufe >= ziel) {
            return;
        }
        if (stufe == 0) {
            fehlt.add("Holz-" + name);
            holzSammeln = true;
        } else if (ziel >= 3) {
            fehlt.add("Stein-" + name);
            steinSammeln = true;
        }
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

    // ------------------------------------------------- Ausruestung erkennen

    /**
     * Die hoechste Werkzeugstufe, die der Spieler schon dabei hat. Gelesen
     * ueber die Item-IDs statt ueber ein Material - das bleibt ueber die
     * Fassungen hinweg stabil, waehrend Minecraft die Werkzeug-Materialien
     * mehrfach umgebaut hat.
     */
    private String werkzeugStufe() {
        int besteSchaufel = 0;
        int besteWaffe = 0;
        for (int i = 0; i < player().getInventory().getContainerSize(); i++) {
            ItemStack stack = player().getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int rang = stufeAus(id);
            if (rang == 0) {
                continue;
            }
            if (id.endsWith("_pickaxe")) {
                besteSchaufel = Math.max(besteSchaufel, rang);
            }
            if (id.endsWith("_sword") || id.endsWith("_axe")) {
                besteWaffe = Math.max(besteWaffe, rang);
            }
        }
        int hoechste = Math.max(besteSchaufel, besteWaffe);
        return NAME[hoechste];
    }

    private static final String[] NAME =
            {"nichts", "Holz", "Gold", "Stein", "Eisen", "Diamant", "Netherit"};

    private static int stufeAus(String id) {
        if (id.startsWith("wooden_")) return 1;
        if (id.startsWith("golden_")) return 2;
        if (id.startsWith("stone_")) return 3;
        if (id.startsWith("iron_")) return 4;
        if (id.startsWith("diamond_")) return 5;
        if (id.startsWith("netherite_")) return 6;
        return 0;
    }

    @Override
    public String hudSuffix() {
        if (aktiverAngreifer != null) {
            return "Notwehr";
        }
        if (fehltListe.isEmpty()) {
            return werkzeugStufe() + ", fertig";
        }
        return "braucht " + fehltListe.size();
    }
}
