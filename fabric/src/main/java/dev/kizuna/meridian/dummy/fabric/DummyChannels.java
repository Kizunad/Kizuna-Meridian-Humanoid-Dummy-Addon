package dev.kizuna.meridian.dummy.fabric;

import net.minecraft.util.Identifier;

/** 假人唯一的 S2C 通道；载荷是 HitReport 的 Protobuf 字节，客户端按 HitReports 的上限校验。 */
public final class DummyChannels {
    public static final Identifier HIT_REPORT = new Identifier(DummyFabric.MOD_ID, "hit_report");

    private DummyChannels() {
        // 只保存常量。
    }
}
