package dev.jun.nosclient.lyricsflow.mixin;

import dev.jun.nosclient.lyricsflow.render.LivePing;
import net.minecraft.client.multiplayer.PingDebugMonitor;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PingDebugMonitor.class)
public abstract class PingDebugMonitorMixin {
   @Inject(method = "onPongReceived", at = @At("HEAD"))
   private void lyricsflow$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
      LivePing.record(Util.getMillis() - packet.time());
   }
}
