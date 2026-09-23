package net.glowcube.client.agent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Guardian-Agent: ein Leibwaechter in voller Ruestung (Eisen, Diamant oder
 * Netherite) mit Schwert. Er haelt sich bei seinem Spieler und greift an:
 * jedes Monster im Umkreis von 16 Bloecken um den Spieler, jeden Mob, der
 * den Spieler ins Visier nimmt, und wer den Spieler gerade getroffen hat.
 * Der naechste zum Spieler zuerst.
 *
 * <p>Hat der Spieler nur noch ein Herz, laesst er den Gegner stehen, stellt
 * sich neben ihn und wehrt nur noch ab, was direkt an ihm dran ist - bis der
 * Spieler wieder drei Herzen hat.
 */
final class AgentWaechter implements AgentArbeiter {
    private static final double UMKREIS = 16;
    private static final double REICHWEITE = 3.0;
    private static final int ANGRIFF_PAUSE = 12;

    private final UUID besitzer;
    private final String ruestung;
    private final float schaden;
    private AgentWerte werte;

    private ServerLevel welt;
    private Mannequin koerper;
    private BlockPos fuesse;
    private final ChunkHalter chunks = new ChunkHalter();

    private LivingEntity ziel;
    private int angriffPause;
    private int suchPause;
    private boolean schuetzen;
    private int kills;
    private boolean fertig;

    private List<BlockPos> pfad;
    private int pfadIndex;
    private int planAlter;
    private Vec3 bewegVon;
    private Vec3 bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private int ticks;

    private AgentWaechter(UUID besitzer, String ruestung, AgentWerte werte) {
        this.besitzer = besitzer;
        this.ruestung = ruestung;
        this.werte = werte;
        // Schwertschaden wie mit Schaerfe III - er soll zuegig aufraeumen.
        this.schaden = switch (ruestung) {
            case "Eisen" -> 8f;
            case "Netherite" -> 12f;
            default -> 10f;
        };
    }

    static AgentWaechter erschaffen(ServerPlayer spieler, String ruestung, AgentWerte werte) {
        AgentWaechter w = new AgentWaechter(spieler.getUUID(), ruestung, werte);
        ServerLevel welt = (ServerLevel) spieler.level();
        if (!w.koerperBauen(welt, Agent.sichererPlatzBei(welt, spieler.blockPosition()))) {
            return null;
        }
        w.effekt(ParticleTypes.PORTAL, 40);
        return w;
    }

    private boolean koerperBauen(ServerLevel neueWelt, BlockPos platz) {
        Mannequin neu = AgentFassung.mannequin(neueWelt);
        if (neu == null) {
            return false;
        }
        neu.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, 0, 0);
        neu.setNoGravity(true);
        AgentFassung.unverwundbar(neu);
        neu.setCustomNameVisible(true);
        ausruesten(neu);
        if (!neueWelt.addFreshEntity(neu)) {
            return false;
        }
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
        welt = neueWelt;
        koerper = neu;
        fuesse = platz;
        pfad = null;
        bewegNach = null;
        namenAktualisieren();
        chunks.halten(welt, fuesse, 1);
        return true;
    }

    private void ausruesten(Mannequin k) {
        switch (ruestung) {
            case "Eisen" -> {
                k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                k.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                k.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                k.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                k.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            case "Netherite" -> {
                k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                k.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                k.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                k.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
                k.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            }
            default -> {
                k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                k.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                k.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                k.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                k.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            }
        }
    }

    @Override
    public UUID besitzer() {
        return besitzer;
    }

    @Override
    public Auftrag auftrag() {
        return Auftrag.WAECHTER;
    }

    @Override
    public String titel() {
        return "Guardian-Agent (" + ruestung + ")";
    }

    @Override
    public boolean beimZurueckkehren() {
        return fertig;
    }

    @Override
    public boolean fertig() {
        return fertig;
    }

    @Override
    public void zurueckrufen() {
        if (koerper != null && !koerper.isRemoved()) {
            effekt(ParticleTypes.POOF, 20);
        }
        fertig = true;
    }

    @Override
    public void einstellen(AgentWerte neu) {
        werte = neu;
    }

    @Override
    public void notfallUebergabe(MinecraftServer server) {
        fertig = true;
    }

    @Override
    public void aufraeumen() {
        chunks.freigeben();
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void tick(MinecraftServer server) {
        if (fertig) {
            return;
        }
        ticks++;
        if (koerper == null || koerper.isRemoved()) {
            fertig = true;
            return;
        }
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        if (spieler == null || !spieler.isAlive()) {
            return;
        }
        if (ticks % 20 == 0) {
            namenAktualisieren();
            chunks.halten(welt, fuesse, 1);
        }
        if (angriffPause > 0) {
            angriffPause--;
        }

        // Zu weit weg oder andere Dimension: zum Spieler springen.
        if (spieler.level() != welt || koerper.distanceToSqr(spieler) > 32 * 32) {
            teleportieren(spieler);
            return;
        }

        // Ein Herz (2 Lebenspunkte) oder weniger: nur noch beschuetzen.
        float leben = spieler.getHealth();
        if (!schuetzen && leben <= 2.0f) {
            schuetzen = true;
            ziel = null;
            pfad = null;
            AgentWelt.melden(spieler, ChatFormatting.RED, "Guardian: Du bist fast tot - ich bleibe bei dir!");
        } else if (schuetzen && leben >= 6.0f) {
            schuetzen = false;
        }

        if (bewegNach != null) {
            bewegen();
        }

        if (--suchPause <= 0 || ziel == null || !ziel.isAlive() || ziel.isRemoved()) {
            suchPause = 5;
            ziel = zielWaehlen(spieler);
        }

        if (ziel != null) {
            kaempfen(spieler);
        } else {
            folgen(spieler);
        }
    }

    /** Der naechste Feind beim Spieler - im Schutzmodus nur, was direkt an ihm dran ist. */
    private LivingEntity zielWaehlen(ServerPlayer spieler) {
        double umkreis = schuetzen ? 4 : UMKREIS;
        AABB box = spieler.getBoundingBox().inflate(umkreis);
        LivingEntity angreifer = spieler.getLastHurtByMob();
        boolean frisch = angreifer != null && spieler.tickCount - spieler.getLastHurtByMobTimestamp() < 100;
        List<LivingEntity> kandidaten = welt.getEntitiesOfClass(LivingEntity.class, box, e ->
                e.isAlive() && e != spieler && e != koerper && !(e instanceof Mannequin)
                        && (e instanceof Enemy
                        || e instanceof Mob m && m.getTarget() == spieler
                        || frisch && e == angreifer && !(e instanceof Player p && p.isCreative())));
        LivingEntity bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (LivingEntity e : kandidaten) {
            double d = e.distanceToSqr(spieler);
            if (d < besterAbstand) {
                besterAbstand = d;
                bester = e;
            }
        }
        return bester;
    }

    private void kaempfen(ServerPlayer spieler) {
        // Nicht zu weit vom Spieler weglocken lassen.
        if (ziel.distanceToSqr(spieler) > (UMKREIS + 6) * (UMKREIS + 6)) {
            ziel = null;
            return;
        }
        anschauen(ziel.getEyePosition());
        double abstand = koerper.distanceTo(ziel);
        if (abstand <= REICHWEITE) {
            if (angriffPause == 0) {
                zuschlagen();
            }
            return;
        }
        if (bewegNach == null) {
            laufenZu(ziel.blockPosition(), 1);
        }
    }

    private void zuschlagen() {
        AgentFassung.schwingen(koerper);
        boolean getroffen = ziel.hurtServer(welt, welt.damageSources().mobAttack(koerper), schaden);
        if (getroffen) {
            double dx = koerper.getX() - ziel.getX();
            double dz = koerper.getZ() - ziel.getZ();
            AgentFassung.rueckstoss(ziel, koerper, welt, dx, dz);
            welt.playSound(null, ziel.getX(), ziel.getY(), ziel.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 1f);
            if (!ziel.isAlive()) {
                kills++;
                ziel = null;
            }
        }
        angriffPause = ANGRIFF_PAUSE;
    }

    private void folgen(ServerPlayer spieler) {
        double abstand = Math.sqrt(fuesse.distSqr(spieler.blockPosition()));
        if (abstand <= (schuetzen ? 1.5 : 3)) {
            pfad = null;
            if (bewegNach == null) {
                anschauen(spieler.getEyePosition());
            }
            return;
        }
        if (bewegNach == null) {
            laufenZu(spieler.blockPosition(), schuetzen ? 1 : 2);
        }
    }

    // ------------------------------------------------------------ Bewegung

    /** Einen Schritt auf dem Weg zum Ziel machen; der Weg wird regelmaessig neu geplant. */
    private void laufenZu(BlockPos zielPos, int nah) {
        if (pfad == null || pfadIndex >= pfad.size() || ++planAlter > 10) {
            planAlter = 0;
            AgentPfad suche = new AgentPfad(welt, false, false);
            pfad = suche.suchen(fuesse, n -> n.distManhattan(zielPos) <= nah, zielPos, 2500, 40);
            pfadIndex = 0;
            if (pfad == null || pfad.isEmpty()) {
                pfad = null;
                return;
            }
        }
        BlockPos nach = pfad.get(pfadIndex);
        if (!AgentBloecke.frei(welt, nach, welt.getBlockState(nach))
                || !AgentBloecke.frei(welt, nach.above(), welt.getBlockState(nach.above()))) {
            pfad = null;
            return;
        }
        pfadIndex++;
        int basis = (int) Math.max(1, Math.round(3 / Math.max(1.0, werte.tempo())));
        bewegVon = koerper.position();
        bewegNach = Vec3.atBottomCenterOf(nach);
        bewegTick = 0;
        bewegDauer = basis;
        Vec3 d = bewegNach.subtract(bewegVon);
        if (d.horizontalDistanceSqr() > 0.01 && ziel == null) {
            float gier = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            ausrichten(gier, 0);
        }
        fuesse = nach;
    }

    private void bewegen() {
        bewegTick++;
        double t = Math.min(1.0, bewegTick / (double) bewegDauer);
        Vec3 p = bewegVon.lerp(bewegNach, t);
        koerper.snapTo(p.x, p.y, p.z, koerper.getYRot(), koerper.getXRot());
        if (t >= 1.0) {
            bewegNach = null;
        }
    }

    private void teleportieren(ServerPlayer spieler) {
        ServerLevel zielWelt = (ServerLevel) spieler.level();
        BlockPos platz = Agent.sichererPlatzBei(zielWelt, spieler.blockPosition());
        effekt(ParticleTypes.PORTAL, 30);
        ziel = null;
        if (zielWelt == welt) {
            koerper.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, koerper.getYRot(), 0);
            fuesse = platz;
            pfad = null;
            bewegNach = null;
        } else if (!koerperBauen(zielWelt, platz)) {
            return;
        }
        effekt(ParticleTypes.PORTAL, 30);
        welt.playSound(null, platz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    private void ausrichten(float gier, float neigung) {
        koerper.setYRot(gier);
        koerper.setXRot(neigung);
        koerper.setYHeadRot(gier);
        koerper.setYBodyRot(gier);
    }

    private void anschauen(Vec3 punkt) {
        Vec3 auge = koerper.getEyePosition();
        Vec3 d = punkt.subtract(auge);
        float gier = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float neigung = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        ausrichten(gier, neigung);
    }

    private void namenAktualisieren() {
        String text = titel() + (kills > 0 ? " · " + kills + " besiegt" : "") + (schuetzen ? " ❤" : "");
        koerper.setCustomName(Component.literal(text).withStyle(schuetzen ? ChatFormatting.RED : ChatFormatting.GOLD));
    }

    private void effekt(SimpleParticleType art, int anzahl) {
        if (koerper != null) {
            welt.sendParticles(art, koerper.getX(), koerper.getY() + 1, koerper.getZ(), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
