package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.DamageKind;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.Injury;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.InjuryModule;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartChange;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartCondition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodyStructure;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueState;
import dev.kizuna.meridian.protocol.pb.HealthMessages;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageResolution;
import dev.kizuna.meridian.protocol.pb.HealthMessages.HpLoss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 把一次伤害的前后快照整理成 HUD 报告；扣除量一律取前后快照之差。 */
public final class HitReportBuilder {
    /** 快照里缺少状态条目的模块按 0 HP 处理。 */
    private static final double ABSENT_HP = 0.0;
    private HitReportBuilder() {
        // 纯函数集合。
    }

    public static HitReport build(HitFacts facts) {
        // 命中部位以提交后的实际构型为准，组织或器官命中沿构型反查所属部位。
        BodySnapshot before = facts.body().before();
        BodySnapshot after = facts.body().after();
        BodyStructure structure = after.getStructure();
        PartDefinition part = part(structure, partOf(structure, facts.hit()));
        Ledger parts = new Ledger(partHp(before), partHp(after));
        double maximum = bounded(part.getMaxHp());
        double remaining = Math.min(bounded(parts.after(part.getId())), maximum);
        double lost = Math.min(bounded(parts.loss(part.getId())), maximum);
        HitReport.Builder report = HitReport.newBuilder();
        report.setProtocolVersion(HitReports.PROTOCOL_VERSION);
        report.setPartName(name(part.getDisplayName(), part.getId()));
        report.setPartHp(remaining);
        report.setPartMaxHp(maximum);
        report.setPartLoss(lost);
        report.setDamageKind(kind(facts.dose().kind()));
        report.setVanillaDose(bounded(facts.dose().vanilla()));
        report.setDoseAfterArmor(bounded(facts.dose().afterArmor()));
        report.addAllInjuries(injuries(facts, part.getId(), parts));
        report.addAllPartChanges(partChanges(structure, parts));
        HitReport built = report.build();
        HitReports.validate(built);
        return built;
    }

    public static HealthTarget resolvedHit(DamageResolution resolution) {
        // Damage 未预选命中时由 Framework 选点；蔓延源就是命中部位，否则首个扣除目标即直接命中。
        if (resolution.hasSpillover()) {
            return resolution.getSpillover().getSource();
        }
        if (resolution.getLossesCount() == 0) {
            throw new IllegalArgumentException(DummyErrors.HIT_PART);
        }
        return resolution.getLosses(0).getTarget();
    }

    private static List<Injury> injuries(HitFacts facts, String hitPartId, Ledger parts) {
        // 组织与器官取本次实际扣除；蔓延部位按回执里的蔓延目标单独列出。
        BodySnapshot before = facts.body().before();
        BodySnapshot after = facts.body().after();
        BodyStructure structure = after.getStructure();
        Set<String> spilled = spilloverParts(facts.resolution());
        Ledger tissues = new Ledger(tissueHp(before), tissueHp(after));
        Ledger organs = new Ledger(organHp(before), organHp(after));
        List<Injury> found = new ArrayList<>();
        for (TissueDefinition tissue : structure.getTissuesList()) {
            Origin origin = origin(tissue.getPartId(), hitPartId, spilled);
            Entry entry = new Entry(tissue.getDisplayName(), tissue.getId(), InjuryModule.TISSUE, origin);
            addInjury(found, entry, tissues.loss(tissue.getId()));
        }
        for (OrganDefinition organ : structure.getOrgansList()) {
            Origin origin = origin(organ.getPartId(), hitPartId, spilled);
            Entry entry = new Entry(organ.getDisplayName(), organ.getId(), InjuryModule.ORGAN, origin);
            addInjury(found, entry, organs.loss(organ.getId()));
        }
        for (String partId : spilled) {
            PartDefinition spilledPart = part(structure, partId);
            Entry entry = new Entry(spilledPart.getDisplayName(), partId, InjuryModule.PART, Origin.SPILLOVER);
            addInjury(found, entry, parts.loss(partId));
        }
        found.sort(Comparator.comparingDouble(Injury::getHp).reversed());
        return List.copyOf(found.subList(0, Math.min(found.size(), HitReports.MAX_INJURIES)));
    }

    /** 伤势来自命中部位本身，还是命中部位溢出后的蔓延。 */
    private enum Origin { DIRECT, SPILLOVER }

    private record Entry(String displayName, String id, InjuryModule module, Origin origin) { }

    private static Origin origin(String partId, String hitPartId, Set<String> spilled) {
        // 命中部位内的组织总算直接伤；其他部位只有出现在蔓延回执里才标蔓延。
        if (!partId.equals(hitPartId) && spilled.contains(partId)) {
            return Origin.SPILLOVER;
        }
        return Origin.DIRECT;
    }

    private static void addInjury(List<Injury> found, Entry entry, double lost) {
        // 扣除不足列出门槛的模块不进入清单，扣除量与报告其他数值使用同一上限。
        if (!HitAmounts.listed(lost)) {
            return;
        }
        Injury.Builder injury = Injury.newBuilder();
        injury.setName(name(entry.displayName(), entry.id()));
        injury.setHp(bounded(lost));
        injury.setModule(entry.module());
        injury.setSpillover(entry.origin() == Origin.SPILLOVER);
        found.add(injury.build());
    }

    private static List<PartChange> partChanges(BodyStructure structure, Ledger parts) {
        // 按构型顺序列出可用与失能之间的切换。
        List<PartChange> changes = new ArrayList<>();
        for (PartDefinition part : structure.getPartsList()) {
            if (changes.size() == HitReports.MAX_PART_CHANGES) {
                break;
            }
            PartCondition before = condition(parts.before(part.getId()));
            PartCondition after = condition(parts.after(part.getId()));
            if (before == after) {
                continue;
            }
            PartChange.Builder change = PartChange.newBuilder();
            change.setPartName(name(part.getDisplayName(), part.getId()));
            change.setBefore(before);
            change.setAfter(after);
            changes.add(change.build());
        }
        return List.copyOf(changes);
    }

    private static PartCondition condition(double hp) {
        if (hp > 0) {
            return PartCondition.USABLE;
        }
        return PartCondition.DISABLED;
    }

    private static Set<String> spilloverParts(DamageResolution resolution) {
        // 蔓延回执只指向部位；保持回执顺序，便于同量条目稳定排序。
        Set<String> parts = new LinkedHashSet<>();
        if (!resolution.hasSpillover()) {
            return parts;
        }
        for (HpLoss loss : resolution.getSpillover().getLossesList()) {
            if (loss.getTarget().hasPartId()) {
                parts.add(loss.getTarget().getPartId());
            }
        }
        return parts;
    }

    static String partOf(BodyStructure structure, HealthTarget target) {
        // 命中组织或器官时取其所属部位；找不到定义说明事件与构型不一致。
        String partId = switch (target.getModuleCase()) {
            case PART_ID -> target.getPartId();
            case TISSUE_ID -> structure.getTissuesList().stream()
                    .filter(definition -> definition.getId().equals(target.getTissueId()))
                    .map(TissueDefinition::getPartId)
                    .findFirst().orElse("");
            case ORGAN_ID -> structure.getOrgansList().stream()
                    .filter(definition -> definition.getId().equals(target.getOrganId()))
                    .map(OrganDefinition::getPartId)
                    .findFirst().orElse("");
            case MODULE_NOT_SET -> "";
        };
        if (partId.isEmpty()) {
            throw new IllegalArgumentException(DummyErrors.HIT_PART);
        }
        return partId;
    }

    private static PartDefinition part(BodyStructure structure, String partId) {
        return structure.getPartsList().stream()
                .filter(definition -> definition.getId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(DummyErrors.HIT_PART));
    }

    private static DamageKind kind(HealthMessages.DamageKind kind) {
        // 两个枚举同名同序，按名称对应；未知机制交给报告校验拒绝。
        String name = kind.name();
        return Arrays.stream(DamageKind.values())
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElse(DamageKind.DAMAGE_KIND_UNSPECIFIED);
    }

    static String name(String displayName, String id) {
        // 构型没有显示名时退回 ID，超长名称截断到协议上限。
        String chosen = displayName;
        if (chosen.isBlank()) {
            chosen = id;
        }
        if (chosen.length() <= HitReports.MAX_NAME_LENGTH) {
            return chosen;
        }
        return chosen.substring(0, HitReports.MAX_NAME_LENGTH);
    }

    static double bounded(double value) {
        // 极端原版剂量按展示上限截断，非法值留给校验拒绝。
        return Math.min(value, HitReports.MAX_VALUE);
    }

    private static Map<String, Double> partHp(BodySnapshot body) {
        Map<String, Double> values = new HashMap<>();
        for (PartState state : body.getPartStatesList()) {
            values.put(state.getId(), state.getHp());
        }
        return values;
    }

    private static Map<String, Double> tissueHp(BodySnapshot body) {
        Map<String, Double> values = new HashMap<>();
        for (TissueState state : body.getTissueStatesList()) {
            values.put(state.getId(), state.getHp());
        }
        return values;
    }

    private static Map<String, Double> organHp(BodySnapshot body) {
        Map<String, Double> values = new HashMap<>();
        for (OrganState state : body.getOrganStatesList()) {
            values.put(state.getId(), state.getHp());
        }
        return values;
    }

    /** 同一类模块提交前后的 HP。 */
    private record Ledger(Map<String, Double> beforeHp, Map<String, Double> afterHp) {
        double before(String id) {
            return beforeHp.getOrDefault(id, ABSENT_HP);
        }

        double after(String id) {
            return afterHp.getOrDefault(id, ABSENT_HP);
        }

        double loss(String id) {
            return Math.max(0, before(id) - after(id));
        }
    }
}
