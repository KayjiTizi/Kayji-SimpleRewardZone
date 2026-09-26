package me.kayji.aemcao;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

final class TeleportTask {
    private final Location from;
    private final BukkitTask task;

    TeleportTask(Player player, BukkitTask task) {
        this.from = player.getLocation().clone();
        this.task = task;
    }

    Location from() {
        return from;
    }

    void cancel() {
        task.cancel();
    }
}
