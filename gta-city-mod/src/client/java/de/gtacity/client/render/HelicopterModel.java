package de.gtacity.client.render;

import de.gtacity.GtaCity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Police helicopter. Texture (256x128) comes from tools/gen_cars.py (HELI_PARTS), keep the box sizes in sync.
 * Model space: y = 24 is the ground, -z is the nose.
 */
public class HelicopterModel extends EntityModel<HelicopterRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(GtaCity.id("helicopter"), "main");

    private final ModelPart rotor;
    private final ModelPart tailRotor;

    public HelicopterModel(ModelPart root) {
        super(root);
        rotor = root.getChild("rotor");
        tailRotor = root.getChild("tail_rotor");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-11, 2, -18, 22, 16, 36),
                PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 56).addBox(-9, 5, -26, 18, 12, 8),
                PartPose.ZERO);
        root.addOrReplaceChild("boom", CubeListBuilder.create().texOffs(120, 0).addBox(-3, 5, 18, 6, 6, 40),
                PartPose.ZERO);
        root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(120, 50).addBox(-1, -7, 52, 2, 14, 8),
                PartPose.ZERO);
        CubeListBuilder skids = CubeListBuilder.create()
                .texOffs(0, 80).addBox(-11, 22, -20, 2, 2, 40)
                .texOffs(0, 80).addBox(9, 22, -20, 2, 2, 40);
        for (int x : new int[]{-11, 9}) {
            for (int z : new int[]{-12, 10}) {
                skids.texOffs(90, 80).addBox(x, 18, z, 2, 4, 2);
            }
        }
        root.addOrReplaceChild("skids", skids, PartPose.ZERO);
        root.addOrReplaceChild("mast", CubeListBuilder.create().texOffs(100, 80).addBox(-2, -2, -2, 4, 4, 4),
                PartPose.ZERO);
        PartDefinition rotor = root.addOrReplaceChild("rotor", CubeListBuilder.create(), PartPose.offset(0, -2, 0));
        for (int i = 0; i < 4; i++) {
            rotor.addOrReplaceChild("blade" + i, CubeListBuilder.create().texOffs(0, 100).addBox(0, -1, -2.5F, 55, 1, 5),
                    PartPose.rotation(0, i * Mth.HALF_PI, 0));
        }
        PartDefinition tail = root.addOrReplaceChild("tail_rotor", CubeListBuilder.create(),
                PartPose.offset(2.5F, 0, 56));
        tail.addOrReplaceChild("a", CubeListBuilder.create().texOffs(120, 80).addBox(0, -8, -1.5F, 1, 16, 3),
                PartPose.ZERO);
        tail.addOrReplaceChild("b", CubeListBuilder.create().texOffs(120, 80).addBox(0, -8, -1.5F, 1, 16, 3),
                PartPose.rotation(Mth.HALF_PI, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(HelicopterRenderer.State state) {
        super.setupAnim(state);
        rotor.yRot = state.rotor * Mth.DEG_TO_RAD;
        tailRotor.xRot = state.rotor * 1.7F * Mth.DEG_TO_RAD;
    }
}
