package net.glowcube.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Fassung fuer <b>26.3 und neuer</b>: dieselben Kleinigkeiten mit den neuen
 * 26.x-Namen.
 */
public final class Netz {
    private static java.lang.reflect.Field schwungFeld;

    /**
     * Holt das Wesen gerade zum Schlag aus? Ab 26.x steckt das in einem
     * eigenen SwingState statt im Feld {@code swinging}. Dessen Innenleben
     * ist nicht zugesichert - darum wird jedes boolesche Feld mit "swing"
     * im Namen oder ein Zaehler groesser null als "holt aus" gelesen.
     */
    public static boolean holtAus(net.minecraft.world.entity.LivingEntity wesen) {
        try {
            if (schwungFeld == null) {
                schwungFeld = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("swingState");
                schwungFeld.setAccessible(true);
            }
            Object zustand = schwungFeld.get(wesen);
            if (zustand == null) {
                return false;
            }
            for (java.lang.reflect.Field f : zustand.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (f.getType() == boolean.class && name.contains("swing")) {
                    if (f.getBoolean(zustand)) {
                        return true;
                    }
                } else if (f.getType() == int.class && (name.contains("time") || name.contains("tick"))) {
                    if (f.getInt(zustand) > 0) {
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException fehler) {
            return false;
        }
        return false;
    }

    private Netz() {
    }

    // Tastencodes: ab 26.x fehlt das Paket org.lwjgl.glfw (Umstieg auf SDL).
    // Minecraft fuehrt die Tasten aber weiter unter den GLFW-kompatiblen
    // Codes - genau die Zahlen, die auch KeyEvent.key() und InputConstants
    // liefern. Deshalb hier fest, statt aus einer nicht mehr vorhandenen
    // Klasse. So bleiben die Bildschirme frei von org.lwjgl.
    public static final int TASTE_UNBEKANNT = -1;
    public static final int TASTE_ESC = 256;
    public static final int TASTE_ENTER = 257;
    public static final int TASTE_RUECK = 259;      // Backspace
    public static final int TASTE_POS1 = 268;       // Home
    public static final int TASTE_NUM_ENTER = 335;  // Enter auf dem Ziffernblock

    public static void schwungSenden(InteractionHand hand) {
        // Ab 26.x gibt es kein eigenes Schwung-Paket mehr (ServerboundSwingPacket
        // ist weg) - der Server leitet den Schwung aus Angriff und Benutzen ab.
    }

    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket.getClass().getSimpleName().contains("Swing");
    }

    public static void schwingen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            SwingAnimation art = mc.player.getItemInHand(hand)
                    .getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT);
            mc.player.swing(hand, art, false);
        }
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

    // Ab 26.x ist allChanged() vom LevelRenderer auf Minecraft.levelExtractor
    // gewandert. Das Feld ist nicht oeffentlich, darum ueber Spiegelung - so
    // baut X-Ray die Chunk-Meshes neu auf und die ausgeblendeten Bloecke
    // verschwinden sofort statt erst beim naechsten Nachladen.
    private static java.lang.reflect.Field extractorFeld;

    public static void chunksNeuZeichnen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (extractorFeld == null) {
                extractorFeld = Minecraft.class.getDeclaredField("levelExtractor");
                extractorFeld.setAccessible(true);
            }
            Object extractor = extractorFeld.get(mc);
            if (extractor == null) {
                return;
            }
            java.lang.reflect.Method allChanged;
            try {
                allChanged = extractor.getClass().getMethod("allChanged");
            } catch (NoSuchMethodException fehlt) {
                allChanged = extractor.getClass().getDeclaredMethod("allChanged");
                allChanged.setAccessible(true);
            }
            allChanged.invoke(extractor);
        } catch (ReflectiveOperationException fehler) {
            // Kein Weltcrash: X-Ray zeigt dann erst nach dem naechsten
            // Chunk-Nachladen, statt das Spiel mitzureissen.
            net.glowcube.client.GlowCubeClient.LOGGER.warn(
                    "GlowCube: Chunk-Neuzeichnen auf 26.3 fehlgeschlagen", fehler);
        }
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
