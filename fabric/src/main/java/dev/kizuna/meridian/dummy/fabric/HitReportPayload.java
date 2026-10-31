package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.dummy.server.HitReports;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 假人唯一的 S2C 包；载荷是 HitReport 的 Protobuf 字节，占满包体剩余部分，线格式与 1.20.1 相同。
 * 通道 ID 与编解码只在这里定义一份；超过 {@link HitReports#MAX_BYTES} 的载荷读包时跳过不复制，
 * 只留下字节数，由客户端记录后丢弃。
 */
public record HitReportPayload(int size, byte[] bytes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HitReportPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(DummyFabric.MOD_ID, "hit_report"));
    static final StreamCodec<FriendlyByteBuf, HitReportPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.bytes()), HitReportPayload::read);

    static HitReportPayload of(byte[] bytes) {
        // 服务端发送已编码并校验过的报告，字节数就是数组长度。
        return new HitReportPayload(bytes.length, bytes);
    }

    public boolean oversized() {
        // 客户端先按上限拒绝，再解码与校验。
        return size > HitReports.MAX_BYTES;
    }

    private static HitReportPayload read(FriendlyByteBuf buffer) {
        // 先按上限判断再复制；超长时跳过剩余字节，不分配数组。
        int size = buffer.readableBytes();
        if (size > HitReports.MAX_BYTES) {
            buffer.skipBytes(size);
            return new HitReportPayload(size, new byte[0]);
        }
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        return new HitReportPayload(size, bytes);
    }

    @Override
    public Type<HitReportPayload> type() {
        // 收发两端都按这个类型查找通道。
        return TYPE;
    }
}
