package me.kayji.simplerewardzone;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RewardTemplate {
    private final String id;
    private String displayName;
    private Material icon;
    private final List<ItemStack> items;

    RewardTemplate(String id, String displayName, Material icon, List<ItemStack> items) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon == null ? Material.CHEST : icon;
        this.items = new ArrayList<>();
        setItems(items);
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    void displayName(String displayName) {
        this.displayName = displayName;
    }

    Material icon() {
        return icon;
    }

    void icon(Material icon) {
        this.icon = icon == null ? Material.CHEST : icon;
    }

    List<ItemStack> items() {
        List<ItemStack> clones = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                clones.add(item.clone());
            }
        }
        return Collections.unmodifiableList(clones);
    }

    void setItems(List<ItemStack> newItems) {
        items.clear();
        if (newItems == null) {
            return;
        }
        for (ItemStack item : newItems) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
    }

    boolean isEmpty() {
        return items.isEmpty();
    }
}
