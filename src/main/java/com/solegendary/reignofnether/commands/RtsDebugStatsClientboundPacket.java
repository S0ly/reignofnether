package com.solegendary.reignofnether.commands;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// Server → client snapshot of pathfinder perf counters. Sent once per second while /rts-debug is enabled.
public class RtsDebugStatsClientboundPacket {

    public final boolean rtsEnabled;
    public final int pathsPerSec;
    public final int inflight;
    public final int failed;
    public final int chunkCache;
    public final float avgMs;
    public final float p99Ms;

    public static void broadcast(boolean rtsEnabled, int pathsPerSec, int inflight, int failed,
                                 int chunkCache, float avgMs, float p99Ms) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new RtsDebugStatsClientboundPacket(rtsEnabled, pathsPerSec, inflight, failed,
                        chunkCache, avgMs, p99Ms));
    }

    public RtsDebugStatsClientboundPacket(boolean rtsEnabled, int pathsPerSec, int inflight, int failed,
                                          int chunkCache, float avgMs, float p99Ms) {
        this.rtsEnabled = rtsEnabled;
        this.pathsPerSec = pathsPerSec;
        this.inflight = inflight;
        this.failed = failed;
        this.chunkCache = chunkCache;
        this.avgMs = avgMs;
        this.p99Ms = p99Ms;
    }

    public RtsDebugStatsClientboundPacket(FriendlyByteBuf buffer) {
        this.rtsEnabled = buffer.readBoolean();
        this.pathsPerSec = buffer.readVarInt();
        this.inflight = buffer.readVarInt();
        this.failed = buffer.readVarInt();
        this.chunkCache = buffer.readVarInt();
        this.avgMs = buffer.readFloat();
        this.p99Ms = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.rtsEnabled);
        buffer.writeVarInt(this.pathsPerSec);
        buffer.writeVarInt(this.inflight);
        buffer.writeVarInt(this.failed);
        buffer.writeVarInt(this.chunkCache);
        buffer.writeFloat(this.avgMs);
        buffer.writeFloat(this.p99Ms);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                RtsDebugClientEvents.rtsEnabled = this.rtsEnabled;
                RtsDebugClientEvents.pathsPerSec = this.pathsPerSec;
                RtsDebugClientEvents.inflight = this.inflight;
                RtsDebugClientEvents.failed = this.failed;
                RtsDebugClientEvents.chunkCache = this.chunkCache;
                RtsDebugClientEvents.avgMs = this.avgMs;
                RtsDebugClientEvents.p99Ms = this.p99Ms;
            });
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
