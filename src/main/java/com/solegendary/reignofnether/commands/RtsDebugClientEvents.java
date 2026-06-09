package com.solegendary.reignofnether.commands;

import com.solegendary.reignofnether.tps.TPSClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Top-right perf-stats overlay. Only renders while RtsDebug.enabled is true.
public class RtsDebugClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static boolean rtsEnabled = false;
    public static int pathsPerSec = 0;
    public static int inflight = 0;
    public static int failed = 0;
    public static int chunkCache = 0;
    public static float avgMs = 0f;
    public static float p99Ms = 0f;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre evt) {
        if (!RtsDebug.enabled || MC.font == null)
            return;

        int x = evt.getWindow().getGuiScaledWidth() - 130;
        int y = 5;
        int lineH = 10;

        double tps = TPSClientEvents.getCappedTPS();
        int tpsCol = tps < 10 ? 0xFF0000 : tps < 20 ? 0xFFFF00 : 0x00FF00;
        String fps = MC.fpsString != null && MC.fpsString.length() >= 6 ? MC.fpsString.substring(0, 6).replace("fps", "").trim() : "?";
        String system = rtsEnabled ? "RTS" : "Vanilla";
        int systemCol = rtsEnabled ? 0x00FFFF : 0xFFAA00;

        net.minecraft.client.gui.GuiGraphics gg = evt.getGuiGraphics();

        gg.drawString(MC.font, "[rts-debug]", x, y, 0xFFFF00); y += lineH;
        gg.drawString(MC.font, "System:  " + system, x, y, systemCol); y += lineH;
        gg.drawString(MC.font, String.format("TPS:%5.1f  FPS:%4s", tps, fps), x, y, tpsCol); y += lineH;
        gg.drawString(MC.font, "---", x, y, 0x888888); y += lineH;
        gg.drawString(MC.font, "Units:    " + UnitClientEvents.getAllUnits().size(), x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "Selected: " + UnitClientEvents.getSelectedUnits().size(), x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "Paths:    " + UnitClientEvents.displayedPathCount(), x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "Chunks:   " + chunkCache, x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "---", x, y, 0x888888); y += lineH;
        gg.drawString(MC.font, "Inflight: " + inflight, x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "Paths/s:  " + pathsPerSec, x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "Failed:   " + failed, x, y, failed > 0 ? 0xFF6060 : 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, "---", x, y, 0x888888); y += lineH;
        gg.drawString(MC.font, String.format("Avg ms:   %.2f", avgMs), x, y, 0xFFFFFF); y += lineH;
        gg.drawString(MC.font, String.format("p99 ms:   %.2f", p99Ms), x, y, 0xFFFFFF);
    }
}
