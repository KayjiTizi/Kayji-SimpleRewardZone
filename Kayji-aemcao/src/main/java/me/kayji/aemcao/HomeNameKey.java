package me.kayji.aemcao;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

final class HomeNameKey {
    static NamespacedKey KEY;
    static final PersistentDataType<String, String> TYPE = PersistentDataType.STRING;

    private HomeNameKey() {
    }
}
