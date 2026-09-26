package me.kayji.simplerewardzone;

import org.bukkit.Location;

interface RewardHologramService {
    void show(RewardTemplate template, Location location);

    void update(RewardTemplate template);

    void remove();
}
