package net.minecraft.world.level.block;
/** Stand-in for Minecraft 26.3's per-colour block sets ({@code Blocks.CONCRETE.white()} ...). */
public final class ColorCollection<T> {
    private final java.util.Map<String, T> byName = new java.util.HashMap<>();
    static ColorCollection<Block> blocks(String suffix) {
        ColorCollection<Block> c = new ColorCollection<>();
        for (String colour : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            c.byName.put(colour, new Block(colour + "_" + suffix));
        }
        return c;
    }
    public T white() { return byName.get("white"); }
    public T orange() { return byName.get("orange"); }
    public T magenta() { return byName.get("magenta"); }
    public T lightBlue() { return byName.get("light_blue"); }
    public T yellow() { return byName.get("yellow"); }
    public T lime() { return byName.get("lime"); }
    public T pink() { return byName.get("pink"); }
    public T gray() { return byName.get("gray"); }
    public T lightGray() { return byName.get("light_gray"); }
    public T cyan() { return byName.get("cyan"); }
    public T purple() { return byName.get("purple"); }
    public T blue() { return byName.get("blue"); }
    public T brown() { return byName.get("brown"); }
    public T green() { return byName.get("green"); }
    public T red() { return byName.get("red"); }
    public T black() { return byName.get("black"); }
}
