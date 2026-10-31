package dev.kizuna.meridian.dummy.fabric;

import com.mojang.authlib.GameProfile;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.server.HitAmounts;
import dev.kizuna.meridian.humanoid.fabric.HumanoidBrain;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianOpeningStage;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TissueState;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** 放置与朝向、左臂命中报告、归零不死、重置、潜行收回、范围清除、凡人档与换档的宿主回归。 */
public final class DummyGameTest implements FabricGameTest {
    private static final int FLOOR_Y = 1;
    private static final int STAND_Y = 2;
    private static final int CENTER = 4;
    private static final int STACK_SIZE = 2;
    private static final int PERMISSION_LEVEL = 2;
    private static final float PLACER_YAW = 90.0F;
    private static final float FACING_PLACER_YAW = -90.0F;
    private static final float SOUTH_YAW = 0.0F;
    private static final double TOLERANCE = 1e-6;
    private static final int HIT_ATTEMPTS = 256;
    private static final float HEAVY_DOSE = 1000.0F;
    private static final int HEAVY_HITS = 40;
    private static final double HUMAN_DEATH_RATIO = 0.2;
    private static final long SURVIVE_TICK = 20;
    private static final int CLEAR_RADIUS = 2;
    private static final int NEAR_DUMMIES = 2;
    private static final double REBIRTH_SEARCH = 1.0;
    private static final double ABSENT_HP = 0.0;
    private static final Vec3 NEAR_EAST = new Vec3(1, 0, 0);
    private static final Vec3 NEAR_NORTH = new Vec3(0, 0, -1);
    private static final Vec3 FAR = new Vec3(3, 0, 3);
    private static final String LEFT_UPPER_ARM = "left_upper_arm";
    private static final String LEFT_UPPER_ARM_NAME = "左上臂";
    private static final String COMMAND = DummyFabric.MOD_ID + " ";
    private static final Map<UUID, List<HitReport>> REPORTS = new ConcurrentHashMap<>();
    private static final Map<UUID, VanillaDamageFabric.DamageInput> INPUTS = new ConcurrentHashMap<>();

    static {
        // 事件只能追加订阅；按假人 UUID 分账，并行的测试互不干扰。
        DummyHitEvents.REPORTED.register((attacker, dummy, report) ->
                REPORTS.computeIfAbsent(dummy.getUUID(), id -> new CopyOnWriteArrayList<>()).add(report));
        VanillaDamageFabric.registerDamageObserver(event -> INPUTS.put(event.target().getUUID(), event.input()));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void itemPlacesDefaultTierDummyFacingThePlacer(GameTestHelper context) {
        // 原版 ItemStack 右键入口放置：消耗一份、面朝放置者、物种表默认档位为游炁，类人大脑不接管。
        ServerLevel world = context.getLevel();
        BlockPos floor = context.absolutePos(new BlockPos(CENTER, FLOOR_Y, CENTER));
        world.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        Player player = context.makeMockSurvivalPlayer();
        player.setYRot(PLACER_YAW);
        ItemStack stack = new ItemStack(DummyContent.item(), STACK_SIZE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false /* insideBlock */);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        context.assertTrue(result == InteractionResult.CONSUME, "假人物品放置失败");
        List<DummyEntity> placed = world.getEntitiesOfClass(DummyEntity.class, new AABB(floor.above()), dummy -> true);
        context.assertTrue(placed.size() == 1 && stack.getCount() == STACK_SIZE - 1, "没有恰好放置一个假人并消耗一份");
        DummyEntity dummy = placed.get(0);
        try {
            float facing = Mth.wrapDegrees(dummy.getYRot() - FACING_PLACER_YAW);
            context.assertTrue(Math.abs(facing) < TOLERANCE, "假人没有面朝放置者");
            BodySnapshot body = DummyBodyAccess.inspect(dummy);
            context.assertTrue(HumanoidTier.of(body) == HumanoidTier.WANDERING_QI, "默认档位不是游炁");
            context.assertTrue(partNames(body).containsKey(LEFT_UPPER_ARM), "假人身体不是 Human 成人构型");
            context.assertTrue(!HumanoidBrain.managed(dummy), "类人大脑接管了训练假人");
        } finally {
            dummy.discard();
        }
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeGroupHoldsOnlyTheDummy(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var display = new CreativeModeTab.ItemDisplayParameters(world.enabledFeatures(),
                true /* hasPermissions */, world.registryAccess());
        CreativeModeTab group = BuiltInRegistries.CREATIVE_MODE_TAB.get(DummyContent.DUMMY_ID);
        group.buildContents(display);
        List<ItemStack> stacks = new ArrayList<>(group.getDisplayItems());
        context.assertTrue(stacks.size() == 1 && stacks.get(0).is(DummyContent.item()), "假人创造栏内容错误");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void swordHitOnLeftArmSendsOneMatchingReport(GameTestHelper context) {
        // 命中部位由 Damage 按目标随机流抽取；逐个种子尝试，直到剑击落在左上臂。
        // 相邻小种子的首个随机数几乎相同，先经原版种子混洗再设入，否则每次都抽到同一部位。
        DummyEntity dummy = place(context, HumanoidTier.WANDERING_QI);
        ServerPlayer attacker = attacker(context);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        try {
            for (int seed = 0; seed < HIT_ATTEMPTS; seed++) {
                DummyBodyAccess.reset(dummy);
                int before = reports(dummy).size();
                INPUTS.remove(dummy.getUUID());
                dummy.getRandom().setSeed(RandomSupport.mixStafford13(seed));
                dummy.invulnerableTime = 0;
                attacker.attack(dummy);
                VanillaDamageFabric.DamageInput input = INPUTS.get(dummy.getUUID());
                if (input == null || input.hit().isEmpty() || !input.hit().get().getPartId().equals(LEFT_UPPER_ARM)) {
                    continue;
                }
                List<HitReport> reports = reports(dummy);
                context.assertTrue(reports.size() == before + 1, "左臂一击没有恰好产生一条报告");
                assertLeftArmReport(context, reports.get(reports.size() - 1), input);
                context.succeed();
                return;
            }
        } finally {
            dummy.discard();
        }
        throw new AssertionError(HIT_ATTEMPTS + " 次剑击都没有落在左上臂");
    }

    private static void assertLeftArmReport(GameTestHelper context, HitReport report,
                                            VanillaDamageFabric.DamageInput input) {
        context.assertTrue(report.getPartName().equals(LEFT_UPPER_ARM_NAME), "报告部位不是左上臂");
        context.assertTrue(report.getDamageKind() == DummyMessages.DamageKind.CUTTING, "剑击类型不是切割");
        context.assertTrue(Math.abs(report.getVanillaDose() - input.rawDamage()) < TOLERANCE,
                "报告护甲前剂量与 Damage 记录不一致");
        context.assertTrue(Math.abs(report.getDoseAfterArmor() - input.damageAfterEffects()) < TOLERANCE,
                "报告护甲后剂量与 Damage 记录不一致");
        context.assertTrue(report.getPartLoss() > 0 && report.getInjuriesCount() > 0, "报告缺少扣血或受伤组织");
        boolean allListed = report.getInjuriesList().stream().allMatch(injury -> HitAmounts.listed(injury.getHp()));
        context.assertTrue(allListed, "报告列出了不足 0.005 的扣减");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dummyBelowHumanDeathLineStaysAlive(GameTestHelper context) {
        // 连续重击把部位总 HP 打到人体死亡线以下，假人仍在世界里且原版生命值为正。
        DummyEntity dummy = place(context, HumanoidTier.WANDERING_QI);
        DamageSource source = context.getLevel().damageSources().playerAttack(attacker(context));
        for (int hit = 0; hit < HEAVY_HITS; hit++) {
            dummy.invulnerableTime = 0;
            dummy.hurt(source, HEAVY_DOSE);
        }
        BodySnapshot body = DummyBodyAccess.inspect(dummy);
        boolean zeroed = body.getPartStatesList().stream().anyMatch(part -> part.getHp() == 0);
        context.assertTrue(zeroed && partRatio(body) < HUMAN_DEATH_RATIO, "重击后部位没有归零到人体死亡线以下");
        context.runAtTickTime(SURVIVE_TICK, () -> {
            boolean alive = dummy.isAlive() && !dummy.isRemoved() && !dummy.isDeadOrDying() && dummy.getHealth() > 0;
            dummy.discard();
            context.assertTrue(alive, "部位归零后假人死亡或被移除");
            context.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void resetCommandRestoresTheFullBody(GameTestHelper context) {
        DummyEntity dummy = place(context, HumanoidTier.WANDERING_QI);
        try {
            dummy.invulnerableTime = 0;
            dummy.hurt(context.getLevel().damageSources().playerAttack(attacker(context)), HEAVY_DOSE);
            context.assertTrue(!full(DummyBodyAccess.inspect(dummy)), "重击没有改变身体");
            int reset = execute(source(context), "reset " + dummy.getStringUUID());
            context.assertTrue(reset == 1, "reset 命令没有处理这一个假人");
            context.assertTrue(full(DummyBodyAccess.inspect(dummy)), "重置后部位、组织或器官没有回满");
        } finally {
            dummy.discard();
        }
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sneakingEmptyHandPicksTheDummyUp(GameTestHelper context) {
        DummyEntity dummy = place(context, HumanoidTier.WANDERING_QI);
        ServerPlayer player = attacker(context);
        player.setShiftKeyDown(true);
        InteractionResult result = dummy.interact(player, InteractionHand.MAIN_HAND);
        boolean removed = dummy.isRemoved();
        dummy.discard();
        context.assertTrue(result.consumesAction() && removed, "潜行空手右键没有收回假人");
        context.assertTrue(player.getInventory().countItem(DummyContent.item()) == 1, "收回后没有得到一个假人物品");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void clearRemovesOnlyDummiesInsideTheRadius(GameTestHelper context) {
        CommandSourceStack source = source(context);
        ServerLevel world = context.getLevel();
        Vec3 center = source.getPosition();
        HumanoidTier tier = HumanoidTier.WANDERING_QI;
        DummyEntity east = DummySpawner.place(world, center.add(NEAR_EAST), SOUTH_YAW, tier);
        DummyEntity north = DummySpawner.place(world, center.add(NEAR_NORTH), SOUTH_YAW, tier);
        DummyEntity far = DummySpawner.place(world, center.add(FAR), SOUTH_YAW, tier);
        int cleared = execute(source, "clear " + CLEAR_RADIUS);
        boolean farKept = !far.isRemoved();
        far.discard();
        boolean nearRemoved = east.isRemoved() && north.isRemoved();
        context.assertTrue(cleared == NEAR_DUMMIES && nearRemoved, "半径内的假人没有全部清除");
        context.assertTrue(farKept, "半径外的假人被清除");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mortalSpawnHasNoOpenedMeridianAndRealmCommandRebirths(GameTestHelper context) {
        // 凡人档从正式命令生成；换档在原位置按真炁重新出生，旧假人移除。
        ServerLevel world = context.getLevel();
        RecordedOutput output = new RecordedOutput();
        CommandSourceStack source = source(context).withSource(output);
        context.assertTrue(execute(source, "spawn mortal") == 1, "spawn mortal 失败");
        String feedback = output.messages.get(output.messages.size() - 1);
        UUID id = UUID.fromString(feedback.substring(feedback.lastIndexOf('：') + 1));
        DummyEntity mortal = (DummyEntity) world.getEntity(id);
        context.assertTrue(mortal != null, "spawn 反馈的 UUID 找不到假人");
        BodySnapshot body = DummyBodyAccess.inspect(mortal);
        context.assertTrue(HumanoidTier.of(body) == HumanoidTier.MORTAL, "凡人档身体档位错误");
        boolean anyOpened = body.getMeridianStatesList().stream()
                .map(MeridianState::getOpening)
                .anyMatch(opening -> opening.getStage() == MeridianOpeningStage.OPENED);
        context.assertTrue(!anyOpened, "凡人档身体有已开经脉");
        Vec3 position = mortal.position();
        int replacedCount = execute(source, "realm " + id + " true");
        context.assertTrue(replacedCount == 1 && mortal.isRemoved(), "realm 命令没有替换假人");
        List<DummyEntity> replaced = world.getEntitiesOfClass(DummyEntity.class,
                AABB.ofSize(position, REBIRTH_SEARCH, REBIRTH_SEARCH, REBIRTH_SEARCH), dummy -> !dummy.isRemoved());
        context.assertTrue(replaced.size() == 1, "换档后原位置没有恰好一个假人");
        DummyEntity rebirth = replaced.get(0);
        HumanoidTier tier = HumanoidTier.of(DummyBodyAccess.inspect(rebirth));
        rebirth.discard();
        context.assertTrue(tier == HumanoidTier.TRUE_QI, "换档后不是真炁");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void commandsCompleteAndRequirePermissionTwo(GameTestHelper context) {
        CommandSourceStack source = source(context);
        Set<String> verbs = suggestions(source, COMMAND);
        context.assertTrue(verbs.equals(Set.of("spawn", "clear", "reset", "realm")), "子命令补全错误：" + verbs);
        Set<String> realms = suggestions(source, COMMAND + "spawn ");
        context.assertTrue(realms.equals(Set.of("mortal", "wandering", "true")), "档位补全错误：" + realms);
        context.assertTrue(suggestions(source, COMMAND + "clear ").contains("16"), "半径补全缺少 16");
        RecordedOutput output = new RecordedOutput();
        CommandSourceStack lowered = source.withSource(output).withPermission(PERMISSION_LEVEL - 1);
        context.assertTrue(execute(lowered, "spawn") == 0 && !output.messages.isEmpty(), "低权限仍能执行假人命令");
        context.assertTrue(execute(source.withSource(output), "spawn immortal") == 0, "未知档位没有被拒绝");
        context.succeed();
    }

    private static DummyEntity place(GameTestHelper context, HumanoidTier tier) {
        // 先铺地面再放置，存活检查期间假人不会掉出测试区域。
        ServerLevel world = context.getLevel();
        BlockPos floor = context.absolutePos(new BlockPos(CENTER, FLOOR_Y, CENTER));
        world.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        return DummySpawner.place(world, Vec3.atBottomCenterOf(floor.above()), SOUTH_YAW, tier);
    }

    private static ServerPlayer attacker(GameTestHelper context) {
        // 每个测试独立的假玩家，背包与手持物互不影响。
        GameProfile profile = new GameProfile(UUID.randomUUID(), "dummy_tester");
        return FakePlayer.get(context.getLevel(), profile);
    }

    private static CommandSourceStack source(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(CENTER, STAND_Y, CENTER)));
        return world.getServer().createCommandSourceStack().withLevel(world).withPosition(position).withPermission(PERMISSION_LEVEL);
    }

    private static int execute(CommandSourceStack source, String arguments) {
        return source.getServer().getCommands().performPrefixedCommand(source, COMMAND + arguments);
    }

    private static Set<String> suggestions(CommandSourceStack source, String command) {
        var dispatcher = source.getServer().getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source))
                .join()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .collect(Collectors.toSet());
    }

    private static List<HitReport> reports(DummyEntity dummy) {
        return REPORTS.getOrDefault(dummy.getUUID(), List.of());
    }

    private static Map<String, String> partNames(BodySnapshot body) {
        Map<String, String> names = new HashMap<>();
        for (PartDefinition part : body.getStructure().getPartsList()) {
            names.put(part.getId(), part.getDisplayName());
        }
        return names;
    }

    private static double partRatio(BodySnapshot body) {
        double remaining = body.getPartStatesList().stream().mapToDouble(PartState::getHp).sum();
        double maximum = body.getStructure().getPartsList().stream().mapToDouble(PartDefinition::getMaxHp).sum();
        return remaining / maximum;
    }

    private static boolean full(BodySnapshot body) {
        // 部位、组织、器官逐个对照上限。
        Map<String, Double> maxima = new HashMap<>();
        for (PartDefinition part : body.getStructure().getPartsList()) {
            maxima.put("part:" + part.getId(), part.getMaxHp());
        }
        for (TissueDefinition tissue : body.getStructure().getTissuesList()) {
            maxima.put("tissue:" + tissue.getId(), tissue.getMaxHp());
        }
        for (OrganDefinition organ : body.getStructure().getOrgansList()) {
            maxima.put("organ:" + organ.getId(), organ.getMaxHp());
        }
        Map<String, Double> current = new HashMap<>();
        for (PartState state : body.getPartStatesList()) {
            current.put("part:" + state.getId(), state.getHp());
        }
        for (TissueState state : body.getTissueStatesList()) {
            current.put("tissue:" + state.getId(), state.getHp());
        }
        for (OrganState state : body.getOrganStatesList()) {
            current.put("organ:" + state.getId(), state.getHp());
        }
        return maxima.entrySet().stream().allMatch(
                entry -> Math.abs(current.getOrDefault(entry.getKey(), ABSENT_HP) - entry.getValue()) < TOLERANCE);
    }

    private static final class RecordedOutput implements CommandSource {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
