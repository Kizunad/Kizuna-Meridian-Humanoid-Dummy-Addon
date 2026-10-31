package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidSpawns;
import dev.kizuna.meridian.humanoid.fabric.HumanoidSpeciesBirths;
import dev.kizuna.meridian.humanoid.server.HumanoidSpecies;
import dev.kizuna.meridian.humanoid.server.HumanoidSpeciesDefinition;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** 物品、命令与测试共用的放置入口：先按指定档位出生身体，再把实体加入世界。 */
public final class DummySpawner {
    private static final float LEVEL_PITCH = 0.0F;
    private static final float HALF_TURN_DEGREES = 180.0F;

    private DummySpawner() {
        // 纯静态入口。
    }

    static void registerPlacement() {
        // /meridian humanoid spawn 出生假人时走这里：Threatening 传入命令源水平朝向，假人转半圈面朝命令源。
        HumanoidSpawns.registerPlacement(species(), (world, position, sourceYaw, tier) ->
                place(world, position, Mth.wrapDegrees(sourceYaw + HALF_TURN_DEGREES), tier));
    }

    public static DummyEntity place(ServerLevel world, Vec3 feet, float yaw, HumanoidTier tier) {
        // 身体在入世界前绑定，Threatening 的惰性出生不会再按自然概率抽一具。
        // 26.2 创建实体要给生成原因；它只用于和平难度与特性开关检查，不传给实体，假人在和平难度同样可放。
        DummyEntity dummy = DummyContent.entityType().create(world, EntitySpawnReason.SPAWN_ITEM_USE);
        if (dummy == null) {
            throw new IllegalStateException("训练假人实体无法创建");
        }
        dummy.snapTo(feet.x, feet.y, feet.z, yaw, LEVEL_PITCH);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        HumanoidSpeciesBirths.birth(dummy, tier);
        if (!world.addFreshEntity(dummy)) {
            throw new IllegalStateException("训练假人无法加入世界");
        }
        return dummy;
    }

    public static HumanoidTier defaultTier() {
        // 默认档位只来自物种表。
        return species().definition()
                .map(HumanoidSpeciesDefinition::defaultTier)
                .orElseThrow(() -> new IllegalStateException("训练假人物种未注册"));
    }

    private static HumanoidSpecies species() {
        // 物种在入口注册到 Threatening；查不到说明入口装配失败。
        return HumanoidSpecies.fromEntityId(DummyContent.ENTITY_ID)
                .orElseThrow(() -> new IllegalStateException("训练假人物种未注册"));
    }
}
