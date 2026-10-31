package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyEntity;
import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** 玩家尺寸的灰色两足模型，可以穿戴原版盔甲以便测试护甲减伤。 */
final class DummyRenderer
        extends HumanoidMobRenderer<DummyEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DummyFabric.MOD_ID, "textures/entity/dummy.png");
    private static final float SHADOW_RADIUS = 0.5F;
    private static final float PIVOT_OFFSET_Y = 0.0F;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    DummyRenderer(EntityRendererProvider.Context context) {
        // 26.2 按渲染状态绘制；盔甲层改用原版的玩家盔甲模型组（按四个部位），取代旧版玩家内外两层盔甲模型。
        super(context, new HumanoidModel<>(context.bakeLayer(DummyClient.DUMMY_LAYER)), SHADOW_RADIUS);
        var armor = ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(),
                HumanoidModel<HumanoidRenderState>::new);
        addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
    }

    static LayerDefinition texturedModelData() {
        // 原版两足骨架加 64×64 贴图；头套层在贴图里透明。
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, PIVOT_OFFSET_Y);
        return LayerDefinition.create(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        // 假人没有额外的渲染字段，用原版两足状态即可。
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        // 贴图固定，不随状态变化。
        return TEXTURE;
    }
}
