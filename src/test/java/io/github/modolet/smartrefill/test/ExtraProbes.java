/**
 * @file ExtraProbes.java @brief Real extraction rollback and network boundary fault probes. @author
 *     XiangX (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.test;

import com.tom.storagemod.Content;
import com.tom.storagemod.components.WorldPos;
import io.github.modolet.smartrefill.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class ExtraProbes {
  static void run(
      ServerPlayer p, BlockPos terminalPos, BlockPos chestPos, BiConsumer<String, Boolean> check) {
    Engine.clear(p);
    p.getInventory().clearContent();
    p.getInventory().selected = 0;
    var chest = (Container) p.serverLevel().getBlockEntity(chestPos);
    // Stock was installed in the previous tick, respecting Tom's per-tick tracker cache.
    ItemStack terminal = new ItemStack(Content.advWirelessTerminal.get());
    terminal.set(
        Content.boundPosComponent.get(), new WorldPos(p.serverLevel().dimension(), terminalPos));
    p.getInventory().setItem(10, terminal);
    p.teleportTo(p.serverLevel(), 8.5, 200, 8.5, 0, 0);
    ItemStack consumed = new ItemStack(Items.STONE);
    p.getInventory().setItem(0, consumed);
    var receipt = Engine.begin(p, consumed, Engine.Cause.USE);
    consumed.shrink(1);
    var batch = TomBridge.take(receipt, 3);
    check.accept(
        "fault_probe_real_extraction",
        batch != null && batch.stack().getCount() == 3 && chest.getItem(0).getCount() == 4);
    if (batch != null) {
      p.getInventory().setItem(0, new ItemStack(Items.DIRT));
      Engine.deliver(receipt, batch.stack(), batch.refund());
      check.accept(
          "target_changed_refund_conservation",
          p.getMainHandItem().is(Items.DIRT) && chest.getItem(0).getCount() == 7);
    }
    p.getInventory().setItem(0, ItemStack.EMPTY);
    Target rejectsWrite =
        new Target() {
          public Object container() {
            return this;
          }

          public int index() {
            return 0;
          }

          public ItemStack get() {
            return ItemStack.EMPTY;
          }

          public boolean accepts(ItemStack stack) {
            return true;
          }

          public void set(ItemStack stack) {}
        };
    var rejected =
        new Engine.Receipt(
            p,
            rejectsWrite,
            new ItemStack(Items.STONE),
            Engine.Cause.USE,
            0,
            p.serverLevel().getGameTime(),
            p.serverLevel());
    batch = TomBridge.take(rejected, 2);
    if (batch != null) Engine.deliver(rejected, batch.stack(), batch.refund());
    check.accept(
        "failed_write_refund_conservation",
        batch != null && chest.getItem(0).getCount() == 7 && p.getMainHandItem().isEmpty());

    var secondChest = (Container) p.serverLevel().getBlockEntity(new BlockPos(11, 200, 4));
    ItemStack secondTerminal = new ItemStack(Content.advWirelessTerminal.get());
    secondTerminal.set(
        Content.boundPosComponent.get(),
        new WorldPos(p.serverLevel().dimension(), new BlockPos(12, 200, 4)));
    p.getInventory().setItem(11, secondTerminal);
    chest.setItem(0, ItemStack.EMPTY);
    batch = TomBridge.take(receipt, 2);
    check.accept(
        "multiple_terminals_do_not_roam_on_shortage",
        batch == null && secondChest.getItem(0).getCount() == 9);
    p.getInventory().setItem(10, secondTerminal);
    p.getInventory().setItem(11, terminal);
    batch = TomBridge.take(receipt, 2);
    check.accept(
        "multiple_terminals_inventory_order",
        batch != null && batch.stack().getCount() == 2 && secondChest.getItem(0).getCount() == 7);
    if (batch != null) batch.refund().accept(batch.stack());
    p.getInventory().setItem(10, terminal);
    p.getInventory().setItem(11, ItemStack.EMPTY);
    chest.setItem(0, new ItemStack(Items.STONE, 7));
    Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_ONLY);
    Engine.consumed(receipt, ItemStack.EMPTY);
    Engine.flush(p);
    check.accept(
        "inventory_only_never_extracts_remote",
        p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
    Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_FIRST);

    // Missing remote block/network is rejected without touching the real chest.
    p.serverLevel().setBlock(terminalPos, Blocks.AIR.defaultBlockState(), 3);
    check.accept(
        "removed_terminal_rejected",
        TomBridge.resolve(p) == null && chest.getItem(0).getCount() == 7);

    var nether = p.getServer().getLevel(Level.NETHER);
    nether.getChunk(0, 0); // Fixture creates a loaded target. The production mod never does this.
    nether.setBlock(terminalPos, Content.terminal.get().defaultBlockState(), 3);
    ItemStack across = new ItemStack(Content.advWirelessTerminal.get());
    across.set(Content.boundPosComponent.get(), new WorldPos(Level.NETHER, terminalPos));
    p.getInventory().setItem(10, across);
    check.accept(
        "cross_dimension_without_beacon_rejected",
        nether.isLoaded(terminalPos)
            && TomBridge.resolve(p) == null
            && chest.getItem(0).getCount() == 7);
    Engine.clear(p);
  }
}
