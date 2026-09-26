package me.kayji.aemcao;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class HomeGuiHolder implements InventoryHolder {
    private final int page;

    HomeGuiHolder(int page) {
        this.page = page;
    }

    int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
