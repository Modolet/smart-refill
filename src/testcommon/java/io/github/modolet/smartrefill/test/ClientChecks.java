/**
 * @file ClientChecks.java @brief Probe actual packaged translations and configuration extension.
 * @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
 * @date 2026-10-19
 */
package io.github.modolet.smartrefill.test;

import io.github.modolet.smartrefill.Settings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class ClientChecks {
  public static void run() {
    var mc = Minecraft.getInstance();
    var en = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us"), false);
    var zh = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", "zh_cn"), false);
    for (String name :
        List.of(
            "enabled",
            "useRefill",
            "foodRefill",
            "toolRefill",
            "armorRefill",
            "totemRefill",
            "accessoryTotemRefill",
            "wirelessRefill",
            "sourcePriority")) {
      translated(en, zh, "smartrefill.configuration." + name);
      translated(en, zh, "smartrefill.configuration." + name + ".tooltip");
    }
    translated(en, zh, "smartrefill.configuration.title");
    translated(en, zh, "fml.menu.mods.info.description.smartrefill");
    for (var priority : Settings.Priority.values()) {
      var text = priority.getTranslatedName();
      if (!(text.getContents() instanceof TranslatableContents content))
        throw new AssertionError("Untranslated priority");
      translated(en, zh, content.getKey());
    }
    var container = ModList.get().getModContainerById("smartrefill").orElseThrow();
    var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
    if (!(factory.createScreen(container, mc.screen) instanceof ConfigurationScreen))
      throw new AssertionError("Missing configuration screen");
  }

  private static void translated(ClientLanguage en, ClientLanguage zh, String key) {
    String english = en.getOrDefault(key, key);
    String chinese = zh.getOrDefault(key, key);
    if (english.equals(key) || chinese.equals(key) || english.equals(chinese))
      throw new AssertionError("Missing distinct translations: " + key);
  }

  private ClientChecks() {}
}
