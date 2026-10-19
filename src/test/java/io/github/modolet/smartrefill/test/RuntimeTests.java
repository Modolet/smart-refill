/**
 * @file RuntimeTests.java @brief Real full-pack integrated-server regression probes. @author XiangX
 *     (Modolet) <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill.test;

import com.tom.storagemod.Content;
import com.tom.storagemod.components.WorldPos;
import io.github.modolet.smartrefill.*;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotReference;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("smartrefill_runtime_tests")
public final class RuntimeTests {
  private boolean started, done;
  private volatile boolean reopenRequested, reopened;
  private int ticks;
  private final AccessoryTerminalProbes accessoryTerminals = new AccessoryTerminalProbes();
  private int failures, passes;
  private final BlockPos base = new BlockPos(8, 200, 8);
  private final BlockPos terminalPos = new BlockPos(6, 200, 4);
  private final BlockPos chestPos = new BlockPos(4, 200, 4);

  public RuntimeTests() {
    NeoForge.EVENT_BUS.addListener(this::client);
    NeoForge.EVENT_BUS.addListener(this::server);
  }

  private void log(String s) {
    System.out.println("SMARTREFILL_TEST " + s);
    try {
      Files.writeString(
          Path.of("smartrefill-tests.log"),
          s + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void client(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (reopenRequested && mc.getSingleplayerServer() == null && mc.player == null) {
      reopenRequested = false;
      reopened = true;
      log("REOPEN_ISOLATED_WORLD");
      mc.setScreen(new TitleScreen());
      mc.createWorldOpenFlows().openWorld("SmartRefill-CI", () -> mc.setScreen(new TitleScreen()));
      return;
    }
    if (!started && mc.screen instanceof TitleScreen) {
      started = true;
      try {
        ClientChecks.run();
        check("localized_resources_and_config_screen", true);
      } catch (Throwable failure) {
        failure.printStackTrace();
        check("localized_resources_and_config_screen", false);
      }
      log("CREATE_NEW_ISOLATED_WORLD");
      mc.createWorldOpenFlows()
          .createFreshLevel(
              "SmartRefill-CI",
              new LevelSettings(
                  "SmartRefill-CI",
                  GameType.SURVIVAL,
                  false,
                  Difficulty.NORMAL,
                  true,
                  new GameRules(),
                  WorldDataConfiguration.DEFAULT),
              new WorldOptions(613842L, false, false),
              WorldPresets::createNormalWorldDimensions,
              mc.screen);
    }
  }

  private void check(String name, boolean condition) {
    if (condition) {
      passes++;
      log("PASS " + name);
    } else {
      failures++;
      log("FAIL " + name);
    }
  }

  private void reset(ServerPlayer p) {
    Engine.clear(p);
    p.getInventory().clearContent();
    p.getInventory().selected = 0;
    p.inventoryMenu.setCarried(ItemStack.EMPTY);
    p.containerMenu = p.inventoryMenu;
    p.setGameMode(GameType.SURVIVAL);
    p.setHealth(20);
    p.invulnerableTime = 0;
    p.fallDistance = 0;
    p.setDeltaMovement(Vec3.ZERO);
    p.teleportTo(p.serverLevel(), 8.5, 200, 10.5, 0, 0);
    AccessoriesCapability.getOptionally(p)
        .ifPresent(
            cap -> cap.getContainers().values().forEach(c -> c.getAccessories().clearContent()));
    Engine.clear(p);
  }

  private void setup(ServerPlayer p, Item item, int reserves) {
    reset(p);
    p.getInventory().setItem(0, new ItemStack(item));
    if (reserves > 0) p.getInventory().setItem(9, new ItemStack(item, reserves));
  }

  private boolean place(ServerPlayer p) {
    var level = p.serverLevel();
    level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
    p.teleportTo(level, 8.5, 200, 10.5, 0, 0);
    var hit =
        new BlockHitResult(
            Vec3.atCenterOf(base.below()).add(0, 0.5, 0), Direction.UP, base.below(), false);
    return p.gameMode
        .useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit)
        .consumesAction();
  }

  private void click(ServerPlayer p, ClickType type, int button) {
    var menu = p.inventoryMenu;
    p.connection.handleContainerClick(
        new ServerboundContainerClickPacket(
            menu.containerId,
            menu.getStateId(),
            36,
            button,
            type,
            ItemStack.EMPTY,
            new Int2ObjectOpenHashMap<>()));
  }

  private void suite(ServerPlayer p) {
    setup(p, Items.STONE, 10);
    check("block_real_place_consumed", place(p) && p.getMainHandItem().isEmpty());
    Engine.flush(p);
    check(
        "block_refill_conservation",
        p.getMainHandItem().is(Items.STONE)
            && p.getMainHandItem().getCount() == 10
            && p.getInventory().getItem(9).isEmpty());

    setup(p, Items.APPLE, 5);
    p.getFoodData().setFoodLevel(1);
    ItemStack meal = p.getMainHandItem();
    p.setItemInHand(InteractionHand.MAIN_HAND, meal.finishUsingItem(p.serverLevel(), p));
    Engine.flush(p);
    check(
        "food_finish_refill",
        p.getMainHandItem().is(Items.APPLE) && p.getMainHandItem().getCount() == 5);

    setup(p, Items.SNOWBALL, 5);
    p.gameMode.useItem(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
    Engine.flush(p);
    check(
        "right_click_refill",
        p.getMainHandItem().is(Items.SNOWBALL) && p.getMainHandItem().getCount() == 5);

    setup(p, Items.IRON_PICKAXE, 1);
    ItemStack tool = p.getMainHandItem();
    tool.setDamageValue(tool.getMaxDamage() - 1);
    tool.hurtAndBreak(10, p, EquipmentSlot.MAINHAND);
    Engine.flush(p);
    check(
        "tool_break_refill",
        p.getMainHandItem().is(Items.IRON_PICKAXE) && p.getInventory().getItem(9).isEmpty());

    reset(p);
    ItemStack helmet = new ItemStack(Items.IRON_HELMET);
    helmet.setDamageValue(helmet.getMaxDamage() - 1);
    p.setItemSlot(EquipmentSlot.HEAD, helmet);
    p.getInventory().setItem(9, new ItemStack(Items.IRON_HELMET));
    helmet.hurtAndBreak(10, p, EquipmentSlot.HEAD);
    Engine.flush(p);
    check(
        "armor_break_refill",
        p.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
            && p.getInventory().getItem(9).isEmpty());

    for (ClickType type :
        List.of(
            ClickType.PICKUP,
            ClickType.QUICK_MOVE,
            ClickType.SWAP,
            ClickType.QUICK_CRAFT,
            ClickType.THROW,
            ClickType.PICKUP_ALL)) {
      setup(p, Items.STONE, 8);
      click(p, type, type == ClickType.SWAP ? 1 : 0);
      Engine.flush(p);
      check(
          "manual_no_refill_" + type,
          type == ClickType.QUICK_MOVE
              ? p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 9
              : p.getInventory().getItem(9).getCount() == 8);
    }
    setup(p, Items.STONE, 8);
    p.connection.handlePlayerAction(
        new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ZERO,
            Direction.DOWN));
    Engine.flush(p);
    check(
        "manual_F_no_refill",
        p.getMainHandItem().isEmpty()
            && p.getOffhandItem().is(Items.STONE)
            && p.getInventory().getItem(9).getCount() == 8);
    setup(p, Items.STONE, 8);
    p.connection.handlePlayerAction(
        new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN));
    Engine.flush(p);
    check(
        "manual_Q_no_refill",
        p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 8);

    setup(p, Items.STONE, 8);
    place(p);
    click(p, ClickType.PICKUP, 0);
    Engine.flush(p);
    check(
        "same_tick_click_cancels_pending",
        p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 8);
    setup(p, Items.STONE, 8);
    place(p);
    p.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
    Engine.flush(p);
    check(
        "same_tick_switch_cancels_old_slot",
        p.getInventory().getItem(0).isEmpty()
            && p.getInventory().getItem(1).isEmpty()
            && p.getInventory().getItem(9).getCount() == 8);
    setup(p, Items.STONE, 8);
    place(p);
    p.getInventory().setItem(0, ItemStack.EMPTY);
    Engine.flush(p);
    check(
        "same_tick_container_touch_cancels",
        p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 8);
    setup(p, Items.STONE, 8);
    place(p);
    try {
      Class<?> payloadType = Class.forName("dev.terminalmc.clientsort.network.payload.SortPayload");
      Object payload =
          payloadType
              .getConstructor(int.class, int[].class)
              .newInstance(p.inventoryMenu.containerId, new int[] {36, 37});
      Class.forName("dev.terminalmc.clientsort.network.handler.SortHandler")
          .getMethod(
              "handle", payloadType, net.minecraft.server.MinecraftServer.class, ServerPlayer.class)
          .invoke(null, payload, p.getServer(), p);
      Engine.flush(p);
      check(
          "clientsort_custom_request_cancels_pending",
          p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 8);
    } catch (Exception e) {
      check("clientsort_custom_request_cancels_pending", false);
      log("SORT_ERROR " + e);
    }

    setup(p, Items.STONE, 8);
    place(p);
    Engine.clear(p);
    Engine.flush(p);
    check(
        "lifecycle_clear_cancels_pending",
        p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 8);
    setup(p, Items.STONE, 0);
    ItemStack named = new ItemStack(Items.STONE, 12);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("Do not take"));
    p.getInventory().setItem(9, named);
    place(p);
    Engine.flush(p);
    check(
        "component_name_exact",
        p.getMainHandItem().isEmpty() && p.getInventory().getItem(9).getCount() == 12);

    reset(p);
    var cap = AccessoriesCapability.getOptionally(p).orElseThrow();
    SlotReference ref = null;
    ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
    outer:
    for (var c : cap.getContainers().values())
      for (int i = 0; i < c.getSize(); i++) {
        var candidate = c.createReference(i);
        if (AccessoriesAPI.canInsertIntoSlot(totem, candidate)) {
          ref = candidate;
          break outer;
        }
      }
    check("accessory_valid_slot_found", ref != null);
    if (ref != null) {
      ref.setStack(totem);
      p.getInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
      log(
          "ACCESSORY_BEFORE ref="
              + ref
              + " equipped="
              + cap.getEquipped(s -> s.is(Items.TOTEM_OF_UNDYING))
              + " hands="
              + Arrays.toString(
                  Arrays.stream(InteractionHand.values())
                      .map(h -> h + ":" + p.getItemInHand(h))
                      .toArray()));
      p.hurt(p.damageSources().generic(), 1000);
      boolean triggered = p.isAlive() && ref.getStack().isEmpty();
      log(
          "ACCESSORY_AFTER_HURT health="
              + p.getHealth()
              + " target="
              + ref.getStack()
              + " reserve="
              + p.getInventory().getItem(9));
      Engine.flush(p);
      log(
          "ACCESSORY_AFTER_FLUSH health="
              + p.getHealth()
              + " target="
              + ref.getStack()
              + " reserve="
              + p.getInventory().getItem(9));
      check(
          "accessory_real_trigger_refill",
          triggered
              && ref.getStack().is(Items.TOTEM_OF_UNDYING)
              && p.getInventory().getItem(9).isEmpty());
      p.getInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
      p.invulnerableTime = 0;
      p.setHealth(20);
      p.hurt(p.damageSources().generic(), 1000);
      Engine.flush(p);
      check(
          "accessory_second_trigger_exactly_one",
          p.isAlive()
              && ref.getStack().is(Items.TOTEM_OF_UNDYING)
              && p.getInventory().getItem(9).isEmpty());
      p.getInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
      ref.setStack(ItemStack.EMPTY);
      Engine.flush(p);
      check(
          "accessory_manual_unequip_no_refill",
          ref.getStack().isEmpty() && p.getInventory().getItem(9).getCount() == 1);
      ref.setStack(new ItemStack(Items.TOTEM_OF_UNDYING));
      p.setHealth(20);
      p.invulnerableTime = 0;
      p.hurt(p.damageSources().generic(), 1000);
      ref.setStack(ItemStack.EMPTY);
      Engine.flush(p);
      check(
          "accessory_same_tick_manual_touch_cancels",
          ref.getStack().isEmpty() && p.getInventory().getItem(9).getCount() == 1);
    }
  }

  private ItemStack bound(ServerPlayer p, BlockPos pos) {
    ItemStack terminal = new ItemStack(Content.advWirelessTerminal.get());
    terminal.set(Content.boundPosComponent.get(), new WorldPos(p.serverLevel().dimension(), pos));
    return terminal;
  }

  private void aimAtTerminal(ServerPlayer p) {
    p.teleportTo(p.serverLevel(), 9.5, 200, 4.5, 90, 0);
    Vec3 toward =
        p.serverLevel()
            .getBlockState(terminalPos)
            .getShape(p.serverLevel(), terminalPos)
            .bounds()
            .getCenter()
            .add(Vec3.atLowerCornerOf(terminalPos))
            .subtract(p.getEyePosition());
    p.setYRot((float) (Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90));
    p.setXRot(
        (float)
            -Math.toDegrees(
                Math.atan2(toward.y, Math.sqrt(toward.x * toward.x + toward.z * toward.z))));
    p.setYHeadRot(p.getYRot());
    p.yHeadRotO = p.getYRot();
    p.setOldPosAndRot();
  }

  private void networkSuite(ServerPlayer p, int stage) {
    var chest = (Container) p.serverLevel().getBlockEntity(chestPos);
    switch (stage) {
      case 200 -> {
        setup(p, Items.STONE, 0);
        p.getInventory().setItem(10, bound(p, terminalPos));
        check("advanced_terminal_permission", TomBridge.resolve(p) != null);
        place(p);
        Engine.flush(p);
        check(
            "wireless_real_extract_closed_gui",
            p.getMainHandItem().getCount() == 64 && chest.getItem(0).isEmpty());
      }
      case 201 -> {
        setup(p, Items.STONE, 3);
        p.getInventory().setItem(10, bound(p, terminalPos));
        chest.setItem(0, new ItemStack(Items.STONE, 10));
      }
      case 202 -> {
        place(p);
        Engine.flush(p);
        check(
            "inventory_first_partial_network_conservation",
            p.getMainHandItem().getCount() == 13
                && p.getInventory().getItem(9).isEmpty()
                && chest.getItem(0).isEmpty());
      }
      case 203 -> {
        setup(p, Items.STONE, 0);
        p.getInventory().setItem(10, bound(p, terminalPos));
      }
      case 204 -> {
        place(p);
        Engine.flush(p);
        check("wireless_out_of_stock", p.getMainHandItem().isEmpty());
      }
      case 205 -> {
        setup(p, Items.STONE, 0);
        p.getInventory().setItem(10, new ItemStack(Content.advWirelessTerminal.get()));
        chest.setItem(0, new ItemStack(Items.STONE, 7));
      }
      case 206 -> {
        place(p);
        Engine.flush(p);
        check(
            "wireless_unbound_rejected",
            p.getMainHandItem().isEmpty() && chest.getItem(0).getCount() == 7);
        reset(p);
        p.getInventory().setItem(10, bound(p, terminalPos));
        p.teleportTo(p.serverLevel(), 400, 200, 400, 0, 0);
        check("wireless_out_of_range_rejected", TomBridge.resolve(p) == null);
        reset(p);
        BlockPos unloaded = new BlockPos(123456, 200, 123456);
        boolean loaded = p.serverLevel().isLoaded(unloaded);
        p.getInventory().setItem(10, bound(p, unloaded));
        check(
            "wireless_no_force_load",
            !loaded && TomBridge.resolve(p) == null && !p.serverLevel().isLoaded(unloaded));
      }
      case 207 -> {
        setup(p, Items.STONE, 0);
        p.getInventory().setItem(10, new ItemStack(Content.wirelessTerminal.get()));
        place(p);
        aimAtTerminal(p);
        check("ordinary_terminal_line_of_sight", TomBridge.resolve(p) != null);
        Engine.flush(p);
        check(
            "ordinary_terminal_real_extraction",
            p.getMainHandItem().getCount() == 7 && chest.getItem(0).isEmpty());
      }
      case 209 -> {
        // Explicit source-priority branch, with stable network stock prepared one tick earlier.
        place(p);
        Engine.flush(p);
        check(
            "wireless_first_priority",
            p.getMainHandItem().getCount() == 64
                && p.getInventory().getItem(9).getCount() == 3
                && chest.getItem(0).isEmpty());
        Settings.SOURCE_PRIORITY.set(Settings.Priority.INVENTORY_FIRST);
        reset(p);
        chest.setItem(0, new ItemStack(Items.STONE, 7));
      }
      case 208 -> {
        setup(p, Items.STONE, 3);
        p.getInventory().setItem(10, bound(p, terminalPos));
        chest.setItem(0, new ItemStack(Items.STONE, 64));
        Settings.SOURCE_PRIORITY.set(Settings.Priority.WIRELESS_FIRST);
      }
      default -> {}
    }
  }

  private int inventoryStones(ServerPlayer p) {
    int count = 0;
    for (int i = 0; i < 36; i++) {
      var stack = p.getInventory().getItem(i);
      if (stack.is(Items.STONE)) count += stack.getCount();
    }
    return count;
  }

  private void server(ServerTickEvent.Post event) {
    if (done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
    ServerPlayer p = event.getServer().getPlayerList().getPlayers().getFirst();
    try {
      ticks++;
      if (reopened) {
        check("actual_relogin_no_phantom_refill", inventoryStones(p) == 8);
        done = true;
        log("RESULT passes=" + passes + " failures=" + failures);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        return;
      }
      if (ticks == 1) {
        for (int x = 2; x < 13; x++)
          for (int z = 2; z < 13; z++)
            p.serverLevel().setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.teleportTo(p.serverLevel(), 8.5, 200, 8.5, 0, 0);
        log("ALLOW_LOADING_PROTECTION_TO_SETTLE");
      }
      if (ticks == 60) {
        suite(p);
        reset(p);
        for (int x = 2; x < 13; x++)
          for (int z = 2; z < 13; z++)
            p.serverLevel().setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
        p.serverLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        ((Container) p.serverLevel().getBlockEntity(chestPos))
            .setItem(0, new ItemStack(Items.STONE, 64));
        p.serverLevel().setBlock(new BlockPos(11, 200, 4), Blocks.CHEST.defaultBlockState(), 3);
        ((Container) p.serverLevel().getBlockEntity(new BlockPos(11, 200, 4)))
            .setItem(0, new ItemStack(Items.STONE, 9));
        p.serverLevel()
            .setBlock(
                new BlockPos(12, 200, 4),
                Content.terminal
                    .get()
                    .defaultBlockState()
                    .setValue(
                        com.tom.storagemod.block.AbstractStorageTerminalBlock.FACING,
                        Direction.WEST),
                3);
        p.serverLevel()
            .setBlock(new BlockPos(5, 200, 4), Content.connector.get().defaultBlockState(), 3);
        p.serverLevel()
            .setBlock(
                terminalPos,
                Content.terminal
                    .get()
                    .defaultBlockState()
                    .setValue(
                        com.tom.storagemod.block.AbstractStorageTerminalBlock.FACING,
                        Direction.WEST),
                3);
        log("NETWORK_WARMUP");
      }
      if (ticks == 200) {
        check("network_actor_alive", p.isAlive() && !p.isRemoved());
      }
      if (ticks >= 200 && ticks <= 209) networkSuite(p, ticks);
      if (ticks >= 210 && ticks <= 233)
        accessoryTerminals.run(
            p,
            terminalPos,
            chestPos,
            ticks - 210,
            this::check,
            this::reset,
            this::place,
            this::aimAtTerminal);
      if (ticks == 240) {
        ExtraProbes.run(p, terminalPos, chestPos, this::check);
        setup(p, Items.STONE, 8);
        place(p);
        var overworld = p.serverLevel();
        p.teleportTo(p.getServer().getLevel(Level.NETHER), 8.5, 200, 8.5, 0, 0);
        Engine.flush(p);
        check(
            "actual_dimension_change_cancels_pending",
            p.serverLevel().dimension() == Level.NETHER
                && p.getInventory().getItem(0).isEmpty()
                && p.getInventory().getItem(9).getCount() == 8);
        p.teleportTo(overworld, 8.5, 200, 8.5, 0, 0);
        Engine.flush(p);
        check(
            "dimension_return_no_phantom_refill",
            p.getInventory().getItem(0).isEmpty() && p.getInventory().getItem(9).getCount() == 8);
      }
      if (ticks == 250) {
        setup(p, Items.STONE, 8);
        place(p);
        p.serverLevel()
            .getGameRules()
            .getRule(GameRules.RULE_KEEPINVENTORY)
            .set(true, p.getServer());
        // Exercise actual vanilla death/clone lifecycle without LoadingProtection cancelling hurt.
        p.setHealth(0);
        p.die(p.damageSources().fellOutOfWorld());
        Engine.flush(p);
        check(
            "actual_death_no_pending_transfer",
            !p.isAlive()
                && p.getInventory().getItem(0).isEmpty()
                && p.getInventory().getItem(9).getCount() == 8);
        p.connection.handleClientCommand(
            new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        var respawned = event.getServer().getPlayerList().getPlayer(p.getUUID());
        Engine.flush(respawned);
        check(
            "actual_respawn_no_phantom_refill",
            respawned != p
                && respawned.isAlive()
                && respawned.getInventory().getItem(0).isEmpty()
                && respawned.getInventory().getItem(9).getCount() == 8);
      }
      if (ticks == 290) {
        setup(p, Items.STONE, 8);
        place(p);
        p.connection.disconnect(Component.literal("SmartRefill isolated reconnect test"));
        Engine.flush(p);
        check("actual_disconnect_no_pending_transfer", inventoryStones(p) == 8);
        reopenRequested = true;
      }
    } catch (Throwable e) {
      done = true;
      log("EXCEPTION " + e);
      e.printStackTrace();
      Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
  }
}
