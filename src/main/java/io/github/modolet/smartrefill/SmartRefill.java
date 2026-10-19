/**
 * @file SmartRefill.java @brief Server event registration. @author XiangX (Modolet)
 *     <52348766+Modolet@users.noreply.github.com> @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@Mod("smartrefill")
public final class SmartRefill {
  public SmartRefill(ModContainer container) {
    container.registerConfig(ModConfig.Type.COMMON, Settings.SPEC);
    NeoForge.EVENT_BUS.addListener(
        (PlayerTickEvent.Post event) -> {
          if (event.getEntity() instanceof ServerPlayer player) Engine.flush(player);
        });
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerLoggedOutEvent e) -> Engine.clear(e.getEntity()));
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerLoggedInEvent e) -> Engine.clear(e.getEntity()));
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerRespawnEvent e) -> Engine.clear(e.getEntity()));
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerChangedDimensionEvent e) -> Engine.clear(e.getEntity()));
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.Clone e) -> {
          Engine.clear(e.getOriginal());
          Engine.clear(e.getEntity());
        });
  }
}
