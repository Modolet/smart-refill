/**
 * @file TotemMixin.java @brief Single actual vanilla/Accessorify consumption point. @author XiangX
 *     (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.modolet.smartrefill.Engine;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class TotemMixin {
  @WrapOperation(
      method = "checkTotemDeathProtection",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
  private void smartrefill$totem(ItemStack stack, int count, Operation<Void> original) {
    var receipt = Engine.begin(this, stack, Engine.Cause.TOTEM);
    original.call(stack, count);
    if (count == 1) Engine.consumed(receipt, stack);
  }
}
