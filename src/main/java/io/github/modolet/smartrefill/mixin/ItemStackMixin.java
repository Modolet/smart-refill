/**
 * @file ItemStackMixin.java @brief Observe completed real use, never inventory diffs. @author
 *     XiangX (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.modolet.smartrefill.Engine;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @WrapMethod(method = "useOn")
  private InteractionResult smartrefill$useOn(
      UseOnContext ctx, Operation<InteractionResult> original) {
    ItemStack self = (ItemStack) (Object) this;
    var receipt = Engine.begin(ctx.getPlayer(), self, Engine.Cause.USE);
    var result = original.call(ctx);
    if (result.consumesAction()) Engine.consumed(receipt, self);
    return result;
  }

  @WrapMethod(method = "use")
  private InteractionResultHolder<ItemStack> smartrefill$use(
      Level level,
      Player player,
      InteractionHand hand,
      Operation<InteractionResultHolder<ItemStack>> original) {
    ItemStack self = (ItemStack) (Object) this;
    var receipt = Engine.begin(player, self, Engine.Cause.USE);
    var result = original.call(level, player, hand);
    if (result.getResult().consumesAction()) Engine.consumed(receipt, self);
    return result;
  }

  @WrapMethod(method = "finishUsingItem")
  private ItemStack smartrefill$finish(
      Level level, LivingEntity entity, Operation<ItemStack> original) {
    ItemStack self = (ItemStack) (Object) this;
    var receipt = Engine.begin(entity, self, Engine.Cause.FOOD);
    ItemStack result = original.call(level, entity);
    if (result.isEmpty()) Engine.consumed(receipt, self);
    return result;
  }

  @WrapMethod(
      method =
          "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")
  private void smartrefill$break(
      int amount,
      ServerLevel level,
      LivingEntity entity,
      Consumer<Item> onBreak,
      Operation<Void> original) {
    ItemStack self = (ItemStack) (Object) this;
    var receipt = Engine.begin(entity, self, Engine.Cause.BREAK);
    original.call(amount, level, entity, onBreak);
    Engine.consumed(receipt, self);
  }
}
