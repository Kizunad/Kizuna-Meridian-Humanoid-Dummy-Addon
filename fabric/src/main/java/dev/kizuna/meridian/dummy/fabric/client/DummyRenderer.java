package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyEntity;
import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** 玩家尺寸的灰色两足模型，可以穿戴原版盔甲以便测试护甲减伤。 */
final class DummyRenderer extends HumanoidMobRenderer<DummyEntity, HumanoidModel<DummyEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DummyFabric.MOD_ID, "textures/entity/dummy.png");
    private static final float SHADOW_RADIUS = 0.5F;
    private static final float PIVOT_OFFSET_Y = 0.0F;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    DummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(DummyClient.DUMMY_LAYER)), SHADOW_RADIUS);
        var inner = new HumanoidModel<DummyEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        var outer = new HumanoidModel<DummyEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        addLayer(new HumanoidArmorLayer<>(this, inner, outer, context.getModelManager()));
    }

    static LayerDefinition texturedModelData() {
        // 原版两足骨架加 64×64 贴图；头套层在贴图里透明。
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, PIVOT_OFFSET_Y);
        return LayerDefinition.create(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public ResourceLocation getTextureLocation(DummyEntity entity) {
        return TEXTURE;
    }
}
