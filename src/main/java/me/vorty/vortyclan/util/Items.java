package me.vorty.vortyclan.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {

    private Items() {
    }

    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) apply(meta, name, lore);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            apply(skull, name, lore);
            it.setItemMeta(skull);
        }
        return it;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, "§r ", null);
    }

    private static void apply(ItemMeta meta, String name, List<String> lore) {
        if (name != null) meta.setDisplayName("§r" + name);
        if (lore != null) {
            List<String> l = new ArrayList<>(lore.size());
            for (String s : lore) l.add("§r" + s);
            meta.setLore(l);
        }
        meta.addItemFlags(ItemFlag.values());
    }

    /** Wool/concrete block that represents a chat color code (0-9, a-f). */
    public static Material colorMaterial(char c) {
        return switch (Character.toLowerCase(c)) {
            case '0' -> Material.BLACK_WOOL;
            case '1' -> Material.BLUE_WOOL;
            case '2' -> Material.GREEN_WOOL;
            case '3' -> Material.CYAN_WOOL;
            case '4' -> Material.RED_WOOL;
            case '5' -> Material.PURPLE_WOOL;
            case '6' -> Material.ORANGE_WOOL;
            case '7' -> Material.LIGHT_GRAY_WOOL;
            case '8' -> Material.GRAY_WOOL;
            case '9' -> Material.BLUE_CONCRETE;
            case 'a' -> Material.LIME_WOOL;
            case 'b' -> Material.LIGHT_BLUE_CONCRETE;
            case 'c' -> Material.RED_CONCRETE;
            case 'd' -> Material.PINK_WOOL;
            case 'e' -> Material.YELLOW_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}
