package me.vorty.vortyclan.gui;

import me.vorty.vortyclan.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A chest GUI whose slots can run actions when clicked. */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Menu(String title, int rows) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return this;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    public Menu fillEmpty() {
        ItemStack filler = Items.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
        return this;
    }

    public void handleClick(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getSlot());
        if (a != null) a.accept(e);
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
