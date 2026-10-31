package dev.kizuna.meridian.dummy.fabric.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kizuna.meridian.dummy.fabric.DummyEntity;
import dev.kizuna.meridian.dummy.fabric.DummySpawner;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 单人世界里由服务端线程持剑击中假人，经正式网络包驱动 HUD：
 * 命中后至少 3 行、3 秒时仍在、4 秒后消失，并比较两张截图右上角的实际像素。
 * 客户端以简体中文启动（见 runClientTest），HUD 文本必须全为中文且没有零值扣减。
 */
public final class DummyHudClientTest implements ClientModInitializer {
    private static final int READY_TICKS = 60;
    private static final int TIMEOUT_TICKS = 600;
    private static final int SETTLE_TICKS = 5;
    private static final int MIN_LINES = 3;
    private static final double STRIKE_DISTANCE = 2.5;
    private static final float HALF_TURN = 180.0F;
    private static final float LEVEL_PITCH = 0.0F;
    private static final long STILL_VISIBLE_MILLIS = 3000;
    private static final long GONE_MARGIN_MILLIS = 250;
    /** 探测区在 GUI 坐标里位于第一块面板内部：右边距 4 之内、顶边距 4 之下。 */
    private static final int PROBE_INSET = 6;
    private static final int PROBE_SIZE = 24;
    private static final double MIN_MEAN_DIFFERENCE = 10.0;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int GREEN_SHIFT = 8;
    private static final int RED_SHIFT = 16;
    private static final int CHANNELS = 3;
    private static final Path SCREENS = Path.of("../hud-screens");
    private static final Path REPORT = Path.of("../clienttest-results.txt");
    private static final Path HUD_TEXT = SCREENS.resolve("hud-visible.txt");
    private static final String LANGUAGE = "zh_cn";
    private static final List<String> CHINESE_LABELS = List.of("护甲前", "护甲后", "受伤：");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    /** 前后都不是数字或小数点的 0.0 / 0.00：扣减按两档精度显示后不该出现零值。 */
    private static final Pattern ZERO_AMOUNT = Pattern.compile("(?<![\\d.])0\\.00?(?!\\d)");
    private Stage stage = Stage.WAITING;
    private int ticks;
    private int shownTick;
    private long shownMillis;
    private DummyHud hud;
    private CompletableFuture<Void> strike;
    private NativeImage shownImage;

    private enum Stage { WAITING, STRIKING, SHOWN, HOLDING, CAPTURING, DONE }

    @Override
    public void onInitializeClient() {
        // 截图在客户端刻末尾发起主帧缓冲拷贝，此时其中是上一帧完整画面（含 HUD）；
        // 26.2 的截图在显存拷贝完成后才回调，等待回调期间停在 CAPTURING。
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        ticks++;
        if (stage == Stage.DONE) {
            return;
        }
        if (ticks > TIMEOUT_TICKS) {
            finish("超时，停在 " + stage);
            return;
        }
        // 26.2 的加载遮罩与当前界面挂在 client.gui 上。
        if (ticks < READY_TICKS || client.player == null || client.gui.overlay() != null
                || client.getSingleplayerServer() == null) {
            return;
        }
        // 新世界教程提示同样画在右上角，每刻清掉，避免混入像素比较。
        client.gui.toastManager().clear();
        if (stage == Stage.CAPTURING) {
            return;
        }
        try {
            switch (stage) {
                case WAITING -> start(client);
                case STRIKING -> awaitReport(client);
                case SHOWN -> awaitStillVisible();
                case HOLDING -> awaitGone(client);
                default -> throw new IllegalStateException(stage.name());
            }
        } catch (Exception failure) {
            finish(failure.toString());
        }
    }

    private void start(Minecraft client) throws Exception {
        // 读取正式入口创建的 HUD；挥剑在服务端线程执行，与玩家攻击走同一条伤害链。
        var entry = FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).stream()
                .filter(DummyClient.class::isInstance)
                .findFirst()
                .orElseThrow();
        var field = DummyClient.class.getDeclaredField("hud");
        field.setAccessible(true /* accessible */);
        hud = (DummyHud) field.get(entry);
        // 26.2 的 F1 隐藏开关改由 client.gui.hud 维护，界面同样经 client.gui 关闭。
        if (client.gui.hud.isHidden()) {
            client.gui.hud.toggle();
        }
        client.options.pauseOnLostFocus = false;
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.gui.setScreen(null);
        Files.createDirectories(SCREENS);
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        strike = server.submit(() -> strike(server.getPlayerList().getPlayer(playerId)));
        stage = Stage.STRIKING;
    }

    private static void strike(ServerPlayer player) {
        // 假人放在视线正前方、面朝玩家，截图里能同时看到假人与右上角报告。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Vec3 ahead = Vec3.directionFromRotation(LEVEL_PITCH, player.getYRot()).scale(STRIKE_DISTANCE);
        float facing = Mth.wrapDegrees(player.getYRot() + HALF_TURN);
        // 26.2 的服务端玩家直接由 level() 给出所在世界。
        DummyEntity dummy = DummySpawner.place(player.level(), player.position().add(ahead), facing,
                HumanoidTier.WANDERING_QI);
        player.attack(dummy);
    }

    private void awaitReport(Minecraft client) throws Exception {
        if (!strike.isDone()) {
            return;
        }
        strike.join();
        long now = Util.getMillis();
        if (shownTick == 0) {
            if (hud.lineCount(now) >= MIN_LINES) {
                shownTick = ticks;
                shownMillis = now;
            }
            return;
        }
        // 检测到报告后再等几刻，保证已有包含报告的完整帧。
        if (ticks - shownTick < SETTLE_TICKS) {
            return;
        }
        requireChineseText(client, now);
        stage = Stage.CAPTURING;
        capture(client, "hud-visible.png", this::keepShown);
    }

    private void keepShown(NativeImage image) {
        // 显示中的截图留到消失后比较；3 秒与 4 秒的计时仍从检测到报告时算起。
        shownImage = image;
        stage = Stage.SHOWN;
    }

    private void requireChineseText(Minecraft client, long now) throws Exception {
        // 文本另存一份供交接核对；英文模板词、零值扣减或缺少中文标签都算失败。
        String language = client.getLanguageManager().getSelected();
        if (!language.equals(LANGUAGE)) {
            throw new IllegalStateException("客户端语言不是简体中文：" + language);
        }
        List<String> lines = hudText(now);
        Files.write(HUD_TEXT, lines);
        String text = String.join("\n", lines);
        if (LATIN.matcher(text).find() || ZERO_AMOUNT.matcher(text).find()) {
            throw new IllegalStateException("HUD 文本混有英文或零值扣减：" + text);
        }
        for (String label : CHINESE_LABELS) {
            if (!text.contains(label)) {
                throw new IllegalStateException("HUD 文本缺少「" + label + "」：" + text);
            }
        }
    }

    private List<String> hudText(long now) {
        List<String> lines = new ArrayList<>();
        for (DummyHud.HudBlock block : hud.blocks(now)) {
            for (DummyHud.HudLine line : block.lines()) {
                lines.add(line.text().getString());
            }
        }
        return lines;
    }

    private void awaitStillVisible() {
        long now = Util.getMillis();
        if (now - shownMillis < STILL_VISIBLE_MILLIS) {
            return;
        }
        int lines = hud.lineCount(now);
        if (lines < MIN_LINES) {
            throw new IllegalStateException("命中后 3 秒 HUD 只剩 " + lines + " 行");
        }
        stage = Stage.HOLDING;
    }

    private void awaitGone(Minecraft client) {
        // 行数归零后再取消失后的截图，回调里比较右上角像素。
        long now = Util.getMillis();
        if (now - shownMillis < DummyHud.VISIBLE_MILLIS + GONE_MARGIN_MILLIS) {
            return;
        }
        int lines = hud.lineCount(now);
        if (lines != 0) {
            throw new IllegalStateException("命中 4 秒后 HUD 仍有 " + lines + " 行");
        }
        stage = Stage.CAPTURING;
        capture(client, "hud-hidden.png", hidden -> compareAndFinish(client, hidden));
    }

    private void compareAndFinish(Minecraft client, NativeImage captured) {
        // 显示与消失两张图的右上角差值过小，说明 HUD 没有真正画出或没有消失。
        try (NativeImage hidden = captured) {
            double difference = probeDifference(shownImage, hidden, client.getWindow().getGuiScale());
            if (difference < MIN_MEAN_DIFFERENCE) {
                throw new IllegalStateException("右上角像素在显示与消失前后几乎相同：" + difference);
            }
        }
        finish(null);
    }

    private void capture(Minecraft client, String name, Consumer<NativeImage> next) {
        // 拷贝完成后先存盘再交给下一步；回调里的失败同样结束测试并写入报告。
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            try {
                image.writeToFile(SCREENS.resolve(name));
                next.accept(image);
            } catch (Exception failure) {
                image.close();
                finish(failure.toString());
            }
        });
    }

    private static double probeDifference(NativeImage shown, NativeImage hidden, double scale) {
        // 逐像素比较右上角面板内部的平均亮度差；昼夜天空下面板与文字都会让差值明显。
        // 26.2 公开的取像素入口按 ARGB 排列；亮度是三通道平均，与旧版按 ABGR 拆分结果相同。
        int size = (int) Math.round(PROBE_SIZE * scale);
        int inset = (int) Math.round(PROBE_INSET * scale);
        int right = shown.getWidth() - inset;
        double total = 0;
        for (int y = inset; y < inset + size; y++) {
            for (int x = right - size; x < right; x++) {
                total += Math.abs(luminance(shown.getPixel(x, y)) - luminance(hidden.getPixel(x, y)));
            }
        }
        return total / (size * size);
    }

    private static double luminance(int argb) {
        // 按 26.2 的 ARGB 排列拆出红绿蓝三通道。
        int red = argb >> RED_SHIFT & CHANNEL_MASK;
        int green = argb >> GREEN_SHIFT & CHANNEL_MASK;
        int blue = argb & CHANNEL_MASK;
        return (double) (red + green + blue) / CHANNELS;
    }

    private void finish(String failure) {
        // 独立报告让断言与截图失败传递给 Gradle。
        stage = Stage.DONE;
        if (shownImage != null) {
            shownImage.close();
        }
        try {
            Files.writeString(REPORT, "success=" + (failure == null) + "\nerror=" + failure + "\n");
        } catch (Exception reportFailure) {
            throw new IllegalStateException(reportFailure);
        }
        Minecraft.getInstance().stop();
    }
}
