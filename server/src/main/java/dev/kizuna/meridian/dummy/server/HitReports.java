package dev.kizuna.meridian.dummy.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.DamageKind;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.Injury;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.InjuryModule;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartChange;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartCondition;

/** 击打报告的编解码与有界校验；发送前和接收后执行同一套规则。 */
public final class HitReports {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_BYTES = 16 * 1024;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_INJURIES = 32;
    public static final int MAX_PART_CHANGES = 32;
    /** 单个 HP 或剂量的展示上限；超出时由构建方截断后再发送。 */
    public static final double MAX_VALUE = 1_000_000;

    private HitReports() {
        // 纯函数集合。
    }

    public static byte[] encode(HitReport report) {
        // 发送方同样校验，越界报告不会离开服务端。
        validate(report);
        return report.toByteArray();
    }

    public static HitReport decode(byte[] payload) {
        // 先限制长度再解析，解析后整份校验，失败时不返回部分内容。
        if (payload.length == 0 || payload.length > MAX_BYTES) {
            throw new IllegalArgumentException(DummyErrors.REPORT_SIZE);
        }
        HitReport report;
        try {
            report = HitReport.parseFrom(payload);
        } catch (InvalidProtocolBufferException failure) {
            throw new IllegalArgumentException(DummyErrors.REPORT_FORMAT, failure);
        }
        validate(report);
        return report;
    }

    public static void validate(HitReport report) {
        // 版本、未知字段、命中部位、剂量和两个列表依次检查。
        if (report.getProtocolVersion() != PROTOCOL_VERSION) {
            throw new IllegalArgumentException(DummyErrors.REPORT_VERSION);
        }
        requireKnownFields(report.getUnknownFields());
        requireName(report.getPartName());
        double maximum = report.getPartMaxHp();
        requireValue(maximum);
        if (maximum == 0) {
            throw new IllegalArgumentException(DummyErrors.REPORT_VALUE);
        }
        requireAtMost(report.getPartHp(), maximum);
        requireAtMost(report.getPartLoss(), maximum);
        DamageKind kind = report.getDamageKind();
        if (kind == DamageKind.DAMAGE_KIND_UNSPECIFIED || kind == DamageKind.UNRECOGNIZED) {
            throw new IllegalArgumentException(DummyErrors.REPORT_KIND);
        }
        requireValue(report.getVanillaDose());
        requireValue(report.getDoseAfterArmor());
        validateInjuries(report);
        validatePartChanges(report);
    }

    private static void validateInjuries(HitReport report) {
        // 每条伤势的扣除都必须达到列出门槛，并说明属于部位、组织还是器官。
        if (report.getInjuriesCount() > MAX_INJURIES) {
            throw new IllegalArgumentException(DummyErrors.REPORT_LIST);
        }
        for (Injury injury : report.getInjuriesList()) {
            requireKnownFields(injury.getUnknownFields());
            requireName(injury.getName());
            double lost = injury.getHp();
            requireValue(lost);
            if (!HitAmounts.listed(lost)) {
                throw new IllegalArgumentException(DummyErrors.REPORT_VALUE);
            }
            InjuryModule module = injury.getModule();
            if (module == InjuryModule.INJURY_MODULE_UNSPECIFIED || module == InjuryModule.UNRECOGNIZED) {
                throw new IllegalArgumentException(DummyErrors.REPORT_VALUE);
            }
        }
    }

    private static void validatePartChanges(HitReport report) {
        // 只接受真实的状态变化，两端都必须是已知状态。
        if (report.getPartChangesCount() > MAX_PART_CHANGES) {
            throw new IllegalArgumentException(DummyErrors.REPORT_LIST);
        }
        for (PartChange change : report.getPartChangesList()) {
            requireKnownFields(change.getUnknownFields());
            requireName(change.getPartName());
            PartCondition before = change.getBefore();
            PartCondition after = change.getAfter();
            requireCondition(before);
            requireCondition(after);
            if (before == after) {
                throw new IllegalArgumentException(DummyErrors.REPORT_CHANGE);
            }
        }
    }

    private static void requireCondition(PartCondition condition) {
        if (condition == PartCondition.PART_CONDITION_UNSPECIFIED || condition == PartCondition.UNRECOGNIZED) {
            throw new IllegalArgumentException(DummyErrors.REPORT_CHANGE);
        }
    }

    private static void requireKnownFields(UnknownFieldSet fields) {
        // 新字段必须随版本号一起升级，旧客户端不静默忽略。
        if (!fields.asMap().isEmpty()) {
            throw new IllegalArgumentException(DummyErrors.REPORT_UNKNOWN_FIELD);
        }
    }

    static void requireName(String name) {
        // 名称直接显示在 HUD 上，拒绝空白、超长和控制字符。
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(DummyErrors.REPORT_NAME);
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(DummyErrors.REPORT_NAME);
        }
    }

    private static void requireValue(double value) {
        if (!Double.isFinite(value) || value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException(DummyErrors.REPORT_VALUE);
        }
    }

    private static void requireAtMost(double value, double maximum) {
        requireValue(value);
        if (value > maximum) {
            throw new IllegalArgumentException(DummyErrors.REPORT_VALUE);
        }
    }
}
