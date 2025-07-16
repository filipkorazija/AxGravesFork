package com.artillexstudios.axgraves.commands.subcommands;

import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.utils.VipUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGES;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

public enum SubCommandList {
    INSTANCE;

    public void subCommand(@NotNull CommandSender sender) {
        if (SpawnedGraves.getGraves().isEmpty()) {
            MESSAGEUTILS.sendLang(sender, "grave-list.no-graves");
            return;
        }

        MESSAGEUTILS.sendFormatted(sender, MESSAGES.getString("grave-list.header"));

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (!sender.hasPermission("axgraves.list.other") &&
                    sender instanceof Player &&
                    !grave.getPlayer().equals(sender)
            ) continue;

            final Location l = grave.getLocation();
            int dTime = VipUtils.getDespawnTime(grave.getPlayer());

            final Map<String, String> map = Map.of("%player%", grave.getPlayerName(),
                    "%world%", l.getWorld().getName(),
                    "%x%", "" + l.getBlockX(),
                    "%y%", "" + l.getBlockY(),
                    "%z%", "" + l.getBlockZ(),
                    "%time%", StringUtils.formatTime(dTime != -1 ? (dTime * 1_000L - (System.currentTimeMillis() - grave.getSpawned())) : System.currentTimeMillis() - grave.getSpawned()));

            BaseComponent[] text = TextComponent.fromLegacyText(StringUtils.formatToString(MESSAGES.getString("grave-list.grave"), new HashMap<>(map)));
            for (BaseComponent component : text) {
                // Use locale-independent formatting to ensure decimal points are always "." not ","
                component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    String.format(Locale.US, "/axgraves tp %s %.6f %.6f %.6f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ())));
            }
            sender.spigot().sendMessage(text);
        }
    }
}
