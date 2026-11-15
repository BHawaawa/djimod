package com.example.avatadrone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import xaero.map.WorldMapSession;
import xaero.map.minimap.MinimapRenderListener;

/** Read Xaero's own current-world disk cache through its loader; never scan arbitrary save folders. */
public final class XaeroFlightMap {
    private static final MinimapRenderListener listener = new MinimapRenderListener();

    public static boolean draw(DrawContext ctx, int x, int y, int w, int h,
            double centerX, double centerZ, double scale) {
        var client = MinecraftClient.getInstance();
        var session = WorldMapSession.getCurrentSession();
        if (client.world == null || client.player == null || session == null || !session.isUsable()) return false;
        var processor = session.getMapProcessor();
        if (!processor.isMapWorldUsable() || processor.getWorld() != client.world
                || processor.getMapWorld().isUsingCustomDimension()
                || !client.world.getRegistryKey().equals(processor.getMapWorld().getCurrentDimensionId())) return false;
        int minX = (int)Math.floor(centerX - w / scale / 2), minZ = (int)Math.floor(centerZ - h / scale / 2);
        int maxX = (int)Math.ceil(centerX + w / scale / 2), maxZ = (int)Math.ceil(centerZ + h / scale / 2);
        boolean drawn = false;
        ctx.draw();
        listener.init(processor, (int)Math.floor(centerX), (int)Math.floor(centerZ));
        int oldTexture = RenderSystem.getShaderTexture(0);
        try {
            // RGB contains Xaero's terrain colours. Opaque daylight display avoids treating
            // the lighting data in its alpha channel as ordinary GUI transparency.
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (int rx = Math.floorDiv(minX, 512); rx <= Math.floorDiv(maxX, 512); rx++) {
                for (int rz = Math.floorDiv(minZ, 512); rz <= Math.floorDiv(maxZ, 512); rz++) {
                    int layer = processor.getCurrentCaveLayer();
                    var region = processor.getLeafMapRegion(layer, rx, rz, processor.regionExists(layer, rx, rz));
                    if (region == null) continue;
                    listener.beforeMinimapRender(region);
                    for (int tx = 0; tx < 8; tx++) for (int tz = 0; tz < 8; tz++) {
                        var texture = region.getTexture(tx, tz);
                        if (texture == null || texture.getGlColorTexture() < 0) continue;
                        double left = x + w / 2.0 + (rx * 512.0 + tx * 64 - centerX) * scale;
                        double top = y + h / 2.0 + (rz * 512.0 + tz * 64 - centerZ) * scale;
                        double size = 64 * scale;
                        if (left >= x+w || top >= y+h || left+size <= x || top+size <= y) continue;
                        float l = (float)Math.max(x, left), r = (float)Math.min(x+w, left+size);
                        float t = (float)Math.max(y, top), b = (float)Math.min(y+h, top+size);
                        float u0 = (float)((l-left)/size), u1 = (float)((r-left)/size);
                        float v0 = (float)((t-top)/size), v1 = (float)((b-top)/size);
                        RenderSystem.setShaderTexture(0, texture.getGlColorTexture());
                        var matrix = ctx.getMatrices().peek().getPositionMatrix();
                        var buffer = Tessellator.getInstance().getBuffer();
                        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                        buffer.vertex(matrix,l,b,0).texture(u0,v1).next();
                        buffer.vertex(matrix,r,b,0).texture(u1,v1).next();
                        buffer.vertex(matrix,r,t,0).texture(u1,v0).next();
                        buffer.vertex(matrix,l,t,0).texture(u0,v0).next();
                        BufferRenderer.drawWithGlobalProgram(buffer.end());
                        drawn = true;
                    }
                }
            }
        } finally {
            listener.finalize(processor);
            RenderSystem.setShaderTexture(0, oldTexture);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        return drawn;
    }
}
