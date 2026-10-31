package dev.kizuna.meridian.dummy.fabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.kizuna.meridian.fabric.MeridianCommands;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** /meridian humanoid realm &lt;targets&gt; &lt;realm&gt;：按新档位原位重新出生训练假人；权限 2，全部节点可补全。 */
final class DummyCommands {
    static final String NODE = "realm";
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private static final String TARGETS = "targets";
    private static final SimpleCommandExceptionType NO_DUMMY =
            new SimpleCommandExceptionType(Component.literal("目标中没有训练假人"));

    private DummyCommands() {
        // 只组装 Brigadier 节点。
    }

    static void register() {
        // 挂到 Framework 注册点的 humanoid 节点下；权限由注册点套在 realm 节点上，子树不再自设。
        MeridianCommands.register(MeridianCommands.Branch.HUMANOID, NODE, MeridianCommands.GAMEMASTER,
                (node, context) -> node.then(targets()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets() {
        // 档位是 literal，词表与 humanoid spawn 相同，自带补全并在解析期拒绝未知档位。
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets =
                Commands.argument(TARGETS, EntityArgument.entities());
        for (HumanoidTier tier : HumanoidTier.values()) {
            LiteralArgumentBuilder<CommandSourceStack> realm = Commands.literal(tier.id());
            realm.executes(context -> changeRealm(context.getSource(),
                    EntityArgument.getEntities(context, TARGETS), tier));
            targets.then(realm);
        }
        return targets;
    }

    private static int changeRealm(CommandSourceStack source, Collection<? extends Entity> targets,
                                   HumanoidTier tier) throws CommandSyntaxException {
        // 档位属于出生事实，不能改写已有身体；在原位置按新档位重新出生后再移除旧假人。
        List<DummyEntity> dummies = dummies(targets);
        int done = 0;
        for (DummyEntity dummy : dummies) {
            try {
                ServerLevel world = (ServerLevel) dummy.level();
                DummyEntity replacement = DummySpawner.place(world, dummy.position(), dummy.getYRot(), tier);
                replacement.setCustomName(dummy.getCustomName());
                dummy.discard();
                done++;
            } catch (RuntimeException failure) {
                LOGGER.error("训练假人换档失败 subject={}", dummy.getStringUUID(), failure);
                source.sendFailure(Component.literal("训练假人换档失败：" + failure.getMessage()));
            }
        }
        int count = done;
        Component feedback = Component.literal("已将 " + count + " 个训练假人换为" + label(tier));
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

    private static String label(HumanoidTier tier) {
        // 反馈沿用原有中文档位名，与 humanoid spawn 的反馈一致。
        return switch (tier) {
            case MORTAL -> "凡人";
            case WANDERING_QI -> "游炁";
            case TRUE_QI -> "真炁";
        };
    }
}
