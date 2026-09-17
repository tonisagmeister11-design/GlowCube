package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Uebertragen aus BleachHack (GPL-3.0), Modul {@code AntiChunkBan}.
 *
 * <p>Wie dort haelt die Modulklasse selbst nichts als den Schalter - die
 * Arbeit machen zwei Mixins, weil beide Grenzen tief im Netzcode stehen und
 * nicht von aussen erreichbar sind:
 *
 * <ul>
 *   <li>{@link net.glowcube.client.mixin.CompressionDecoderMixin} hebt die
 *       Groessengrenze fuers Entpacken - das ist der Chunkban.</li>
 *   <li>{@link net.glowcube.client.mixin.FriendlyByteBufMixin} hebt die
 *       Groessengrenze fuer NBT - das ist der Bookban.</li>
 * </ul>
 *
 * <p>Beides ist rein clientseitig: es aendert nur, was der eigene Client noch
 * annimmt, statt hinauszugehen. Der Server merkt davon nichts.
 */
public final class AntiChunkBan extends Module {
    /**
     * Die Mixins laufen im Netz-Thread, lange bevor und noch nachdem es eine
     * Modulliste gibt. Ein statischer Verweis auf die eine Instanz ist dort
     * das Einzige, was sicher ist - eine Suche ueber den ModuleManager waere
     * je Paket ein Listendurchlauf und im ungeladenen Zustand ein Absturz.
     */
    private static AntiChunkBan instanz;

    public AntiChunkBan() {
        super("AntiChunkBan", "Nimmt auch uebergrosse Chunk- und Buchpakete an", Category.EXPLOIT);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
