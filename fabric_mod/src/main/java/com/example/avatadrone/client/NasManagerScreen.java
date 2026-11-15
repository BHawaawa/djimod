package com.example.avatadrone.client;

import com.example.avatadrone.NasSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** NAS manager login and an in-game airspace/registration/review console. */
public final class NasManagerScreen extends Screen {
    private final boolean mapOnly; private TextFieldWidget user, pass; private boolean logged; private int tab;
    private final List<Zone> zones = new ArrayList<>();
    private record Zone(int x, int y, int type, int radius) {}
    public NasManagerScreen() { this(false); }
    public NasManagerScreen(boolean mapOnly) { super(Text.literal("NAS 管理器")); this.mapOnly = mapOnly; this.logged = mapOnly; }
    @Override protected void init() {
        if (!logged) {
            user = new TextFieldWidget(textRenderer, width / 2 - 110, height / 2 - 42, 220, 20, Text.literal("用户名"));
            pass = new TextFieldWidget(textRenderer, width / 2 - 110, height / 2 - 14, 220, 20, Text.literal("密码")); pass.setRenderTextProvider((s, i) -> Text.literal("•".repeat(s.length())).asOrderedText());
            addDrawableChild(user); addDrawableChild(pass);
            addDrawableChild(ButtonWidget.builder(Text.literal("登录"), b -> { if (NasSystem.authenticate(user.getText(), pass.getText())) { logged = true; clearAndInit(); } else pass.setSuggestion("账号或密码错误"); }).dimensions(width / 2 - 110, height / 2 + 18, 220, 22).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("空域管理"), b -> tab = 0).dimensions(16, 30, 90, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("实名管理"), b -> tab = 1).dimensions(112, 30, 90, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("审核管理"), b -> tab = 2).dimensions(208, 30, 90, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("绘制多边形"), b -> tab = 3).dimensions(width - 250, 30, 110, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("机场禁飞预设"), b -> tab = 4).dimensions(width - 134, 30, 118, 22).build());
        }
    }
    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xFF0B131B);
        if (!logged) { c.drawCenteredTextWithShadow(textRenderer, "NAS 管理器授权登录", width / 2, height / 2 - 72, 0xFFE9F6FF); super.render(c,mx,my,delta); return; }
        c.fill(0, 0, width, 22, 0xFF1F3A4B); c.drawTextWithShadow(textRenderer, "NAS 管理器  ·  授权会话", 16, 8, 0xFFE9F6FF);
        super.render(c, mx, my, delta);
        if (tab == 0 || tab >= 3) drawMap(c, mx, my); else drawList(c);
    }
    private void drawMap(DrawContext c, int mx, int my) {
        int l=18,t=62,r=width-18,b=height-20; c.fill(l,t,r,b,0xFF243741);
        for(int x=l;x<r;x+=32)c.fill(x,t,x+1,b,0x223F6570); for(int y=t;y<b;y+=32)c.fill(l,y,r,y+1,0x223F6570);
        c.drawTextWithShadow(textRenderer, "飞行地图 · 点击地图放置区域", l+8,t+8,0xFFE0EFF2);
        for (Zone z: zones) { int color=switch(z.type){case 1->0x669ED8FF;case 2->0x66FFFFFF;case 3->0x66F04444;case 4->0x66E8C63C;default->0x6655C878;}; c.fill(z.x-z.radius,z.y-z.radius,z.x+z.radius,z.y+z.radius,color); }
        c.drawTextWithShadow(textRenderer,"适飞  管制  禁飞  加强警示  景观示范",l+8,b-14,0xFFD6E7E8);
    }
    private void drawList(DrawContext c) { String title=tab==1?"实名登记信息":"待审核飞行申请"; c.drawTextWithShadow(textRenderer,title,24,66,0xFFEAF4F6); c.drawTextWithShadow(textRenderer,tab==1?"暂无提交记录 · 手机端提交后会同步显示":"暂无待审申请 · 自动驳回：关闭",24,90,0xFF9CBAC5); }
    @Override public boolean mouseClicked(double x,double y,int button) { if(logged && tab>=3 && y>62){ zones.add(new Zone((int)x,(int)y,tab==4?3:1,tab==4?32:18)); return true;} return super.mouseClicked(x,y,button); }
    @Override public boolean shouldPause(){return false;}
}
