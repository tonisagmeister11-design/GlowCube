package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Keulen-Aura: greift Ziele in Reichweite mit der Keule an und loest dabei den
 * Schmetterschlag aus.
 *
 * <p>Es gibt in Meteor, BleachHack oder Meteor Rejects <em>kein</em> eigenes
 * Modul dieses Namens - Meteor macht die Keule ueber KillAura (Zielauswahl,
 * Waffenwechsel) zusammen mit Criticals (der Schmetterschlag). Dieses Modul
 * ist genau das, in einem Schalter zusammengefasst, aus zwei schon
 * uebertragenen Teilen:
 *
 * <ul>
 *   <li>Die Zielauswahl - Reichweite, Sichtlinie, welche Wesen zaehlen - ist
 *       dieselbe wie in der aus Meteor uebertragenen KillAura.</li>
 *   <li>Der Schmetterschlag ist der aus Meteors Criticals uebertragene Kniff:
 *       vor dem Schlag drei Positionsmeldungen, die dem Server einen Fall
 *       vorgaukeln (Hoehe 1,501 - die Schwelle, ab der Vanilla den
 *       Schmetterschlag wertet). Mehr Hoehe bedeutet mehr Schaden.</li>
 * </ul>
 *
 * <p>Es wird nur zugeschlagen, wenn wirklich eine Keule in der Hotbar liegt.
 * Auf Wunsch wird auf sie gewechselt und danach zurueckgetauscht.
 */
public final class MaceAura extends Module {
    private final NumberSetting range = register(new NumberSetting("Range",
            "Reichweite", 4.0, 1.0, 6.0, 0.1));
    private final NumberSetting zusatzHoehe = register(new NumberSetting("Zusatzhoehe",
            "Wie viel Fallhoehe zusaetzlich vorgegaukelt wird - mehr Schaden", 0, 0, 100, 1));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Spieler", true));
    private final BooleanSetting hostile = register(new BooleanSetting("Hostile", "Monster", true));
    private final BooleanSetting passive = register(new BooleanSetting("Passive", "Tiere", false));
    private final BooleanSetting autoSwitch = register(new BooleanSetting("AutoSwitch",
            "Auf die Keule wechseln", true));
    private final BooleanSetting swapBack = register(new BooleanSetting("SwapBack",
            "Danach zurueck auf das vorherige Feld", true));
    private final NumberSetting hitDelay = register(new NumberSetting("HitDelay",
            "Ticks zwischen zwei Schlaegen", 10, 0, 40, 1));

    private int vorherigesFeld = -1;
    private boolean gewechselt;
    private int wartet;

    public MaceAura() {
        super("MaceAura", "Schlaegt Ziele mit der Keule und loest den Schmetterschlag aus",
                Category.COMBAT);
    }

    @Override
    public void onDisable() {
        zuruecktauschen();
    }

    @Override
    public void onTick() {
        if (!player().isAlive()) {
            zuruecktauschen();
            return;
        }

        int keulenFeld = keuleInHotbar();
        if (keulenFeld < 0) {
            zuruecktauschen();
            return;
        }

        LivingEntity ziel = zielSuchen();
        if (ziel == null) {
            zuruecktauschen();
            return;
        }

        if (wartet > 0) {
            wartet--;
            return;
        }

        // Auf die Keule wechseln.
        if (autoSwitch.get() && player().getInventory().getSelectedSlot() != keulenFeld) {
            if (!gewechselt) {
                vorherigesFeld = player().getInventory().getSelectedSlot();
                gewechselt = true;
            }
            player().getInventory().setSelectedSlot(keulenFeld);
        }

        // Der Schmetterschlag: drei Positionsmeldungen, die einen Fall
        // vorgaukeln - genau der Kniff aus Meteors Criticals.
        if (player().getMainHandItem().getItem() instanceof MaceItem && !player().isFallFlying()) {
            fallVorgaukeln(0.0);
            fallVorgaukeln(1.501 + zusatzHoehe.get());
            fallVorgaukeln(0.0);
        }

        mc.gameMode.attack(player(), ziel);
        net.glowcube.client.render.Netz.schwingen(InteractionHand.MAIN_HAND);
        // Angriffszaehler zuruecksetzen wie beim echten Linksklick - sonst
        // schlaegt die Aura jeden Tick und der Server verwirft die Schlaege.
        player().resetAttackStrengthTicker();
        wartet = hitDelay.getInt();
    }

    /** Eine Positionsmeldung um {@code hoehe} hoeher, ohne Bodenkontakt. */
    private void fallVorgaukeln(double hoehe) {
        player().connection.send(new ServerboundMovePlayerPacket.Pos(
                player().getX(), player().getY() + hoehe, player().getZ(), false, false));
    }

    private int keuleInHotbar() {
        for (int feld = 0; feld < 9; feld++) {
            if (player().getInventory().getItem(feld).getItem() instanceof MaceItem) {
                return feld;
            }
        }
        return -1;
    }

    private LivingEntity zielSuchen() {
        AABB feld = player().getBoundingBox().inflate(range.get());
        List<Entity> gefunden = level().getEntities(player(), feld,
                e -> e instanceof LivingEntity lebend && taugt(lebend));
        gefunden.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player())));
        return gefunden.isEmpty() ? null : (LivingEntity) gefunden.get(0);
    }

    private boolean taugt(LivingEntity wesen) {
        if (wesen == player() || !wesen.isAlive() || wesen.isInvulnerable()) {
            return false;
        }
        boolean erlaubt;
        if (wesen instanceof Player) {
            erlaubt = players.get();
        } else if (wesen instanceof Monster) {
            erlaubt = hostile.get();
        } else if (wesen instanceof Animal) {
            erlaubt = passive.get();
        } else {
            erlaubt = false;
        }
        if (!erlaubt) {
            return false;
        }
        if (wesen.distanceTo(player()) > range.get()) {
            return false;
        }
        // Nur feste Bloecke zaehlen als Sichtblocker - zu mehreren Punkten
        // des Ziels gestrahlt, damit Gras oder der eigene Koerper nicht stoert.
        AABB box = wesen.getBoundingBox();
        Vec3 auge = player().getEyePosition();
        Vec3[] punkte = {wesen.getEyePosition(), box.getCenter(),
                new Vec3(box.getCenter().x, box.minY + 0.1, box.getCenter().z)};
        for (Vec3 punkt : punkte) {
            if (level().clip(new net.minecraft.world.level.ClipContext(auge, punkt,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player()))
                    .getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private void zuruecktauschen() {
        if (gewechselt && swapBack.get() && vorherigesFeld >= 0 && inGame()) {
            player().getInventory().setSelectedSlot(vorherigesFeld);
        }
        gewechselt = false;
        vorherigesFeld = -1;
    }
}
