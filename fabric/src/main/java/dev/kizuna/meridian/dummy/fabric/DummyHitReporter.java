package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.server.HitFacts;
import dev.kizuna.meridian.dummy.server.HitReportBuilder;
import dev.kizuna.meridian.dummy.server.HitReports;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageResolution;
import dev.kizuna.meridian.vanilla.VanillaDamageBridge;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Damage 观察方：玩家打到假人时把提交后的事实整理成报告，只发给攻击者本人。 */
final class DummyHitReporter {
    private DummyHitReporter() {
        // 纯静态观察方。
    }

    static void onDamage(VanillaDamageFabric.DamageEvent event) {
        if (!(event.target() instanceof DummyEntity dummy)) {
            return;
        }
        if (!(event.attacker() instanceof ServerPlayer attacker)) {
            return;
        }
        HitReport report = HitReportBuilder.build(facts(event));
        DummyHitEvents.REPORTED.invoker().onReported(attacker, dummy, report);
        if (!ServerPlayNetworking.canSend(attacker, DummyChannels.HIT_REPORT)) {
            return;
        }
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeBytes(HitReports.encode(report));
        ServerPlayNetworking.send(attacker, DummyChannels.HIT_REPORT, buffer);
    }

    private static HitFacts facts(VanillaDamageFabric.DamageEvent event) {
        // 命中优先取 Damage 的预选目标；未预选时由回执还原 Framework 实际选中的部位。
        VanillaDamageBridge.CommitResult result = event.result();
        DamageResolution resolution = result.health().getDamageResolution();
        VanillaDamageFabric.DamageInput input = event.input();
        HealthTarget hit = input.hit().orElseGet(() -> HitReportBuilder.resolvedHit(resolution));
        var change = new HitFacts.BodyChange(result.before(), result.body());
        var dose = new HitFacts.Dose(input.kind(), input.rawDamage(), input.damageAfterEffects());
        return new HitFacts(change, resolution, hit, dose);
    }
}
