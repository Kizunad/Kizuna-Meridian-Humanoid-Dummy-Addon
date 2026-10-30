package dev.kizuna.meridian.dummy.server;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;

import java.util.Arrays;
import java.util.Optional;

/** 命令中的三档名称；身体档位本身由 Threatening 的 HumanoidTier 定义。 */
public enum DummyRealm {
    MORTAL("mortal", "凡人", HumanoidTier.MORTAL),
    WANDERING("wandering", "游炁", HumanoidTier.WANDERING_QI),
    TRUE("true", "真炁", HumanoidTier.TRUE_QI);

    private final String id;
    private final String label;
    private final HumanoidTier tier;

    DummyRealm(String id, String label, HumanoidTier tier) {
        this.id = id;
        this.label = label;
        this.tier = tier;
    }

    public String id() {
        return id;
    }

    /** 命令反馈使用的中文档位名。 */
    public String label() {
        return label;
    }

    public HumanoidTier tier() {
        return tier;
    }

    public static Optional<DummyRealm> find(String id) {
        return Arrays.stream(values()).filter(realm -> realm.id.equals(id)).findFirst();
    }

    public static DummyRealm fromId(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException(DummyErrors.REALM));
    }

    public static DummyRealm of(HumanoidTier tier) {
        return Arrays.stream(values())
                .filter(realm -> realm.tier == tier)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(DummyErrors.REALM));
    }
}
