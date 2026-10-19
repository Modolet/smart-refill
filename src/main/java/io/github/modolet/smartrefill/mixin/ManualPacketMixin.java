/**
 * @file ManualPacketMixin.java @brief Cancel deferred work on explicit player
 *     rearrangement. @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com> @date
 *     2026-10-19
 */
package io.github.modolet.smartrefill.mixin;

import io.github.modolet.smartrefill.Engine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ManualPacketMixin {
  @Shadow public ServerPlayer player;

  @Inject(
      method = {
        "handleContainerClick",
        "handleSetCarriedItem",
        "handlePlayerAction",
        "handleSetCreativeModeSlot"
      },
      at = @At("HEAD"))
  private void smartrefill$cancel(CallbackInfo ci) {
    Engine.clear(player);
  }
}
