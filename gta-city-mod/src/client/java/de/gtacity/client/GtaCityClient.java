package de.gtacity.client;

import de.gtacity.GtaCity;
import de.gtacity.client.hud.CityHud;
import de.gtacity.client.render.CarModel;
import de.gtacity.client.render.CarRenderer;
import de.gtacity.client.render.HelicopterModel;
import de.gtacity.client.render.HelicopterRenderer;
import de.gtacity.client.render.NpcRenderer;
import de.gtacity.client.screen.JobBoardScreen;
import de.gtacity.client.screen.ShopScreen;
import de.gtacity.gameplay.Jobs;
import de.gtacity.entity.CarVariant;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModEntities;
import de.gtacity.shop.ShopType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class GtaCityClient implements ClientModInitializer {
    public static final ModelLayerLocation NPC_LAYER = new ModelLayerLocation(GtaCity.id("npc"), "main");

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(NPC_LAYER,
                () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        for (CarVariant.Shape shape : CarVariant.Shape.values()) {
            ModelLayerRegistry.registerModelLayer(CarModel.layer(shape), () -> CarModel.create(shape));
        }

        EntityRenderers.register(ModEntities.PEDESTRIAN, NpcRenderer::new);
        EntityRenderers.register(ModEntities.POLICE, NpcRenderer::new);
        EntityRenderers.register(ModEntities.CAR, CarRenderer::new);
        ModelLayerRegistry.registerModelLayer(HelicopterModel.LAYER, HelicopterModel::create);
        EntityRenderers.register(ModEntities.HELICOPTER, HelicopterRenderer::new);
        EntityRenderers.register(ModEntities.ROCKET, ThrownItemRenderer::new);
        EntityRenderers.register(ModEntities.GRENADE, ThrownItemRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenShop.TYPE, (payload, context) -> {
            ShopType[] types = ShopType.values();
            if (payload.shop() >= 0 && payload.shop() < types.length) {
                Minecraft.getInstance().gui.setScreen(new ShopScreen(types[payload.shop()]));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenJobs.TYPE, (payload, context) -> {
            Jobs.Station[] stations = Jobs.Station.values();
            if (payload.station() >= 0 && payload.station() < stations.length) {
                Minecraft.getInstance().gui.setScreen(new JobBoardScreen(stations[payload.station()]));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ShotFx.TYPE, (payload, context) -> ShotEffects.spawn(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Recoil.TYPE, (payload, context) -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.setXRot(player.getXRot() - payload.amount());
                player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * payload.amount() * 0.4F);
            }
        });

        // Before the chat, so chat lines are drawn over the minimap instead of disappearing behind it.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, GtaCity.id("hud"), CityHud::render);
        ClientInput.init();
    }
}
