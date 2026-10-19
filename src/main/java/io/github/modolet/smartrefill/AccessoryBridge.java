/**
 * @file AccessoryBridge.java @brief Optional Accessories API boundary. @author XiangX (Modolet)
 *     <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AccessoryBridge {
  public static Target locate(ServerPlayer player, ItemStack consumed) {
    var capability = AccessoriesCapability.getOptionally(player).orElse(null);
    if (capability == null) return null;
    for (var entry : capability.getContainers().entrySet()) {
      var container = entry.getValue();
      for (int i = 0; i < container.getSize(); i++) {
        if (container.getAccessories().getItem(i) != consumed) continue;
        SlotReference ref = container.createReference(i);
        return new Target() {
          public Object container() {
            return container.getAccessories();
          }

          public int index() {
            return ref.slot();
          }

          public ItemStack get() {
            var current = capability.getContainers().get(ref.slotName());
            return current == container && index() >= 0 && index() < current.getSize()
                ? ref.getStack()
                : ItemStack.EMPTY;
          }

          public boolean accepts(ItemStack stack) {
            return capability.getContainers().get(ref.slotName()) == container
                && index() >= 0
                && index() < container.getSize()
                && get().isEmpty()
                && AccessoriesAPI.canInsertIntoSlot(stack, ref);
          }

          public void set(ItemStack stack) {
            ref.setStack(stack);
          }
        };
      }
    }
    return null;
  }

  private AccessoryBridge() {}
}
