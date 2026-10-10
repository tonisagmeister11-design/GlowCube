package net.glowcube.client.module.combat;

import java.util.Random;

import net.glowcube.client.agent.PvpFreigabe;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.render.Netz;
import net.glowcube.client.util.Rotations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Legendora - menschlicher Nahkaempfer.
 *
 * Der Spieler, den man als Naechstes schlaegt, wird zum Ziel: Das Fadenkreuz folgt ihm weich
 * (nicht ruckartig), man laeuft ihm hinterher, und geschlagen wird nur, wenn das Fadenkreuz
 * wirklich auf ihm liegt. Der Takt ist bewusst nie derselbe: Die Aufladung wird knapp unter
 * oder genau am Optimum abgewartet, manchmal ein, zwei Ticks laenger - der Schaden bleibt
 * fast voll, aber es entsteht kein starres Schlagmuster.
 *
 * Wie PvP Pro nur in der eigenen Welt oder auf Servern, die es freigeben.
 */
public final class Legendora extends Module {

    private static final double VERLUST = 24.0;

    private final NumberSetting drehTempo;
    private final NumberSetting streuung;
    private final NumberSetting abstand;
    private final BooleanSetting verfolgen;
    private final BooleanSetting sprinten;
    private final BooleanSetting nurWaffe;

    private final Random zufall = new Random();

    private Player ziel;
    private Vec3 versatz = new Vec3(0.0, 0.0, 0.0);
    private int versatzAlter;
    private int schlagPause;
    private float schwelle = 1.0f;
    private boolean steuert;

    public Legendora() {
        super("Legendora", "Folgt dem Spieler, den du schlaegst, und trifft in menschlichem Takt", Category.COMBAT);
        drehTempo = register(new NumberSetting("TurnSpeed", "Wie schnell das Fadenkreuz nachzieht (Grad pro Tick)", 14.0, 4.0, 40.0, 1.0));
        streuung = register(new NumberSetting("Variance", "Wie stark der Schlagtakt schwankt (Ticks)", 2.0, 0.0, 5.0, 1.0));
        abstand = register(new NumberSetting("Distance", "Wie nah man dem Ziel auf die Pelle rueckt", 2.4, 1.5, 3.2, 0.1));
        verfolgen = register(new BooleanSetting("Follow", "Dem Ziel hinterherlaufen", true));
        sprinten = register(new BooleanSetting("Sprint", "Beim Hinterherlaufen sprinten", true));
        nurWaffe = register(new BooleanSetting("WeaponOnly", "Nur mit Schwert oder Axt in der Hand", true));
    }

    @Override
    public void onEnable() {
        ziel = null;
        schlagPause = 0;
        neuerTakt();
        if (inGame() && !PvpFreigabe.erlaubt()) {
            Netz.nachricht(Component.literal("[Legendora] Nur in deiner eigenen Welt oder auf Servern, die es freigeben (GlowCube-Plugin, pvp-pro-erlaubt)."), false);
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        ziel = null;
        tastenLos();
    }

    /** Der Spieler, den man schlaegt, wird zum Ziel. Der Schlag selbst bleibt unangetastet. */
    @Override
    public boolean onEntityAttack(Entity entity) {
        if (inGame() && entity instanceof Player spieler && spieler != player() && spieler.isAlive()) {
            if (ziel != spieler) {
                ziel = spieler;
                neuerVersatz();
                Netz.nachricht(Component.literal("[Legendora] Ziel: " + spieler.getName().getString()), false);
            }
        }
        return false;
    }

    @Override
    public String hudSuffix() {
        return ziel == null ? "" : ziel.getName().getString();
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            return;
        }
        if (!PvpFreigabe.erlaubt()) {
            setEnabled(false);
            return;
        }
        LocalPlayer ich = player();
        if (Netz.bildschirm() != null || !ich.isAlive() || ich.isSpectator()) {
            tastenLos();
            return;
        }
        if (ziel != null && (!ziel.isAlive() || ziel.isSpectator() || ziel.distanceToSqr(ich) > VERLUST * VERLUST)) {
            ziel = null;
        }
        if (ziel == null) {
            tastenLos();
            return;
        }

        zielen(ich);
        laufen(ich);
        schlagen(ich);
    }

    /** Fadenkreuz weich auf einen leicht wandernden Punkt am Koerper des Ziels ziehen. */
    private void zielen(LocalPlayer ich) {
        if (--versatzAlter <= 0) {
            neuerVersatz();
        }
        Vec3 punkt = ziel.position().add(versatz.x, versatz.y + ziel.getBbHeight() * 0.62, versatz.z);

        float yaw = ich.getYRot();
        float pitch = ich.getXRot();
        float diffYaw = Mth.wrapDegrees((float) Rotations.getYaw(punkt) - yaw);
        float diffPitch = (float) Rotations.getPitch(punkt) - pitch;

        float tempo = (float) drehTempo.get() * (0.65f + zufall.nextFloat() * 0.7f);
        float pitchTempo = tempo * 0.6f;
        // weich anfahren, am Ziel abbremsen - und nie ganz exakt ankommen
        float schrittYaw = Mth.clamp(diffYaw * (0.30f + zufall.nextFloat() * 0.15f), -tempo, tempo);
        float schrittPitch = Mth.clamp(diffPitch * (0.25f + zufall.nextFloat() * 0.15f), -pitchTempo, pitchTempo);

        ich.setYRot(yaw + schrittYaw);
        ich.setXRot(Mth.clamp(pitch + schrittPitch, -90.0f, 90.0f));
    }

    private void laufen(LocalPlayer ich) {
        if (!verfolgen.get()) {
            tastenLos();
            return;
        }
        steuert = true;
        double dx = ziel.getX() - ich.getX();
        double dz = ziel.getZ() - ich.getZ();
        double weite = Math.sqrt(dx * dx + dz * dz);
        boolean vor = weite > abstand.get() - 0.2 + zufall.nextDouble() * 0.4;
        mc.options.keyUp.setDown(vor);
        mc.options.keySprint.setDown(vor && sprinten.get());
        mc.options.keyJump.setDown(vor && ich.horizontalCollision && ich.onGround());
    }

    private void schlagen(LocalPlayer ich) {
        if (schlagPause > 0) {
            schlagPause--;
            return;
        }
        if (ich.isUsingItem()) {
            return;
        }
        if (nurWaffe.get() && !waffeInHand(ich)) {
            return;
        }
        if (ich.getAttackStrengthScale(0.0f) < schwelle) {
            return;
        }
        // nur schlagen, wenn das Fadenkreuz wirklich auf dem Ziel liegt - sonst gar nicht
        HitResult getroffen = mc.hitResult;
        if (!(getroffen instanceof EntityHitResult treffer) || treffer.getEntity() != ziel) {
            return;
        }
        mc.gameMode.attack(ich, ziel);
        Netz.schwingen(InteractionHand.MAIN_HAND);
        ich.resetAttackStrengthTicker();
        neuerTakt();
    }

    /** Naechster Schlag: Schwelle knapp unter dem Optimum, dazu manchmal ein paar Ticks Verzoegerung. */
    private void neuerTakt() {
        schwelle = 0.93f + zufall.nextFloat() * 0.07f;
        int verzoegerung = 0;
        int max = streuung.getInt();
        while (verzoegerung < max && zufall.nextInt(3) == 0) {
            verzoegerung++;
        }
        schlagPause = verzoegerung;
    }

    private void neuerVersatz() {
        versatz = new Vec3((zufall.nextDouble() - 0.5) * 0.4, (zufall.nextDouble() - 0.5) * 0.35, (zufall.nextDouble() - 0.5) * 0.4);
        versatzAlter = 6 + zufall.nextInt(10);
    }

    private static boolean waffeInHand(LocalPlayer ich) {
        ItemStack stack = ich.getMainHandItem();
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    }

    private void tastenLos() {
        if (!steuert) {
            return;
        }
        steuert = false;
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }
}
