package me.kayji.simplerewardzone;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class RewardGuiHolder implements InventoryHolder {
    enum Type {
        MAIN,
        TEMPLATES,
        SPAWN_SELECT,
        EDITOR,
        SETTINGS
    }

    private final Type type;
    private final String templateId;

    RewardGuiHolder(Type type, String templateId) {
        this.type = type;
        this.templateId = templateId;
    }

    Type type() {
        return type;
    }

    String templateId() {
        return templateId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
