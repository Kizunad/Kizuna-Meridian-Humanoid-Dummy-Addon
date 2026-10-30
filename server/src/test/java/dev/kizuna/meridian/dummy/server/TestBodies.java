package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodyStructure;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueState;

/** 两个部位、三个组织、一个器官的最小构型，数值便于手算。 */
final class TestBodies {
    static final String ARM = "left_upper_arm";
    static final String CHEST = "chest";
    static final String DELTOID = "left_deltoid";
    static final String HUMERUS = "left_humerus";
    static final String SKIN = "chest_skin";
    static final String HEART = "heart";
    static final double ARM_MAX = 15;
    static final double CHEST_MAX = 40;
    static final double DELTOID_MAX = 10;
    static final double HUMERUS_MAX = 8;
    static final double SKIN_MAX = 5;
    static final double HEART_MAX = 10;

    private TestBodies() {
    }

    static BodyStructure structure() {
        BodyStructure.Builder structure = BodyStructure.newBuilder();
        structure.addParts(part(ARM, "左上臂", ARM_MAX));
        structure.addParts(part(CHEST, "胸部", CHEST_MAX));
        structure.addTissues(tissue(DELTOID, "左三角肌", ARM, DELTOID_MAX));
        structure.addTissues(tissue(HUMERUS, "左肱骨", ARM, HUMERUS_MAX));
        structure.addTissues(tissue(SKIN, "胸部皮肤", CHEST, SKIN_MAX));
        OrganDefinition.Builder heart = OrganDefinition.newBuilder();
        heart.setId(HEART);
        heart.setDisplayName("心脏");
        heart.setPartId(CHEST);
        heart.setMaxHp(HEART_MAX);
        structure.addOrgans(heart);
        return structure.build();
    }

    static BodySnapshot full() {
        return body(new Hp(ARM_MAX, CHEST_MAX), new Hp(DELTOID_MAX, HUMERUS_MAX), SKIN_MAX);
    }

    /** 部位 HP 与左臂两个组织的 HP；胸部皮肤单独给出。 */
    record Hp(double first, double second) { }

    static BodySnapshot body(Hp parts, Hp armTissues, double skin) {
        BodySnapshot.Builder body = BodySnapshot.newBuilder();
        body.setId("dummy-body");
        body.setStructure(structure());
        body.addPartStates(partState(ARM, parts.first()));
        body.addPartStates(partState(CHEST, parts.second()));
        body.addTissueStates(tissueState(DELTOID, armTissues.first()));
        body.addTissueStates(tissueState(HUMERUS, armTissues.second()));
        body.addTissueStates(tissueState(SKIN, skin));
        OrganState.Builder heart = OrganState.newBuilder();
        heart.setId(HEART);
        heart.setHp(HEART_MAX);
        body.addOrganStates(heart);
        return body.build();
    }

    private static PartDefinition part(String id, String name, double maximum) {
        PartDefinition.Builder part = PartDefinition.newBuilder();
        part.setId(id);
        part.setDisplayName(name);
        part.setMaxHp(maximum);
        return part.build();
    }

    private static TissueDefinition tissue(String id, String name, String partId, double maximum) {
        TissueDefinition.Builder tissue = TissueDefinition.newBuilder();
        tissue.setId(id);
        tissue.setDisplayName(name);
        tissue.setPartId(partId);
        tissue.setMaxHp(maximum);
        return tissue.build();
    }

    private static PartState partState(String id, double hp) {
        PartState.Builder state = PartState.newBuilder();
        state.setId(id);
        state.setHp(hp);
        return state.build();
    }

    private static TissueState tissueState(String id, double hp) {
        TissueState.Builder state = TissueState.newBuilder();
        state.setId(id);
        state.setHp(hp);
        return state.build();
    }
}
