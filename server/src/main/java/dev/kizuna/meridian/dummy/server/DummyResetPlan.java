package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueState;
import dev.kizuna.meridian.protocol.pb.HealthMessages.FatigueRelief;
import dev.kizuna.meridian.protocol.pb.HealthMessages.HealBody;
import dev.kizuna.meridian.protocol.pb.HealthMessages.Healing;
import dev.kizuna.meridian.protocol.pb.HealthMessages.RestoreFatigue;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把身体的全部伤势与疲劳换成 Framework 健康命令；命令不带身体 ID 与版本，
 * 由提交方逐条绑定当前快照后依次执行。
 */
public final class DummyResetPlan {
    public static final String SOURCE_ID = "meridian_humanoid_dummy:reset";
    /** 快照里缺少状态条目的模块按 0 HP 处理，重置时补满整条上限。 */
    private static final double ABSENT_HP = 0.0;
    static final int MAX_HEALING_TARGETS = 256;
    static final int MAX_FATIGUE_TARGETS = 32;

    private DummyResetPlan() {
        // 纯函数集合。
    }

    public static List<BodyCommand> commands(BodySnapshot body) {
        // 先治疗再恢复疲劳；每条命令的目标数不超过 Framework 上限，超出时分批。
        List<Healing> healing = healing(body);
        List<FatigueRelief> relief = relief(body);
        List<BodyCommand> commands = new ArrayList<>();
        for (List<Healing> batch : batches(healing, MAX_HEALING_TARGETS)) {
            HealBody.Builder heal = HealBody.newBuilder();
            heal.setSourceId(SOURCE_ID);
            heal.addAllDoses(batch);
            BodyCommand.Builder command = BodyCommand.newBuilder();
            command.setHealBody(heal);
            commands.add(command.build());
        }
        for (List<FatigueRelief> batch : batches(relief, MAX_FATIGUE_TARGETS)) {
            RestoreFatigue.Builder restore = RestoreFatigue.newBuilder();
            restore.setSourceId(SOURCE_ID);
            restore.addAllDoses(batch);
            BodyCommand.Builder command = BodyCommand.newBuilder();
            command.setRestoreFatigue(restore);
            commands.add(command.build());
        }
        return List.copyOf(commands);
    }

    private static List<Healing> healing(BodySnapshot body) {
        // 部位、组织、器官各自独立记账；治疗局部不会同时修复所属部位，所以三类都要列出。
        List<Healing> doses = new ArrayList<>();
        Map<String, Double> parts = new HashMap<>();
        for (PartState state : body.getPartStatesList()) {
            parts.put(state.getId(), state.getHp());
        }
        for (PartDefinition part : body.getStructure().getPartsList()) {
            HealthTarget target = HealthTarget.newBuilder().setPartId(part.getId()).build();
            addHealing(doses, target, part.getMaxHp() - parts.getOrDefault(part.getId(), ABSENT_HP));
        }
        Map<String, Double> tissues = new HashMap<>();
        for (TissueState state : body.getTissueStatesList()) {
            tissues.put(state.getId(), state.getHp());
        }
        for (TissueDefinition tissue : body.getStructure().getTissuesList()) {
            HealthTarget target = HealthTarget.newBuilder().setTissueId(tissue.getId()).build();
            addHealing(doses, target, tissue.getMaxHp() - tissues.getOrDefault(tissue.getId(), ABSENT_HP));
        }
        Map<String, Double> organs = new HashMap<>();
        for (OrganState state : body.getOrganStatesList()) {
            organs.put(state.getId(), state.getHp());
        }
        for (OrganDefinition organ : body.getStructure().getOrgansList()) {
            HealthTarget target = HealthTarget.newBuilder().setOrganId(organ.getId()).build();
            addHealing(doses, target, organ.getMaxHp() - organs.getOrDefault(organ.getId(), ABSENT_HP));
        }
        return doses;
    }

    private static void addHealing(List<Healing> doses, HealthTarget target, double missing) {
        // 满血模块不发治疗，Framework 拒绝零剂量。
        if (missing <= 0) {
            return;
        }
        Healing.Builder dose = Healing.newBuilder();
        dose.setTarget(target);
        dose.setHp(missing);
        doses.add(dose.build());
    }

    private static List<FatigueRelief> relief(BodySnapshot body) {
        // 组织与经脉按定义的疲劳单位、器官按焦耳，全部减到 0。
        List<FatigueRelief> doses = new ArrayList<>();
        for (TissueState state : body.getTissueStatesList()) {
            FatigueRelief.Builder dose = FatigueRelief.newBuilder();
            dose.setTissueId(state.getId());
            addRelief(doses, dose, state.getFatigue());
        }
        for (OrganState state : body.getOrganStatesList()) {
            FatigueRelief.Builder dose = FatigueRelief.newBuilder();
            dose.setOrganId(state.getId());
            addRelief(doses, dose, state.getFatigueJoules());
        }
        for (MeridianState state : body.getMeridianStatesList()) {
            FatigueRelief.Builder dose = FatigueRelief.newBuilder();
            dose.setMeridianId(state.getId());
            addRelief(doses, dose, state.getFatigue());
        }
        return doses;
    }

    private static void addRelief(List<FatigueRelief> doses, FatigueRelief.Builder dose, double fatigue) {
        // 没有疲劳的模块不发命令，Framework 拒绝零剂量。
        if (fatigue <= 0) {
            return;
        }
        dose.setAmount(fatigue);
        doses.add(dose.build());
    }

    private static <T> List<List<T>> batches(List<T> values, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            batches.add(List.copyOf(values.subList(start, Math.min(values.size(), start + size))));
        }
        return batches;
    }
}
