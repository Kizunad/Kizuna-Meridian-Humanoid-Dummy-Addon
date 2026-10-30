package dev.kizuna.meridian.dummy.server;

import com.google.protobuf.UnknownFieldSet;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.DamageKind;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.Injury;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.InjuryModule;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartChange;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartCondition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.HealthMessages;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageResolution;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageSpillover;
import dev.kizuna.meridian.protocol.pb.HealthMessages.HpLoss;
import org.junit.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class HitReportsTest {
    private static final double TOLERANCE = 1e-9;
    private static final double VANILLA_DOSE = 7;
    private static final double AFTER_ARMOR = 5.5;
    private static final double CHEST_AFTER = 37;
    private static final double DELTOID_AFTER = 6;
    private static final double SKIN_AFTER = 4;
    private static final double FAINT_LOSS = 0.004;
    private static final double SLIGHT_LOSS = 0.006;
    private static final String HUMERUS_NAME = "左肱骨";
    private static final int UNKNOWN_FIELD = 99;

    @Test
    public void reportCarriesHitPartKindDoseInjuriesAndDisabledPart() {
        // 左上臂打空：部位失能，组织按扣除量排序，胸部及其皮肤标蔓延，心脏未扣血不列出。
        HitReport report = HitReportBuilder.build(facts(partTarget(TestBodies.ARM)));
        assertEquals("左上臂", report.getPartName());
        assertEquals(0, report.getPartHp(), TOLERANCE);
        assertEquals(TestBodies.ARM_MAX, report.getPartMaxHp(), TOLERANCE);
        assertEquals(TestBodies.ARM_MAX, report.getPartLoss(), TOLERANCE);
        assertEquals(DamageKind.CUTTING, report.getDamageKind());
        assertEquals(VANILLA_DOSE, report.getVanillaDose(), TOLERANCE);
        assertEquals(AFTER_ARMOR, report.getDoseAfterArmor(), TOLERANCE);
        List<Injury> injuries = report.getInjuriesList();
        assertEquals(List.of("左三角肌", "胸部", "胸部皮肤"), injuries.stream().map(Injury::getName).toList());
        assertEquals(TestBodies.DELTOID_MAX - DELTOID_AFTER, injuries.get(0).getHp(), TOLERANCE);
        assertFalse(injuries.get(0).getSpillover());
        assertEquals(InjuryModule.PART, injuries.get(1).getModule());
        assertTrue(injuries.get(1).getSpillover());
        assertTrue(injuries.get(2).getSpillover());
        PartChange change = report.getPartChanges(0);
        assertEquals(1, report.getPartChangesCount());
        assertEquals("左上臂", change.getPartName());
        assertEquals(PartCondition.USABLE, change.getBefore());
        assertEquals(PartCondition.DISABLED, change.getAfter());
    }

    @Test
    public void injuriesBelowListedMinimumAreLeftOut() {
        // 左肱骨只擦掉不足 0.005 时不进清单；刚过门槛的照常列出。
        HealthTarget arm = partTarget(TestBodies.ARM);
        HitReport faint = HitReportBuilder.build(facts(arm, TestBodies.HUMERUS_MAX - FAINT_LOSS));
        assertFalse(injuryNames(faint).contains(HUMERUS_NAME));
        HitReport slight = HitReportBuilder.build(facts(arm, TestBodies.HUMERUS_MAX - SLIGHT_LOSS));
        assertTrue(injuryNames(slight).contains(HUMERUS_NAME));
    }

    @Test
    public void tissueHitIsReportedOnItsOwningPart() {
        HealthTarget.Builder target = HealthTarget.newBuilder();
        target.setTissueId(TestBodies.DELTOID);
        HitReport report = HitReportBuilder.build(facts(target.build()));
        assertEquals("左上臂", report.getPartName());
    }

    @Test
    public void resolvedHitPrefersSpilloverSourceThenFirstLoss() {
        DamageResolution spilled = facts(partTarget(TestBodies.ARM)).resolution();
        assertEquals(partTarget(TestBodies.ARM), HitReportBuilder.resolvedHit(spilled));
        HpLoss.Builder direct = HpLoss.newBuilder();
        direct.setTarget(partTarget(TestBodies.CHEST));
        direct.setHp(1);
        DamageResolution.Builder plain = DamageResolution.newBuilder();
        plain.addLosses(direct);
        assertEquals(partTarget(TestBodies.CHEST), HitReportBuilder.resolvedHit(plain.build()));
        DamageResolution empty = DamageResolution.getDefaultInstance();
        assertThrows(IllegalArgumentException.class, () -> HitReportBuilder.resolvedHit(empty));
    }

    @Test
    public void hitOutsideStructureIsRejected() {
        HealthTarget target = partTarget("tail");
        assertThrows(IllegalArgumentException.class, () -> HitReportBuilder.build(facts(target)));
    }

    @Test
    public void encodedReportRoundTripsThroughBoundedDecode() {
        HitReport report = HitReportBuilder.build(facts(partTarget(TestBodies.ARM)));
        assertEquals(report, HitReports.decode(HitReports.encode(report)));
    }

    @Test
    public void decodeRejectsSizeVersionAndUnknownFields() {
        HitReport report = HitReportBuilder.build(facts(partTarget(TestBodies.ARM)));
        assertThrows(IllegalArgumentException.class, () -> HitReports.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> HitReports.decode(new byte[HitReports.MAX_BYTES + 1]));
        HitReport wrongVersion = report.toBuilder().setProtocolVersion(HitReports.PROTOCOL_VERSION + 1).build();
        assertThrows(IllegalArgumentException.class, () -> HitReports.decode(wrongVersion.toByteArray()));
        UnknownFieldSet.Field field = UnknownFieldSet.Field.newBuilder().addVarint(1).build();
        UnknownFieldSet unknown = UnknownFieldSet.newBuilder().addField(UNKNOWN_FIELD, field).build();
        byte[] extended = report.toBuilder().setUnknownFields(unknown).build().toByteArray();
        assertThrows(IllegalArgumentException.class, () -> HitReports.decode(extended));
    }

    @Test
    public void validationRejectsOutOfRangeValues() {
        HitReport report = HitReportBuilder.build(facts(partTarget(TestBodies.ARM)));
        assertRejected(report, builder -> builder.setPartName(""));
        assertRejected(report, builder -> builder.setPartName("左\n臂"));
        assertRejected(report, builder -> builder.setPartName("名".repeat(HitReports.MAX_NAME_LENGTH + 1)));
        assertRejected(report, builder -> builder.setPartHp(TestBodies.ARM_MAX + 1));
        assertRejected(report, builder -> builder.setPartMaxHp(0));
        assertRejected(report, builder -> builder.setVanillaDose(Double.NaN));
        assertRejected(report, builder -> builder.setDoseAfterArmor(-1));
        assertRejected(report, builder -> builder.setDamageKind(DamageKind.DAMAGE_KIND_UNSPECIFIED));
        PartChange unchanged = report.getPartChanges(0).toBuilder().setAfter(PartCondition.USABLE).build();
        assertRejected(report, builder -> builder.setPartChanges(0, unchanged));
        Injury empty = report.getInjuries(0).toBuilder().setHp(0).build();
        assertRejected(report, builder -> builder.setInjuries(0, empty));
        Injury faint = report.getInjuries(0).toBuilder().setHp(FAINT_LOSS).build();
        assertRejected(report, builder -> builder.setInjuries(0, faint));
        Injury injury = report.getInjuries(0);
        assertRejected(report, builder -> {
            builder.clearInjuries();
            for (int index = 0; index <= HitReports.MAX_INJURIES; index++) {
                builder.addInjuries(injury);
            }
            return builder;
        });
    }

    @Test
    public void builderTruncatesLongNamesAndExtremeDoses() {
        // 构建方保证输出总能通过校验：名称截断，极端剂量按展示上限显示。
        assertEquals(HitReports.MAX_NAME_LENGTH, HitReportBuilder.name("长".repeat(40), "id").length());
        assertEquals("id", HitReportBuilder.name(" ", "id"));
        assertEquals(HitReports.MAX_VALUE, HitReportBuilder.bounded(Float.MAX_VALUE), TOLERANCE);
    }

    private static void assertRejected(HitReport report, UnaryOperator<HitReport.Builder> change) {
        HitReport changed = change.apply(report.toBuilder()).build();
        assertThrows(IllegalArgumentException.class, () -> HitReports.validate(changed));
    }

    private static List<String> injuryNames(HitReport report) {
        return report.getInjuriesList().stream().map(Injury::getName).toList();
    }

    private static HitFacts facts(HealthTarget hit) {
        return facts(hit, TestBodies.HUMERUS_MAX);
    }

    private static HitFacts facts(HealthTarget hit, double humerusAfter) {
        // 左上臂打空、胸部蔓延的固定一击；左肱骨剩余 HP 由调用方给出。
        BodySnapshot before = TestBodies.full();
        var parts = new TestBodies.Hp(0, CHEST_AFTER);
        var armTissues = new TestBodies.Hp(DELTOID_AFTER, humerusAfter);
        BodySnapshot after = TestBodies.body(parts, armTissues, SKIN_AFTER);
        HpLoss.Builder spilled = HpLoss.newBuilder();
        spilled.setTarget(partTarget(TestBodies.CHEST));
        spilled.setHp(TestBodies.CHEST_MAX - CHEST_AFTER);
        DamageSpillover.Builder spillover = DamageSpillover.newBuilder();
        spillover.setSource(partTarget(TestBodies.ARM));
        spillover.addLosses(spilled);
        DamageResolution.Builder resolution = DamageResolution.newBuilder();
        resolution.setSpillover(spillover);
        var dose = new HitFacts.Dose(HealthMessages.DamageKind.CUTTING, VANILLA_DOSE, AFTER_ARMOR);
        return new HitFacts(new HitFacts.BodyChange(before, after), resolution.build(), hit, dose);
    }

    private static HealthTarget partTarget(String partId) {
        HealthTarget.Builder target = HealthTarget.newBuilder();
        target.setPartId(partId);
        return target.build();
    }
}
