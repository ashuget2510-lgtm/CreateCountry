package me.states;

import org.bukkit.Color;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements CommandExecutor, TabCompleter {

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getLogger().info("Плагин запущен на версии 1.21.8");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length < 1) {
                p.sendMessage("§cИспользуй: /create <название>");
                return true;
            }
            p.sendMessage("§aСтрана " + args[0] + " создана (1.21.8)");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length == 1) {
                return Collections.singletonList("Название_Страны");
            }
        }
        // Возвращаем ПУСТОЙ список, чтобы Minecraft НЕ предлагал ники игроков
        return new ArrayList<>();
    }
}
