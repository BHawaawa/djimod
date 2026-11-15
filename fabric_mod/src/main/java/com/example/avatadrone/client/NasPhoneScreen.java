package com.example.avatadrone.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** NAS mobile app: the three UOM areas and their requested sub-services. */
public final class NasPhoneScreen extends Screen {
    private static final String[] GROUPS = {"实名登记", "操控员资质", "运行管理"};
    private static final String[][] ITEMS = {
            {"实名登记", "信息更新", "注销登记"},
            {"个人基本信息", "学员培训信息", "飞行记录本", "飞行记录确认", "签注申请", "签注审批", "理论考试申请", "实践考试申请", "成绩查看", "执照申请", "我的执照"},
            {"空域信息查询", "飞行活动申请", "产品缺陷问题", "我的航空器", "我的操纵员", "我的常飞空域"}
    };
    public NasPhoneScreen() { super(Text.literal("NAS · 不予批准系统")); }
    @Override protected void init() {
        int y = 48;
        for (int i = 0; i < GROUPS.length; i++) {
            final int g = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(GROUPS[i]), b -> {}).dimensions(20 + i * 105, 20, 100, 22).build());
            for (int j = 0; j < ITEMS[i].length; j++) {
                final int itemIndex = j;
                int col = j % 2, row = j / 2;
                addDrawableChild(ButtonWidget.builder(Text.literal(ITEMS[i][j]), b -> {
                    if (g == 2 && ITEMS[g][itemIndex].equals("空域信息查询")) client.setScreen(new NasManagerScreen(true));
                }).dimensions(20 + col * 155, y + row * 24, 145, 20).build());
            }
            y += ((ITEMS[i].length + 1) / 2) * 24 + 28;
        }
    }
    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xF0131D27);
        c.fill(12, 10, width - 12, 42, 0xFF1F3442);
        c.drawTextWithShadow(textRenderer, "NAS  不予批准系统", 24, 21, 0xFFE8F4FA);
        c.drawTextWithShadow(textRenderer, "民用无人驾驶航空器综合管理", 24, 33, 0xFF9BBCCC);
        super.render(c, mx, my, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
