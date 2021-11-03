/*
 * Copyright (c) 2021 - Despical
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package me.imoltres.bbu.utils.command;

import me.imoltres.bbu.utils.CC;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CommandFramework implements CommandExecutor, TabCompleter {

    public static TextComponent ONLY_BY_PLAYERS = CC.translate("&cThis command is only executable by players.");
    public static TextComponent ONLY_BY_CONSOLE = CC.translate("&cThis command is only executable by console.");
    public static TextComponent NO_PERMISSION = CC.translate("&cYou don't have enough permission to execute this command.");
    public static TextComponent SHORT_OR_LONG_ARG_SIZE = CC.translate("&cRequired argument length is less or greater than needed.");
    public static TextComponent WAIT_BEFORE_USING_AGAIN = CC.translate("&cYou have to wait before using this command again.");
    /**
     * Main instance of framework.
     */
    @NotNull
    private final Plugin plugin;
    /**
     * Map of registered commands by framework.
     */
    @NotNull
    private final Map<CommandInfo, Map.Entry<Method, Object>> commands = new HashMap<>();
    /**
     * Map of registered tab completions by framework.
     */
    @NotNull
    private final Map<CommandInfo, Map.Entry<Method, Object>> completions = new HashMap<>();
    /**
     * Map of registered command cooldowns by framework.
     */
    @NotNull
    private final Map<CommandSender, Long> cooldowns = new HashMap<>();
    /**
     * Consumer to accept if there is no matched commands related framework.
     */
    @Nullable
    private Consumer<CommandArgs> anyMatchConsumer;

    // Error Message Handler
    /**
     * Default command map of Bukkit.
     */
    @Nullable
    private CommandMap commandMap;

    public CommandFramework(@NotNull Plugin plugin) {
        this.plugin = plugin;

        if (plugin.getServer().getPluginManager() instanceof SimplePluginManager) {
            SimplePluginManager manager = (SimplePluginManager) plugin.getServer().getPluginManager();

            try {
                Field field = SimplePluginManager.class.getDeclaredField("commandMap");
                field.setAccessible(true);

                commandMap = (CommandMap) field.get(manager);
            } catch (IllegalArgumentException | SecurityException | IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Consumer to accept if there is no matched commands related framework.
     *
     * @param anyMatchConsumer to be accepted if there is no matched commands
     */
    public void setAnyMatch(@NotNull Consumer<CommandArgs> anyMatchConsumer) {
        this.anyMatchConsumer = anyMatchConsumer;
    }

    /**
     * Register command methods in object class.
     *
     * @param classes command classes
     */
    public void registerCommands(@NotNull Class<? extends Command>... classes) {
        for (Class<? extends Command> c : classes) {
            try {
                Command instance = c.getDeclaredConstructor().newInstance();
                CommandInfo command = instance.getClass().getAnnotation(CommandInfo.class);
                Method method = instance.getClass().getMethod("execute", CommandArgs.class);
                if (command != null) {
                    if (method.getParameterTypes().length > 0 && method.getParameterTypes()[0] != CommandArgs.class) {
                        System.out.println("Invalid command in class '" + instance.getClass().getSimpleName() + "'");
                        return;
                    }

                    registerCommand(command, method, instance);
                } else {
                    System.out.println("Invalid command in class '" + instance.getClass().getSimpleName() + "'");
                    return;
                }

                for (Method m : instance.getClass().getMethods()) {
                    if (m.getAnnotation(Completer.class) != null) {
                        completions.put(command, new AbstractMap.SimpleEntry<>(m, instance));
                    }
                }
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Register the command with given parameters.
     *
     * @param command  of the main object
     * @param method   that command will run
     * @param instance of the method above
     */
    private void registerCommand(CommandInfo command, Method method, Object instance) {
        commands.put(command, new AbstractMap.SimpleEntry<>(method, instance));

        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);

            String splittedCommand = command.name().split("\\.")[0];

            PluginCommand pluginCommand = constructor.newInstance(splittedCommand, plugin);
            pluginCommand.setTabCompleter(this);
            pluginCommand.setExecutor(this);
            pluginCommand.setUsage(command.usage());
            pluginCommand.setPermission(command.permission());
            pluginCommand.setDescription(command.desc());
            pluginCommand.setAliases(Arrays.asList(command.aliases()));

            commandMap.register(splittedCommand, pluginCommand);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd, @NotNull String label, String[] args) {
        for (Map.Entry<CommandInfo, Map.Entry<Method, Object>> entry : commands.entrySet()) {
            CommandInfo command = entry.getKey();
            String[] splitted = command.name().split("\\.");
            String allArgs = args.length == 0 ? "" : String.join(".", Arrays.copyOfRange(args, 0, splitted.length - 1));
            String cmdName = (command.name().contains(".") ? splitted[0] : cmd.getName()) + (splitted.length == 1 && allArgs.isEmpty() ? "" : "." + allArgs);

            if (command.name().equalsIgnoreCase(cmdName) || Stream.of(command.aliases()).anyMatch(cmdName::equalsIgnoreCase)) {
                if (!sender.hasPermission(command.permission())) {
                    sender.sendMessage(NO_PERMISSION);
                    return true;
                }

                if (command.senderType() == CommandInfo.SenderType.PLAYER && !(sender instanceof Player)) {
                    sender.sendMessage(ONLY_BY_PLAYERS);
                    return true;
                }

                if (command.senderType() == CommandInfo.SenderType.CONSOLE && sender instanceof Player) {
                    sender.sendMessage(ONLY_BY_CONSOLE);
                    return true;
                }

                if (cooldowns.containsKey(sender)) {
                    if (command.cooldown() > 0 && ((System.currentTimeMillis() - cooldowns.get(sender)) / 1000) % 60 <= command.cooldown()) {
                        sender.sendMessage(WAIT_BEFORE_USING_AGAIN);
                        return true;
                    } else {
                        cooldowns.remove(sender);
                    }
                } else {
                    cooldowns.put(sender, System.currentTimeMillis());
                }

                String[] newArgs = Arrays.copyOfRange(args, splitted.length - 1, args.length);

                if (args.length >= command.min() + splitted.length - 1 && newArgs.length <= (command.max() == -1 ? newArgs.length + 1 : command.max())) {
                    try {
                        entry.getValue().getKey().invoke(entry.getValue().getValue(), new CommandArgs(sender, cmd, label, newArgs));
                        return true;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                        return true;
                    }
                } else {
                    sender.sendMessage(SHORT_OR_LONG_ARG_SIZE);
                    return true;
                }
            }
        }

        if (anyMatchConsumer != null) {
            anyMatchConsumer.accept(new CommandArgs(sender, cmd, label, args));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, String[] args) {
        for (Map.Entry<CommandInfo, Map.Entry<Method, Object>> entry : completions.entrySet()) {
            CommandInfo commandInfo = entry.getKey();

            if (command.getName().equalsIgnoreCase(commandInfo.name()) || Stream.of(commandInfo.aliases()).anyMatch(command.getName()::equalsIgnoreCase)) {
                try {
                    Object instance = entry.getValue().getKey().invoke(entry.getValue().getValue(), new CommandArgs(sender, command, label, args));

                    return (List<String>) instance;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    /**
     * Get the copied list of registered commands.
     *
     * @return list of commands.
     */
    @NotNull
    public List<CommandInfo> getCommands() {
        return new ArrayList<>(commands.keySet());
    }
}