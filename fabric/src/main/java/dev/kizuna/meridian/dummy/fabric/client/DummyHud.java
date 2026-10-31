package dev.kizuna.meridian.dummy.fabric.client;

import dev.kizuna.meridian.dummy.fabric.DummyFabric;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.DamageKind;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.HitReport;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.Injury;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartChange;
import dev.kizuna.meridian.dummy.protocol.pb.DummyMessages.PartCondition;
import dev.kizuna.meridian.dummy.server.HitAmounts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 右上角击打报告：最近 3 次命中自上而下叠放，越旧越淡；最后一次命中 4 秒后整体消失，
 * 消失前 0.5 秒渐隐。每次命中占一块：部位 HP、伤害类型与剂量、受伤组织，以及高亮的部位状态变化。
 * HP 保留一位小数；扣减与剂量按 {@link HitAmounts} 的两档精度显示。
 */
final class DummyHud {
    static final int MAX_ENTRIES = 3;
    static final long VISIBLE_MILLIS = 4000;
    private static final long FADE_MILLIS = 500;
    private static final float[] ENTRY_OPACITY = {1.0F, 0.7F, 0.45F};
    private static final int MAX_INJURIES_SHOWN = 4;
    private static final int MARGIN = 4;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;
    private static final int ENTRY_GAP = 3;
    private static final int TITLE_RGB = 0xE6E6E6;
    private static final int DETAIL_RGB = 0xB4B4B4;
    private static final int HIGHLIGHT_RGB = 0xFFD27A;
    private static final int PANEL_RGB = 0x1C1C1C;
    private static final float PANEL_OPACITY = 0.6F;
    private static final int MAX_ALPHA = 255;
    /** 1.20.1 的 Font 把低于 4 的 alpha 当作不透明，渐隐到底时停在这里；26.2 照样停在这里，观感不变。 */
    private static final int MIN_TEXT_ALPHA = 4;
    private static final int ALPHA_SHIFT = 24;
    private static final String KEY = "hud." + DummyFabric.MOD_ID + ".";
    /** 伤害类型名引用 Damage 的公开键，本模组不另写一份。 */
    private static final String DAMAGE_KIND_KEY = "damage_kind.meridian.";
    private final Deque<HitReport> entries = new ArrayDeque<>();
    private long lastHitMillis;

    void push(HitReport report, long now) {
        // 上一轮已经过期时从空白开始，旧条目不会随新命中重新出现。
        if (blocks(now).isEmpty()) {
            entries.clear();
        }
        entries.addFirst(report);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        lastHitMillis = now;
    }

    void clear() {
        entries.clear();
    }

    List<HudBlock> blocks(long now) {
        long age = now - lastHitMillis;
        if (entries.isEmpty() || age >= VISIBLE_MILLIS) {
            return List.of();
        }
        float fade = fade(age);
        List<HudBlock> blocks = new ArrayList<>();
        int index = 0;
        for (HitReport report : entries) {
            blocks.add(new HudBlock(lines(report), ENTRY_OPACITY[index] * fade));
            index++;
        }
        return blocks;
    }

    int lineCount(long now) {
        return blocks(now).stream().mapToInt(block -> block.lines().size()).sum();
    }

    void render(GuiGraphicsExtractor context, Font textRenderer, long now) {
        // 26.2 的 HUD 层交给提取器登记绘制；各块自上而下排列。
        int top = MARGIN;
        for (HudBlock block : blocks(now)) {
            int bottom = drawBlock(context, textRenderer, block, top);
            top = bottom + ENTRY_GAP;
        }
    }

    private static int drawBlock(GuiGraphicsExtractor context, Font textRenderer, HudBlock block, int top) {
        // 每块右对齐，宽度取本块最长一行；文字与旧版 drawString 一样默认带阴影。
        int textWidth = 0;
        for (HudLine line : block.lines()) {
            textWidth = Math.max(textWidth, textRenderer.width(line.text()));
        }
        int right = context.guiWidth() - MARGIN;
        int left = right - textWidth - PADDING - PADDING;
        int bottom = top + block.lines().size() * LINE_HEIGHT + PADDING + PADDING;
        context.fill(left, top, right, bottom, color(PANEL_RGB, PANEL_OPACITY * block.opacity()));
        int y = top + PADDING;
        for (HudLine line : block.lines()) {
            int argb = color(line.rgb(), block.opacity());
            context.text(textRenderer, line.text(), left + PADDING, y, argb);
            y += LINE_HEIGHT;
        }
        return bottom;
    }

    private static float fade(long age) {
        long remaining = VISIBLE_MILLIS - age;
        if (remaining >= FADE_MILLIS) {
            return 1.0F;
        }
        return (float) remaining / FADE_MILLIS;
    }

    private static List<HudLine> lines(HitReport report) {
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine(part(report), TITLE_RGB));
        String beforeArmor = HitAmounts.format(report.getVanillaDose());
        String afterArmor = HitAmounts.format(report.getDoseAfterArmor());
        Component dose = Component.translatable(KEY + "dose", kind(report.getDamageKind()), beforeArmor, afterArmor);
        lines.add(new HudLine(dose, DETAIL_RGB));
        lines.add(new HudLine(injuries(report.getInjuriesList()), DETAIL_RGB));
        for (PartChange change : report.getPartChangesList()) {
            Component changed = Component.translatable(KEY + "change", change.getPartName(), condition(change.getBefore()),
                    condition(change.getAfter()));
            lines.add(new HudLine(changed, HIGHLIGHT_RGB));
        }
        return lines;
    }

    private static Component part(HitReport report) {
        // 打在已归零的部位上时部位本身不再扣血，只写 HP，不写「−0.00」。
        String hp = number(report.getPartHp());
        String maximum = number(report.getPartMaxHp());
        if (!HitAmounts.listed(report.getPartLoss())) {
            return Component.translatable(KEY + "part.unchanged", report.getPartName(), hp, maximum);
        }
        return Component.translatable(KEY + "part", report.getPartName(), hp, maximum,
                HitAmounts.format(report.getPartLoss()));
    }

    private static Component injuries(List<Injury> injuries) {
        // 报告已按扣除量降序；只显示前几项，其余折叠为计数。
        if (injuries.isEmpty()) {
            return Component.translatable(KEY + "injuries.none");
        }
        MutableComponent list = Component.empty();
        int shown = Math.min(injuries.size(), MAX_INJURIES_SHOWN);
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                list.append(Component.translatable(KEY + "separator"));
            }
            list.append(injury(injuries.get(index)));
        }
        int hidden = injuries.size() - shown;
        if (hidden > 0) {
            list.append(Component.translatable(KEY + "more", hidden));
        }
        return Component.translatable(KEY + "injuries", list);
    }

    private static Component injury(Injury injury) {
        String lost = HitAmounts.format(injury.getHp());
        if (injury.getSpillover()) {
            return Component.translatable(KEY + "injury.spillover", injury.getName(), lost);
        }
        return Component.translatable(KEY + "injury", injury.getName(), lost);
    }

    private static Component kind(DamageKind kind) {
        return Component.translatable(DAMAGE_KIND_KEY + kind.name().toLowerCase(Locale.ROOT));
    }

    private static Component condition(PartCondition condition) {
        return Component.translatable(KEY + "condition." + condition.name().toLowerCase(Locale.ROOT));
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int color(int rgb, float opacity) {
        int alpha = Math.max(MIN_TEXT_ALPHA, Math.round(opacity * MAX_ALPHA));
        return alpha << ALPHA_SHIFT | rgb;
    }

    /** 一次命中对应的一块；opacity 已乘上整体渐隐。 */
    record HudBlock(List<HudLine> lines, float opacity) { }

    record HudLine(Component text, int rgb) { }
}
