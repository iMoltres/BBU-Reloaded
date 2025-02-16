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

import lombok.Getter;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Arguments passed into the execute method
 */
public class CommandArgs {

    private final CommandSender commandSender;

    /**
     * base command
     */
    @Getter
    private final Command command;
    /**
     * label of the command
     */
    @Getter
    private final String label;
    /**
     * arguments of the command
     */
    @Getter
    private final String[] arguments;

    public CommandArgs(CommandSender commandSender, Command command, String label, String... arguments) {
        this.commandSender = commandSender;
        this.command = command;
        this.label = label;
        this.arguments = arguments;
    }

    /**
     * Do not try to cast objects except subclasses of {@link CommandSender}
     * otherwise {@link ClassCastException} will occur. Also casting for {@link Player}
     * or {@link org.bukkit.command.ConsoleCommandSender} isn't needed.
     *
     * @return sender of command as Player or CommandSender
     */
    @NotNull
    public <T extends CommandSender> T getSender() {
        return (T) commandSender;
    }

    // SOME GETTER METHODS FOR COMMON PRIMITIVE TYPES //

    /**
     * @param i index
     * @return indexed element or null if index out of bounds
     */
    @Nullable
    public String getArgument(int i) {
        return arguments.length > i && i >= 0 ? arguments[i] : null;
    }

    /**
     * @param i index
     * @return Integer if indexed element is primitive type of int
     * or 0 if element is null.
     */
    @NotNull
    public Integer getArgumentAsInt(int i) {
        return NumberUtils.toInt(this.getArgument(i));
    }

    /**
     * @param i index
     * @return Double if indexed element is primitive type of double
     * or 0 if element is null.
     */
    @NotNull
    public Double getArgumentAsDouble(int i) {
        return NumberUtils.toDouble(this.getArgument(i));
    }

    // ---------------------------------------------- //

    /**
     * @return true if command arguments are empty otherwise false
     */
    public boolean isArgumentsEmpty() {
        return arguments.length == 0;
    }

    /**
     * Sends message to sender without receiving command
     * sender.
     *
     * @param message to send
     */
    public void sendMessage(String message) {
        if (message == null) return;
        commandSender.sendMessage(message);
    }

    /**
     * Checks if command sender is console.
     *
     * @return true if sender is console otherwise false
     */
    public boolean isSenderConsole() {
        return !isSenderPlayer();
    }

    /**
     * Checks if command sender is player.
     *
     * @return true if sender is player otherwise false
     */
    public boolean isSenderPlayer() {
        return commandSender instanceof Player;
    }

    /**
     * Checks if command sender has specified permission.
     *
     * @param permission to check
     * @return true if sender has permission otherwise false
     */
    public boolean hasPermission(String permission) {
        return commandSender.hasPermission(permission);
    }

    /**
     * @return length of the arguments
     */
    public int getArgumentsLength() {
        return arguments.length;
    }
}