package net.glowcube.client.module.combat;

import net.glowcube.client.agent.PvpFreigabe;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.render.Netz;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.DamageUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.PlayerUtils;
import net.glowcube.client.util.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * PvP Pro: kaempft fuer dich gegen einen Spieler - so, wie ein guter Spieler
 * es tun wuerde, nicht wie eine KillAura.
 *
 * <p><b>Nur mit Erlaubnis:</b> laeuft in der eigenen Welt (Einzelspieler,
 * LAN) und auf Servern, die es ueber das GlowCube-Plugin ausdruecklich
 * freigeben ({@link PvpFreigabe}). Ueberall sonst schaltet es sich ab.
 *
 *
 * <ul>
 *   <li><b>Ziel:</b> der erste Spieler, den du schlaegst (oder der naechste,
 *       je nach Einstellung). Das Ziel steht rechts im HUD.</li>
 *   <li><b>Zielen:</b> die Kamera dreht sich sichtbar und weich zum Gegner,
 *       mit leichtem Zittern und wechselndem Trefferpunkt - kein Einrasten.
 *       Geschlagen wird nur, wenn das Fadenkreuz wirklich auf ihm liegt.</li>
 *   <li><b>Schwert / Axt:</b> Schlaege mit vollem Cooldown (leicht
 *       schwankend), auf Wunsch kritisch aus dem Sprung, Schild brechen mit
 *       der Axt, gelegentliche Fehlschlaege wie bei einem Menschen.</li>
 *   <li><b>Crystal:</b> setzt End-Kristalle auf Obsidian/Bedrock neben dem
 *       Gegner (setzt Obsidian, wenn keins da ist) und zuendet sie - aber nur,
 *       wenn es ihm genug schadet und dir hoechstens so viel, wie eingestellt.
 *       Nie so, dass du daran stirbst.</li>
 *   <li><b>Ueberleben:</b> isst bei Hunger, isst goldene Aepfel, wenn das
 *       Leben knapp wird, haelt ein Totem in der Nebenhand und weicht zurueck,
 *       wenn nichts mehr hilft.</li>
 *   <li><b>Laufen:</b> geht auf Abstand oder ran, kreist seitlich (Strafen),
 *       sprintet, springt ueber Kanten.</li>
 * </ul>
 */
public final class PvpPro extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus", "Wie gekaempft wird",
            "Schwert", "Schwert", "Axt", "Crystal"));
    private final ModeSetting zielWahl = register(new ModeSetting("Ziel",
            "Erster Schlag: wen du zuerst schlaegst. Naechster: der naechste Spieler",
            "Erster Schlag", "Erster Schlag", "Naechster"));
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Ab welchem Abstand geschlagen wird", 2.9, 2.5, 3.0, 0.05));
    private final NumberSetting zielTempo = register(new NumberSetting("Zielen-Tempo",
            "Wie schnell die Kamera dreht (Grad pro Tick, niedriger = menschlicher)", 22, 6, 60, 1));
    private final BooleanSetting laufen = register(new BooleanSetting("Laufen",
            "Selbst auf den Gegner zu- und um ihn herumlaufen", true));
    private final BooleanSetting strafen = register(new BooleanSetting("Strafen",
            "Seitlich um den Gegner kreisen", true));
    private final BooleanSetting krits = register(new BooleanSetting("Kritische Treffer",
            "Aus dem Sprung schlagen (Schwert/Axt)", true));
    private final BooleanSetting schildBrechen = register(new BooleanSetting("Schild brechen",
            "Blockt der Gegner, kurz zur Axt wechseln", true));
    private final NumberSetting fehler = register(new NumberSetting("Menschliche Fehler",
            "Wie oft (in Prozent) er daneben schlaegt oder zoegert", 6, 0, 25, 1));
    private final BooleanSetting essen = register(new BooleanSetting("Essen",
            "Bei Hunger essen", true));
    private final NumberSetting gappleLeben = register(new NumberSetting("Gapple ab Leben",
            "Goldenen Apfel essen, wenn das Leben darunter faellt (0 = nie)", 12, 0, 20, 1));
    private final BooleanSetting totem = register(new BooleanSetting("Totem",
            "Ein Totem in der Nebenhand halten", true));
    private final NumberSetting rueckzug = register(new NumberSetting("Rueckzug ab Leben",
            "Zurueckweichen, wenn das Leben darunter faellt und nichts mehr zu essen da ist", 5, 0, 20, 1));
    private final NumberSetting kristallMin = register(new NumberSetting("Kristall Mindestschaden",
            "Crystal: so viel Schaden muss ein Kristall dem Gegner mindestens machen", 6, 1, 20, 0.5));
    private final NumberSetting kristallEigen = register(new NumberSetting("Kristall max. Eigenschaden",
            "Crystal: hoechstens so viel Schaden darf er dir machen", 7, 0, 20, 0.5));
    private final BooleanSetting obsidian = register(new BooleanSetting("Obsidian setzen",
            "Crystal: Obsidian neben den Gegner setzen, wenn keins da ist", true));

    private final Random zufall = new Random();

    private Player ziel;
    private Vec3 trefferVersatz = Vec3.ZERO;
    private int versatzAlter;
    private int aktionPause;
    private float cooldownSchwelle = 0.95f;
    private boolean springtFuerKrit;
    private int strafeRichtung = 1;
    private int strafeWechsel;
    private boolean isst;
    private int essTicks;
    private boolean steuert;

    public PvpPro() {
        super("PvP Pro", "Kaempft fuer dich gegen einen Spieler - Schwert, Axt oder Crystal", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        ziel = null;
        isst = false;
        if (inGame() && !PvpFreigabe.erlaubt()) {
            Netz.nachricht(Component.literal("[PvP Pro] Nur in deiner eigenen Welt oder auf Servern, "
                    + "die es freigeben (GlowCube-Plugin, pvp-pro-erlaubt)."), false);
            setEnabledSilently(false);
            return;
        }
        if (inGame()) {
            Netz.nachricht(Component.literal(zielWahl.is("Erster Schlag")
                    ? "[PvP Pro] Bereit - schlag den Spieler, gegen den ich kaempfen soll."
                    : "[PvP Pro] Bereit - ich nehme den naechsten Spieler."), false);
        }
    }

    @Override
    public void onDisable() {
        tastenLos();
        if (isst) {
            mc.options.keyUse.setDown(false);
            isst = false;
        }
        ziel = null;
    }

    /** Der erste Spieler, den du schlaegst, wird zum Ziel. */
    @Override
    public boolean onEntityAttack(Entity getroffen) {
        if (ziel == null && getroffen instanceof Player p && p != player()) {
            ziel = p;
            Netz.nachricht(Component.literal("[PvP Pro] Ziel: " + p.getName().getString()), false);
        }
        return false;
    }

    @Override
    public String hudSuffix() {
        if (ziel == null) {
            return null;
        }
        return ziel.getName().getString() + " " + Math.round(PlayerUtils.lebenGesamt(ziel));
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void onTick() {
        if (!inGame() || Netz.bildschirm() != null) {
            return;
        }
        if (!PvpFreigabe.erlaubt()) {
            setEnabled(false);
            return;
        }
        if (aktionPause > 0) {
            aktionPause--;
        }
        zielPflegen();
        if (totem.get()) {
            totemHalten();
        }
        if (essenPflegen()) {
            return;
        }
        if (ziel == null) {
            tastenLos();
            return;
        }

        Vec3 punkt = trefferPunkt();
        double abstand = player().distanceTo(ziel);
        boolean crystal = modus.is("Crystal");

        // Zurueckweichen, wenn es brenzlig wird und nichts mehr hilft.
        if (PlayerUtils.lebenGesamt(player()) <= rueckzug.get() && !hatGapple()) {
            zielen(punkt);
            bewegen(abstand, 8, 12);
            return;
        }

        if (crystal) {
            if (!kristallKampf()) {
                zielen(punkt);
            }
            if (laufen.get()) {
                bewegen(abstand, 3.2, 4.6);
            }
        } else {
            zielen(punkt);
            if (laufen.get()) {
                bewegen(abstand, reichweite.get() - 0.6, reichweite.get() - 0.1);
            }
            nahkampf(abstand);
        }
    }

    // --------------------------------------------------------------- Ziel

    private void zielPflegen() {
        if (ziel != null && (!ziel.isAlive() || ziel.isRemoved() || ziel.level() != level()
                || player().distanceTo(ziel) > 48)) {
            Netz.nachricht(Component.literal("[PvP Pro] " + ziel.getName().getString()
                    + (ziel.isAlive() ? " ist weg." : " ist besiegt.")), false);
            ziel = null;
            tastenLos();
        }
        if (ziel == null && zielWahl.is("Naechster")) {
            Player bester = null;
            double besterAbstand = 16 * 16;
            for (Player p : level().players()) {
                if (p == player() || !p.isAlive() || p.isSpectator() || p.isCreative()) {
                    continue;
                }
                double d = p.distanceToSqr(player());
                if (d < besterAbstand) {
                    besterAbstand = d;
                    bester = p;
                }
            }
            ziel = bester;
        }
    }

    /** Ein Punkt am Oberkoerper - wechselt alle ein, zwei Sekunden, wie bei einem Menschen. */
    private Vec3 trefferPunkt() {
        if (--versatzAlter <= 0) {
            versatzAlter = 20 + zufall.nextInt(25);
            trefferVersatz = new Vec3((zufall.nextDouble() - 0.5) * 0.35,
                    ziel.getBbHeight() * (0.55 + zufall.nextDouble() * 0.3),
                    (zufall.nextDouble() - 0.5) * 0.35);
        }
        return ziel.position().add(trefferVersatz);
    }

    // ------------------------------------------------------------- Zielen

    /**
     * Die Kamera weich auf den Punkt drehen: grosse Winkel schnell, kleine
     * langsam (wie eine Handbewegung), nie mehr als das Zielen-Tempo, mit
     * etwas Zittern. Das ist die echte Blickrichtung - kein unsichtbares Drehen.
     */
    private boolean zielen(Vec3 punkt) {
        float gier = player().getYRot();
        float neigung = player().getXRot();
        float dGier = Mth.wrapDegrees((float) (Rotations.getYaw(punkt) - gier));
        float dNeigung = (float) (Rotations.getPitch(punkt) - neigung);
        float max = (float) (zielTempo.get() * (0.75 + zufall.nextDouble() * 0.5));
        float schrittGier = Mth.clamp(dGier * 0.45f, -max, max) + (float) ((zufall.nextDouble() - 0.5) * 0.5);
        float schrittNeigung = Mth.clamp(dNeigung * 0.45f, -max, max) + (float) ((zufall.nextDouble() - 0.5) * 0.3);
        player().setYRot(gier + schrittGier);
        player().setXRot(Mth.clamp(neigung + schrittNeigung, -90f, 90f));
        return Math.abs(dGier) < 6 && Math.abs(dNeigung) < 6;
    }

    // ------------------------------------------------------------ Nahkampf

    private void nahkampf(double abstand) {
        boolean axt = modus.is("Axt") || schildBrechen.get() && ziel.isBlocking();
        waffeNehmen(axt);

        boolean imFadenkreuz = mc.hitResult instanceof EntityHitResult e && e.getEntity() == ziel;
        boolean bereit = player().getAttackStrengthScale(0.5f) >= cooldownSchwelle;
        if (!bereit || abstand > reichweite.get() + 0.3 || aktionPause > 0) {
            return;
        }
        // Kritischer Treffer: erst hochspringen, im Fallen schlagen.
        if (krits.get() && player().onGround() && !player().isInWater()) {
            if (!springtFuerKrit) {
                mc.options.keyJump.setDown(true);
                springtFuerKrit = true;
                return;
            }
        }
        if (springtFuerKrit) {
            mc.options.keyJump.setDown(false);
            if (player().onGround() || player().getDeltaMovement().y > -0.05) {
                return; // noch im Aufstieg
            }
            springtFuerKrit = false;
        }
        if (!imFadenkreuz || abstand > reichweite.get()) {
            return;
        }
        // Ab und zu daneben oder zu spaet - wie ein Mensch.
        if (zufall.nextInt(100) < fehler.getInt()) {
            Netz.schwingen(InteractionHand.MAIN_HAND);
            aktionPause = 2 + zufall.nextInt(4);
            return;
        }
        mc.gameMode.attack(player(), ziel);
        Netz.schwingen(InteractionHand.MAIN_HAND);
        cooldownSchwelle = 0.9f + zufall.nextFloat() * 0.1f;
        aktionPause = zufall.nextInt(3);
    }

    private void waffeNehmen(boolean axt) {
        FindItemResult fund = InvUtils.findeInHotbar(s -> axt ? s.is(ItemTags.AXES) : s.is(ItemTags.SWORDS));
        if (!fund.found() && axt) {
            fund = InvUtils.findeInHotbar(s -> s.is(ItemTags.SWORDS));
        }
        if (fund.found() && fund.isHotbar() && player().getInventory().getSelectedSlot() != fund.slot()) {
            InvUtils.tausche(fund.slot(), false);
        }
    }

    // ------------------------------------------------------------- Crystal

    /** @return true, wenn diesen Tick gezielt wurde (auf Kristall oder Block) */
    private boolean kristallKampf() {
        // 1. Liegt ein lohnender Kristall in Reichweite? Anvisieren und zuenden.
        EndCrystal kristall = besterKristall();
        if (kristall != null) {
            boolean drauf = zielen(kristall.position().add(0, 0.6, 0));
            if (drauf && aktionPause == 0 && mc.hitResult instanceof EntityHitResult e && e.getEntity() == kristall) {
                mc.gameMode.attack(player(), kristall);
                Netz.schwingen(InteractionHand.MAIN_HAND);
                aktionPause = 2 + zufall.nextInt(3);
            }
            return true;
        }
        // 2. Einen Kristall setzen.
        FindItemResult kristalle = InvUtils.findeInHotbar(Items.END_CRYSTAL);
        if (!kristalle.found()) {
            return false;
        }
        BlockPos basis = besteBasis();
        if (basis != null) {
            Vec3 oben = Vec3.atCenterOf(basis).add(0, 0.5, 0);
            boolean drauf = zielen(oben);
            if (drauf && aktionPause == 0 && mc.hitResult instanceof BlockHitResult b
                    && b.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(basis)) {
                InvUtils.tausche(kristalle.slot(), false);
                BlockUtils.benutzen(new BlockHitResult(oben, Direction.UP, basis, false), InteractionHand.MAIN_HAND, true);
                aktionPause = 2 + zufall.nextInt(3);
            }
            return true;
        }
        // 3. Keine Basis: Obsidian neben den Gegner setzen.
        if (obsidian.get()) {
            FindItemResult obsi = InvUtils.findeInHotbar(Items.OBSIDIAN);
            BlockPos platz = obsidianPlatz();
            if (obsi.found() && platz != null) {
                boolean drauf = zielen(Vec3.atCenterOf(platz.below()).add(0, 0.5, 0));
                if (drauf && aktionPause == 0) {
                    BlockUtils.setzen(platz, obsi, false, 50, true, true);
                    aktionPause = 3 + zufall.nextInt(3);
                }
                return true;
            }
        }
        return false;
    }

    private EndCrystal besterKristall() {
        EndCrystal bester = null;
        float besterSchaden = 0;
        AABB box = player().getBoundingBox().inflate(5);
        for (EndCrystal k : level().getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive)) {
            if (player().getEyePosition().distanceTo(k.position().add(0, 0.6, 0)) > 4.5) {
                continue;
            }
            float anZiel = DamageUtils.kristallSchaden(ziel, k.position());
            if (anZiel < kristallMin.get() || !sicherFuerMich(k.position())) {
                continue;
            }
            if (anZiel > besterSchaden) {
                besterSchaden = anZiel;
                bester = k;
            }
        }
        return bester;
    }

    private BlockPos besteBasis() {
        BlockPos mitte = ziel.blockPosition();
        BlockPos beste = null;
        float besterSchaden = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos b = mitte.offset(dx, dy, dz);
                    BlockState s = level().getBlockState(b);
                    if (!s.is(Blocks.OBSIDIAN) && !s.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (!level().getBlockState(b.above()).isAir() || !level().getBlockState(b.above(2)).isAir()) {
                        continue;
                    }
                    Vec3 oben = Vec3.atCenterOf(b).add(0, 0.5, 0);
                    if (player().getEyePosition().distanceTo(oben) > 4.5) {
                        continue;
                    }
                    AABB platz = new AABB(b.above()).expandTowards(0, 1, 0);
                    if (!level().getEntitiesOfClass(Entity.class, platz, Entity::isAlive).isEmpty()) {
                        continue;
                    }
                    Vec3 explosion = Vec3.atBottomCenterOf(b.above());
                    float anZiel = DamageUtils.kristallSchaden(ziel, explosion);
                    if (anZiel < kristallMin.get() || !sicherFuerMich(explosion)) {
                        continue;
                    }
                    if (anZiel > besterSchaden) {
                        besterSchaden = anZiel;
                        beste = b;
                    }
                }
            }
        }
        return beste;
    }

    /** Ein freier Platz neben dem Gegner, auf Fusshoehe, mit Boden darunter und Luft darueber. */
    private BlockPos obsidianPlatz() {
        BlockPos fuesse = ziel.blockPosition();
        BlockPos bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int weite = 1; weite <= 2; weite++) {
                BlockPos p = fuesse.relative(d, weite);
                if (!BlockUtils.kannSetzen(p) || !level().getBlockState(p.above()).isAir()
                        || !level().getBlockState(p.above(2)).isAir()
                        || level().getBlockState(p.below()).isAir()) {
                    continue;
                }
                double abstand = player().getEyePosition().distanceTo(Vec3.atCenterOf(p));
                if (abstand > 4.5 || !sicherFuerMich(Vec3.atBottomCenterOf(p.above()))) {
                    continue;
                }
                if (abstand < besterAbstand) {
                    besterAbstand = abstand;
                    bester = p;
                }
            }
        }
        return bester;
    }

    /** Nie so, dass ein Kristall dich toetet oder mehr schadet als eingestellt. */
    private boolean sicherFuerMich(Vec3 explosion) {
        float eigen = DamageUtils.kristallSchaden(player(), explosion);
        return eigen <= kristallEigen.get() && eigen < PlayerUtils.lebenGesamt(player()) - 2;
    }

    // ------------------------------------------------------------ Bewegung

    /** Auf Abstand zwischen nah und fern bleiben, seitlich kreisen, sprinten, ueber Kanten springen. */
    private void bewegen(double abstand, double nah, double fern) {
        steuert = true;
        boolean vor = abstand > fern;
        boolean zurueck = abstand < nah;
        mc.options.keyUp.setDown(vor);
        mc.options.keyDown.setDown(zurueck);
        mc.options.keySprint.setDown(vor);
        if (strafen.get() && abstand < 7) {
            if (--strafeWechsel <= 0) {
                strafeWechsel = 15 + zufall.nextInt(30);
                strafeRichtung = zufall.nextBoolean() ? 1 : -1;
            }
            mc.options.keyLeft.setDown(strafeRichtung < 0);
            mc.options.keyRight.setDown(strafeRichtung > 0);
        } else {
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
        }
        if (player().horizontalCollision && player().onGround() && !springtFuerKrit) {
            mc.options.keyJump.setDown(true);
        } else if (!springtFuerKrit) {
            mc.options.keyJump.setDown(false);
        }
    }

    private void tastenLos() {
        if (!steuert) {
            return;
        }
        steuert = false;
        springtFuerKrit = false;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    // ---------------------------------------------------------- Ueberleben

    private void totemHalten() {
        if (player().getOffhandItem().is(Items.TOTEM_OF_UNDYING) || isst) {
            return;
        }
        FindItemResult fund = InvUtils.finde(Items.TOTEM_OF_UNDYING);
        if (fund.found() && aktionPause == 0) {
            InvUtils.verschieben().von(fund.slot()).nachNebenhand();
            aktionPause = 2;
        }
    }

    private boolean hatGapple() {
        return InvUtils.findeInHotbar(s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)).found();
    }

    /**
     * Essen hat Vorrang: goldener Apfel bei knappem Leben, sonst normales
     * Essen bei Hunger (nicht mitten im Schlagabtausch). Beim Essen weicht er
     * zurueck und behaelt den Gegner im Blick.
     *
     * @return true, solange gegessen wird
     */
    private boolean essenPflegen() {
        if (isst) {
            essTicks++;
            boolean fertig = essTicks > 45 && !player().isUsingItem() || essTicks > 80;
            if (fertig) {
                mc.options.keyUse.setDown(false);
                isst = false;
                return false;
            }
            mc.options.keyUse.setDown(true);
            if (ziel != null) {
                zielen(trefferPunkt());
                if (laufen.get()) {
                    bewegen(player().distanceTo(ziel), 6, 12);
                }
            }
            return true;
        }
        float leben = PlayerUtils.lebenGesamt(player());
        if (gappleLeben.get() > 0 && leben <= gappleLeben.get() && player().getAbsorptionAmount() <= 0) {
            FindItemResult apfel = InvUtils.findeInHotbar(s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE));
            if (apfel.found()) {
                return essenAnfangen(apfel.slot());
            }
        }
        boolean hungrig = player().getFoodData().getFoodLevel() <= 14;
        boolean ruhig = ziel == null || player().distanceTo(ziel) > 6;
        if (essen.get() && hungrig && ruhig) {
            FindItemResult futter = InvUtils.findeInHotbar(s -> s.has(DataComponents.FOOD)
                    && !s.is(Items.GOLDEN_APPLE) && !s.is(Items.ENCHANTED_GOLDEN_APPLE)
                    && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE) && !s.is(Items.POISONOUS_POTATO)
                    && !s.is(Items.PUFFERFISH) && !s.is(Items.CHORUS_FRUIT));
            if (futter.found()) {
                return essenAnfangen(futter.slot());
            }
        }
        return false;
    }

    private boolean essenAnfangen(int platz) {
        if (!InvUtils.tausche(platz, false)) {
            return false;
        }
        ItemStack hand = player().getInventory().getItem(platz);
        if (hand.isEmpty()) {
            return false;
        }
        mc.options.keyUse.setDown(true);
        isst = true;
        essTicks = 0;
        return true;
    }
}
