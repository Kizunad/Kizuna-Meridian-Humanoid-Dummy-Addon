package dev.kizuna.meridian.dummy.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.kizuna.meridian.dummy.server.DummyRealm;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** /meridian_humanoid_dummy spawn | clear | reset | realm；权限 2，全部节点可 Tab 补全。 */
final class DummyCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private static final int PERMISSION_LEVEL = 2;
    private static final int DEFAULT_RADIUS = 16;
    private static final int MAX_RADIUS = 256;
    private static final float HALF_TURN_DEGREES = 180.0F;
    private static final String REALM = "realm";
    private static final String RADIUS = "radius";
    private static final String TARGETS = "targets";
    private static final DynamicCommandExceptionType UNKNOWN_REALM =
            new DynamicCommandExceptionType(value -> Component.literal("未知档位：" + value));
    private static final SimpleCommandExceptionType NO_DUMMY =
            new SimpleCommandExceptionType(Component.literal("目标中没有训练假人"));

    private DummyCommands() {
        // 只组装 Brigadier 节点。
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(DummyFabric.MOD_ID);
        root.requires(source -> source.hasPermission(PERMISSION_LEVEL));
        root.then(spawnNode());
        root.then(clearNode());
        root.then(resetNode());
        root.then(realmNode());
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnNode() {
        // 不带档位时使用物种表默认档位。
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        spawn.executes(context -> spawn(context.getSource(), DummySpawner.defaultTier()));
        RequiredArgumentBuilder<CommandSourceStack, String> realm = realmArgument();
        realm.executes(context -> spawn(context.getSource(), realm(context).tier()));
        spawn.then(realm);
        return spawn;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearNode() {
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear");
        clear.executes(context -> clear(context.getSource(), DEFAULT_RADIUS));
        RequiredArgumentBuilder<CommandSourceStack, Integer> radius =
                Commands.argument(RADIUS, IntegerArgumentType.integer(1, MAX_RADIUS));
        radius.suggests((context, builder) -> SharedSuggestionProvider.suggest(
                List.of(String.valueOf(DEFAULT_RADIUS)), builder));
        radius.executes(context -> clear(context.getSource(), IntegerArgumentType.getInteger(context, RADIUS)));
        clear.then(radius);
        return clear;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetNode() {
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
        RequiredArgumentBuilder<CommandSourceStack, ?> targets =
                Commands.argument(TARGETS, EntityArgument.entities());
        targets.executes(context -> reset(context.getSource(), EntityArgument.getEntities(context, TARGETS)));
        reset.then(targets);
        return reset;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> realmNode() {
        LiteralArgumentBuilder<CommandSourceStack> realmCommand = Commands.literal(REALM);
        RequiredArgumentBuilder<CommandSourceStack, ?> targets =
                Commands.argument(TARGETS, EntityArgument.entities());
        RequiredArgumentBuilder<CommandSourceStack, String> realm = realmArgument();
        realm.executes(context -> changeRealm(context.getSource(),
                EntityArgument.getEntities(context, TARGETS), realm(context)));
        targets.then(realm);
        realmCommand.then(targets);
        return realmCommand;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> realmArgument() {
        RequiredArgumentBuilder<CommandSourceStack, String> realm =
                Commands.argument(REALM, StringArgumentType.word());
        List<String> ids = Arrays.stream(DummyRealm.values()).map(DummyRealm::id).toList();
        realm.suggests((context, builder) -> SharedSuggestionProvider.suggest(ids, builder));
        return realm;
    }

    private static DummyRealm realm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, REALM);
        Optional<DummyRealm> realm = DummyRealm.find(id);
        if (realm.isEmpty()) {
            throw UNKNOWN_REALM.create(id);
        }
        return realm.get();
    }

    private static int spawn(CommandSourceStack source, HumanoidTier tier) {
        // 在命令源位置生成，面朝命令源。
        ServerLevel world = source.getLevel();
        Vec3 feet = source.getPosition();
        if (!world.isInWorldBounds(BlockPos.containing(feet))) {
            source.sendFailure(Component.literal("生成位置超出世界边界"));
            return 0;
        }
        float yaw = Mth.wrapDegrees(source.getRotation().y + HALF_TURN_DEGREES);
        DummyEntity dummy;
        try {
            dummy = DummySpawner.place(world, feet, yaw, tier);
        } catch (RuntimeException failure) {
            LOGGER.error("训练假人生成失败", failure);
            source.sendFailure(Component.literal("训练假人生成失败：" + failure.getMessage()));
            return 0;
        }
        String label = DummyRealm.of(tier).label();
        Component feedback = Component.literal("已生成训练假人（" + label + "）：" + dummy.getStringUUID());
        source.sendSuccess(() -> feedback, false /* broadcastToOps */);
        return 1;
    }

    private static int clear(CommandSourceStack source, int radius) {
        // 按球形半径移除，移除即丢弃身体；身体里的炁由 Threatening 的移除流程释放。
        Vec3 center = source.getPosition();
        double squaredRadius = (double) radius * radius;
        AABB area = new AABB(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        List<DummyEntity> found = source.getLevel().getEntitiesOfClass(DummyEntity.class, area,
                dummy -> dummy.distanceToSqr(center) <= squaredRadius);
        for (DummyEntity dummy : found) {
            dummy.discard();
        }
        int count = found.size();
        source.sendSuccess(() -> Component.literal("已清除 " + count + " 个训练假人"), false /* broadcastToOps */);
        return count;
    }

    private static int reset(CommandSourceStack source, Collection<? extends Entity> targets)
            throws CommandSyntaxException {
        // 每个假人独立提交 Framework 健康事务；一个失败不影响已完成的其他假人。
        List<DummyEntity> dummies = dummies(targets);
        int done = 0;
        for (DummyEntity dummy : dummies) {
            try {
                DummyBodyAccess.reset(dummy);
                done++;
            } catch (RuntimeException failure) {
                LOGGER.error("训练假人重置失败 subject={}", dummy.getStringUUID(), failure);
                source.sendFailure(Component.literal("训练假人重置失败：" + failure.getMessage()));
            }
        }
        int count = done;
        source.sendSuccess(() -> Component.literal("已重置 " + count + " 个训练假人"), false /* broadcastToOps */);
        return count;
    }

    private static int changeRealm(CommandSourceStack source, Collection<? extends Entity> targets,
                                   DummyRealm realm) throws CommandSyntaxException {
        // 档位属于出生事实，不能改写已有身体；在原位置按新档位重新出生后再移除旧假人。
        List<DummyEntity> dummies = dummies(targets);
        int done = 0;
        for (DummyEntity dummy : dummies) {
            try {
                ServerLevel world = (ServerLevel) dummy.level();
                DummyEntity replacement = DummySpawner.place(world, dummy.position(), dummy.getYRot(), realm.tier());
                replacement.setCustomName(dummy.getCustomName());
                dummy.discard();
                done++;
            } catch (RuntimeException failure) {
                LOGGER.error("训练假人换档失败 subject={}", dummy.getStringUUID(), failure);
                source.sendFailure(Component.literal("训练假人换档失败：" + failure.getMessage()));
            }
        }
        int count = done;
        Component feedback = Component.literal("已将 " + count + " 个训练假人换为" + realm.label());
        source.sendSuccess(() -> feedback, false /* broadcastToOps */);
        return count;
    }

    private static List<DummyEntity> dummies(Collection<? extends Entity> targets) throws CommandSyntaxException {
        // 选择器可以混入其他实体，只处理假人；一个假人都没有时报错。
        List<DummyEntity> dummies = new ArrayList<>();
        for (Entity target : targets) {
            if (target instanceof DummyEntity dummy) {
                dummies.add(dummy);
            }
        }
        if (dummies.isEmpty()) {
            throw NO_DUMMY.create();
        }
        return dummies;
    }
}
