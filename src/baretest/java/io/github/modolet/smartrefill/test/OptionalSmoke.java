/**
 * @file OptionalSmoke.java @brief Runtime absence test without Accessories, Tom, Curios, or
 *     ClientSort. @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com> @date
 *     2026-10-19
 */
package io.github.modolet.smartrefill.test;

import io.github.modolet.smartrefill.Engine;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("smartrefill_runtime_tests")
public final class OptionalSmoke {
  private boolean started, done, clientPassed;

  public OptionalSmoke() {
    NeoForge.EVENT_BUS.addListener(
        (ClientTickEvent.Post e) -> {
          var mc = Minecraft.getInstance();
          if (started || !(mc.screen instanceof TitleScreen)) return;
          started = true;
          try {
            ClientChecks.run();
            clientPassed = true;
          } catch (Throwable failure) {
            failure.printStackTrace();
          }
          mc.createWorldOpenFlows()
              .createFreshLevel(
                  "SmartRefill-Bare",
                  new LevelSettings(
                      "SmartRefill-Bare",
                      GameType.SURVIVAL,
                      false,
                      Difficulty.PEACEFUL,
                      true,
                      new GameRules(),
                      WorldDataConfiguration.DEFAULT),
                  new WorldOptions(136, false, false),
                  WorldPresets::createNormalWorldDimensions,
                  mc.screen);
        });
    NeoForge.EVENT_BUS.addListener(
        (ServerTickEvent.Post e) -> {
          if (done || e.getServer().getPlayerList().getPlayers().isEmpty()) return;
          done = true;
          boolean passed = false;
          try {
            var p = e.getServer().getPlayerList().getPlayers().getFirst();
            p.getInventory().clearContent();
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(Items.STONE));
            p.getInventory().setItem(9, new ItemStack(Items.STONE, 8));
            var pos = new BlockPos(8, 200, 8);
            p.serverLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            p.teleportTo(p.serverLevel(), 8.5, 200, 10.5, 0, 0);
            var hit =
                new BlockHitResult(
                    Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
            p.gameMode.useItemOn(
                p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            Engine.flush(p);
            passed =
                clientPassed
                    && !ModList.get().isLoaded("accessories")
                    && !ModList.get().isLoaded("toms_storage")
                    && !ModList.get().isLoaded("clientsort")
                    && p.getMainHandItem().getCount() == 8
                    && p.getInventory().getItem(9).isEmpty();
            Files.writeString(
                Path.of("smartrefill-tests.log"),
                "OPTIONAL_ABSENCE " + (passed ? "PASS" : "FAIL") + "\n");
          } catch (Throwable ex) {
            ex.printStackTrace();
            try {
              Files.writeString(
                  Path.of("smartrefill-tests.log"), "OPTIONAL_ABSENCE EXCEPTION " + ex + "\n");
            } catch (Exception ignored) {
            }
          }
          System.out.println("SMARTREFILL_OPTIONAL_TEST " + passed);
          Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        });
  }
}
