/**
 * @file SmartRefillClient.java @brief Client-only NeoForge configuration screen registration.
 * @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
 * @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "smartrefill", dist = Dist.CLIENT)
public final class SmartRefillClient {
  public SmartRefillClient(ModContainer container) {
    container.registerExtensionPoint(
        IConfigScreenFactory.class, (IConfigScreenFactory) ConfigurationScreen::new);
  }
}
