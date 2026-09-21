package net.glowcube.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.mixin.ServerboundInteractPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Fassung fuer <b>1.21.x</b>: gebuendelte versionsabhaengige Kleinigkeiten aus
 * Netzwerk, ChunkPos, Kamera und Chat, die sich ab 26.x geaendert haben. So
 * bleiben die Module fassungsneutral - sie rufen nur {@code Netz.*}.
 */
public final class Netz {
    private Netz() {
    }

    public static void schwungSenden(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundSwingPacket(hand));
        }
    }

    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket instanceof ServerboundSwingPacket;
    }

    public static void schwingen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.swing(hand);
        }
    }

    public static int chunkX(ChunkPos pos) {
        return pos.x;
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z;
    }

    /** ChunkPos -> long, mit Minecrafts eigener Bitformel (fassungsstabil). */
    public static long chunkAlsLong(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static long chunkAlsLong(ChunkPos pos) {
        return chunkAlsLong(chunkX(pos), chunkZ(pos));
    }

    public static void chunksNeuZeichnen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    public static int interaktZielId(ServerboundInteractPacket paket) {
        return ((ServerboundInteractPacketAccessor) paket).glowcube$zielNummer();
    }

    public static Vec3 kameraPosition() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }

    public static void nachricht(Component text, boolean ueberlage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(text, ueberlage);
        }
    }

    public static Screen bildschirm() {
        return Minecraft.getInstance().screen;
    }

    public static void bildschirmSetzen(Screen bildschirm) {
        Minecraft.getInstance().setScreen(bildschirm);
    }

    public static boolean tasteUnten(int taste) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), taste);
    }
}
