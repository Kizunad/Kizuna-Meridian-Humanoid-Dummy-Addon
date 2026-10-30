package dev.kizuna.meridian.dummy.fabric;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 训练假人：不寻路、不攻击、不转头、不被击退、不消失，可以被推动。
 * 身体与伤害全部由 Threatening 与 Damage 承担；这里只保证原版生命值永远不触发死亡。
 */
public final class DummyEntity extends MobEntity {
    /** 原版生命值的下限；身体部位可以全部归零，原版投影仍保持存活。 */
    static final float MINIMUM_HEALTH = Float.MIN_NORMAL;

    public DummyEntity(EntityType<? extends DummyEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    @Override
    public void tick() {
        // 朝向固定为放置时的方向；身体朝向控制和视线都不再改变它。
        super.tick();
        float facing = getYaw();
        setHeadYaw(facing);
        setBodyYaw(facing);
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        // 击退被整体忽略；推挤走的是碰撞路径，不经过这里。
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public void setHealth(float health) {
        // Healing 按部位总 HP 比例投影原版生命值；比例为零时也停在下限，不进入原版死亡。
        super.setHealth(Math.max(health, MINIMUM_HEALTH));
    }

    @Override
    public void kill() {
        // /kill 直接移除假人，不走伤害结算，也不产生死亡事件。
        discard();
    }

    @Override
    protected void tickInVoid() {
        discard();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        // 潜行空手右键收回为物品；身体随实体一起丢弃，下一次放置重新出生。
        if (hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }
        World world = getWorld();
        if (!world.isClient()) {
            player.getInventory().offerOrDrop(new ItemStack(DummyContent.item()));
            discard();
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(DummyContent.item());
    }
}
