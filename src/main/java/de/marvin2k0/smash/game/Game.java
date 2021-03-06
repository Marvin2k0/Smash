package de.marvin2k0.smash.game;

import de.marvin2k0.smash.Smash;
import de.marvin2k0.smash.characters.CharacterUtils;
import de.marvin2k0.smash.item.SmashItem;
import de.marvin2k0.smash.item.items.*;
import de.marvin2k0.smash.utils.CountdownTimer;
import de.marvin2k0.smash.utils.ItemUtils;
import de.marvin2k0.smash.utils.Locations;
import de.marvin2k0.smash.utils.Text;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;

import java.util.*;

public class Game
{
    public static final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    public static final Objective objective = scoreboard.registerNewObjective("damage", "dummy");
    private static final Team team = scoreboard.registerNewTeam("damage");
    public static final ArrayList<Game> games = new ArrayList<>();
    public static final ArrayList<GamePlayer> gameplayers = new ArrayList<>();
    private static final ArrayList<Player> players = new ArrayList<>();
    private static final int MIN_PLAYERS = Integer.parseInt(Text.get("minplayers", false));
    private static final int MAX_PLAYERS = Integer.parseInt(Text.get("maxplayers", false));

    public ArrayList<GamePlayer> prot;
    public ArrayList<Location> signs;
    public HashMap<Location, Material> blocks;
    public ArrayList<Location> itemSpawns;
    private final String name;
    private boolean hasStarted;
    public boolean inGame;
    private Objective liveObj;
    private int lastLoc;
    public GamePlayer hunter;
    private CountdownTimer timer;
    private boolean ranked;

    private Game(String name, boolean ranked)
    {
        this.prot = new ArrayList<>();
        this.itemSpawns = new ArrayList<>();
        this.blocks = new HashMap<>();
        this.name = name;
        this.hasStarted = false;
        this.ranked = ranked;
        this.inGame = false;
        this.lastLoc = -1;
        this.liveObj = scoreboard.registerNewObjective(getName(), "", "§9Leben");
        this.liveObj.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.signs = loadSigns();

        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    public void join(Player player)
    {
        if (inGame)
        {
            player.sendMessage(Text.get("alreadystarted"));
            return;
        }

        if (players.size() >= MAX_PLAYERS)
        {
            player.sendMessage(Text.get("gamefull"));
            return;
        }

        if (Smash.gameplayers.containsKey(player))
        {
            player.sendMessage(Text.get("alreadyingame"));
            return;
        }

        if (!Smash.plugin.getConfig().isSet("games." + getName() + ".lobby") || !Smash.plugin.getConfig().isSet("games." + getName() + ".spawn"))
        {
            player.sendMessage(Text.get("lobbynotset"));
            return;
        }

        players.add(player);

        GamePlayer gamePlayer = new GamePlayer(this, player, player.getLocation(), player.getInventory().getContents(), player.getLevel());
        gamePlayer.setGame(this);
        gameplayers.add(gamePlayer);
        gamePlayer.inLobby = true;

        sendMessage(Text.get("joinmessage").replace("%player%", player.getName()));
        player.teleport(Locations.get("games." + getName() + ".lobby"));
        player.getInventory().clear();
        player.setFoodLevel(20);
        player.setHealth(player.getHealthScale());
        player.setLevel(0);
        player.getInventory().setItem(4, ItemUtils.create(Material.NETHER_STAR, "§9Charakter wählen"));
        player.setGameMode(GameMode.SURVIVAL);

        if (signs != null)
        {
            for (Location loc : signs)
            {
                if (!loc.getBlock().getType().toString().contains("SIGN"))
                {
                    continue;
                }

                Sign sign = (Sign) loc.getBlock().getState();
                sign.setLine(2, "§a" + players.size() + "/" + Text.get("maxplayers", false));
                sign.update();
            }
        }

        if (players.size() >= MIN_PLAYERS && !hasStarted)
        {
            start();
        }
    }

    public void reset()
    {
        if (!Smash.plugin.getConfig().isSet("games." + getName() + ".spawn"))
            return;

        Location spawn = Locations.get("games." + getName() + ".spawn");


        for (Entity e : spawn.getWorld().getNearbyEntities(spawn, 75, 20, 75))
        {
            if (!(e instanceof Player))
                e.remove();
        }

        resetBlocks();

        for (Player player : players)
            leave(player, false);

        gameplayers.clear();
        players.clear();
        prot.clear();
        blocks.clear();
        itemSpawns.clear();
        hunter = null;
        hasStarted = false;
        inGame = false;

        if (timer != null)
            timer.cancel();

        if (signs == null)
            return;

        for (Location loc : signs)
        {
            if (!loc.getBlock().getType().toString().contains("SIGN"))
                continue;

            Sign sign = (Sign) loc.getBlock().getState();
            sign.setLine(2, "§a" + players.size() + "/" + Text.get("maxplayers", false));
            sign.update();
        }
    }

    public void resetBlocks()
    {
        for (Map.Entry<Location, Material> entry : blocks.entrySet())
        {
            Location loc = entry.getKey();
            Material type = entry.getValue();

            loc.getBlock().setType(type);
        }
    }

    private void check()
    {
        if (gameplayers.size() <= 1)
        {
            if (gameplayers.size() != 0)
            {
                String winner = gameplayers.get(0).getName();

                for (Player player : players)
                {
                    player.sendTitle(Text.get("wintitle", false).replace("%player%", winner), Text.get("winsubtitle", false).replace("%player%", winner), 20, 100, 20);
                }
            }

            reset();
        }
    }

    public int getLevel()
    {
        int level = 5;

        if (Smash.plugin.getConfig().isSet("games." + getName() + ".level"))
            level = Smash.plugin.getConfig().getInt("games." + getName() + ".level");

        return level;
    }

    public void die(GamePlayer gp)
    {
        gp.reduceLives();
        gp.addDamage(-gp.getDamage());
        liveObj.getScore("§7" + gp.getName()).setScore(gp.getLives());
        GameListener.arr.remove(gp.getPlayer());
        gp.getPlayer().getInventory().clear();

        if (ranked)
        {
            gp.addDeath();

            if (gp.getLastDamage() != null)
            {
                gp.getLastDamage().addKill();
                gp.getLastDamage().sendMessage(Text.get("killed").replace("%player%", gp.getName()));
                gp.setLastDamage(null);
            }
        }

        if (gp.getLives() <= 0)
        {
            gp.getPlayer().setGameMode(GameMode.SPECTATOR);
            gp.getPlayer().getInventory().clear();
            gameplayers.remove(gp);
            check();
        }
        else
        {
            gp.getPlayer().spigot().respawn();
            gp.getPlayer().teleport(Locations.get("games." + getName() + ".spawn"));
            giveItems(gp);
        }
    }

    private void startGame()
    {
        if (gameplayers.size() <= 1)
        {
            reset();
            return;
        }

        inGame = true;

        if (signs != null)
        {
            for (Location loc : signs)
            {
                if (!loc.getBlock().getType().toString().contains("SIGN"))
                {
                    continue;
                }

                Sign sign = (Sign) loc.getBlock().getState();
                sign.setLine(2, "§cIm Spiel");
                sign.update();
            }
        }

        for (GamePlayer gp : gameplayers)
        {
            team.addPlayer(gp.getPlayer());
            gp.getPlayer().setAllowFlight(true);
            gp.getPlayer().getInventory().clear();
            gp.getPlayer().setLevel(0);
            setDamageTag(gp);
            giveItems(gp);

            gp.getPlayer().setScoreboard(scoreboard);
            gp.getPlayer().teleport(Locations.get("games." + getName() + ".spawn"));

            prot.add(gp);
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(Smash.plugin, () -> prot.clear(), 10 * 20);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(Smash.plugin, () -> {
            if (inGame)
                spawnItems();
        }, 0, 5 * 20);
    }

    String[] smashItems = {
            "singularity",
            "launcher",
            "reset",
            "tnt",
            "monster",
            "ice",
            "rod",
            "shotbow",
            "bow",
            "jetpack",
            "flower",
            "soup",
            "pearl",
            "dia",
            "gold",
            "iron",
            "stone",
            "wood",
            "sugar",
            "apple",
            "bread",
            "chicken",
            "pork",
            "ice"
    };

    Random random = new Random();

    private void spawnItems()
    {
        if (itemSpawns.size() == 0)
            return;

        int randomItem = random.nextInt(smashItems.length);
        int randomLoc = random.nextInt(itemSpawns.size());
        String itemName = smashItems[randomItem];
        SmashItem item = null;

        switch (itemName)
        {
            case "singularity":
                item = new SingularityItem();
                break;
            case "launcher":
                item = new DiamondPickaxe();
                break;
            case "reset":
                item = new MapReset();
                break;
            case "tnt":
                item = new TNTItem();
                break;
            case "monster":
                item = new MonsterSpawnItem();
                break;
            case "ice":
                item = new SlowIceItem();
                break;
            case "rod":
                item = new RodItem();
                break;
            case "shotbow":
                item = new ShotBowItem();
                break;
            case "bow":
                item = new SimpleBowItem();
                break;
            case "jetpack":
                item = new JetpackItem();
                break;
            case "flower":
                item = new FireFlowerItem();
                break;
            case "soup":
                item = new SoupItem();
                break;
            case "pearl":
                item = new EnderpearlItem();
                break;
            case "dia":
                item = new DiamondSword();
                break;
            case "gold":
                item = new GoldenSword();
                break;
            case "iron":
                item = new IronSword();
                break;
            case "sugar":
                item = new SpeedSugar();
                break;
            case "stone":
                item = new StoneSword();
                break;
            case "wood":
                item = new WoodenSword();
                break;
            case "apple":
                item = new AppleItem();
                break;
            case "bread":
                item = new BreadItem();
                break;
            case "chicken":
                item = new ChickenItem();
                break;
            case "pork":
                item = new PorkItem();
                break;
            case "steak":
                item = new SteakItem();
                break;
        }

        Location randomLocation = itemSpawns.get(randomLoc);

        double x = random.nextInt(10) - 5;
        double z = random.nextInt(10) - 5;
        double y = randomLocation.getWorld().getHighestBlockYAt((int) x, (int) z) + 2;

        Location dropLocation = randomLocation.add(x, 0, z);
        dropLocation.setY(y);

        lastLoc = randomLoc;

        item.drop(dropLocation);

        SmashItem finalItem = item;

        System.out.println(item.getItem().getItemStack().getType() + " bei " + dropLocation.getX() + " " + dropLocation.getY() + " " + dropLocation.getZ() + " gespawnt");

        Bukkit.getScheduler().scheduleSyncDelayedTask(Smash.plugin, () -> finalItem.getItem().remove(), 50 * 20);
    }

    public void addSign(Sign sign)
    {
        Locations.setLocation("games." + getName() + ".signs." + UUID.randomUUID(), sign.getLocation());
        Smash.plugin.saveConfig();

        signs = loadSigns();
    }

    private ArrayList<Location> loadSigns()
    {
        if (!Smash.plugin.getConfig().isSet("games." + getName() + ".signs"))
            return null;

        ArrayList<Location> signs = new ArrayList<>();

        for (Map.Entry<String, Object> entry : Smash.plugin.getConfig().getConfigurationSection("games." + getName() + ".signs").getValues(false).entrySet())
        {
            signs.add(Locations.get("games." + getName() + ".signs." + entry.getKey()));
        }

        return signs;
    }

    private void giveItems(GamePlayer gp)
    {
        ItemStack schutz = ItemUtils.create(Material.GLASS, Text.get("schutzitem", false));
        gp.getPlayer().getInventory().clear();
        gp.getPlayer().getInventory().setItem(8, schutz);
    }

    public void setDamageTag(GamePlayer gp)
    {
        String name = gp.getName();

        objective.setDisplayName("%");
        objective.getScore(name).setScore(0);
        liveObj.getScore("§7" + gp.getName()).setScore(gp.getLives());

        gp.getPlayer().setScoreboard(scoreboard);
    }

    private void start()
    {
        hasStarted = true;

        timer = new CountdownTimer(Smash.plugin, 30,
                () -> {
                },
                () -> startGame(),
                (t) -> countdown(t.getSecondsLeft()));

        timer.scheduleTimer();
    }

    private void countdown(int seconds)
    {
        if (seconds <= 5)
            sendMessage(Text.get("countdown").replace("%seconds%", seconds + ""));
        else if (seconds % 5 == 0)
            sendMessage(Text.get("countdown").replace("%seconds%", seconds + ""));

        for (Player player : players)
            player.setLevel(seconds);

    }

    public void leave(GamePlayer gp)
    {
        gameplayers.remove(gp);
        players.remove(gp.getPlayer());

        leave(gp.getPlayer(), true);
    }

    public void leaveCommand(GamePlayer gp)
    {
        gameplayers.remove(gp);
        players.remove(gp.getPlayer());

        leave(gp.getPlayer(), true);
    }

    public void leave(Player player, boolean check)
    {
        GamePlayer gp = Smash.gameplayers.get(player);

        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        player.setAllowFlight(false);
        gp.teleportBack();

        CharacterUtils.setCharacter(gp, gp.getPlayer().getUniqueId().toString());

        Smash.gameplayers.remove(gp.getPlayer());

        if (signs != null)
        {
            for (Location loc : signs)
            {
                if (!loc.getBlock().getType().toString().contains("SIGN"))
                {
                    continue;
                }

                Sign sign = (Sign) loc.getBlock().getState();
                sign.setLine(2, "§a" + players.size() + "/" + Text.get("maxplayers", false));
                sign.update();
            }
        }

        if (check)
            check();
    }

    public void sendMessage(String msg)
    {
        for (Player player : players)
            player.sendMessage(msg);
    }

    public String getName()
    {
        return name;
    }

    public static Game getGameFromName(String name)
    {
        for (Game game : games)
        {
            if (game.getName().equalsIgnoreCase(name))
                return game;
        }

        return null;
    }

    public static void createGame(String name, boolean ranked)
    {
        if (!exists(name))
        {
            Game game = new Game(name, ranked);
            games.add(game);
        }
    }

    public static boolean exists(String name)
    {
        for (Game game : games)
        {
            if (game.getName().equalsIgnoreCase(name))
                return true;
        }

        return false;
    }

    public static boolean inGame(Player player)
    {
        return Smash.gameplayers.containsKey(player);
    }
}
