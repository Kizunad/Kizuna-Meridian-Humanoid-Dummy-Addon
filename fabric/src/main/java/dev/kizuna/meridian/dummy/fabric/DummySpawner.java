package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidSpeciesBirths;
import dev.kizuna.meridian.humanoid.server.HumanoidSpecies;
import dev.kizuna.meridian.humanoid.server.HumanoidSpeciesDefinition;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** 物品、命令与测试共用的放置入口：先按指定档位出生身体，再把实体加入世界。 */
public final class DummySpawner {
    private static final float LEVEL_PITCH = 0.0F;

    private DummySpawner() {
        // 纯静态入口。
    }

    public static DummyEntity place(ServerLevel world, Vec3 feet, float yaw, HumanoidTier tier) {
        // 身体在入世界前绑定，Threatening 的惰性出生不会再按自然概率抽一具。
        DummyEntity dummy = DummyContent.entityType().create(world);
        if (dummy == null) {
            throw new IllegalStateException("训练假人实体无法创建");
        }
        dummy.moveTo(feet.x, feet.y, feet.z, yaw, LEVEL_PITCH);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        HumanoidSpeciesBirths.birth(dummy, tier);
        if (!world.addFreshEntity(dummy)) {
            throw new IllegalStateException("训练假人无法加入世界");
        }
        return dummy;
    }

    public static HumanoidTier defaultTier() {
        // 默认档位只来自物种表；物种未注册说明入口装配失败。
        return HumanoidSpecies.fromEntityId(DummyContent.ENTITY_ID)
                .flatMap(HumanoidSpecies::definition)
                .map(HumanoidSpeciesDefinition::defaultTier)
                .orElseThrow(() -> new IllegalStateException("训练假人物种未注册"));
    }
}
