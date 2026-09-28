package de.gtacity.shop;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum ShopType implements StringRepresentable {
    WEAPONS("weapons", "Ammu-Nation"),
    STORE("store", "24/7 Supermarkt"),
    CARS("cars", "Premium Deluxe Autohaus");

    public static final Codec<ShopType> CODEC = StringRepresentable.fromEnum(ShopType::values);

    private final String name;
    public final String title;

    ShopType(String name, String title) {
        this.name = name;
        this.title = title;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
