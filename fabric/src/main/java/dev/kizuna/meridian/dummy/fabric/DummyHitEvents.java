package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

/** 服务端击打报告事件：报告校验通过、发往攻击者之前触发，供测试与其他附属读取同一份报告。 */
public final class DummyHitEvents {
    public static final Event<Reported> REPORTED = EventFactory.createArrayBacked(Reported.class,
            listeners -> (attacker, dummy, report) -> {
                for (Reported listener : listeners) {
                    listener.onReported(attacker, dummy, report);
                }
            });

    private DummyHitEvents() {
        // 只保存事件。
    }

    @FunctionalInterface
    public interface Reported {
        void onReported(ServerPlayerEntity attacker, DummyEntity dummy, HitReport report);
    }
}
