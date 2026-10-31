package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyChannels;
import dev.kizuna.meridian.dummy.fabric.DummyContent;
import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.server.HitReports;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 客户端入口：假人渲染、击打报告接收与右上角 HUD。 */
public final class DummyClient implements ClientModInitializer {
    static final ModelLayerLocation DUMMY_LAYER = new ModelLayerLocation(DummyContent.DUMMY_ID, "main");
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private final DummyHud hud = new DummyHud();

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(DUMMY_LAYER, DummyRenderer::texturedModelData);
        EntityRendererRegistry.register(DummyContent.entityType(), DummyRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DummyChannels.HIT_REPORT, this::receive);
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            Minecraft client = Minecraft.getInstance();
            hud.render(context, client.font, Util.getMillis());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(hud::clear));
    }

    private void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buffer,
                         PacketSender sender) {
        // 先按上限拒绝超长载荷再复制；解码与范围校验回到主线程，连接已切换时丢弃。
        int size = buffer.readableBytes();
        if (size > HitReports.MAX_BYTES) {
            LOGGER.warn("丢弃超长的训练假人击打报告：{} 字节", size);
            return;
        }
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        client.execute(() -> {
            if (client.getConnection() != handler) {
                return;
            }
            try {
                HitReport report = HitReports.decode(bytes);
                hud.push(report, Util.getMillis());
            } catch (IllegalArgumentException failure) {
                LOGGER.warn("丢弃无效的训练假人击打报告：{}", failure.getMessage());
            }
        });
    }
}
