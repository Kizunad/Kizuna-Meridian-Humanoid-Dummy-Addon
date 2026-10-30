package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageKind;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageResolution;

import java.util.Objects;

/** 一次已提交伤害的固定事实，全部来自 Damage 观察事件，不在这里重新结算。 */
public record HitFacts(BodyChange body, DamageResolution resolution, HealthTarget hit, Dose dose) {
    public HitFacts {
        Objects.requireNonNull(body);
        Objects.requireNonNull(resolution);
        Objects.requireNonNull(hit);
        Objects.requireNonNull(dose);
    }

    /** 同一事务提交前后的两份权威快照。 */
    public record BodyChange(BodySnapshot before, BodySnapshot after) {
        public BodyChange {
            Objects.requireNonNull(before);
            Objects.requireNonNull(after);
        }
    }

    /** vanilla 为 Damage 日志的「原版」剂量，afterArmor 为进入身体结算的「护甲后」剂量。 */
    public record Dose(DamageKind kind, double vanilla, double afterArmor) {
        public Dose {
            Objects.requireNonNull(kind);
        }
    }
}
