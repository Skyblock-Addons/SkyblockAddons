package codes.biscuit.skyblockaddons.misc.scheduler;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.core.Feature;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.*;

public class Scheduler {

    private final SkyblockAddons main = SkyblockAddons.getInstance();
    private long totalTicks = 0;
    private final Map<Long, Set<Command>> queue = new HashMap<>();

    /**
     * This class is a little something I came up with in order to schedule things
     * by client ticks reliably.
     *
     * @param commandType What you want to schedule
     * @param delaySeconds The delay in seconds (must be greater than 0)
     */
    public void schedule(CommandType commandType, int delaySeconds, Object... data) {
        // If the delay isn't greater than zero, the command never gets executed.
        if (!(delaySeconds > 0)) {
            throw new IllegalArgumentException("Delay must be greater than zero!");
        }

        long ticks = totalTicks + (delaySeconds * 20L);
        Set<Command> commands = queue.get(ticks);
        if (commands != null) {
            for (Command command : commands) {
                if (command.getCommandType() == commandType) {
                    command.addCount(data);
                    return;
                }
            }
            commands.add(new Command(commandType, data));
        } else {
            Set<Command> commandSet = new HashSet<>();
            commandSet.add(new Command(commandType, data));
            queue.put(ticks, commandSet);
        }
    }

    /**
     * Removes all queued full inventory warnings.
     */
    public void removeQueuedFullInventoryWarnings() {
        Iterator<Map.Entry<Long, Set<Command>>> queueIterator = queue.entrySet().iterator();
        List<Long> resetTitleFeatureTicks = new LinkedList<>();

        while (queueIterator.hasNext()) {
            Map.Entry<Long, Set<Command>> entry = queueIterator.next();

            if (entry.getValue().removeIf(command -> CommandType.SHOW_FULL_INVENTORY_WARNING.equals(command.commandType))) {
                resetTitleFeatureTicks.add(entry.getKey() + main.getConfigValues().getWarningSeconds() * 20L);
            }

            // Remove the corresponding reset title feature command.
            if (resetTitleFeatureTicks.contains(entry.getKey())) {
                Set<Command> commands = entry.getValue();
                Iterator<Command> commandIterator = commands.iterator();

                while (commandIterator.hasNext()) {
                    Command command = commandIterator.next();
                    if (command.commandType.equals(CommandType.RESET_TITLE_FEATURE)) {
                        commandIterator.remove();
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent()
    public void ticker(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            totalTicks++;
            Set<Command> commands = queue.get(totalTicks);
            if (commands != null) {
                for (Command command : commands) {
                    for (int times = 0; times < command.getCount().getValue(); times++) {
                        command.getCommandType().execute();
                    }
                }
                queue.remove(totalTicks);
            }
        }
    }

    @Getter
    private static class Command {
        private final CommandType commandType;
        private final MutableInt count = new MutableInt(1);
        private final Map<Integer, Object[]> countData = new HashMap<>();

        private Command(CommandType commandType, Object... data) {
            this.commandType = commandType;
            if (data.length > 0) {
                countData.put(1, data);
            }
        }

        private void addCount(Object... data) {
            count.increment();
            if (data.length > 0) {
                countData.put(count.getValue(), data);
            }
        }

        Object[] getData(int count) {
            return countData.get(count);
        }
    }

    public enum CommandType {
        RESET_TITLE_FEATURE,
        RESET_SUBTITLE_FEATURE,
        ERASE_UPDATE_MESSAGE,
        SHOW_FULL_INVENTORY_WARNING,
        CHECK_FOR_UPDATE;

        public void execute() {
            SkyblockAddons main = SkyblockAddons.getInstance();
            if (this == SHOW_FULL_INVENTORY_WARNING) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld == null || mc.thePlayer == null || !main.getUtils().isOnSkyblock()) {
                    return;
                }

                main.getInventoryUtils().showFullInventoryWarning();

                // Schedule a repeat if needed.
                if (main.getConfigValues().isEnabled(Feature.REPEAT_FULL_INVENTORY_WARNING)) {
                    main.getScheduler().schedule(Scheduler.CommandType.SHOW_FULL_INVENTORY_WARNING, 10);
                    main.getScheduler().schedule(Scheduler.CommandType.RESET_TITLE_FEATURE, 10 + main.getConfigValues().getWarningSeconds());
                }
            } else if (this == RESET_TITLE_FEATURE) {
                main.getRenderListener().setTitleFeature(null);
            } else if (this == RESET_SUBTITLE_FEATURE) {
                main.getRenderListener().setSubtitleFeature(null);
            } else if (this == ERASE_UPDATE_MESSAGE) {
                main.getRenderListener().setUpdateMessageDisplayed(true);
            } else if (this == CHECK_FOR_UPDATE) {
                main.getUpdater().checkForUpdate();
            }
        }
    }
}
