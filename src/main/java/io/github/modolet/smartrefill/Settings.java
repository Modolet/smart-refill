/**
 * @file Settings.java @brief Server configuration and translated client labels.
 * @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
 * @date 2026-10-19
 */
package io.github.modolet.smartrefill;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

public final class Settings {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.BooleanValue ENABLED,
      BLOCKS,
      FOOD,
      TOOLS,
      ARMOR,
      TOTEMS,
      ACCESSORIES,
      WIRELESS;
  public static final ModConfigSpec.EnumValue<Priority> SOURCE_PRIORITY;

  public enum Priority implements TranslatableEnum {
    INVENTORY_FIRST,
    WIRELESS_FIRST,
    INVENTORY_ONLY;

    @Override
    public Component getTranslatedName() {
      return Component.translatable(
          "smartrefill.configuration.sourcePriority." + name().toLowerCase(Locale.ROOT));
    }
  }

  static {
    ModConfigSpec.Builder b = new ModConfigSpec.Builder();
    ENABLED =
        b.translation("smartrefill.configuration.enabled")
            .comment("Enable genuine-consumption refill on the server.")
            .define("enabled", true);
    BLOCKS =
        b.translation("smartrefill.configuration.useRefill")
            .comment("Refill after right-click use actually exhausts the stack.")
            .define("useRefill", true);
    FOOD =
        b.translation("smartrefill.configuration.foodRefill")
            .comment(
                "Refill an empty slot after eating or drinking; never overwrite a bowl or bottle.")
            .define("foodRefill", true);
    TOOLS =
        b.translation("smartrefill.configuration.toolRefill")
            .comment(
                "Match the item and every component except damage; replacement must have durability"
                    + " left.")
            .define("toolRefill", true);
    ARMOR =
        b.translation("smartrefill.configuration.armorRefill")
            .comment("Replace broken armor using the same component rules as tools.")
            .define("armorRefill", true);
    TOTEMS =
        b.translation("smartrefill.configuration.totemRefill")
            .comment("Refill one vanilla totem after actual death protection consumes it.")
            .define("totemRefill", true);
    ACCESSORIES =
        b.translation("smartrefill.configuration.accessoryTotemRefill")
            .comment(
                "Refill the original Accessories functional slot after actual totem consumption;"
                    + " requires a compatible totem-use mod such as Accessorify.")
            .define("accessoryTotemRefill", true);
    WIRELESS =
        b.translation("smartrefill.configuration.wirelessRefill")
            .comment(
                "Allow terminals in normal inventory or offhand. Ordinary terminals require line of"
                    + " sight; accessory-carried terminals are unsupported.")
            .define("wirelessRefill", true);
    SOURCE_PRIORITY =
        b.translation("smartrefill.configuration.sourcePriority")
            .comment(
                "Select the first accessible network in inventory order, offhand last. A shortage"
                    + " never switches networks.")
            .defineEnum("sourcePriority", Priority.INVENTORY_FIRST);
    SPEC = b.build();
  }

  private Settings() {}
}
