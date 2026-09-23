package net.glowcube.plugin.bauplan;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Ein kleiner NBT-Leser ohne Minecraft-Klassen - so liest dieselbe Datei im
 * Client und im Server-Plugin. Compounds werden zu {@code Map<String,Object>},
 * Listen zu {@code List<Object>}, Zahlen zu ihren Java-Typen, Arrays zu
 * {@code byte[]}, {@code int[]}, {@code long[]}.
 *
 * <p>Diese Datei liegt wortgleich auch im Client (glowcube-client) - bei
 * Aenderungen beide nachziehen.
 */
public final class Nbt {
    private Nbt() {
    }

    /** Liest eine (meist gzip-gepackte) NBT-Datei und gibt das Wurzel-Compound zurueck. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> lesen(InputStream roh) throws IOException {
        java.io.BufferedInputStream gepuffert = new java.io.BufferedInputStream(roh);
        gepuffert.mark(2);
        int a = gepuffert.read();
        int b = gepuffert.read();
        gepuffert.reset();
        InputStream quelle = (a == 0x1F && b == 0x8B) ? new GZIPInputStream(gepuffert) : gepuffert;
        DataInputStream in = new DataInputStream(quelle);
        int typ = in.readUnsignedByte();
        if (typ != 10) {
            throw new IOException("Kein NBT-Compound an der Wurzel");
        }
        in.readUTF();
        return (Map<String, Object>) wert(in, 10, 0);
    }

    private static Object wert(DataInputStream in, int typ, int tiefe) throws IOException {
        if (tiefe > 512) {
            throw new IOException("NBT zu tief verschachtelt");
        }
        switch (typ) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                return b;
            }
            case 8: {
                int laenge = in.readUnsignedShort();
                byte[] b = new byte[laenge];
                in.readFully(b);
                // Modified UTF-8 ist fuer Blocknamen dasselbe wie UTF-8.
                return new String(b, StandardCharsets.UTF_8);
            }
            case 9: {
                int elementTyp = in.readUnsignedByte();
                int anzahl = in.readInt();
                List<Object> liste = new ArrayList<>(Math.max(0, Math.min(anzahl, 1 << 16)));
                for (int i = 0; i < anzahl; i++) {
                    liste.add(wert(in, elementTyp, tiefe + 1));
                }
                return liste;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) {
                        return map;
                    }
                    String name = in.readUTF();
                    map.put(name, wert(in, t, tiefe + 1));
                }
            }
            case 11: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("Unbekannter NBT-Typ " + typ);
        }
    }
}
