/**
 * @file TomBridge.java @brief Tom 2.4.2 access checks and real extraction. @author XiangX (Modolet)
 *     <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import com.tom.storagemod.Config;
import com.tom.storagemod.Content;
import com.tom.storagemod.StorageTags;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.inventory.StoredItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;

public final class TomBridge {
  public static StorageTerminalBlockEntity resolve(ServerPlayer p) {
    // Inventory first, then offhand, then Accessories slot name/index. No network roaming.
    for (int i = 0; i < 36; i++) {
      var terminal = resolve(p, p.getInventory().getItem(i));
      if (terminal != null) return terminal;
    }
    var offhand = resolve(p, p.getOffhandItem());
    if (offhand != null) return offhand;
    if (Settings.ACCESSORY_WIRELESS.get() && ModList.get().isLoaded("accessories")) {
      for (ItemStack stack : AccessoryBridge.carried(p, TomBridge::isTerminal)) {
        var terminal = resolve(p, stack);
        if (terminal != null) return terminal;
      }
    }
    return null;
  }

  private static boolean isTerminal(ItemStack stack) {
    return stack.is(Content.advWirelessTerminal.get()) || stack.is(Content.wirelessTerminal.get());
  }

  private static StorageTerminalBlockEntity resolve(ServerPlayer p, ItemStack carried) {
    ServerLevel level;
    BlockPos pos;
    if (carried.is(Content.advWirelessTerminal.get())) {
      var binding = carried.get(Content.boundPosComponent.get());
      if (binding == null) return null;
      level = p.getServer().getLevel(binding.dim());
      pos = binding.pos();
    } else if (carried.is(Content.wirelessTerminal.get())) {
      var hit = p.pick(Config.get().wirelessRange, 0, true);
      if (!(hit instanceof BlockHitResult blockHit)
          || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
      level = p.serverLevel();
      pos = blockHit.getBlockPos();
    } else return null;
    if (level == null
        || !level.isLoaded(pos)
        || !level.getBlockState(pos).is(StorageTags.REMOTE_ACTIVATE)) return null;
    // Tom alone decides range, dimensions, beacon access and extra-slot recognition.
    // In BMC5 its existing Curios/Accessories compatibility bridge recognizes worn terminals.
    if (level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal
        && terminal.canInteractWith(p, false)) return terminal;
    return null;
  }

  public static Engine.Batch take(Engine.Receipt r, int wanted) {
    var terminal = resolve(r.player());
    if (terminal == null) return null;
    // pullStack relies on Tom's slot tracker. With no open GUI that tracker can be cold.
    // Request and run the original refresh only when a real consumption needs extraction.
    terminal.getStacks();
    terminal.updateServer();
    ItemStack template = r.template();
    if (r.cause() == Engine.Cause.BREAK) {
      // Cache only selects a component-exact query. It never supplies credited item counts.
      template =
          terminal.getStacks().keySet().stream()
              .map(StoredItemStack::getStack)
              .filter(s -> Engine.matches(s, r.template(), r.cause()))
              .sorted(java.util.Comparator.comparingInt(ItemStack::getDamageValue))
              .findFirst()
              .orElse(ItemStack.EMPTY);
      if (template.isEmpty()) return null;
    }
    var extracted =
        terminal.pullStack(
            new StoredItemStack(template.copyWithCount(1)),
            Math.min(wanted, template.getMaxStackSize()));
    if (extracted == null) return null;
    ItemStack actual = extracted.getActualStack();
    if (actual.isEmpty()) return null;
    if (!Engine.matches(actual, r.template(), r.cause())) {
      Engine.recover(r.player(), terminal.pushStack(actual));
      return null;
    }
    return new Engine.Batch(
        actual, unused -> Engine.recover(r.player(), terminal.pushStack(unused)));
  }

  private TomBridge() {}
}
