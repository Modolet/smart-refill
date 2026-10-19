/**
 * @file Target.java @brief Revalidated destination, never overwrites. @author XiangX (Modolet)
 *     <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import net.minecraft.world.item.ItemStack;

public interface Target {
  Object container();

  int index();

  ItemStack get();

  boolean accepts(ItemStack stack);

  void set(ItemStack stack);

  default boolean same(Target other) {
    return container() == other.container() && index() == other.index();
  }
}
