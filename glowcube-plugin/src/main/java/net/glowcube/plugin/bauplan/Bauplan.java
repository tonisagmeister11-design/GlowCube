package net.glowcube.plugin.bauplan;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ein geladenes Schematic: Groesse und je Block eine Position mit
 * Blockzustand als Text ({@code minecraft:oak_stairs[facing=north,half=bottom]}).
 * Liest die drei gaengigen Formate:
 * <ul>
 *   <li>{@code .schem} - Sponge-Schematic Version 2 und 3 (WorldEdit, FAWE)</li>
 *   <li>{@code .litematic} - Litematica (alle Regionen zusammengelegt)</li>
 *   <li>{@code .nbt} - Minecrafts Strukturbloecke</li>
 * </ul>
 * Luft steht mit drin - der Builder raeumt damit auf, was im Weg ist.
 * Strukturleere ({@code structure_void}) laesst er aus.
 *
 * <p>Ohne Minecraft-Klassen, damit Client und Plugin dieselbe Datei nutzen.
 * Liegt wortgleich auch im Client - bei Aenderungen beide nachziehen.
 */
public final class Bauplan {
    /** Ein Block des Plans, Position relativ zur Ecke (0,0,0). */
    public record Block(int x, int y, int z, String zustand) {
    }

    public final String name;
    public final int breite;
    public final int hoehe;
    public final int laenge;
    public final List<Block> bloecke;

    private Bauplan(String name, int breite, int hoehe, int laenge, List<Block> bloecke) {
        this.name = name;
        this.breite = breite;
        this.hoehe = hoehe;
        this.laenge = laenge;
        this.bloecke = bloecke;
    }

    /** Wie viele Bloecke keine Luft sind - fuer die Meldung am Ende. */
    public int festeBloecke() {
        int n = 0;
        for (Block b : bloecke) {
            if (!istLuft(b.zustand)) {
                n++;
            }
        }
        return n;
    }

    public static boolean istLuft(String zustand) {
        return zustand.equals("minecraft:air") || zustand.equals("minecraft:cave_air")
                || zustand.equals("minecraft:void_air") || zustand.equals("air");
    }

    // ------------------------------------------------------------- Laden

    public static Bauplan laden(String name, String dateiname, InputStream in) throws IOException {
        Map<String, Object> wurzel = Nbt.lesen(in);
        String klein = dateiname.toLowerCase(java.util.Locale.ROOT);
        if (klein.endsWith(".litematic")) {
            return litematic(name, wurzel);
        }
        if (klein.endsWith(".nbt")) {
            return struktur(name, wurzel);
        }
        return sponge(name, wurzel);
    }

    /** Sponge v2 (Palette/BlockData an der Wurzel) und v3 (alles unter Schematic/Blocks). */
    @SuppressWarnings("unchecked")
    private static Bauplan sponge(String name, Map<String, Object> wurzel) throws IOException {
        Map<String, Object> s = wurzel.containsKey("Schematic") ? (Map<String, Object>) wurzel.get("Schematic") : wurzel;
        int w = zahl(s.get("Width"));
        int h = zahl(s.get("Height"));
        int l = zahl(s.get("Length"));
        Map<String, Object> palette;
        byte[] daten;
        if (s.containsKey("Blocks")) {
            Map<String, Object> blocks = (Map<String, Object>) s.get("Blocks");
            palette = (Map<String, Object>) blocks.get("Palette");
            daten = (byte[]) blocks.get("Data");
        } else {
            palette = (Map<String, Object>) s.get("Palette");
            daten = (byte[]) s.get("BlockData");
        }
        if (palette == null || daten == null) {
            throw new IOException("Schematic ohne Palette oder Blockdaten");
        }
        String[] nachId = new String[palette.size() + 1];
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            int id = zahl(e.getValue());
            if (id >= nachId.length) {
                nachId = java.util.Arrays.copyOf(nachId, id + 1);
            }
            nachId[id] = e.getKey();
        }
        List<Block> liste = new ArrayList<>();
        int stelle = 0;
        int index = 0;
        while (stelle < daten.length && index < w * h * l) {
            int wert = 0;
            int verschiebung = 0;
            while (true) {
                byte b = daten[stelle++];
                wert |= (b & 0x7F) << verschiebung;
                if ((b & 0x80) == 0) {
                    break;
                }
                verschiebung += 7;
            }
            int y = index / (w * l);
            int rest = index % (w * l);
            int z = rest / w;
            int x = rest % w;
            String zustand = wert < nachId.length ? nachId[wert] : null;
            if (zustand != null && !zustand.startsWith("minecraft:structure_void")) {
                liste.add(new Block(x, y, z, zustand));
            }
            index++;
        }
        return new Bauplan(name, w, h, l, liste);
    }

    /** Minecrafts Strukturdatei: size, palette (oder palettes), blocks. */
    @SuppressWarnings("unchecked")
    private static Bauplan struktur(String name, Map<String, Object> wurzel) throws IOException {
        List<Object> groesse = (List<Object>) wurzel.get("size");
        List<Object> palette = (List<Object>) wurzel.get("palette");
        if (palette == null && wurzel.get("palettes") instanceof List<?> mehrere && !mehrere.isEmpty()) {
            palette = (List<Object>) mehrere.get(0);
        }
        List<Object> bloecke = (List<Object>) wurzel.get("blocks");
        if (groesse == null || palette == null || bloecke == null) {
            throw new IOException("Strukturdatei unvollstaendig");
        }
        String[] zustaende = new String[palette.size()];
        for (int i = 0; i < zustaende.length; i++) {
            zustaende[i] = zustandText((Map<String, Object>) palette.get(i));
        }
        List<Block> liste = new ArrayList<>();
        for (Object o : bloecke) {
            Map<String, Object> b = (Map<String, Object>) o;
            List<Object> pos = (List<Object>) b.get("pos");
            int st = zahl(b.get("state"));
            if (st < 0 || st >= zustaende.length || zustaende[st].startsWith("minecraft:structure_void")) {
                continue;
            }
            liste.add(new Block(zahl(pos.get(0)), zahl(pos.get(1)), zahl(pos.get(2)), zustaende[st]));
        }
        return new Bauplan(name, zahl(groesse.get(0)), zahl(groesse.get(1)), zahl(groesse.get(2)), liste);
    }

    /** Litematica: jede Region hat ihre Palette und gepackte Blockzustaende; alle werden zusammengelegt. */
    @SuppressWarnings("unchecked")
    private static Bauplan litematic(String name, Map<String, Object> wurzel) throws IOException {
        Map<String, Object> regionen = (Map<String, Object>) wurzel.get("Regions");
        if (regionen == null || regionen.isEmpty()) {
            throw new IOException("Litematic ohne Regionen");
        }
        List<Block> roh = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Object o : regionen.values()) {
            Map<String, Object> r = (Map<String, Object>) o;
            Map<String, Object> pos = (Map<String, Object>) r.get("Position");
            Map<String, Object> gr = (Map<String, Object>) r.get("Size");
            int px = zahl(pos.get("x")), py = zahl(pos.get("y")), pz = zahl(pos.get("z"));
            int sx = zahl(gr.get("x")), sy = zahl(gr.get("y")), sz = zahl(gr.get("z"));
            // Negative Groessen heissen: die Region waechst von Position aus nach unten/hinten.
            int ox = sx < 0 ? px + sx + 1 : px;
            int oy = sy < 0 ? py + sy + 1 : py;
            int oz = sz < 0 ? pz + sz + 1 : pz;
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            List<Object> pal = (List<Object>) r.get("BlockStatePalette");
            long[] gepackt = (long[]) r.get("BlockStates");
            String[] zustaende = new String[pal.size()];
            for (int i = 0; i < zustaende.length; i++) {
                zustaende[i] = zustandText((Map<String, Object>) pal.get(i));
            }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(zustaende.length - 1));
            long maske = (1L << bits) - 1;
            long gesamt = (long) ax * ay * az;
            for (long i = 0; i < gesamt; i++) {
                long bitStart = i * bits;
                int feld = (int) (bitStart >>> 6);
                int versatz = (int) (bitStart & 63);
                long wert = gepackt[feld] >>> versatz;
                if (versatz + bits > 64 && feld + 1 < gepackt.length) {
                    wert |= gepackt[feld + 1] << (64 - versatz);
                }
                int id = (int) (wert & maske);
                String zustand = id < zustaende.length ? zustaende[id] : "minecraft:air";
                if (zustand.startsWith("minecraft:structure_void")) {
                    continue;
                }
                int x = (int) (i % ax);
                int z = (int) ((i / ax) % az);
                int y = (int) (i / ((long) ax * az));
                int wx = ox + x, wy = oy + y, wz = oz + z;
                minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
                maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
                roh.add(new Block(wx, wy, wz, zustand));
            }
        }
        List<Block> liste = new ArrayList<>(roh.size());
        for (Block b : roh) {
            liste.add(new Block(b.x - minX, b.y - minY, b.z - minZ, b.zustand));
        }
        return new Bauplan(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, liste);
    }

    /** {Name, Properties} -> "minecraft:name[a=b,c=d]" (Eigenschaften sortiert). */
    @SuppressWarnings("unchecked")
    private static String zustandText(Map<String, Object> eintrag) {
        String name = (String) eintrag.get("Name");
        Object props = eintrag.get("Properties");
        if (!(props instanceof Map<?, ?> p) || p.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean erstes = true;
        for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) p).entrySet()) {
            if (!erstes) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            erstes = false;
        }
        return sb.append(']').toString();
    }

    private static int zahl(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("Keine Zahl: " + o);
    }
}
