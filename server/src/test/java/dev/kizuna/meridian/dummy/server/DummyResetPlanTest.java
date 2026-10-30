package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodyStructure;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueState;
import dev.kizuna.meridian.protocol.pb.HealthMessages.FatigueRelief;
import dev.kizuna.meridian.protocol.pb.HealthMessages.Healing;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class DummyResetPlanTest {
    private static final double TOLERANCE = 1e-9;
    private static final double ARM_AFTER = 3;
    private static final double CHEST_AFTER = 30;
    private static final double DELTOID_AFTER = 2;
    private static final double FATIGUE = 12.5;
    private static final int MANY_PARTS = 300;

    @Test
    public void healsEveryInjuredModuleAndClearsFatigue() {
        // 满血的肱骨与心脏不出现；其余目标的剂量正好补到上限。
        var parts = new TestBodies.Hp(ARM_AFTER, CHEST_AFTER);
        var armTissues = new TestBodies.Hp(DELTOID_AFTER, TestBodies.HUMERUS_MAX);
        BodySnapshot injured = TestBodies.body(parts, armTissues, 0);
        TissueState tired = injured.getTissueStates(1).toBuilder().setFatigue(FATIGUE).build();
        BodySnapshot body = injured.toBuilder().setTissueStates(1, tired).build();
        List<BodyCommand> commands = DummyResetPlan.commands(body);
        assertEquals(2, commands.size());
        List<Healing> doses = commands.get(0).getHealBody().getDosesList();
        assertEquals(DummyResetPlan.SOURCE_ID, commands.get(0).getHealBody().getSourceId());
        assertEquals(4, doses.size());
        assertEquals(TestBodies.ARM_MAX - ARM_AFTER, doses.get(0).getHp(), TOLERANCE);
        assertEquals(TestBodies.CHEST_MAX - CHEST_AFTER, doses.get(1).getHp(), TOLERANCE);
        assertEquals(TestBodies.DELTOID_MAX - DELTOID_AFTER, doses.get(2).getHp(), TOLERANCE);
        assertEquals(TestBodies.SKIN_MAX, doses.get(3).getHp(), TOLERANCE);
        FatigueRelief relief = commands.get(1).getRestoreFatigue().getDoses(0);
        assertEquals(TestBodies.HUMERUS, relief.getTissueId());
        assertEquals(FATIGUE, relief.getAmount(), TOLERANCE);
    }

    @Test
    public void fullBodyNeedsNoCommand() {
        assertTrue(DummyResetPlan.commands(TestBodies.full()).isEmpty());
    }

    @Test
    public void largeStructuresAreSplitAtTheFrameworkTargetLimit() {
        BodyStructure.Builder structure = BodyStructure.newBuilder();
        for (int index = 0; index < MANY_PARTS; index++) {
            PartDefinition.Builder part = PartDefinition.newBuilder();
            part.setId("part_" + index);
            part.setMaxHp(1);
            structure.addParts(part);
        }
        BodySnapshot body = BodySnapshot.newBuilder().setStructure(structure).build();
        List<BodyCommand> commands = DummyResetPlan.commands(body);
        assertEquals(2, commands.size());
        assertEquals(DummyResetPlan.MAX_HEALING_TARGETS, commands.get(0).getHealBody().getDosesCount());
        assertEquals(MANY_PARTS - DummyResetPlan.MAX_HEALING_TARGETS, commands.get(1).getHealBody().getDosesCount());
    }

    @Test
    public void realmNamesMapToHumanoidTiers() {
        assertEquals(HumanoidTier.MORTAL, DummyRealm.fromId("mortal").tier());
        assertEquals(HumanoidTier.WANDERING_QI, DummyRealm.fromId("wandering").tier());
        assertEquals(HumanoidTier.TRUE_QI, DummyRealm.fromId("true").tier());
        assertEquals(DummyRealm.TRUE, DummyRealm.of(HumanoidTier.TRUE_QI));
        assertThrows(IllegalArgumentException.class, () -> DummyRealm.fromId("immortal"));
    }
}
