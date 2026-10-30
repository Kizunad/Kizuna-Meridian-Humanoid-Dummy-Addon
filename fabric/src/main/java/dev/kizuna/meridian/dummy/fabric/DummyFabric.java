package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidSpeciesDefinitions;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 训练假人入口：注册实体与物品，把物种表交给 Threatening，并订阅 Damage 的提交后伤害事实。 */
public final class DummyFabric implements ModInitializer {
    public static final String MOD_ID = "meridian_humanoid_dummy";
    static final String SPECIES_RESOURCE = "/meridian-humanoid-dummy-species.toml";

    @Override
    public void onInitialize() {
        // 物种在任何世界加载前注册，Threatening 的认领、身体存档与健康宿主因此直接覆盖假人。
        DummyContent.register();
        HumanoidSpeciesDefinitions.registerAll(speciesTable());
        DummyBodyAccess.bindHosts();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(DummyCommands.command()));
        VanillaDamageFabric.registerDamageObserver(DummyHitReporter::onDamage);
    }

    private static String speciesTable() {
        // 物种表是 JAR 内固定资源，缺失或不可读都是打包错误。
        try (InputStream input = DummyFabric.class.getResourceAsStream(SPECIES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("缺少训练假人物种表 " + SPECIES_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取训练假人物种表 " + SPECIES_RESOURCE, failure);
        }
    }
}
