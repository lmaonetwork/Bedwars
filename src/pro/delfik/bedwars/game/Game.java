package pro.delfik.bedwars.game;

import com.boydti.fawe.FaweAPI;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import implario.util.Converter;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pro.delfik.bedwars.BWPlugin;
import pro.delfik.bedwars.Bedwars;
import pro.delfik.bedwars.util.Colors;
import pro.delfik.bedwars.util.CyclicIterator;
import pro.delfik.bedwars.util.FixedArrayList;
import pro.delfik.bedwars.util.Resources;
import pro.delfik.bedwars.world.Schematics;
import pro.delfik.bedwars.world.WorldUtils;
import pro.delfik.lmao.outward.item.I;
import pro.delfik.lmao.user.Person;
import pro.delfik.lmao.util.Cooldown;
import pro.delfik.lmao.util.U;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class Game {
	
	// Ограничение на максимальное количество одновременных игр
	public static final int MAX_RUNNING_GAMES = 5;
	
	// Текущие игры
	private static final FixedArrayList<Game> RUNNING = new FixedArrayList<>(MAX_RUNNING_GAMES);
	private List<Chunk> chunks = new LinkedList<>();

	public static Game get(World world) {
		String name = world.getName();
		if (!name.startsWith("BW_")) return null;
		int i = Converter.toInt(name.substring(3), -1);
		return i == -1 ? null : i < MAX_RUNNING_GAMES ? RUNNING.get(i) : null;
	}
	
	// ID мира, в котором идёт игра
	private final int id;
	
	// Карта, на которой идёт игра
	private final Map map;
	
	// Референс по командам, участвующим в игре
	private final Colors<BWTeam> teams;
	
	// Скорбоард для хранения команд и информации в сайдбаре
	private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
	
	// Задача в скорборде (Отображение информации в сайдбаре)
	private final Objective objective = scoreboard.registerNewObjective("bedwars", "dummy");
	
	// Мир, в котором идёт игра
	private final World world;
	
	// Состояние игры
	private volatile State state = State.NOTHING;
	
	// Референс на таски, которые нужно отменить, чтобы прекратить спавн ресурсов
	private final Resources<BukkitTask> resourceTasks = new Resources<>();
	
	// Мап для получения команды по нику игрока
	protected final HashMap<String, BWTeam> byName = new HashMap<>();

	// Таски спавна ресурсов

	
	/**
	 * Создание новой игры и генерация карты
	 *
	 * @param map     Карта, на которой будет идти игра.
	 * @param players Отсортированные по командам игроки.
	 */
	public Game(Map map, Colors<Collection<Person>> players) {
		
		// Проверка на наличие свободных карт и запись в список текущих игр.
		int id = RUNNING.firstEmpty();
		if (id == -1) throw new IllegalStateException("Нет свободных карт!");
		this.id = id;
		RUNNING.set(id, this);
		
		this.map = map;
		
		// Настройка скорборда
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.setDisplayName("§c§lBed§f§lWars");

		// Настройка команд
		teams = players.convert((color, people) -> new BWTeam(color, this, people.toArray(new Person[0])));

		// Прогрузка мира
		world = loadWorld();
		
		// Генерация карты
		buildMap();
	}

	public static FixedArrayList<Game> running() {
		return RUNNING;
	}

	public void startCooldown() {
		setState(State.COOLDOWN);
		new Cooldown("BW_" + id, 4, getPlayers(), this::start);
	}
	
	public State getState() {
		return state;
	}
	
	private void setState(State state) {
		this.state = state;
	}
	
	public List<Person> getPlayers() {
		List<Person> list = new ArrayList<>();
		for (BWTeam t : teams.values()) list.addAll(t.getPlayers());
		return list;
	}
	
	/**
	 * Быстрая итерация по всем игрокам, принадлежащим этой игре.
	 * @param consumer Функция, которую нужно применить к каждому из игроков.
	 */
	protected void forPlayers(Consumer<Person> consumer) {
		for (Player p : world.getPlayers()) consumer.accept(Person.get(p));
	}
	
	/**
	 * Загрузить (При необходимости) и подготовить мир к вставке карты.
	 * @return Готовый к вставке карты мир
	 */
	private World loadWorld() {
		String worldName = "BW_" + id;
		World world = WorldUtils.loadWorld(worldName);
		WorldUtils.clear(world);
		return world;
	}
	
	/**
	 * Вставить карту из схематики в мир.
	 */
	private void buildMap() {
		setState(State.GENERATING);
//		new Thread(() -> {
//			Schematics schematics = new Schematics(world);
//			schematics.loadSchematic(map.getName(), map.getCenter().toVec3i());
			try {
				Location loc = map.getCenter().toLocation(world);
				FaweAPI.load(new File("plugins/WorldEdit/schematics/" + map.getSchematic() + ".schematic"))
						.paste(BukkitUtil.getLocalWorld(world), new Vector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), false);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			// После успешной вставки схематики можно начинать игру.
			this.startCooldown();
//		}).start();
	}
	
	/**
	 * Начать игру.
	 * Телепортировать игроков на карту, включить спавнеры ресурсов.
	 */
	public void start() {
		for (BWTeam team : teams.values()) {
			for (Person p : team.getPlayers()) {
				p.teleport(map.getSpawnLocation(team.getColor(), world));
				Bedwars.toGame(p.getHandle());
				p.sendTitle("§aИгра началась!");
			}
		}
		enableSpawners();
	}
	
	/**
	 * Начать раздавать ресурсы из спавнеров.
	 */
	private void enableSpawners() {
		for (BWTeam team : teams.values()) team.enableSpawners(this);
		for (Resource resource : Resource.values()) {
			BukkitTask task = I.timer(() -> {
				for (BWTeam t : teams.values()) if (!t.defeated()) t.getResourceSpawners().getDefault(resource).forEach(ResourceSpawner::spawn);
			}, resource.getSpawnTicks());
			resourceTasks.put(resource, task);
		}
	}

	public void checkWinner() {
		BWTeam winner = null;
		for (BWTeam team : teams.values()) {
			if (team.defeated()) continue;
			if (winner != null) return;
			winner = team;
		}
		end(winner);
	}

	private static final Iterator<String> ranks = new CyclicIterator<>(new String[] {
			"Непревзойдённый", "Чудесный", "Великолепный", "Непобедимый", "Грациозный", "Ангельский",
			"Бесподобный", "Прекрасный", "Лучший", "Волшебный", "Удивительный", "Божественный", "Пламенный"
	});

	public void end(BWTeam winner) {
		if (state == State.ENDING || state == State.RESETTING) return;
		setState(State.ENDING);
		String colorDesc = winner.getColor().toString();
		List<TextComponent> list = Converter.transform(winner.getPlayers(), p -> U.constructComponent("# §e" + ranks.next() + " ", p));
		resourceTasks.forEach(BukkitTask::cancel);
		forPlayers(p -> {
			p.sendTitle("Победили " + colorDesc + "§f!");
			p.sendMessage("##############################");
			p.sendMessage("# " + colorDesc + "§f победили!");
			p.sendMessage("# §aСписок победителей:");
			p.sendMessage("##############################");
			for (TextComponent t : list) p.msg(t);
			p.sendMessage("##############################");
		});
		endCooldown();
	}

	public void endCooldown() {
		forPlayers(p -> p.sendMessage("§6Игра закончится через 10 секунд."));
		I.delay(() -> {
			for (Player p : world.getPlayers()) Bedwars.toLobby(p);
			setState(State.RESETTING);
			clear();
		}, 200);
	}

	private volatile int clearTask = -1;

	public void clear() {
		world.getEntities().forEach(Entity::remove);
		chunks.addAll(Schematics.getAllChunksBetween(world, map.getMin(), map.getMax()));
		Bukkit.broadcastMessage("§c§oРазмер массива: §f§o" + chunks.size());
		Iterator<Chunk> iterator = chunks.iterator();
		Chunk ch = world.getSpawnLocation().getChunk();
		world.regenerateChunk(ch.getX(), ch.getZ());
		clearTask = I.s().scheduleSyncRepeatingTask(BWPlugin.instance, () -> {
			for (int i = 0; i < 5; i++) {
				if (iterator.hasNext()) {
					Chunk c = iterator.next();
					world.regenerateChunk(c.getX(), c.getZ());
					world.unloadChunk(c);
				} else if (clearTask != -1) {
					RUNNING.set(id, null);
					I.s().cancelTask(clearTask);
					return;
				}
			}
		}, 1, 0);
//		I.s().runTaskAsynchronously(BWPlugin.instance, () -> {
//			EditSession editSession = new EditSessionBuilder(BukkitUtil.getLocalWorld(world)).fastmode(true).build();
//			editSession.replaceBlocks(new CuboidRegion(BukkitUtil.getLocalWorld(world), map.getMin(), map.getMax()), v -> true, new BaseBlock(0));
//			editSession.flushQueue();
//			editSession.commit();
//		});
	}

	public void destroyBed(Color color, Player p) {
		BWTeam team = teams.getDefault(color);
		if (!team.hasBed()) {
			p.sendMessage("§cКровать этой команды уже сломана. Что вы, б***ь, вообще делали?");
			return;
		}
		team.setHasBed(false);
		for (Person player : team.getPlayers()) {
			player.sendTitle("Кровать уничтожена!");
			if (p == null) player.sendTitle("§7Никто не знает, кто сломал кровать...");
			player.sendSubtitle(p.getDisplayName() + "§f сломал вашу кровать!");
		}
		forPlayers(player -> U.msg(player.getHandle(), p, " разрушил " + color.getBedBroken()));
	}
	
	public Map getMap() {
		return map;
	}
	
	public int getId() {
		return id;
	}
	
	public Colors<BWTeam> getTeams() {
		return teams;
	}
	
	protected Objective getObjective() {
		return objective;
	}
	
	protected Scoreboard getScoreboard() {
		return scoreboard;
	}
	
	public World getWorld() {
		return world;
	}
	
	public BWTeam getTeam(String playername) {
		return byName.get(playername);
	}
	
	public Location getSpawnLocation(Color color) {
		return map.getSpawnLocation(color, world);
	}

	public static Game get(Integer slot) {
		if (slot < 0 || slot > MAX_RUNNING_GAMES) throw new IllegalArgumentException();
		else return RUNNING.get(slot);
	}

	public static Game get(Player player) {
		return get(player.getWorld());
	}

	public void eliminate(Player p) {
		BWTeam t = getTeam(p.getName());
		if (t == null) return;
		t.remove(Person.get(p));
		checkWinner();
	}

	public void addChunk(Chunk chunk) {
		if (!chunks.contains(chunk)) chunks.add(chunk);
	}

	/**
	 * Состояние игры.
	 * NOTHING: Сектор свободен, здесь можно начать игру.
	 * GENERATING: Генерация карты.
	 * COOLDOWN: Отсчёт до начала игры.
	 * GAME: Процесс игры, битва игроков.
	 * ENDING: Состояние после конца игры, выдача наград и отсчёт до конца.
	 * RESETTING: Очищение мира после игры.
	 */
	public enum State {
		NOTHING("§aОжидание игроков", Material.EMERALD_BLOCK),
		GENERATING("§bГенерация карты", Material.DIAMOND_BLOCK),
		COOLDOWN("§bНачало игры", Material.DIAMOND_BLOCK),
		GAME("§6Идёт игра", Material.GOLD_BLOCK),
		ENDING("§cКонец игры", Material.REDSTONE_BLOCK),
		RESETTING("§6Сброс карты", Material.COMMAND);
		
		private final String title;
		private final Material material;

		State(String title, Material material) {
			this.title = title;
			this.material = material;
		}
		
		public String getTitle() {
			return title;
		}
		
		public Material getMaterial() {
			return material;
		}
	}
}
