package me.kayji.simplerewardzone;

import org.bukkit.Location;

final class DisabledRewardHologramService implements RewardHologramService {
    @Override
    public void show(RewardTemplate template, Location location) {
    }

    @Override
    public void update(RewardTemplate template) {
    }

    @Override
    public void remove() {
    }
}
