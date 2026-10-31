package dev.kizuna.meridian.dummy.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 右键地面放置训练假人，默认档位取物种表的 default_tier，面朝放置者。 */
final class DummyItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private static final float HALF_TURN_DEGREES = 180.0F;
    private static final float PLACE_VOLUME = 0.75F;
    private static final float PLACE_PITCH = 0.8F;

    DummyItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 落点沿用方块放置规则：可替换方块就地放，否则放到点击面外侧；空间被占时不放。
        Level world = context.getLevel();
        if (!(world instanceof ServerLevel serverWorld)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos position = new BlockPlaceContext(context).getClickedPos();
        Vec3 feet = Vec3.atBottomCenterOf(position);
        AABB space = DummyContent.entityType().getDimensions().makeBoundingBox(feet);
        if (!world.noCollision(null, space) || !world.getEntities(null, space).isEmpty()) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = Mth.wrapDegrees(context.getRotation() + HALF_TURN_DEGREES);
        try {
            DummySpawner.place(serverWorld, feet, yaw, DummySpawner.defaultTier());
        } catch (RuntimeException failure) {
            LOGGER.error("训练假人放置失败 pos={}", position, failure);
            if (player != null) {
                player.displayClientMessage(Component.literal("训练假人放置失败：" + failure.getMessage()), true /* overlay */);
            }
            return InteractionResult.FAIL;
        }
        world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS,
                PLACE_VOLUME, PLACE_PITCH);
        world.gameEvent(player, GameEvent.ENTITY_PLACE, position);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
