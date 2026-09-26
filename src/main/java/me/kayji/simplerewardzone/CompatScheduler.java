package me.kayji.simplerewardzone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

final class CompatScheduler {
    private final Plugin plugin;
    private final boolean folia;

    CompatScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = isFoliaServer();
    }

    boolean isFolia() {
        return folia;
    }

    CompatTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (!folia) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }
        long safeDelayTicks = Math.max(1L, delayTicks);
        long safePeriodTicks = Math.max(1L, periodTicks);
        Object scheduledTask = invokeScheduler("getGlobalRegionScheduler", "runAtFixedRate", new Class<?>[]{
                Plugin.class, Consumer.class, long.class, long.class
        }, plugin, taskConsumer(runnable), safeDelayTicks, safePeriodTicks);
        return cancelHandle(scheduledTask);
    }

    CompatTask runGlobalLater(Runnable runnable, long delayTicks) {
        if (!folia) {
            BukkitTask task = delayTicks <= 0
                    ? Bukkit.getScheduler().runTask(plugin, runnable)
                    : Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
            return task::cancel;
        }
        Object scheduledTask = delayTicks <= 0
                ? invokeScheduler("getGlobalRegionScheduler", "run", new Class<?>[]{
                Plugin.class, Consumer.class
        }, plugin, taskConsumer(runnable))
                : invokeScheduler("getGlobalRegionScheduler", "runDelayed", new Class<?>[]{
                Plugin.class, Consumer.class, long.class
        }, plugin, taskConsumer(runnable), delayTicks);
        return cancelHandle(scheduledTask);
    }

    void runAtLocation(Location location, Runnable runnable) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method run = regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            run.invoke(regionScheduler, plugin, location, taskConsumer(runnable));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Khong the chay task theo Folia region: " + schedulerError(exception));
        }
    }

    private Object invokeScheduler(String schedulerGetter, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Object scheduler = Bukkit.class.getMethod(schedulerGetter).invoke(null);
            Method method = scheduler.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(scheduler, args);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Khong the chay Folia scheduler: " + schedulerError(exception));
            return null;
        }
    }

    private Consumer<Object> taskConsumer(Runnable runnable) {
        return ignored -> runnable.run();
    }

    private CompatTask cancelHandle(Object scheduledTask) {
        return () -> {
            if (scheduledTask == null) {
                return;
            }
            try {
                scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
            } catch (ReflectiveOperationException ignored) {
            }
        };
    }

    private boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private String schedulerError(ReflectiveOperationException exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }
}
