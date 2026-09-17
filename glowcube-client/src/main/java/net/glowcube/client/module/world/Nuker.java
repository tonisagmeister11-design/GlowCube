package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.mixin.MultiPlayerGameModeAccessor;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code Nuker}.
 *
 * <p>Das Vorbild ist deutlich reicher als ein "alles im Umkreis abbauen":
 *
 * <ul>
 *   <li><b>Form.</b> Kugel (echter Abstand), Quader (sechs Richtungen
 *       einzeln einstellbar, relativ zur Blickrichtung) und Wuerfel
 *       (Schachbrettabstand, damit die Kanten wirklich gerade sind).</li>
 *   <li><b>Betriebsart.</b> Alles, Einebnen (nichts unter der eigenen
 *       Fusshoehe) und Zerschlagen (nur, was sofort bricht).</li>
 *   <li><b>Reihenfolge.</b> Naechstes zuerst, fernstes zuerst, von oben
 *       nach unten, oder gar nicht sortiert. Das entscheidet, ob man sich
 *       einen Tunnel graebt oder eine Grube.</li>
 *   <li><b>Sichtpruefung.</b> Ein Strahl zum Block; trifft er etwas
 *       anderes, gilt die kleinere Reichweite durch Waende.</li>
 * </ul>
 *
 * <p>Auch die Kleinigkeit mit dem Wartetakt stammt von dort: die Pause gilt
 * erst ab dem zweiten Block, und wenn laenger gar nichts gefunden wurde,
 * faellt sie fuer den naechsten Fund weg. Sonst ruckelt der Abbau bei jedem
 * Schritt.
 *
 * <p><b>Nicht uebernommen:</b> Meteors Einfaerbung der Bloecke und die
 * Taste zum Anlernen der Liste im Spiel.
 */
public final class Nuker extends Module {
    private final ModeSetting form = register(new ModeSetting("Form",
            "Kugel, Quader oder Wuerfel", "Kugel", "Kugel", "Quader", "Wuerfel"));
    private final ModeSetting betriebsart = register(new ModeSetting("Betriebsart",
            "Alles, nur nach unten einebnen, oder nur sofort brechende Bloecke",
            "Einebnen", "Alles", "Einebnen", "Zerschlagen"));
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Radius bei Kugel und Wuerfel", 4.0, 1.0, 6.0, 0.5));
    private final NumberSetting nachOben = register(new NumberSetting("Nach oben",
            "Quader: Bloecke ueber dir", 1, 0, 6, 1));
    private final NumberSetting nachUnten = register(new NumberSetting("Nach unten",
            "Quader: Bloecke unter dir", 1, 0, 6, 1));
    private final NumberSetting nachLinks = register(new NumberSetting("Nach links",
            "Quader: Bloecke links", 1, 0, 6, 1));
    private final NumberSetting nachRechts = register(new NumberSetting("Nach rechts",
            "Quader: Bloecke rechts", 1, 0, 6, 1));
    private final NumberSetting nachVorn = register(new NumberSetting("Nach vorn",
            "Quader: Bloecke vor dir", 1, 0, 6, 1));
    private final NumberSetting nachHinten = register(new NumberSetting("Nach hinten",
            "Quader: Bloecke hinter dir", 1, 0, 6, 1));
    private final NumberSetting durchWaende = register(new NumberSetting("Durch Waende",
            "Reichweite fuer Bloecke, die man nicht sieht", 4.5, 0.0, 6.0, 0.5));
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Bloecken", 0, 0, 20, 1));
    private final NumberSetting proTick = register(new NumberSetting("Bloecke je Tick",
            "Wie viele Bloecke hoechstens gleichzeitig", 1, 1, 64, 1));
    private final ModeSetting reihenfolge = register(new ModeSetting("Reihenfolge",
            "Welcher Block zuerst drankommt",
            "Naechster", "Naechster", "Fernster", "Von oben", "Ohne"));
    private final BooleanSetting paketAbbau = register(new BooleanSetting("Paketabbau",
            "Abbau ueber Pakete statt ueber die Spielmechanik - schneller, faellt aber auf", false));
    private final BooleanSetting nurPassendesWerkzeug = register(new BooleanSetting("Nur passendes Werkzeug",
            "Nichts anfassen, wofuer das Werkzeug in der Hand nichts taugt", false));
    private final BooleanSetting drehen = register(new BooleanSetting("Drehen",
            "Dem Server die passende Blickrichtung melden", true));
    private final BooleanSetting schwingen = register(new BooleanSetting("Schwingen",
            "Die Hand sichtbar bewegen", true));
    private final ModeSetting listenArt = register(new ModeSetting("Listenart",
            "Schwarz sperrt die Liste, Weiss erlaubt nur sie",
            "Schwarzliste", "Schwarzliste", "Weissliste"));
    private final BlockListSetting liste = register(new BlockListSetting("Bloecke",
            "Die Liste zur eingestellten Listenart", "bedrock"));

    private final List<BlockPos> gefunden = new ArrayList<>();
    private final BlockPos.MutableBlockPos letzter = new BlockPos.MutableBlockPos();
    private boolean ersterBlock;
    private int wartetakt;
    private int leerlauf;

    public Nuker() {
        super("Nuker", "Baut alles im Umkreis ab", Category.WORLD);
    }

    @Override
    public void onEnable() {
        ersterBlock = true;
        wartetakt = 0;
        leerlauf = 0;
        gefunden.clear();
    }

    @Override
    public void onTick() {
        if (wartetakt > 0) {
            wartetakt--;
            return;
        }

        sammeln();
        sortieren();

        if (gefunden.isEmpty()) {
            // Laenger nichts gefunden: die Pause fuer den naechsten Fund
            // fallenlassen, sonst ruckelt jeder Schritt.
            if (leerlauf++ >= pause.getInt()) {
                ersterBlock = true;
            }
            return;
        }
        leerlauf = 0;

        if (!ersterBlock && !letzter.equals(gefunden.get(0))) {
            wartetakt = pause.getInt();
            letzter.set(gefunden.get(0));
            if (wartetakt > 0) {
                return;
            }
        }

        int gezaehlt = 0;
        for (BlockPos pos : gefunden) {
            if (gezaehlt >= proTick.getInt()) {
                break;
            }
            boolean sofort = sofortAbbaubar(pos);

            if (drehen.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 20,
                        () -> abbauen(pos));
            } else {
                abbauen(pos);
            }
            letzter.set(pos);
            gezaehlt++;

            // Was nicht sofort bricht, braucht den ganzen Tick fuer sich -
            // ausser im Paketabbau, der alles auf einmal anstoesst.
            if (!sofort && !paketAbbau.get()) {
                break;
            }
        }
        ersterBlock = false;
        gefunden.clear();
    }

    // -------------------------------------------------------------- Sammeln

    private void sammeln() {
        gefunden.clear();
        double pX = player().getX();
        double pY = player().getY();
        double pZ = player().getZ();
        BlockPos mitte = player().blockPosition();

        int r = (int) Math.ceil(reichweite.get());
        AABB quader = quaderGrenzen();
        int weite = form.is("Quader")
                ? 1 + Math.max(Math.max(nachHinten.getInt(), nachRechts.getInt()),
                        Math.max(nachVorn.getInt(), nachLinks.getInt()))
                : r;
        int hoehe = form.is("Quader")
                ? 1 + Math.max(nachOben.getInt(), nachUnten.getInt())
                : r;

        double reichweiteQuadrat = reichweite.get() * reichweite.get();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int dx = -weite; dx <= weite; dx++) {
            for (int dy = -hoehe; dy <= hoehe; dy++) {
                for (int dz = -weite; dz <= weite; dz++) {
                    probe.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    Vec3 zentrum = Vec3.atCenterOf(probe);

                    switch (form.get()) {
                        case "Kugel" -> {
                            if (zentrum.distanceToSqr(pX, pY, pZ) > reichweiteQuadrat) {
                                continue;
                            }
                        }
                        case "Wuerfel" -> {
                            if (schachbrett(mitte, probe) >= reichweite.get()) {
                                continue;
                            }
                        }
                        default -> {
                            if (quader == null || !quader.contains(zentrum)) {
                                continue;
                            }
                        }
                    }

                    if (betriebsart.is("Einebnen") && probe.getY() + 0.5 < pY) {
                        continue;
                    }
                    BlockState zustand = level().getBlockState(probe);
                    if (zustand.isAir()) {
                        continue;
                    }
                    if (betriebsart.is("Zerschlagen")
                            && zustand.getDestroySpeed(level(), probe) != 0.0f) {
                        continue;
                    }
                    if (nurPassendesWerkzeug.get()
                            && !player().getMainHandItem().isCorrectToolForDrops(zustand)) {
                        continue;
                    }
                    if (!BlockUtils.kannAbbauen(probe, zustand)) {
                        continue;
                    }
                    if (ausserReichweite(probe)) {
                        continue;
                    }

                    String id = BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString();
                    boolean drauf = liste.contains(id);
                    if (listenArt.is("Weissliste") ? !drauf : drauf) {
                        continue;
                    }

                    gefunden.add(probe.immutable());
                }
            }
        }
    }

    /**
     * Der Quader liegt relativ zur Blickrichtung - "vorn" ist da, wo man
     * hinsieht, nicht Norden. Die Zuordnung der sechs Werte zu den Achsen
     * stammt aus dem Original.
     */
    private AABB quaderGrenzen() {
        if (!form.is("Quader")) {
            return null;
        }
        double pX = player().getX();
        double pY = player().getY();
        double pZ = player().getZ();
        Direction blick = player().getDirection();

        double x1;
        double z1;
        double x2;
        double z2;
        switch (blick) {
            case SOUTH -> {
                x1 = pX + 1 - (nachRechts.get() + 1);
                z1 = pZ + 1 - (nachHinten.get() + 1);
                x2 = pX + 1 + nachLinks.get();
                z2 = pZ + 1 + nachVorn.get();
            }
            case WEST -> {
                x1 = pX - nachVorn.get();
                z1 = pZ - nachRechts.get();
                x2 = pX + nachHinten.get() + 1;
                z2 = pZ + nachLinks.get() + 1;
            }
            case NORTH -> {
                x1 = pX + 1 - (nachLinks.get() + 1);
                z1 = pZ + 1 - (nachVorn.get() + 1);
                x2 = pX + 1 + nachRechts.get();
                z2 = pZ + 1 + nachHinten.get();
            }
            default -> {
                x1 = pX + 1 - (nachHinten.get() + 1);
                z1 = pZ - nachLinks.get();
                x2 = pX + 1 + nachVorn.get();
                z2 = pZ + nachRechts.get() + 1;
            }
        }
        double y1 = Math.ceil(pY) - nachUnten.get();
        double y2 = Math.ceil(pY + nachOben.get() + 1);
        if (betriebsart.is("Einebnen")) {
            y1 = Math.floor(pY + 0.5);
        }
        return new AABB(x1, y1, z1, x2, y2, z2);
    }

    private static int schachbrett(BlockPos a, BlockPos b) {
        return Math.max(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
                Math.abs(a.getZ() - b.getZ()));
    }

    /**
     * Ein Strahl zum Block. Trifft er etwas anderes, steht der Block hinter
     * einer Wand - dann gilt die kleinere Reichweite.
     */
    private boolean ausserReichweite(BlockPos pos) {
        Vec3 zentrum = Vec3.atCenterOf(pos);
        BlockHitResult treffer = level().clip(new ClipContext(
                player().getEyePosition(), zentrum,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player()));
        if (treffer.getType() == HitResult.Type.BLOCK && treffer.getBlockPos().equals(pos)) {
            return false;
        }
        double erlaubt = durchWaende.get();
        return player().getEyePosition().distanceToSqr(zentrum) > erlaubt * erlaubt;
    }

    private void sortieren() {
        double pX = player().getX();
        double pY = player().getY();
        double pZ = player().getZ();
        switch (reihenfolge.get()) {
            case "Von oben" -> gefunden.sort(Comparator.comparingDouble(p -> -p.getY()));
            case "Ohne" -> {
            }
            default -> {
                int richtung = reihenfolge.is("Naechster") ? 1 : -1;
                gefunden.sort(Comparator.comparingDouble(p ->
                        Vec3.atCenterOf(p).distanceToSqr(pX, pY, pZ) * richtung));
            }
        }
    }

    // -------------------------------------------------------------- Abbauen

    private boolean sofortAbbaubar(BlockPos pos) {
        if (player().isCreative()) {
            return true;
        }
        BlockState zustand = level().getBlockState(pos);
        float haerte = zustand.getDestroySpeed(level(), pos);
        if (haerte < 0.0f) {
            return false;
        }
        if (haerte == 0.0f) {
            return true;
        }
        int teiler = player().hasCorrectToolForDrops(zustand) ? 30 : 100;
        return player().getDestroySpeed(zustand) / haerte / teiler >= 1.0f;
    }

    private void abbauen(BlockPos pos) {
        if (paketAbbau.get()) {
            Direction seite = BlockUtils.naechsteSetzSeite(pos);
            if (seite == null) {
                seite = Direction.UP;
            }
            final Direction gewaehlt = seite;
            ((MultiPlayerGameModeAccessor) mc.gameMode).glowcube$vorhersagen(mc.level, folge -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, gewaehlt, folge));
            if (schwingen.get()) {
                player().swing(InteractionHand.MAIN_HAND);
            } else {
                player().connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            ((MultiPlayerGameModeAccessor) mc.gameMode).glowcube$vorhersagen(mc.level, folge -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, gewaehlt, folge));
        } else {
            BlockUtils.abbauen(pos, schwingen.get());
        }
    }

    @Override
    public String hudSuffix() {
        return betriebsart.get();
    }
}
