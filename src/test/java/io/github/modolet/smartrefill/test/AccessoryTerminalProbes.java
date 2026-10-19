/**
 * @file AccessoryTerminalProbes.java @brief Dedicated real-game accessory terminal regression
 *     suite.
 * @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
 * @date 2026-10-19
 */
package io.github.modolet.smartrefill.test;

import com.tom.storagemod.Config;
import com.tom.storagemod.Content;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.components.WorldPos;
import io.github.modolet.smartrefill.*;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

final class AccessoryTerminalProbes {
  private SlotReference ref;
  private AccessoriesContainer expanded;
  private static final ResourceLocation EXTRA_BELT =
      ResourceLocation.fromNamespaceAndPath("smartrefill_runtime_tests", "extra_belt");

  void run(
      ServerPlayer p,
      BlockPos terminalPos,
      BlockPos chestPos,
      int stage,
      BiConsumer<String, Boolean> output,
      Consumer<ServerPlayer> reset,
      Consumer<ServerPlayer> consume,
      Consumer<ServerPlayer> aim) {
    BiConsumer<String, Boolean> check =
        (name, success) -> output.accept("accessory_terminal_" + name, success);
    var chest = (Container) p.serverLevel().getBlockEntity(chestPos);
    var secondChest = (Container) p.serverLevel().getBlockEntity(new BlockPos(11, 200, 4));
    var terminal = (StorageTerminalBlockEntity) p.serverLevel().getBlockEntity(terminalPos);
    switch (stage) {
      case 0 -> {
        prepare(p, reset);
        chest.setItem(0, new ItemStack(Items.STONE, 7));
        ref = equip(p, bound(p, terminalPos));
        check.accept(
            "valid_functional_slot", AccessoriesAPI.canInsertIntoSlot(ref.getStack(), ref));
      }
      case 1 -> {
        ItemStack carried = ref.getStack();
        ItemStack snapshot = carried.copy();
        consume.accept(p);
        // Beyond the default six-block interaction distance: Tom must recognize the worn terminal.
        p.teleportTo(p.serverLevel(), terminalPos.getX() + 16.5, 200, 4.5, 0, 0);
        check.accept(
            "native_extended_range",
            Config.get().advWirelessRange >= 17
                && terminal.canInteractWith(p, false)
                && TomBridge.resolve(p) == terminal);
        Engine.flush(p);
        check.accept(
            "advanced_real_extraction_conservation",
            p.getMainHandItem().getCount() == 7
                && chest.getItem(0).isEmpty()
                && p.containerMenu == p.inventoryMenu);
        check.accept(
            "terminal_unchanged_no_duplicate",
            ref.getStack() == carried
                && ItemStack.matches(snapshot, carried)
                && carried.getCount() == 1
                && noInventoryTerminal(p));
      }
      case 2 -> {
        prepare(p, reset);
        chest.setItem(0, new ItemStack(Items.STONE, 7));
        ref = equip(p, new ItemStack(Content.wirelessTerminal.get()));
      }
      case 3 -> {
        consume.accept(p);
        p.setYRot(0);
        p.setXRot(0);
        p.setYHeadRot(0);
        p.setOldPosAndRot();
        check.accept("ordinary_requires_line_of_sight", TomBridge.resolve(p) == null);
        aim.accept(p);
        check.accept(
            "ordinary_native_access",
            TomBridge.resolve(p) == terminal && terminal.canInteractWith(p, false));
        Engine.flush(p);
        check.accept(
            "ordinary_real_extraction",
            p.getMainHandItem().getCount() == 7
                && chest.getItem(0).isEmpty()
                && ref.getStack().getCount() == 1);
      }
      case 4 -> {
        prepare(p, reset);
        chest.setItem(0, new ItemStack(Items.STONE, 7));
        ref = equip(p, new ItemStack(Content.advWirelessTerminal.get()));
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "unbound_rejected", p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
      }
      case 5 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        consume.accept(p);
        p.teleportTo(
            p.serverLevel(),
            terminalPos.getX() + Config.get().advWirelessRange + 40,
            200,
            4.5,
            0,
            0);
        check.accept(
            "out_of_range_rejected",
            !terminal.canInteractWith(p, false) && TomBridge.resolve(p) == null);
        Engine.flush(p);
        check.accept(
            "denied_access_preserves_stock",
            p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
      }
      case 6 -> {
        prepare(p, reset);
        BlockPos unloaded = new BlockPos(123456, 200, 123456);
        ref = equip(p, bound(p, unloaded));
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "unloaded_no_force_load",
            !p.serverLevel().isLoaded(unloaded)
                && p.getMainHandItem().isEmpty()
                && chest.getItem(0).getCount() == 7);
      }
      case 7 -> {
        prepare(p, reset);
        var nether = p.getServer().getLevel(Level.NETHER);
        nether.getChunk(0, 0); // Test fixture only; production never loads remote chunks.
        nether.setBlock(terminalPos, Content.terminal.get().defaultBlockState(), 3);
        ItemStack across = bound(p, terminalPos);
        across.set(Content.boundPosComponent.get(), new WorldPos(Level.NETHER, terminalPos));
        ref = equip(p, across);
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "cross_dimension_without_beacon",
            p.getMainHandItem().isEmpty()
                && chest.getItem(0).getCount() == 7
                && TomBridge.resolve(p) == null);
      }
      case 8 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        ItemStack carried = ref.getStack();
        ref.setStack(ItemStack.EMPTY);
        var container =
            AccessoriesCapability.getOptionally(p)
                .orElseThrow()
                .getContainers()
                .get(ref.slotName());
        container.getCosmeticAccessories().setItem(ref.slot(), carried);
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "cosmetic_slot_ignored",
            TomBridge.resolve(p) == null
                && p.getMainHandItem().isEmpty()
                && chest.getItem(0).getCount() == 7
                && carried.getCount() == 1);
      }
      case 9 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        consume.accept(p);
        ItemStack removed = ref.getStack();
        ref.setStack(ItemStack.EMPTY);
        Engine.flush(p);
        check.accept(
            "unequipped_before_flush",
            p.getMainHandItem().isEmpty()
                && chest.getItem(0).getCount() == 7
                && removed.getCount() == 1);
      }
      case 10 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        consume.accept(p);
        Settings.ACCESSORY_WIRELESS.set(false);
        Engine.flush(p);
        check.accept(
            "source_toggle_disabled",
            p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
        Settings.ACCESSORY_WIRELESS.set(true);
        p.getInventory().setItem(0, new ItemStack(Items.STONE));
        consume.accept(p);
        Settings.WIRELESS.set(false);
        Engine.flush(p);
        check.accept(
            "wireless_master_disabled",
            p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
        Settings.WIRELESS.set(true);
      }
      case 11 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        p.getInventory().setItem(10, bound(p, new BlockPos(12, 200, 4)));
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "inventory_terminal_precedes_accessory",
            p.getMainHandItem().getCount() == 9
                && chest.getItem(0).getCount() == 7
                && secondChest.getItem(0).isEmpty());
      }
      case 12 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        p.getInventory().setItem(40, bound(p, new BlockPos(12, 200, 4)));
        secondChest.setItem(0, new ItemStack(Items.STONE, 11));
      }
      case 13 -> {
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "offhand_terminal_precedes_accessory",
            p.getMainHandItem().getCount() == 11
                && chest.getItem(0).getCount() == 7
                && secondChest.getItem(0).isEmpty());
      }
      case 14 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        p.getInventory().setItem(9, new ItemStack(Items.STONE, 3));
      }
      case 15 -> {
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "inventory_first_partial_conservation",
            p.getMainHandItem().getCount() == 10
                && p.getInventory().getItem(9).isEmpty()
                && chest.getItem(0).isEmpty());
      }
      case 16 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        expanded =
            AccessoriesCapability.getOptionally(p)
                .orElseThrow()
                .getContainers()
                .get(ref.slotName());
        // Extra belt is a transient API test fixture, not a production slot change.
        expanded.addTransientModifier(
            new AttributeModifier(EXTRA_BELT, 1, AttributeModifier.Operation.ADD_VALUE));
        expanded.update();
        check.accept("multiple_functional_slots_fixture", expanded.getSize() >= 2);
        expanded.createReference(0).setStack(bound(p, terminalPos));
        expanded.createReference(1).setStack(bound(p, new BlockPos(12, 200, 4)));
        secondChest.setItem(0, new ItemStack(Items.STONE, 9));
      }
      case 17 -> {
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "multiple_accessories_no_network_roaming",
            p.getMainHandItem().isEmpty()
                && secondChest.getItem(0).getCount() == 9
                && chest.getItem(0).isEmpty());
        ItemStack first = expanded.getAccessories().getItem(0),
            second = expanded.getAccessories().getItem(1);
        expanded.createReference(0).setStack(second);
        expanded.createReference(1).setStack(first);
        p.getInventory().setItem(0, new ItemStack(Items.STONE));
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "multiple_accessories_index_order",
            p.getMainHandItem().getCount() == 9
                && secondChest.getItem(0).isEmpty()
                && first.getCount() == 1
                && second.getCount() == 1);
      }
      case 18 -> {
        prepare(p, reset);
        expanded.removeModifier(EXTRA_BELT);
        expanded.update();
        expanded = null;
        chest.setItem(0, new ItemStack(Items.STONE, 7));
        ref = equip(p, bound(p, terminalPos));
      }
      case 19 -> {
        consume.accept(p);
        p.getInventory().setItem(0, new ItemStack(Items.DIRT));
        Engine.flush(p);
        check.accept(
            "changed_target_preserves_stock",
            p.getMainHandItem().is(Items.DIRT) && chest.getItem(0).getCount() == 7);
        prepare(p, reset);
        ItemStack invalid = bound(p, terminalPos);
        var cap = AccessoriesCapability.getOptionally(p).orElseThrow();
        SlotReference wrong =
            cap.getContainers().values().stream()
                .flatMap(
                    c ->
                        java.util.stream.IntStream.range(0, c.getSize())
                            .mapToObj(c::createReference))
                .filter(r -> !AccessoriesAPI.canInsertIntoSlot(invalid, r))
                .findFirst()
                .orElseThrow();
        wrong.setStack(invalid);
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "invalid_functional_slot_ignored",
            p.getMainHandItem().isEmpty()
                && chest.getItem(0).getCount() == 7
                && invalid.getCount() == 1);
      }
      case 20 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
      }
      case 21 -> {
        var receipt = Engine.begin(p, p.getMainHandItem(), Engine.Cause.USE);
        consume.accept(p);
        Engine.clear(p);
        var batch = TomBridge.take(receipt, 3);
        check.accept(
            "rollback_real_extraction",
            batch != null && batch.stack().getCount() == 3 && chest.getItem(0).getCount() == 4);
        if (batch != null) {
          p.getInventory().setItem(0, new ItemStack(Items.DIRT));
          Engine.deliver(receipt, batch.stack(), batch.refund());
        }
        check.accept(
            "rollback_conservation",
            p.getMainHandItem().is(Items.DIRT) && chest.getItem(0).getCount() == 7);
      }
      case 22 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_ONLY);
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "inventory_only_no_wireless",
            p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
        Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_FIRST);
      }
      case 23 -> {
        prepare(p, reset);
        ref = equip(p, bound(p, terminalPos));
        p.getInventory().setItem(9, new ItemStack(Items.STONE, 4));
        Settings.SOURCE_PRIORITY.set(Settings.Priority.WIRELESS_FIRST);
        consume.accept(p);
        Engine.flush(p);
        check.accept(
            "wireless_first_partial_conservation",
            p.getMainHandItem().getCount() == 11
                && p.getInventory().getItem(9).isEmpty()
                && chest.getItem(0).isEmpty());
        Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_FIRST);
        prepare(p, reset);
        chest.setItem(0, new ItemStack(Items.STONE, 7));
        secondChest.setItem(0, new ItemStack(Items.STONE, 9));
      }
      default -> {}
    }
  }

  private static void prepare(ServerPlayer p, Consumer<ServerPlayer> reset) {
    reset.accept(p);
    AccessoriesCapability.getOptionally(p)
        .orElseThrow()
        .getContainers()
        .values()
        .forEach(c -> c.getCosmeticAccessories().clearContent());
    p.getInventory().setItem(0, new ItemStack(Items.STONE));
  }

  private static SlotReference equip(ServerPlayer p, ItemStack terminal) {
    var cap = AccessoriesCapability.getOptionally(p).orElseThrow();
    for (String name : cap.getContainers().keySet().stream().sorted().toList()) {
      var c = cap.getContainers().get(name);
      for (int i = 0; i < c.getSize(); i++) {
        var r = c.createReference(i);
        if (AccessoriesAPI.canInsertIntoSlot(terminal, r)) {
          r.setStack(terminal);
          return r;
        }
      }
    }
    throw new AssertionError("No valid Accessories slot for " + terminal);
  }

  private static ItemStack bound(ServerPlayer p, BlockPos pos) {
    ItemStack stack = new ItemStack(Content.advWirelessTerminal.get());
    stack.set(Content.boundPosComponent.get(), new WorldPos(p.serverLevel().dimension(), pos));
    return stack;
  }

  private static boolean noInventoryTerminal(ServerPlayer p) {
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var s = p.getInventory().getItem(i);
      if (s.is(Content.wirelessTerminal.get()) || s.is(Content.advWirelessTerminal.get()))
        return false;
    }
    return true;
  }
}
