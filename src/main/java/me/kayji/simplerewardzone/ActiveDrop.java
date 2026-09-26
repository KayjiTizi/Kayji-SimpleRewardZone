package me.kayji.simplerewardzone;

import org.bukkit.Location;

final class ActiveDrop {
    private final String templateId;
    private final Location location;
    private final long spawnedAtMillis;

    ActiveDrop(String templateId, Location location, long spawnedAtMillis) {
        this.templateId = templateId;
        this.location = location;
        this.spawnedAtMillis = spawnedAtMillis;
    }

    String templateId() {
        return templateId;
    }

    Location location() {
        return location.clone();
    }

    long spawnedAtMillis() {
        return spawnedAtMillis;
    }
}
