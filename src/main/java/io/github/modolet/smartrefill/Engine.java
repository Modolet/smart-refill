/**
 * @file Engine.java @brief Consumption receipts and bounded server transfers. @author XiangX
 *     (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public final class Engine {
  public enum Cause {
    USE,
    FOOD,
    BREAK,
    TOTEM
  }

  public record Receipt(
      ServerPlayer player,
      Target target,
      ItemStack template,
      Cause cause,
      int selected,
      long tick,
      Object level) {}

  public record Batch(ItemStack stack, java.util.function.Consumer<ItemStack> refund) {}

  private static final ThreadLocal<Map<UUID, List<Receipt>>> PENDING =
      ThreadLocal.withInitial(HashMap::new);

  public static Receipt begin(Object entity, ItemStack stack, Cause cause) {
    if (!(entity instanceof ServerPlayer p)
        || !p.getServer().isSameThread()
        || stack.isEmpty()
        || p.hasInfiniteMaterials()
        || !Settings.ENABLED.get()) return null;
    Target target = null;
    var inv = p.getInventory();
    for (int i = 0; i < inv.getContainerSize(); i++) {
      if (inv.getItem(i) != stack) continue;
      // Only the active hand, offhand, or worn armor is a consumption target.
      if (i != inv.selected && i < 36) return null;
      final int slot = i;
      target =
          new Target() {
            public Object container() {
              return inv;
            }

            public int index() {
              return slot;
            }

            public ItemStack get() {
              return inv.getItem(slot);
            }

            public boolean accepts(ItemStack s) {
              if (!get().isEmpty()) return false;
              if (slot >= 36 && slot <= 39) {
                EquipmentSlot equipment =
                    switch (slot) {
                      case 36 -> EquipmentSlot.FEET;
                      case 37 -> EquipmentSlot.LEGS;
                      case 38 -> EquipmentSlot.CHEST;
                      default -> EquipmentSlot.HEAD;
                    };
                return p.canUseSlot(equipment) && s.canEquip(equipment, p);
              }
              return true;
            }

            public void set(ItemStack s) {
              inv.setItem(slot, s);
              inv.setChanged();
            }
          };
      break;
    }
    if (target == null
        && cause == Cause.TOTEM
        && stack.is(Items.TOTEM_OF_UNDYING)
        && Settings.ACCESSORIES.get()
        && ModList.get().isLoaded("accessories")) target = AccessoryBridge.locate(p, stack);
    if (target == null) return null;
    boolean accessory = target.container() != inv;
    boolean allowed =
        switch (cause) {
          case USE -> Settings.BLOCKS.get();
          case FOOD -> Settings.FOOD.get();
          case BREAK ->
              target.index() >= 36 && target.index() <= 39
                  ? Settings.ARMOR.get()
                  : Settings.TOOLS.get();
          case TOTEM ->
              stack.is(Items.TOTEM_OF_UNDYING)
                  && (accessory ? Settings.ACCESSORIES.get() : Settings.TOTEMS.get());
        };
    return allowed
        ? new Receipt(
            p,
            target,
            stack.copyWithCount(1),
            cause,
            inv.selected,
            p.serverLevel().getGameTime(),
            p.serverLevel())
        : null;
  }

  public static void consumed(Receipt receipt, ItemStack original) {
    if (receipt == null || !original.isEmpty() || !receipt.target.get().isEmpty()) return;
    var list = PENDING.get().computeIfAbsent(receipt.player.getUUID(), k -> new ArrayList<>());
    // Nested use hooks and compatibility layers cannot enqueue twice for one empty slot.
    if (list.stream().noneMatch(r -> r.target.same(receipt.target))) list.add(receipt);
  }

  public static void clear(Player player) {
    if (player instanceof ServerPlayer p && p.getServer().isSameThread())
      PENDING.get().remove(p.getUUID());
  }

  public static void touch(Object container, int slot) {
    PENDING
        .get()
        .values()
        .forEach(
            list ->
                list.removeIf(
                    r ->
                        r.target.container() == container
                            && (slot < 0 || r.target.index() == slot)));
  }

  public static boolean matches(ItemStack a, ItemStack b, Cause cause) {
    if (a.isEmpty() || b.isEmpty()) return false;
    if (cause != Cause.BREAK) return ItemStack.isSameItemSameComponents(a, b);
    if (!a.isDamageableItem() || !b.isDamageableItem() || b.getDamageValue() >= b.getMaxDamage())
      return false;
    ItemStack aa = a.copy(), bb = b.copy();
    aa.remove(DataComponents.DAMAGE);
    bb.remove(DataComponents.DAMAGE);
    return ItemStack.isSameItemSameComponents(aa, bb);
  }

  public static void flush(ServerPlayer p) {
    List<Receipt> work = PENDING.get().remove(p.getUUID());
    if (work == null) return;
    for (Receipt r : work) {
      if (!Settings.ENABLED.get()
          || !p.isAlive()
          || p.isRemoved()
          || p.hasDisconnected()
          || p.serverLevel() != r.level
          || p.serverLevel().getGameTime() - r.tick > 1
          || p.getInventory().selected != r.selected
          || !r.target.accepts(r.template)) continue;
      int wanted =
          r.cause == Cause.BREAK || r.cause == Cause.TOTEM ? 1 : r.template.getMaxStackSize();
      Settings.Priority priority = Settings.SOURCE_PRIORITY.get();
      List<Batch> batches = new ArrayList<>();
      if (priority == Settings.Priority.WIRELESS_FIRST) {
        collectWireless(r, wanted, batches);
        collectInventory(r, wanted, batches);
      } else {
        collectInventory(r, wanted, batches);
        if (priority != Settings.Priority.INVENTORY_ONLY) collectWireless(r, wanted, batches);
      }
      if (batches.isEmpty()) continue;
      int[] counts = batches.stream().mapToInt(b -> b.stack.getCount()).toArray();
      ItemStack combined = batches.getFirst().stack;
      for (int i = 1; i < batches.size(); i++) {
        ItemStack part = batches.get(i).stack;
        combined.grow(part.getCount());
        part.setCount(0);
      }
      deliver(
          r,
          combined,
          unused -> {
            for (int i = 0; i < batches.size(); i++)
              batches.get(i).refund.accept(unused.split(counts[i]));
          });
    }
    p.inventoryMenu.broadcastChanges();
    if (p.containerMenu != p.inventoryMenu) p.containerMenu.broadcastChanges();
  }

  private static int remaining(int wanted, List<Batch> batches) {
    return wanted - batches.stream().mapToInt(b -> b.stack.getCount()).sum();
  }

  private static void collectInventory(Receipt r, int wanted, List<Batch> batches) {
    if (!r.target.accepts(r.template)) return;
    var inv = r.player.getInventory();
    for (int i = 0; i < 36; i++) {
      int left = remaining(wanted, batches);
      if (left <= 0) return;
      if (r.target.container() == inv && r.target.index() == i) continue;
      ItemStack source = inv.getItem(i);
      if (!matches(source, r.template, r.cause)) continue;
      ItemStack moved = source.split(Math.min(left, source.getCount()));
      inv.setChanged();
      final int sourceSlot = i;
      batches.add(
          new Batch(
              moved,
              unused -> {
                if (inv.getItem(sourceSlot) == source
                    && (source.isEmpty() || ItemStack.isSameItemSameComponents(source, unused))
                    && source.getCount() + unused.getCount() <= unused.getMaxStackSize()) {
                  source.grow(unused.getCount());
                  inv.setChanged();
                } else recover(r.player, unused);
              }));
    }
  }

  private static void collectWireless(Receipt r, int wanted, List<Batch> batches) {
    int left = remaining(wanted, batches);
    if (left > 0
        && Settings.WIRELESS.get()
        && ModList.get().isLoaded("toms_storage")
        && r.target.accepts(r.template)) {
      Batch batch = TomBridge.take(r, left);
      if (batch != null) batches.add(batch);
    }
  }

  public static void deliver(
      Receipt r, ItemStack actual, java.util.function.Consumer<ItemStack> rollback) {
    if (actual.isEmpty()) return;
    if (!matches(actual, r.template, r.cause) || !r.target.accepts(actual)) {
      rollback.accept(actual);
      return;
    }
    r.target.set(actual);
    // API return values alone do not prove insertion. Only ownership of this exact stack counts.
    if (r.target.get() != actual) rollback.accept(actual);
  }

  public static void recover(ServerPlayer p, ItemStack remainder) {
    if (remainder.isEmpty()) return;
    p.getInventory().add(remainder);
    if (!remainder.isEmpty())
      p.drop(remainder, false); // Real extracted remainder, never regenerate from template.
  }

  private Engine() {}
}
