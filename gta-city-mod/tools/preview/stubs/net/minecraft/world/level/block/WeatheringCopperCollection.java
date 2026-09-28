package net.minecraft.world.level.block;
/** Stand-in for Minecraft 26.3's copper block sets ({@code Blocks.CUT_COPPER.waxed().oxidized()} ...). */
public final class WeatheringCopperCollection<T> {
    public record ByState<T>(T unaffected, T exposed, T weathered, T oxidized) {
    }
    private final ByState<T> weathering;
    private final ByState<T> waxed;
    private WeatheringCopperCollection(ByState<T> weathering, ByState<T> waxed) {
        this.weathering = weathering;
        this.waxed = waxed;
    }
    static WeatheringCopperCollection<Block> blocks(String name) {
        return new WeatheringCopperCollection<>(
                new ByState<>(new Block(name), new Block("exposed_" + name), new Block("weathered_" + name), new Block("oxidized_" + name)),
                new ByState<>(new Block("waxed_" + name), new Block("waxed_exposed_" + name), new Block("waxed_weathered_" + name), new Block("waxed_oxidized_" + name)));
    }
    public ByState<T> weathering() { return weathering; }
    public ByState<T> waxed() { return waxed; }
}
