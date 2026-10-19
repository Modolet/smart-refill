/**
 * @file SortMixin.java @brief Optional ClientSort server request cancellation. @author XiangX
 *     (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.mixin;

import io.github.modolet.smartrefill.Engine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
      "dev.terminalmc.clientsort.network.handler.SortHandler",
      "dev.terminalmc.clientsort.network.handler.CollectHandler"
    },
    remap = false)
public abstract class SortMixin {
  @Inject(method = "handle", at = @At("HEAD"), remap = false)
  private static void smartrefill$cancel(
      @Coerce Object payload, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
    // handle can run off-thread. Queue in the same order, ahead of the original sort runnable.
    server.execute(() -> Engine.clear(player));
  }
}
