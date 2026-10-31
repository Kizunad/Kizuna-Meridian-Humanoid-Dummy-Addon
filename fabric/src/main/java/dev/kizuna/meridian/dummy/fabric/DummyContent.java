package dev.kizuna.meridian.dummy.fabric;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 假人的实体类型、物品与独立创造栏；注册只在模组初始化时执行一次。 */
public final class DummyContent {
    public static final ResourceLocation DUMMY_ID = new ResourceLocation(DummyFabric.MOD_ID, "dummy");
    public static final String ENTITY_ID = DUMMY_ID.toString();
    private static final float WIDTH = 0.6F;
    private static final float HEIGHT = 1.8F;
    private static final int TRACKING_RANGE_CHUNKS = 10;
    private static final int MAX_STACK = 16;
    private static EntityType<DummyEntity> entityType;
    private static Item item;

    private DummyContent() {
        // 只保存注册结果。
    }

    static void register() {
        // 体型与玩家相同，让 Damage 的命中选点和原版碰撞都按成人尺寸计算。
        FabricEntityTypeBuilder<DummyEntity> type = FabricEntityTypeBuilder.create(MobCategory.MISC, DummyEntity::new);
        type.dimensions(EntityDimensions.fixed(WIDTH, HEIGHT));
        type.trackRangeChunks(TRACKING_RANGE_CHUNKS);
        entityType = Registry.register(BuiltInRegistries.ENTITY_TYPE, DUMMY_ID, type.build());
        FabricDefaultAttributeRegistry.register(entityType, Mob.createMobAttributes());
        Item.Properties settings = new Item.Properties();
        settings.stacksTo(MAX_STACK);
        item = Registry.register(BuiltInRegistries.ITEM, DUMMY_ID, new DummyItem(settings));
        CreativeModeTab.Builder group = FabricItemGroup.builder();
        group.title(Component.translatable("itemGroup." + DummyFabric.MOD_ID));
        group.icon(() -> new ItemStack(item));
        group.displayItems((context, entries) -> entries.accept(item));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DUMMY_ID, group.build());
    }

    public static EntityType<DummyEntity> entityType() {
        return entityType;
    }

    public static Item item() {
        return item;
    }
}
