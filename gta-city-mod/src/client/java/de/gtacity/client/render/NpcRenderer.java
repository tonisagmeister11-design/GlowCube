package de.gtacity.client.render;

import de.gtacity.GtaCity;
import de.gtacity.client.GtaCityClient;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.item.GunItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/** Renders pedestrians and police with the player model and the mod's own skins. */
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcRenderer.State, NpcModel> {
    private static final Identifier[] CIVILIANS = textures("civilian", NpcEntity.CIVILIAN_SKINS);
    private static final Identifier[] GANG = textures("gang", NpcEntity.GANG_SKINS);
    private static final Identifier[] POLICE = textures("police", PoliceEntity.POLICE_SKINS);
    private static final Identifier SWAT = GtaCity.id("textures/entity/npc/swat_0.png");

    public static class State extends HumanoidRenderState {
        public Identifier texture = CIVILIANS[0];
        public int gesture;
        public float gestureAge = -1;
        public float gestureYaw;
    }

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(context.bakeLayer(GtaCityClient.NPC_LAYER)), 0.5F);
    }

    private static Identifier[] textures(String kind, int count) {
        Identifier[] ids = new Identifier[count];
        for (int i = 0; i < count; i++) {
            ids[i] = GtaCity.id("textures/entity/npc/" + kind + "_" + i + ".png");
        }
        return ids;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NpcEntity npc, State state, float partialTick) {
        super.extractRenderState(npc, state, partialTick);
        state.gesture = npc.gestureKind();
        state.gestureAge = npc.gestureAge(partialTick);
        state.gestureYaw = npc.gestureYaw();
        int skin = Math.max(0, npc.getSkin());
        if (npc instanceof PoliceEntity cop) {
            state.texture = cop.isSwat() ? SWAT : POLICE[skin % POLICE.length];
        } else if (npc.isGang()) {
            state.texture = GANG[skin % GANG.length];
        } else {
            state.texture = CIVILIANS[skin % CIVILIANS.length];
        }
    }

    /** Officers who are after someone hold their gun up with both hands, like a loaded crossbow. */
    @Override
    protected HumanoidModel.ArmPose getArmPose(NpcEntity npc, HumanoidArm arm) {
        if (npc.isAggressive() && npc.getMainArm() == arm && npc.getMainHandItem().getItem() instanceof GunItem) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return super.getArmPose(npc, arm);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return state.texture;
    }
}
