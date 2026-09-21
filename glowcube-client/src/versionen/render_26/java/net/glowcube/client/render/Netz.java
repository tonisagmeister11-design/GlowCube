package net.glowcube.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Fassung fuer <b>26.3 und neuer</b>: dieselben Kleinigkeiten mit den neuen
 * 26.x-Namen. Arm-Animation und Chunk-Neuzeichnen folgen mit der uebrigen
 * Render-Anbindung; der Angriff laeuft ohnehin ueber {@code gameMode.attack}.
 */
public final class Netz {
    private Netz() {
    }

    public static void schwungSenden(InteractionHand hand) {
        // Auf 26.3 vorerst ohne eigenes Schwung-Paket.
    }

    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket.getClass().getSimpleName().contains("Swing");
    }

    public static void schwingen(InteractionHand hand) {
        // Arm-Animation auf 26.3 vorerst aus (neuer SwingAnimation-Parameter).
    }

    public static int chunkX(ChunkPos pos) {
        return pos.x();
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z();
    }

    public static long chunkAlsLong(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static long chunkAlsLong(ChunkPos pos) {
        return chunkAlsLong(chunkX(pos), chunkZ(pos));
    }

    public static void chunksNeuZeichnen() {
        // Chunk-Neuzeichnen auf 26.3 noch offen.
    }

    public static int interaktZielId(ServerboundInteractPacket paket) {
        return paket.entityId();
    }

    public static Vec3 kameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    public static void nachricht(Component text, boolean ueberlage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (ueberlage) {
            mc.player.sendOverlayMessage(text);
        } else {
            mc.player.sendSystemMessage(text);
        }
    }

    // Das Feld screen ist ab 26.x privat und hat keinen Getter; ueber
    // Spiegelung kommt man neutral heran.
    private static java.lang.reflect.Field screenFeld;

    public static Screen bildschirm() {
        try {
            if (screenFeld == null) {
                screenFeld = Minecraft.class.getDeclaredField("screen");
                screenFeld.setAccessible(true);
            }
            return (Screen) screenFeld.get(Minecraft.getInstance());
        } catch (ReflectiveOperationException fehler) {
            return null;
        }
    }

    public static void bildschirmSetzen(Screen bildschirm) {
        Minecraft.getInstance().setScreenAndShow(bildschirm);
    }

    public static boolean tasteUnten(int taste) {
        // Ab 26.x nimmt isKeyDown nur noch den Tastencode (kein Fenster).
        return InputConstants.isKeyDown(taste);
    }
}
