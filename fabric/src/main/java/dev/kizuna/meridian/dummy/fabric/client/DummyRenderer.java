package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyEntity;
import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/** 玩家尺寸的灰色两足模型，可以穿戴原版盔甲以便测试护甲减伤。 */
final class DummyRenderer extends BipedEntityRenderer<DummyEntity, BipedEntityModel<DummyEntity>> {
    private static final Identifier TEXTURE = new Identifier(DummyFabric.MOD_ID, "textures/entity/dummy.png");
    private static final float SHADOW_RADIUS = 0.5F;
    private static final float PIVOT_OFFSET_Y = 0.0F;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    DummyRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(DummyClient.DUMMY_LAYER)), SHADOW_RADIUS);
        var inner = new BipedEntityModel<DummyEntity>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR));
        var outer = new BipedEntityModel<DummyEntity>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
        addFeature(new ArmorFeatureRenderer<>(this, inner, outer, context.getModelManager()));
    }

    static TexturedModelData texturedModelData() {
        // 原版两足骨架加 64×64 贴图；头套层在贴图里透明。
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, PIVOT_OFFSET_Y);
        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public Identifier getTexture(DummyEntity entity) {
        return TEXTURE;
    }
}
