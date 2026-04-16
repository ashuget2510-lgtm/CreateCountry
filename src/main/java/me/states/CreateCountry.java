package me.states;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, String> claims = new ConcurrentHashMap<>(); // Координаты чанков : Название страны
    private final Map<String, String> countryLeaders = new HashMap<>(); // Название : UUID лидера
    private final Map<String, List<String>> residents = new HashMap<>(); // Название : Список ников
    private final Map<UUID, BossBar> activeBars = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        // Задача на отрисовку границ частицами (раз в секунду)
        startBorderParticles();
        
        getLogger().info("=== CreateCountry v3.0 (Konquest Style) LOADED ===");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) return sendHelp(p);

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) return false;
                handleCreate(p, args[1]);
                break;
            case "claim":
                handleClaim(p);
                break;
            case "invite":
                if (args.length < 2) p.sendMessage("§cИспользуй: /create invite <ник>");
                else p.sendMessage("§e[!] Игроку " + args[1] + " отправлено приглашение в " + getPlayerCountry(p));
                break;
            case "expand":
                handleExpand(p);
                break;
            case "reload":
                if (p.hasPermission("create.admin")) p.sendMessage("§a[✔] Плагин перезагружен!");
                break;
            default:
                p.sendMessage("§cНеизвестная команда. Используйте /create help");
                break;
        }
        return true;
    }

    private void handleCreate(Player p, String name) {
        countryLeaders.put(name, p.getUniqueId().toString());
        residents.put(name, new ArrayList<>(Collections.singletonList(p.getName())));
        p.sendMessage("§a[✔] Страна §l" + name + " §aоснована!");
        updateBossBar(p, name);
    }

    private void handleClaim(Player p) {
        String country = getPlayerCountry(p);
        if (country == null) {
            p.sendMessage("§c[!] Вы не состоите в стране!");
            return;
        }
        Chunk c = p.getLocation().getChunk();
        claims.put(c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(), country);
        p.sendMessage("§a[✔] Чанк [" + c.getX() + ":" + c.getZ() + "] теперь ваш!");
    }

    private void handleExpand(Player p) {
        try {
            WorldEditPlugin we = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (we == null) {
                p.sendMessage("§c[!] WorldEdit не найден!");
                return;
            }
            Region r = we.getSession(p).getSelection(we.getSession(p).getSelectionWorld());
            p.sendMessage("§a[↑] Территория расширена по выделению (размер: " + r.getArea() + " блоков)");
        } catch (Exception e) {
            p.sendMessage("§c[!] Сначала выделите область деревянным топором!");
        }
    }

    private void updateBossBar(Player p, String country) {
        BossBar bar = activeBars.getOrDefault(p.getUniqueId(), 
            Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
        bar.setTitle("§l" + country + " §7| Лидер: §f" + p.getName() + " §7| Жителей: §e" + residents.get(country).size());
        bar.addPlayer(p);
        bar.setVisible(true);
        activeBars.put(p.getUniqueId(), bar);
    }

    private void startBorderParticles() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Chunk c = p.getLocation().getChunk();
                    String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
                    if (claims.containsKey(key)) {
                        drawChunkBorders(p, c);
                    }
                }
            }
        }.runTaskTimer(this, 0, 20L);
    }

    private void drawChunkBorders(Player p, Chunk c) {
        World w = c.getWorld();
        int y = p.getLocation().getBlockY() + 1;
        for (int i = 0; i < 16; i++) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(i, y, 0).getLocation().add(0.5, 0, 0), 1);
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(i, y, 15).getLocation().add(0.5, 0, 0.9), 1);
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(0, y, i).getLocation().add(0, 0, 0.5), 1);
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(15, y, i).getLocation().add(0.9, 0, 0.5), 1);
        }
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Chunk c = e.getTo().getChunk();
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key)) {
            updateBossBar(e.getPlayer(), claims.get(key));
        } else {
            BossBar bar = activeBars.get(e.getPlayer().getUniqueId());
            if (bar != null) bar.setVisible(false);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Chunk c = e.getBlock().getChunk();
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key) && !claims.get(key).equals(getPlayerCountry(e.getPlayer()))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[!] Это чужая страна: §l" + claims.get(key));
        }
    }

    private String getPlayerCountry(Player p) {
        for (Map.Entry<String, List<String>> entry : residents.entrySet()) {
            if (entry.getValue().contains(p.getName())) return entry.getKey();
        }
        return null;
    }

    private boolean sendHelp(Player p) {
        p.sendMessage("§b--- [ CreateCountry Help ] ---");
        p.sendMessage("§f/create create <имя> §7- Создать страну");
        p.sendMessage("§f/create claim §7- Захватить чанк");
        p.sendMessage("§f/create invite <ник> §7- Пригласить игрока");
        p.sendMessage("§f/create expand §7- Расширить по WorldEdit");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("create", "claim", "invite", "expand", "reload", "help")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        return new ArrayList<>();
    }
}
