package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyEntity;
import dev.kizuna.meridian.dummy.fabric.DummySpawner;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private static final int BLUE_SHIFT = 16;
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

    private enum Stage { WAITING, STRIKING, SHOWN, HOLDING, DONE }

    @Override
    public void onInitializeClient() {
        // 截图在客户端刻末尾读取主帧缓冲，此时其中是上一帧完整画面（含 HUD）。
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        ticks++;
        if (stage == Stage.DONE) {
            return;
        }
        if (ticks > TIMEOUT_TICKS) {
            finish("超时，停在 " + stage);
            return;
        }
        if (ticks < READY_TICKS || client.player == null || client.getOverlay() != null || client.getServer() == null) {
            return;
        }
        // 新世界教程提示同样画在右上角，每刻清掉，避免混入像素比较。
        client.getToastManager().clear();
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

    private void start(MinecraftClient client) throws Exception {
        // 读取正式入口创建的 HUD；挥剑在服务端线程执行，与玩家攻击走同一条伤害链。
        var entry = FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).stream()
                .filter(DummyClient.class::isInstance)
                .findFirst()
                .orElseThrow();
        var field = DummyClient.class.getDeclaredField("hud");
        field.setAccessible(true /* accessible */);
        hud = (DummyHud) field.get(entry);
        client.options.hudHidden = false;
        client.options.pauseOnLostFocus = false;
        client.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        client.setScreen(null);
        Files.createDirectories(SCREENS);
        var server = client.getServer();
        UUID playerId = client.player.getUuid();
        strike = server.submit(() -> strike(server.getPlayerManager().getPlayer(playerId)));
        stage = Stage.STRIKING;
    }

    private static void strike(ServerPlayerEntity player) {
        // 假人放在视线正前方、面朝玩家，截图里能同时看到假人与右上角报告。
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Vec3d ahead = Vec3d.fromPolar(LEVEL_PITCH, player.getYaw()).multiply(STRIKE_DISTANCE);
        float facing = MathHelper.wrapDegrees(player.getYaw() + HALF_TURN);
        DummyEntity dummy = DummySpawner.place(player.getServerWorld(), player.getPos().add(ahead), facing,
                HumanoidTier.WANDERING_QI);
        player.attack(dummy);
    }

    private void awaitReport(MinecraftClient client) throws Exception {
        if (!strike.isDone()) {
            return;
        }
        strike.join();
        long now = Util.getMeasuringTimeMs();
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
        shownImage = capture("hud-visible.png");
        stage = Stage.SHOWN;
    }

    private void requireChineseText(MinecraftClient client, long now) throws Exception {
        // 文本另存一份供交接核对；英文模板词、零值扣减或缺少中文标签都算失败。
        String language = client.getLanguageManager().getLanguage();
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
        long now = Util.getMeasuringTimeMs();
        if (now - shownMillis < STILL_VISIBLE_MILLIS) {
            return;
        }
        int lines = hud.lineCount(now);
        if (lines < MIN_LINES) {
            throw new IllegalStateException("命中后 3 秒 HUD 只剩 " + lines + " 行");
        }
        stage = Stage.HOLDING;
    }

    private void awaitGone(MinecraftClient client) throws Exception {
        long now = Util.getMeasuringTimeMs();
        if (now - shownMillis < DummyHud.VISIBLE_MILLIS + GONE_MARGIN_MILLIS) {
            return;
        }
        int lines = hud.lineCount(now);
        if (lines != 0) {
            throw new IllegalStateException("命中 4 秒后 HUD 仍有 " + lines + " 行");
        }
        try (NativeImage hidden = capture("hud-hidden.png")) {
            double difference = probeDifference(shownImage, hidden, client.getWindow().getScaleFactor());
            if (difference < MIN_MEAN_DIFFERENCE) {
                throw new IllegalStateException("右上角像素在显示与消失前后几乎相同：" + difference);
            }
        }
        finish(null);
    }

    private static NativeImage capture(String name) throws Exception {
        NativeImage image = ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer());
        image.writeTo(SCREENS.resolve(name));
        return image;
    }

    private static double probeDifference(NativeImage shown, NativeImage hidden, double scale) {
        // 逐像素比较右上角面板内部的平均亮度差；昼夜天空下面板与文字都会让差值明显。
        int size = (int) Math.round(PROBE_SIZE * scale);
        int inset = (int) Math.round(PROBE_INSET * scale);
        int right = shown.getWidth() - inset;
        double total = 0;
        for (int y = inset; y < inset + size; y++) {
            for (int x = right - size; x < right; x++) {
                total += Math.abs(luminance(shown.getColor(x, y)) - luminance(hidden.getColor(x, y)));
            }
        }
        return total / (size * size);
    }

    private static double luminance(int abgr) {
        int red = abgr & CHANNEL_MASK;
        int green = abgr >> GREEN_SHIFT & CHANNEL_MASK;
        int blue = abgr >> BLUE_SHIFT & CHANNEL_MASK;
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
        MinecraftClient.getInstance().scheduleStop();
    }
}
