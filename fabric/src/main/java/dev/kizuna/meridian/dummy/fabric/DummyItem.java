package dev.kizuna.meridian.dummy.fabric;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 右键地面放置训练假人，默认档位取物种表的 default_tier，面朝放置者。 */
final class DummyItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFabric.MOD_ID);
    private static final float HALF_TURN_DEGREES = 180.0F;
    private static final float PLACE_VOLUME = 0.75F;
    private static final float PLACE_PITCH = 0.8F;

    DummyItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        // 落点沿用方块放置规则：可替换方块就地放，否则放到点击面外侧；空间被占时不放。
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }
        BlockPos position = new ItemPlacementContext(context).getBlockPos();
        Vec3d feet = Vec3d.ofBottomCenter(position);
        Box space = DummyContent.entityType().getDimensions().getBoxAt(feet);
        if (!world.isSpaceEmpty(null, space) || !world.getOtherEntities(null, space).isEmpty()) {
            return ActionResult.FAIL;
        }
        PlayerEntity player = context.getPlayer();
        float yaw = MathHelper.wrapDegrees(context.getPlayerYaw() + HALF_TURN_DEGREES);
        try {
            DummySpawner.place(serverWorld, feet, yaw, DummySpawner.defaultTier());
        } catch (RuntimeException failure) {
            LOGGER.error("训练假人放置失败 pos={}", position, failure);
            if (player != null) {
                player.sendMessage(Text.literal("训练假人放置失败：" + failure.getMessage()), true /* overlay */);
            }
            return ActionResult.FAIL;
        }
        world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENTITY_ARMOR_STAND_PLACE, SoundCategory.BLOCKS,
                PLACE_VOLUME, PLACE_PITCH);
        world.emitGameEvent(player, GameEvent.ENTITY_PLACE, position);
        if (player == null || !player.getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }
}
