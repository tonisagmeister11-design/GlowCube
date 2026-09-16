package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Setzt Bloecke unter den Spieler, damit man ueber Luecken laufen kann.
 *
 * Minecraft nimmt einen Block nur an, wenn er gegen eine vorhandene Flaeche
 * gesetzt wird - frei in die Luft geht nicht. Deshalb wird zuerst ein
 * Nachbar gesucht, der fest ist, und dann dessen zugewandte Seite angeklickt.
 * Nachempfunden Scaffold aus BleachHack (GPL-3.0).
 */
public final class Scaffold extends Module {
    private final NumberSetting delay = register(new NumberSetting("Delay",
            "Ticks zwischen zwei Bloecken", 1, 0, 10, 1));
    private final BooleanSetting nurBeimFallen = register(new BooleanSetting("OnlyWhenFalling",
            "Nur setzen, wenn unter einem nichts ist", true));
    private final BooleanSetting swing = register(new BooleanSetting("Swing",
            "Mit der Hand ausholen", true));

    /** Die sechs Nachbarn, gegen die man setzen kann. */
    private static final Direction[] SEITEN = Direction.values();

    private int ticks;
    private int gesetzt;

    public Scaffold() {
        super("Scaffold", "Baut den Boden unter dir mit", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        gesetzt = 0;
    }

    @Override
    public void onTick() {
        if (ticks++ < delay.getInt()) {
            return;
        }
        ticks = 0;

        BlockPos ziel = player().blockPosition().below();
        if (!level().getBlockState(ziel).isAir()) {
            return;
        }
        if (nurBeimFallen.get() && player().onGround()) {
            return;
        }

        int platz = blockImGuertel();
        if (platz < 0) {
            return;
        }
        int vorher = player().getInventory().getSelectedSlot();
        player().getInventory().setSelectedSlot(platz);

        if (setzen(ziel)) {
            gesetzt++;
            if (swing.get()) {
                player().swing(InteractionHand.MAIN_HAND);
            }
        }
        player().getInventory().setSelectedSlot(vorher);
    }

    /**
     * Gegen einen festen Nachbarn setzen. Die angeklickte Seite ist die, die
     * dem Zielplatz zugewandt ist - sonst landet der Block woanders.
     */
    private boolean setzen(BlockPos ziel) {
        for (Direction seite : SEITEN) {
            BlockPos nachbar = ziel.relative(seite);
            if (level().getBlockState(nachbar).isAir()) {
                continue;
            }
            Direction flaeche = seite.getOpposite();
            Vec3 mitte = Vec3.atCenterOf(nachbar).add(
                    flaeche.getStepX() * 0.5, flaeche.getStepY() * 0.5, flaeche.getStepZ() * 0.5);
            mc.gameMode.useItemOn(player(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(mitte, flaeche, nachbar, false));
            return true;
        }
        return false;
    }

    /** Platz in der Hotbar mit einem setzbaren Block, oder -1. */
    private int blockImGuertel() {
        ItemStack inHand = player().getMainHandItem();
        if (inHand.getItem() instanceof BlockItem) {
            return player().getInventory().getSelectedSlot();
        }
        for (int platz = 0; platz < 9; platz++) {
            if (player().getInventory().getItem(platz).getItem() instanceof BlockItem) {
                return platz;
            }
        }
        return -1;
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(gesetzt);
    }
}
