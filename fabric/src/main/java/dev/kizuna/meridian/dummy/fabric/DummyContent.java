package dev.kizuna.meridian.dummy.fabric;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 假人的实体类型、物品与独立创造栏；注册只在模组初始化时执行一次。 */
public final class DummyContent {
    public static final Identifier DUMMY_ID = new Identifier(DummyFabric.MOD_ID, "dummy");
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
        FabricEntityTypeBuilder<DummyEntity> type = FabricEntityTypeBuilder.create(SpawnGroup.MISC, DummyEntity::new);
        type.dimensions(EntityDimensions.fixed(WIDTH, HEIGHT));
        type.trackRangeChunks(TRACKING_RANGE_CHUNKS);
        entityType = Registry.register(Registries.ENTITY_TYPE, DUMMY_ID, type.build());
        FabricDefaultAttributeRegistry.register(entityType, MobEntity.createMobAttributes());
        Item.Settings settings = new Item.Settings();
        settings.maxCount(MAX_STACK);
        item = Registry.register(Registries.ITEM, DUMMY_ID, new DummyItem(settings));
        ItemGroup.Builder group = FabricItemGroup.builder();
        group.displayName(Text.translatable("itemGroup." + DummyFabric.MOD_ID));
        group.icon(() -> new ItemStack(item));
        group.entries((context, entries) -> entries.add(item));
        Registry.register(Registries.ITEM_GROUP, DUMMY_ID, group.build());
    }

    public static EntityType<DummyEntity> entityType() {
        return entityType;
    }

    public static Item item() {
        return item;
    }
}
