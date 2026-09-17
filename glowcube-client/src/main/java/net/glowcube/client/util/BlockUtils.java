package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code BlockUtils}.
 *
 * <p>Setzen und Abbauen an einer Stelle. Der Grund, warum das nicht in den
 * Modulen steht: die Grenzfaelle sind zahlreich und ueberall dieselben.
 * Welche Seite eines Blocks man anklicken darf, wie man den Klickpunkt
 * ausrechnet, dass man dabei nicht schleichen darf (sonst oeffnet man
 * Kisten statt zu bauen), und dass ein Werkbank-artiger Nachbar kein
 * gueltiger Halt ist - all das steckt hier drin, weil es dort drin steckt.
 */
public final class BlockUtils {
    /** Ob im letzten Tick abgebaut wurde - haelt den Abbau am Laufen. */
    private static boolean baeutAb;
    private static boolean baeutAbDiesenTick;

    private BlockUtils() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ---------------------------------------------------------------- Setzen

    public static boolean setzen(BlockPos pos, FindItemResult fund, boolean drehen,
                                 int prioritaet, boolean schwingen, boolean wesenPruefen) {
        if (fund.isOffhand()) {
            return setzen(pos, InteractionHand.OFF_HAND,
                    mc().player.getInventory().getSelectedSlot(), drehen, prioritaet,
                    schwingen, wesenPruefen);
        }
        if (fund.isHotbar()) {
            return setzen(pos, InteractionHand.MAIN_HAND, fund.slot(), drehen, prioritaet,
                    schwingen, wesenPruefen);
        }
        return false;
    }

    public static boolean setzen(BlockPos pos, InteractionHand hand, int platz, boolean drehen,
                                 int prioritaet, boolean schwingen, boolean wesenPruefen) {
        if (platz < 0 || platz > 8 || mc().player == null || mc().level == null) {
            return false;
        }

        Block zuSetzen = Blocks.OBSIDIAN;
        ItemStack stack = hand == InteractionHand.MAIN_HAND
                ? mc().player.getInventory().getItem(platz)
                : mc().player.getInventory().getItem(SlotUtils.OFFHAND);
        if (stack.getItem() instanceof BlockItem blockItem) {
            zuSetzen = blockItem.getBlock();
        }
        if (!kannSetzen(pos, wesenPruefen, zuSetzen)) {
            return false;
        }

        Vec3 klickpunkt = Vec3.atCenterOf(pos);
        BlockPos nachbar;
        Direction seite = setzSeite(pos);

        if (seite == null) {
            // Kein fester Nachbar: das geht nur mit AirPlace, und dann ist
            // der Klick auf die eigene Stelle nach oben die richtige Luege.
            seite = Direction.UP;
            nachbar = pos;
        } else {
            nachbar = pos.relative(seite);
            klickpunkt = klickpunkt.add(seite.getStepX() * 0.5, seite.getStepY() * 0.5,
                    seite.getStepZ() * 0.5);
        }

        BlockHitResult treffer = new BlockHitResult(klickpunkt, seite.getOpposite(), nachbar, false);
        final int gewaehlterPlatz = platz;
        final InteractionHand gewaehlteHand = hand;
        Runnable tun = () -> {
            InvUtils.tausche(gewaehlterPlatz, true);
            benutzen(treffer, gewaehlteHand, schwingen);
            InvUtils.tauscheZurueck();
        };

        if (drehen) {
            Rotations.rotate(Rotations.getYaw(klickpunkt), Rotations.getPitch(klickpunkt),
                    prioritaet, tun);
        } else {
            tun.run();
        }
        return true;
    }

    /**
     * Klicken ohne zu schleichen. Wer geduckt auf eine Kiste klickt, oeffnet
     * sie; wer aufrecht klickt, setzt davor. Das Schleichen wird kurz
     * abgeschaltet und danach wiederhergestellt.
     */
    public static void benutzen(BlockHitResult treffer, InteractionHand hand, boolean schwingen) {
        boolean warGeduckt = mc().player.isShiftKeyDown();
        mc().player.setShiftKeyDown(false);

        InteractionResult ergebnis = mc().gameMode.useItemOn(mc().player, hand, treffer);

        if (ergebnis.consumesAction()) {
            if (schwingen) {
                mc().player.swing(hand);
            } else {
                mc().player.connection.send(new ServerboundSwingPacket(hand));
            }
        }

        mc().player.setShiftKeyDown(warGeduckt);
    }

    public static boolean kannSetzen(BlockPos pos, boolean wesenPruefen, Block block) {
        if (pos == null || mc().level == null) {
            return false;
        }
        if (!Level.isInSpawnableBounds(pos)) {
            return false;
        }
        if (!mc().level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        return !wesenPruefen
                || mc().level.isUnobstructed(block.defaultBlockState(), pos, CollisionContext.empty());
    }

    public static boolean kannSetzen(BlockPos pos) {
        return kannSetzen(pos, true, Blocks.OBSIDIAN);
    }

    /**
     * Die Seite, an die sich der neue Block anlehnen kann. Gewaehlt wird die,
     * die am ehesten in Blickrichtung liegt - so wie es das Original macht,
     * damit der gemeldete Blick und der Klick zusammenpassen.
     */
    public static Direction setzSeite(BlockPos pos) {
        if (mc().player == null || mc().level == null) {
            return null;
        }
        Vec3 blick = Vec3.atCenterOf(pos).subtract(mc().player.getEyePosition());
        double beste = -Double.MAX_VALUE;
        Direction besteSeite = null;

        for (Direction seite : Direction.values()) {
            BlockState nachbar = mc().level.getBlockState(pos.relative(seite));
            if (nachbar.isAir() || istAnklickbar(nachbar.getBlock())) {
                continue;
            }
            if (!nachbar.getFluidState().isEmpty()) {
                continue;
            }
            double wert = seite.getAxis().choose(blick.x(), blick.y(), blick.z())
                    * seite.getAxisDirection().getStep();
            if (wert > beste) {
                beste = wert;
                besteSeite = seite;
            }
        }
        return besteSeite;
    }

    /** Die naechstgelegene brauchbare Seite - fuer die Reichweitenpruefung. */
    public static Direction naechsteSetzSeite(BlockPos pos) {
        if (mc().player == null || mc().level == null) {
            return null;
        }
        Vec3 auge = mc().player.getEyePosition();
        Direction naechste = null;
        double kuerzeste = Double.MAX_VALUE;

        for (Direction seite : Direction.values()) {
            BlockPos nachbar = pos.relative(seite);
            BlockState zustand = mc().level.getBlockState(nachbar);
            if (zustand.isAir() || istAnklickbar(zustand.getBlock())) {
                continue;
            }
            if (!zustand.getFluidState().isEmpty()) {
                continue;
            }
            double abstand = auge.distanceToSqr(nachbar.getX(), nachbar.getY(), nachbar.getZ());
            if (abstand < kuerzeste) {
                kuerzeste = abstand;
                naechste = seite;
            }
        }
        return naechste;
    }

    // -------------------------------------------------------------- Abbauen

    /** Zu Beginn jedes Ticks aufrufen. */
    public static void tickBeginn() {
        baeutAbDiesenTick = false;
    }

    /** Am Ende jedes Ticks aufrufen - beendet einen nicht fortgesetzten Abbau. */
    public static void tickEnde() {
        if (!baeutAbDiesenTick && baeutAb) {
            baeutAb = false;
            if (mc().gameMode != null) {
                mc().gameMode.stopDestroyBlock();
            }
        }
    }

    public static boolean abbauen(BlockPos pos, boolean schwingen) {
        if (mc().level == null || mc().gameMode == null) {
            return false;
        }
        if (!kannAbbauen(pos, mc().level.getBlockState(pos))) {
            return false;
        }

        // Eine eigene Kopie: das Spiel legt die uebergebene Position in ein
        // Feld, und eine MutableBlockPos wuerde sich danach unter der Hand
        // weiterbewegen.
        BlockPos fest = pos.immutable();
        Direction seite = naechsteSetzSeite(fest);
        if (seite == null) {
            seite = Direction.UP;
        }

        if (mc().gameMode.isDestroying()) {
            mc().gameMode.continueDestroyBlock(fest, seite);
        } else {
            mc().gameMode.startDestroyBlock(fest, seite);
        }

        if (schwingen) {
            mc().player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc().player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        baeutAb = true;
        baeutAbDiesenTick = true;
        return true;
    }

    public static boolean kannAbbauen(BlockPos pos, BlockState zustand) {
        if (mc().player == null || mc().level == null) {
            return false;
        }
        if (!mc().player.isCreative() && zustand.getDestroySpeed(mc().level, pos) < 0.0f) {
            return false;
        }
        return zustand.getShape(mc().level, pos) != Shapes.empty();
    }

    public static boolean kannAbbauen(BlockPos pos) {
        return mc().level != null && kannAbbauen(pos, mc().level.getBlockState(pos));
    }

    // --------------------------------------------------------------- Sonstiges

    /**
     * Bloecke, die auf einen Rechtsklick etwas anderes tun als sich bebauen
     * zu lassen. Die Liste ist die des Originals.
     */
    public static boolean istAnklickbar(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof BaseEntityBlock
                || block instanceof BedBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapDoorBlock;
    }
}
