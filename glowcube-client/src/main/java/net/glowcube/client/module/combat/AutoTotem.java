package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.PlayerUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code AutoTotem}.
 *
 * <p>Zwei Betriebsarten wie dort: <b>Streng</b> haelt immer ein Totem, was
 * die Nebenhand dauerhaft blockiert; <b>Klug</b> greift erst zu, wenn es eng
 * wird. "Eng" ist dabei keine Schaetzung, sondern eine Rechnung: Leben plus
 * Absorption minus dem, was der naechste Kristall oder der naechste Sturz
 * wirklich abziehen wuerde (siehe {@link net.glowcube.client.util.DamageUtils}).
 *
 * <p>Die Elytra-Ausnahme aus dem Original ist mit drin - beim Fliegen ist ein
 * Aufprall schneller da, als eine Rechnung reagieren kann.
 *
 * <p>Und das Feinste am Original: wenn der Server meldet, dass ein Totem
 * gerade gezogen hat, wird der Warteschritt auf null gesetzt. So liegt das
 * naechste Totem im selben Augenblick wieder in der Hand statt erst nach
 * der eingestellten Pause.
 */
public final class AutoTotem extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Klug greift bei Gefahr zu, Streng haelt immer eines",
            "Klug", "Klug", "Streng"));
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Griffen in den Rucksack", 0, 0, 20, 1));
    private final NumberSetting leben = register(new NumberSetting("Leben",
            "Ab wie wenig verbleibendem Leben zugegriffen wird", 10, 0, 36, 1));
    private final BooleanSetting elytra = register(new BooleanSetting("Elytra",
            "Beim Elytra-Flug immer ein Totem halten", true));
    private final BooleanSetting sturz = register(new BooleanSetting("Sturz",
            "Auch drohenden Sturzschaden einrechnen", true));
    private final BooleanSetting explosion = register(new BooleanSetting("Explosion",
            "Auch drohenden Explosionsschaden einrechnen", true));

    private boolean haelt;
    private int totems;
    private int takte;

    public AutoTotem() {
        super("AutoTotem", "Haelt ein Totem in der Zweithand", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        haelt = false;
        takte = 0;
    }

    @Override
    public void onTick() {
        FindItemResult fund = InvUtils.finde(Items.TOTEM_OF_UNDYING);
        totems = fund.count();

        if (totems <= 0) {
            haelt = false;
            return;
        }
        if (takte < pause.getInt()) {
            takte++;
            return;
        }

        float uebrig = PlayerUtils.lebenGesamt(player())
                - PlayerUtils.moeglicherSchaden(explosion.get(), sturz.get());
        boolean knapp = uebrig <= leben.get();
        boolean imFlug = elytra.get()
                && player().getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                && player().isFallFlying();

        haelt = modus.is("Streng") || (modus.is("Klug") && (knapp || imFlug));

        if (haelt && player().getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
            InvUtils.verschieben().von(fund.slot()).nachNebenhand();
        }
        takte = 0;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        // Der Server meldet "Totem hat gezogen" als Wesen-Ereignis. Dann
        // sofort nachlegen, nicht erst nach der eingestellten Pause.
        if (packet instanceof ClientboundEntityEventPacket ereignis
                && ereignis.getEventId() == EntityEvent.PROTECTED_FROM_DEATH
                && inGame()
                && player().equals(ereignis.getEntity(level()))) {
            takte = pause.getInt();
        }
        return false;
    }

    /** Ob die Nebenhand gerade fuer das Totem belegt gehalten wird. */
    public boolean belegt() {
        return isEnabled() && haelt;
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(totems);
    }
}
