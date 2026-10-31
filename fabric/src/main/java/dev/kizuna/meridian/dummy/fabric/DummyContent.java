package dev.kizuna.meridian.dummy.fabric;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 假人的实体类型、物品与独立创造栏；注册只在模组初始化时执行一次。 */
public final class DummyContent {
    public static final Identifier DUMMY_ID = Identifier.fromNamespaceAndPath(DummyFabric.MOD_ID, "dummy");
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
        // Fabric 的实体类型构建器在 26.x 并入原版构建器：杂项类别同样可远离玩家生成，更新间隔同为 3 刻；
        // 原版构建器只给可缩放尺寸，假人不是幼体，1.20.1 也没有体型属性，尺寸与固定尺寸相同。
        EntityType.Builder<DummyEntity> type = EntityType.Builder.of(DummyEntity::new, MobCategory.MISC);
        type.sized(WIDTH, HEIGHT);
        type.clientTrackingRange(TRACKING_RANGE_CHUNKS);
        entityType = Registry.register(BuiltInRegistries.ENTITY_TYPE, DUMMY_ID,
                type.build(ResourceKey.create(Registries.ENTITY_TYPE, DUMMY_ID)));
        FabricDefaultAttributeRegistry.register(entityType, Mob.createMobAttributes());
        // 26.2 的物品构造时就要带注册键，语言键由它推出，与 1.20.1 按注册名推出的键相同。
        Item.Properties settings = new Item.Properties();
        settings.stacksTo(MAX_STACK);
        settings.setId(ResourceKey.create(Registries.ITEM, DUMMY_ID));
        item = Registry.register(BuiltInRegistries.ITEM, DUMMY_ID, new DummyItem(settings));
        CreativeModeTab.Builder group = FabricCreativeModeTab.builder();
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
