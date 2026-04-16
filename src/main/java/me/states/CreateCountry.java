package me.states;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Connection db;
    private Economy econ = null;
    private final Map<String, String> claims = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        setupEconomy();
        setupDatabase();
        loadClaims();
        
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getCommand("state").setExecutor(this);
        getCommand("state").setTabCompleter(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(">>> Плагин на Страны ЗАПУЩЕН! <<<");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdir();
            db = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/states.db");
            Statement s = db.createStatement();
            s.execute("CREATE TABLE IF NOT EXISTS states (name TEXT PRIMARY KEY, leader TEXT, r INT, g INT, b INT)");
            s.execute("CREATE TABLE IF NOT EXISTS claims (world TEXT, x INT, z INT, owner TEXT)");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadClaims() {
        claims.clear();
        try (ResultSet rs = db.createStatement().executeQuery("SELECT * FROM claims")) {
            while (rs.next()) {
                claims.put(rs.getString("world") + ":" + rs.getInt("x") + ":" + rs.getInt("z"), rs.getString("owner"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length < 1) {
                p.sendMessage("§e[!] Используй: /create <название> [R G B]");
                return true;
            }
            
            String name = args[0];
            int r = 255, g = 255, b = 255;
            
            if (args.length >= 4) {
                try {
                    r = Integer.parseInt(args[1]);
                    g = Integer.parseInt(args[2]);
                    b = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    p.sendMessage("§c[!] RGB должны быть числами. Использую белый цвет.");
                }
            }

            if (econ != null && !econ.has(p, 10000)) {
                p.sendMessage("§c[✘] У вас нет $10,000 для создания страны!");
                return true;
            }

            try {
                if (econ != null) econ.withdrawPlayer(p, 10000);
                saveState(name, p.getUniqueId().toString(), Color.fromRGB(r, g, b));
                p.sendMessage("§a[✔] Страна §l" + name + " §aуспешно создана!");
                p.sendMessage("§7Цвет RGB: " + r + ", " + g + ", " + b);
            } catch (SQLException e) {
                p.sendMessage("§c[!] Ошибка БД: Такое название уже занято!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
                String state = getPlayerState(p);
                if (state == null) {
                    p.sendMessage("§c[!] Вы не лидер страны! Создайте её: /create <имя>");
                    return true;
                }
                if (econ != null && econ.withdrawPlayer(p, 500).transactionSuccess()) {
                    Chunk c = p.getLocation().getChunk();
                    String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
                    claims.put(key, state);
                    saveClaim(c, state);
                    p.sendMessage("§a[✔] Чанк теперь под защитой страны §l" + state);
                } else p.sendMessage("§c[✘] Нужно $500!");
                return true;
            }
            p.sendMessage("§b--- [ Система Стран ] ---");
            p.sendMessage("§f/create <имя> §7- Создать ($10k)");
            p.sendMessage("§f/state claim §7- Захват чанка ($500)");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length == 1) return Arrays.asList("claim", "help").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length == 1) return Collections.singletonList("Название_Страны");
            if (args.length >= 2 && args.length <= 4) return Collections.singletonList("255");
        }
        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Chunk c = e.getBlock().getChunk();
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key)) {
            String owner = claims.get(key);
            if (!owner.equals(getPlayerState(e.getPlayer()))) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§c[!] Это территория страны §l" + owner);
            }
        }
    }

    private String getPlayerState(Player p) {
        try (PreparedStatement ps = db.prepareStatement("SELECT name FROM states WHERE leader = ?")) {
            ps.setString(1, p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void saveState(String n, String l, Color c) throws SQLException {
        PreparedStatement ps = db.prepareStatement("INSERT OR REPLACE INTO states VALUES (?, ?, ?, ?, ?)");
        ps.setString(1, n); ps.setString(2, l); ps.setInt(3, c.getRed()); ps.setInt(4, c.getGreen()); ps.setInt(5, c.getBlue());
        ps.execute();
    }

    private void saveClaim(Chunk c, String o) {
        try (PreparedStatement ps = db.prepareStatement("INSERT OR REPLACE INTO claims VALUES (?, ?, ?, ?)")) {
            ps.setString(1, c.getWorld().getName()); ps.setInt(2, c.getX()); ps.setInt(3, c.getZ()); ps.setString(4, o);
            ps.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
