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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Guardian- und Jaeger-Agent. Der <b>Jaeger</b> nutzt denselben Koerper und
 * dieselbe Kampflogik, greift aber Tiere an statt Monster: Kuehe, Schweine,
 * Schafe, Huehner und Kaninchen im Umkreis von 24 Bloecken um den Spieler,
 * nie Jungtiere, nie benannte Tiere, und von jeder Sorte laesst er so viele
 * uebrig wie eingestellt. Die Beute sammelt er ein; beim Zurueckrufen wirft
 * er sie dem Spieler zu, mit Sammelkiste bringt er sie dorthin.
 *
 * <p>Guardian-Agent: ein Leibwaechter in voller Ruestung (Eisen, Diamant oder
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
    private final Auftrag auftrag;
    private final int nummer;
    private final boolean jaeger;
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

    // Jaeger: Beute
    private final SimpleContainer lager = new SimpleContainer(27);
    private BlockPos beuteOrt;
    private int beuteTicks;
    private boolean abliefern;
    private int wurfPause;
    private int geliefert;

    private List<BlockPos> pfad;
    private int pfadIndex;
    private int planAlter;
    private Vec3 bewegVon;
    private Vec3 bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private int ticks;

    private AgentWaechter(UUID besitzer, Auftrag auftrag, int nummer, String ruestung, AgentWerte werte) {
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.nummer = nummer;
        this.jaeger = auftrag == Auftrag.JAEGER;
        this.ruestung = ruestung;
        this.werte = werte;
        // Schwertschaden wie mit Schaerfe III - er soll zuegig aufraeumen.
        this.schaden = jaeger ? 8f : switch (ruestung) {
            case "Eisen" -> 8f;
            case "Netherite" -> 12f;
            default -> 10f;
        };
    }

    static AgentWaechter erschaffen(ServerPlayer spieler, Auftrag auftrag, int nummer, String art, AgentWerte werte) {
        AgentWaechter w = new AgentWaechter(spieler.getUUID(), auftrag, nummer, art, werte);
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
        neu.setGlowingTag(werte.leuchten());
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
        if (jaeger) {
            k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            k.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            k.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            k.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            k.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            return;
        }
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
        return auftrag;
    }

    @Override
    public int nummer() {
        return nummer;
    }

    @Override
    public String titel() {
        return auftrag.anzeigename() + " #" + nummer + " (" + ruestung + ")";
    }

    @Override
    public net.minecraft.world.entity.Entity koerper() {
        return koerper;
    }

    @Override
    public String zustandText() {
        if (abliefern) {
            return "bringt die Beute";
        }
        if (schuetzen) {
            return "beschuetzt dich";
        }
        return ziel != null ? (jaeger ? "jagt" : "kaempft") : (jaeger ? "sucht Tiere" : "wacht");
    }

    @Override
    public int beute() {
        return jaeger ? AgentKiste.anzahl(lager) : kills;
    }

    @Override
    public boolean beimZurueckkehren() {
        return fertig || abliefern;
    }

    @Override
    public boolean fertig() {
        return fertig;
    }

    @Override
    public void zurueckrufen() {
        if (jaeger && AgentKiste.anzahl(lager) > 0 && koerper != null && !koerper.isRemoved()) {
            // Erst die Beute zuwerfen, dann gehen.
            abliefern = true;
            ziel = null;
            return;
        }
        if (koerper != null && !koerper.isRemoved()) {
            effekt(ParticleTypes.POOF, 20);
        }
        fertig = true;
    }

    @Override
    public void einstellen(AgentWerte neu) {
        werte = neu;
        if (koerper != null) {
            koerper.setGlowingTag(neu.leuchten());
        }
    }

    @Override
    public void notfallUebergabe(MinecraftServer server) {
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.isEmpty()) {
                continue;
            }
            lager.setItem(i, ItemStack.EMPTY);
            if (spieler != null && spieler.getInventory().add(stapel) && stapel.isEmpty()) {
                continue;
            }
            if (spieler != null) {
                spieler.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(spieler.level(),
                        spieler.getX(), spieler.getY() + 0.5, spieler.getZ(), stapel));
            }
        }
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
        if (!jaeger && !schuetzen && leben <= 2.0f) {
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

        if (jaeger) {
            if (abliefern) {
                abliefern(spieler);
                return;
            }
            beuteEinsammeln();
            if (lagerVoll()) {
                lagerLeeren(spieler);
                return;
            }
        }

        if (--suchPause <= 0 || ziel == null || !ziel.isAlive() || ziel.isRemoved()) {
            suchPause = 5;
            ziel = jaeger ? tierWaehlen(spieler) : zielWaehlen(spieler);
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

    // ----------------------------------------------------------------- Jaeger

    private static final double JAGD_UMKREIS = 24;

    /** Passt das Tier zur Auswahl? */
    private boolean passt(LivingEntity e) {
        // Ueber den Registernamen - ab 26.x fuehrt EntityType diese Felder nicht mehr.
        String t = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return switch (ruestung) {
            case "Kuh" -> t.equals("cow");
            case "Schwein" -> t.equals("pig");
            case "Schaf" -> t.equals("sheep");
            case "Huhn" -> t.equals("chicken");
            case "Kaninchen" -> t.equals("rabbit");
            default -> t.equals("cow") || t.equals("pig") || t.equals("sheep") || t.equals("chicken")
                    || t.equals("rabbit");
        };
    }

    /** Das naechste erwachsene, unbenannte Tier - aber nur, wenn von seiner Sorte genug uebrig bleiben. */
    private LivingEntity tierWaehlen(ServerPlayer spieler) {
        AABB box = spieler.getBoundingBox().inflate(JAGD_UMKREIS);
        List<LivingEntity> tiere = welt.getEntitiesOfClass(LivingEntity.class, box, e ->
                e.isAlive() && !e.isBaby() && !e.hasCustomName() && passt(e));
        java.util.Map<net.minecraft.world.entity.EntityType<?>, Integer> anzahl = new java.util.HashMap<>();
        for (LivingEntity e : tiere) {
            anzahl.merge(e.getType(), 1, Integer::sum);
        }
        int uebrig = Math.max(0, werte.zahl());
        LivingEntity bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (LivingEntity e : tiere) {
            if (anzahl.get(e.getType()) <= uebrig) {
                continue;
            }
            double d = e.distanceToSqr(koerper);
            if (d < besterAbstand) {
                besterAbstand = d;
                bester = e;
            }
        }
        return bester;
    }

    /** Nach einer Jagd die fallengelassenen Items am Ort des Tieres einsammeln. */
    private void beuteEinsammeln() {
        if (beuteOrt == null || --beuteTicks < 0 || beuteTicks % 5 != 0) {
            return;
        }
        AABB box = new AABB(beuteOrt.getX(), beuteOrt.getY(), beuteOrt.getZ(),
                beuteOrt.getX() + 1, beuteOrt.getY() + 1, beuteOrt.getZ() + 1).inflate(3);
        for (net.minecraft.world.entity.item.ItemEntity item
                : welt.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, i -> i.isAlive())) {
            ItemStack rest = lager.addItem(item.getItem().copy());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
        }
        if (beuteTicks <= 0) {
            beuteOrt = null;
        }
    }

    private boolean lagerVoll() {
        for (int i = 0; i < lager.getContainerSize(); i++) {
            if (lager.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Lager voll: in die Sammelkiste (hin und zurueck teleportieren) oder dem Spieler geben. */
    private void lagerLeeren(ServerPlayer spieler) {
        AgentWelt.WeltOrt kiste = AgentWelt.kiste(besitzer);
        if (kiste == null || !AgentKiste.istKiste(kiste.welt(), kiste.pos())) {
            AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + ": Mein Beutel ist voll - hier, fang!");
            abliefern = true;
            return;
        }
        BlockPos zurueck = fuesse;
        ServerLevel zurueckWelt = welt;
        springen(kiste.welt(), Agent.sichererPlatzBei(kiste.welt(), kiste.pos()));
        int bewegt = AgentKiste.einlagern(welt, kiste.pos(), lager, x -> false);
        if (bewegt > 0) {
            geliefert += bewegt;
            welt.playSound(null, kiste.pos(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 1f);
        }
        springen(zurueckWelt, zurueck);
        if (lagerVoll()) {
            AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + ": Die Sammelkiste ist voll - hier, fang!");
            abliefern = true;
        }
    }

    /** Beute zuwerfen, ein Stapel alle drei Ticks - dann gehen. */
    private void abliefern(ServerPlayer spieler) {
        anschauen(spieler.getEyePosition());
        if (koerper.distanceToSqr(spieler) > 5 * 5) {
            if (bewegNach == null) {
                laufenZu(spieler.blockPosition(), 2);
            }
            return;
        }
        if (wurfPause-- > 0) {
            return;
        }
        wurfPause = 3;
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.isEmpty()) {
                continue;
            }
            lager.setItem(i, ItemStack.EMPTY);
            Vec3 von = koerper.getEyePosition().subtract(0, 0.3, 0);
            Vec3 zum = spieler.getEyePosition().subtract(von);
            Vec3 schwung = zum.normalize().scale(Math.min(0.45, 0.12 * zum.length())).add(0, 0.18, 0);
            net.minecraft.world.entity.item.ItemEntity wurf = new net.minecraft.world.entity.item.ItemEntity(
                    welt, von.x, von.y, von.z, stapel, schwung.x, schwung.y, schwung.z);
            wurf.setPickUpDelay(8);
            welt.addFreshEntity(wurf);
            AgentFassung.schwingen(koerper);
            geliefert += stapel.getCount();
            return;
        }
        abliefern = false;
        AgentWelt.melden(spieler, ChatFormatting.GREEN, titel() + " hat dir " + geliefert + " Items gebracht ("
                + kills + " Tiere erlegt).");
        effekt(ParticleTypes.POOF, 20);
        fertig = true;
    }

    private void springen(ServerLevel zielWelt, BlockPos platz) {
        effekt(ParticleTypes.PORTAL, 20);
        if (zielWelt == welt) {
            koerper.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, koerper.getYRot(), 0);
            fuesse = platz;
            pfad = null;
            bewegNach = null;
        } else {
            koerperBauen(zielWelt, platz);
        }
        effekt(ParticleTypes.PORTAL, 20);
    }

    // ------------------------------------------------------------ Kampf

    private void kaempfen(ServerPlayer spieler) {
        // Nicht zu weit vom Spieler weglocken lassen.
        double grenze = (jaeger ? JAGD_UMKREIS : UMKREIS) + 6;
        if (ziel.distanceToSqr(spieler) > grenze * grenze) {
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
                if (jaeger) {
                    beuteOrt = ziel.blockPosition();
                    beuteTicks = 40;
                }
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
        String text = titel() + (kills > 0 ? " · " + kills + (jaeger ? " erlegt" : " besiegt") : "")
                + (schuetzen ? " ❤" : "");
        koerper.setCustomName(Component.literal(text).withStyle(schuetzen ? ChatFormatting.RED : ChatFormatting.GOLD));
    }

    private void effekt(SimpleParticleType art, int anzahl) {
        if (koerper != null) {
            welt.sendParticles(art, koerper.getX(), koerper.getY() + 1, koerper.getZ(), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
