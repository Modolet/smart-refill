/**
 * @file ContainerMixin.java @brief Observe accessory container mutations through vanilla
 *     API. @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.mixin;

import io.github.modolet.smartrefill.Engine;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleContainer.class)
public abstract class ContainerMixin {
  @Inject(method = "setItem", at = @At("HEAD"))
  private void smartrefill$set(int index, ItemStack stack, CallbackInfo ci) {
    Engine.touch(this, index);
  }

  @Inject(method = "removeItem", at = @At("HEAD"))
  private void smartrefill$remove(int index, int count, CallbackInfoReturnable<ItemStack> cir) {
    Engine.touch(this, index);
  }

  @Inject(method = "removeItemNoUpdate", at = @At("HEAD"))
  private void smartrefill$removeQuiet(int index, CallbackInfoReturnable<ItemStack> cir) {
    Engine.touch(this, index);
  }

  @Inject(method = "clearContent", at = @At("HEAD"))
  private void smartrefill$clear(CallbackInfo ci) {
    Engine.touch(this, -1);
  }
}
