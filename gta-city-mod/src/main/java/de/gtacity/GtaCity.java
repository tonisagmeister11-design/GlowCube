package de.gtacity;

import de.gtacity.gameplay.CityEvents;
import de.gtacity.network.ModNetworking;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModBlocks;
import de.gtacity.registry.ModComponents;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import de.gtacity.registry.ModSounds;
import de.gtacity.world.CityChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GtaCity implements ModInitializer {
    public static final String MOD_ID = "gtacity";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModSounds.init();
        ModEntities.init();
        ModAttachments.init();
        ModNetworking.init();
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("city"), CityChunkGenerator.CODEC);
        CityEvents.init();
        de.gtacity.gameplay.Crew.init();
        LOG.info("GTA City geladen - willkommen in Los Santos!");
    }
}
