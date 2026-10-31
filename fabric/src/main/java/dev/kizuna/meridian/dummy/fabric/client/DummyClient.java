package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyContent;
import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import dev.kizuna.meridian.dummy.fabric.HitReportPayload;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.server.HitReports;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 客户端入口：假人渲染、击打报告接收与右上角 HUD。 */
public final class DummyClient implements ClientModInitializer {
    static final ModelLayerLocation DUMMY_LAYER = new ModelLayerLocation(DummyContent.DUMMY_ID, "main");
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(DummyFabric.MOD_ID, "hit_report");
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private final DummyHud hud = new DummyHud();

    @Override
    public void onInitializeClient() {
        // 渲染器登记到原版表：Fabric 经传递访问扩展公开了它，原包装 EntityRendererRegistry 在 26.2 已弃用。
        // 原 HUD 回调在原版界面之后绘制，26.2 改为登记到 HUD 层末尾。
        ModelLayerRegistry.registerModelLayer(DUMMY_LAYER, DummyRenderer::texturedModelData);
        EntityRenderers.register(DummyContent.entityType(), DummyRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(HitReportPayload.TYPE, (payload, context) -> receive(payload));
        HudElementRegistry.addLast(HUD_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            hud.render(context, client.font, Util.getMillis());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(hud::clear));
    }

    private void receive(HitReportPayload payload) {
        // 超长载荷在读包时已按上限跳过，这里只记录字节数后丢弃；解码与范围校验失败同样丢弃。
        // 26.2 的收包回调已在客户端主线程上、属于当前连接，不再需要旧版跨线程后的连接切换检查。
        if (payload.oversized()) {
            LOGGER.warn("丢弃超长的训练假人击打报告：{} 字节", payload.size());
            return;
        }
        try {
            HitReport report = HitReports.decode(payload.bytes());
            hud.push(report, Util.getMillis());
        } catch (IllegalArgumentException failure) {
            LOGGER.warn("丢弃无效的训练假人击打报告：{}", failure.getMessage());
        }
    }
}
