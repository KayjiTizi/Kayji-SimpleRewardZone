package me.kayji.aemcao;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

final class HomeGuiActionKey {
    static NamespacedKey KEY;
    static final PersistentDataType<String, String> TYPE = PersistentDataType.STRING;

    private HomeGuiActionKey() {
    }
}
