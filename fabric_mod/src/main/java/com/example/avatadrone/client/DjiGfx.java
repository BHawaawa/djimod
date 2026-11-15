package com.example.avatadrone.client;

public interface DjiGfx {
    public float scale();

    public void fillPx(int var1, int var2, int var3, int var4, int var5);

    public void gradientPx(int var1, int var2, int var3, int var4, int var5, int var6);

    public void batch(Runnable var1);

    public void icon(int var1, float var2, float var3, float var4, float var5, int var6);

    public float text(String var1, float var2, float var3, float var4, int var5, boolean var6, boolean var7);

    public float textWidth(String var1, float var2, boolean var3);

    public void pushClip(float var1, float var2, float var3, float var4);

    public void popClip();

    public boolean mapTiles(float var1, float var2, float var3, float var4, double var5, double var7, double var9);

    public boolean feed(float var1, float var2, float var3, float var4);

    public boolean assist(float var1, float var2, float var3, float var4);
}

