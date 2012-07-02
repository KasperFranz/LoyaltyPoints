/* 
 * AUTHOR: Kasper Franz
 * Loyalty Points 1.0.9
 * Last Changed: fixed the save error, and the Mysql error, added new config file and a new config index 
 * 
 */

/*
 * Planned Features Possibility to pay a defined amount of money when a
 * player gains a specified amount of LoyaltyPoints Only pay points if the
 * Server-wide announcements when a player gains a certain
 * amount of points (reaches a point milestone) Receive item rewards on
 * specified point milestones
 */

package com.github.franzmedia.LoyaltyPoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import lib.PatPeter.SQLibrary.MySQL;
import lib.PatPeter.SQLibrary.SQLite;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class LoyaltyPoints extends JavaPlugin {
	public final Logger logger = Logger.getLogger("Minecraft");
	private SQLite sqlite;
	private MySQL mysql;
	private int status = 0;
	private int increment = 1, cycleNumber = 600,
			updateTimer = cycleNumber / 4, startingPoints = 0,
			SaveTimer = 3600, check = -10, version, newestVersion;
	private final int debug = 0;
	private boolean afkTrackingSystem = true;
	private int pointType = 2;
	public String newVersion, checkString = "";
	private final Map<String, LPUser> users = new HashMap<String, LPUser>();

	/* Mysql */
	private String mysql_host = null, mysql_port = "3306", mysql_user = null,
			mysql_pass = null, mysql_database = null;
	private FileConfiguration config;
	private File mapFile;
	private FileConfiguration mapFileConfig;
	private LPFileManager LPFM;

	/* Messages EDITABLE */
	public String pluginTag = "&6[LoyaltyPoints]";
	public String consoleCheck = pluginTag + " Sorry, I dont track consoles.";
	public String selfcheckMessage = colorize(pluginTag
			+ " &3You have &b%POINTS% &3Loyalty Points.");
	public String checkotherMessage = colorize(pluginTag
			+ " &3%PLAYERNAME% has &b%POINTS% &3Loyalty Points.");

	// public List<String> milestones = new ArrayList<String>();
	// public Map<String, List<Integer>> rewardsTracker = new HashMap<String,
	// List<Integer>>(); // For tracking rewards, etc.

	// public static Economy economy = null;
	// public boolean economyPresent = true;
	@Override
	public void onDisable() {

		for (final Player players : getServer().getOnlinePlayers()) {
			users.get(players.getName()).givePoint();
		}

		if (pointType == 1) {
			LPFM.save();
		} else {
			save();
			if (pointType == 2) {
				sqlite.close();
			} else {
				mysql.close();
			}

		}
		users.clear();
		info(this.getDescription(), "disabled");
	}

	@Override
	public void onEnable() {
		checkConfig();
		loadVariables();
		getServer().getScheduler().scheduleSyncDelayedTask(this,
				new Runnable() {

					@Override
					public void run() {
						loadPointsData();

					}
				});

		this.getServer().getPluginManager()
				.registerEvents(new LCListener(this), this);
		getServer().getScheduler().scheduleSyncDelayedTask(this,
				new Runnable() {

					@Override
					public void run() {
						startUpdateCheck();
					}
				});
		getCommand("lp").setExecutor(new LPCommand(this));
		info(getDescription(), "enabled");

		/*
		 * if (!setupEconomy()) {
		 * this.logger.severe("[LoyaltyPoints] Vault dependency not found!"); th
		 * .logger.severe("[LoyaltyPoints] Milestones paying feature disabled."
		 * ); economyPresent = false; }
		 */
		getServer().getScheduler().scheduleAsyncRepeatingTask(this,
				new LPScheduler(this), updateTimer, updateTimer);

	}

	public void loadPointsData() {
		int usersCount = 0;

		logger.info(pluginTag + " Beginning to load User points.");
		if (pointType == 2 || pointType == 3) {
			status = 1;
			checkTable();
			if (status == 1) {
				final Long now = new Date().getTime();
				ResultSet rs;
				final String sql = "SELECT count(username) as c FROM users";
				if (pointType == 2) {
					rs = sqlite.query(sql);
				} else {
					rs = mysql.query(sql);
				}

				try {
					rs.next();
					usersCount = rs.getInt("c");
					rs.close();
					debug(usersCount + "");
					final String sql1 = "SELECT * FROM users";
					if (pointType == 2) {
						rs = sqlite.query(sql1);
					} else {
						rs = mysql.query(sql1);
					}
					for (int i = 0; i < usersCount; i++) {
						rs.next();
						debug("user insert" + rs.getString("username"));
						users.put(
								rs.getString("username"),
								new LPUser(this, rs.getString("username"), rs
										.getInt("point"), rs.getInt("time"), rs
										.getInt("totaltime"), now));
					}
					rs.close();
				} catch (final SQLException e1) {
					debug("error with loading users");
				}

			}

		} else if (pointType == 1) {
			LPFM = new LPFileManager(this);
			info(getDescription(), "LPFM");
			mapFile = new File(getDataFolder(), "points.yml");
			mapFileConfig = YamlConfiguration.loadConfiguration(mapFile);
			for (final String s : mapFileConfig.getKeys(false)) {
				kickStartFile(s);
				usersCount++;
			}
		} else {
			logger.info(pluginTag
					+ " it seems like there are a error on your PointType,  1-3 is a allowed value!");

		}

		logger.info(pluginTag + " there have been loaded a total of "
				+ usersCount + " users.");

	}

	public String checkStringVariable(final String name) {
		String str = "";
		if (config.contains(name)) {
			str = config.getString(name);
			debug(str + "" + name);
		} else {
			debug(pluginTag + " You have a error with you config file around: "
					+ name + " we use default option.");
		}
		return str;
	}

	public int checkVariable(final String name) {
		int str = -10;

		if (config.contains(name)) {
			str = config.getInt(name);
			debug(str + "" + name);
		} else {
			debug(pluginTag + " You have a error with you config file around: "
					+ name + " we use default option.");
		}
		return str;
	}

	public void loadVariables() {

		config = this.getConfig();
		check = checkVariable("increment-per-cycle");
		if (check > 0) {
			increment = check;
		}

		check = checkVariable("cycle-time-in-seconds");
		if (check > 0) {
			cycleNumber = check;
		}

		check = checkVariable("update-timer");
		if (check > 0) {
			updateTimer = check * 20;
		}

		check = checkVariable("starting-points");
		if (check > 0) {
			startingPoints = check;
		}

		check = checkVariable("SaveTimer");
		if (check > 0) {
			SaveTimer = check;
		}

		checkString = checkStringVariable("plugin-tag");
		if (!checkString.isEmpty()) {
			pluginTag = colorize(checkString);
		}

		checkString = checkStringVariable("self-check-message");
		if (!checkString.isEmpty()) {
			selfcheckMessage = colorize(checkString.replaceAll("%TAG%",
					pluginTag));
		}

		checkString = checkStringVariable("check-otherplayer-message");
		if (!checkString.isEmpty()) {
			selfcheckMessage = colorize(checkString.replaceAll("%TAG%",
					pluginTag));
		}

		if (!config.contains("afk-tracking-system")) {
			config.set("afk-tracking-system", 1);
			try {
				config.save(new File(this.getDataFolder(), "config.yml"));
			} catch (final IOException e) {
				debug("ERROR while loading saving variable");
			}
		} else {
			final int gettedValue = config.getInt("afk-tracking-system");
			switch (gettedValue) {
			case 1:
				afkTrackingSystem = true;
				break;
			case 0:
				afkTrackingSystem = false;
				break;

			default:
				afkTrackingSystem = true;
				break;
			}

		}

		check = checkVariable("point-type");
		if (check > 0) {
			pointType = check;
		}
		if (pointType == 2) {
			sqlite = new SQLite(this.getLogger(), pluginTag, "lp", this
					.getDataFolder().toString());

		} else if (pointType == 3) {
			// if it's mysql

			String miss = "";
			checkString = checkStringVariable("mysql-host");
			if (!checkString.isEmpty()) {
				mysql_host = checkString;
			} else {
				miss = "host ";
			}

			checkString = checkStringVariable("mysql-port");
			if (!checkString.isEmpty()) {
				mysql_port = checkString;
			}

			checkString = checkStringVariable("mysql-user");
			if (!checkString.isEmpty()) {
				mysql_user = checkString;
			} else {
				miss = miss + "user ";
			}
			checkString = checkStringVariable("mysql-pass");
			if (!checkString.isEmpty()) {
				mysql_pass = checkString;
			} else {
				miss = miss + "pass ";
			}

			checkString = checkStringVariable("mysql-database");
			if (!checkString.isEmpty()) {
				mysql_database = checkString;

			}
			if (miss.length() < 3) {
				mysql = new MySQL(this.getLogger(), pluginTag, mysql_host,
						mysql_port, mysql_database, mysql_user, mysql_pass);
				mysql.open();
			} else {
				logger.warning(pluginTag
						+ "We have a error with the following mysql things:"
						+ miss);
				setEnabled(false);
			}
		}
		// ConfigurationSection milestonesCS =
		// config.getConfigurationSection("points-milestones.Amounts");
		// List<String> l = new ArrayList<String>(milestonesCS.getKeys(false));
		// milestones.addAll(l);
	}

	public void kickStart(final String player) {

		if (users.containsKey(player)) {
			users.get(player).setTimeComparison(new Date().getTime());
		} else {
			if (pointType == 3 || pointType == 2) {
				kickStartSQL(player);
			} else if (pointType == 1) {
				kickStartFile(player);
			} else {
				logger.info("LoyaltyPoints You PointType " + pointType
						+ " isn't Valid");
			}

		}

	}

	private void kickStartSQL(final String player) { // gets the users elements
														// and if
		// new creates him

		users.put(player, new LPUser(this, player, startingPoints, 0, 0,
				new Date().getTime()));
		debug("NEW USER INSERTED" + player);

	}

	private void kickStartFile(final String player) { // gets the users elements
														// and
		// if new creates him
		if (!LPFM.load(player)) { // if player dont excists
			// we put starting points, TotalTime, and time since last point
			users.put(player, new LPUser(this, player, startingPoints, 0, 0,
					new Date().getTime()));
			debug("NEW USER INSERTED" + player);
		}
		debug(users.get(player).getTime() + "");
	}

	public String colorize(final String message) {
		return message.replaceAll("&([a-f0-9])", ChatColor.COLOR_CHAR + "$1");
	}

	public int getTimeLeft(final String player) {
		// return (int) (getCycleNumber()-(((new
		// Date().getTime()-users.get(player).getTimeComparison())/1000)+users.get(player).getTime()));
		return users.get(player).getTimeLeft();
	}

	public String getNiceNumber(int millsec) {
		String str = "";
		int g = 0;

		if (millsec >= 31556926) { // year
			g = 1;
			str = str + (millsec / 31556926) + "y";
			millsec = millsec - ((millsec / 31556926) * 31556926);

		}

		if (millsec >= 2629744) { // month
			if (g == 1) {
				str = str + ", ";
				g = 0;
			}
			str = str + (millsec / 2629744) + "m";
			millsec = millsec - ((millsec / 2629744) * 2629744);
			g = 1;
		}

		if (millsec >= 86400) { // day
			if (g == 1) {
				str = str + ", ";
				g = 0;
			}
			str = str + (millsec / 86400) + "d";
			millsec = millsec - ((millsec / 86400) * 86400);
			g = 1;
		}
		if (millsec >= 3600) { // time
			if (g == 1) {
				str = str + ", ";
				g = 0;
			}
			str = str + (millsec / 3600) + "h";
			millsec = millsec - ((millsec / 3600) * 3600);
			g = 1;
		}
		if (millsec >= 60) { // min
			if (g == 1) {
				str = str + ", ";
				g = 0;
			}
			str = str + (millsec / 60) + "m";
			millsec = millsec - ((millsec / 60) * 60);
			g = 1;
		}

		if (millsec >= 1) {
			if (g == 1) {
				str = str + ", ";

			}
			str = str + millsec + "s";
			millsec = millsec - millsec;

		}

		return str;
	}

	public void info(final PluginDescriptionFile pdf, final String status) {
		this.logger.info("[LoyaltyPoints] version " + pdf.getVersion()
				+ " by Franzmedia is now " + status + "!");
	}

	/*
	 * private boolean setupEconomy() { if
	 * (getServer().getPluginManager().getPlugin("Vault") == null) { return
	 * false; } RegisteredServiceProvider<Economy> rsp =
	 * getServer().getServicesManager().getRegistration(Economy.class); if (rsp
	 * == null) { return false; } economy = rsp.getProvider(); return economy !=
	 * null; }
	 */

	/*
	 * public void checkReward(String player) { int pointsAmount =
	 * this.loyaltyMap.get(player); List<Integer> i = new ArrayList<Integer>();
	 * for (Iterator<String> it = this.milestones.iterator(); it.hasNext();) {
	 * int msAmount = Integer.parseInt(it.next()); if (pointsAmount > msAmount)
	 * { i.add(msAmount); } } if (i.isEmpty()) { return; } int isMax; for (int f
	 * = 0; f < i.size(); f++) { EconomyResponse paid =
	 * LoyaltyPoints.economy.depositPlayer(player, i.get(f)); // You have been
	 * payed so and so amount... // Broadcast here.. } // int topSize =
	 * i.size()-1; /*while
	 * (this.getConfig().getBoolean("points-milestones.Amounts." +
	 * String.valueOf(i.get(topSize)) + ".broadcast") == false) {
	 * 
	 * // } }
	 */

	private void checkConfig() {
		debug("hmm checkconfig");
		final String name = "config.yml";
		final File actual = new File(getDataFolder(), name);
		if (!actual.exists()) {

			getDataFolder().mkdir();
			final InputStream input = this.getClass().getResourceAsStream(
					"/defaults/config.yml");
			if (input != null) {
				FileOutputStream output = null;

				try {
					output = new FileOutputStream(actual);
					final byte[] buf = new byte[4096]; // [8192]?
					int length = 0;
					while ((length = input.read(buf)) > 0) {
						output.write(buf, 0, length);
					}
					this.logger
							.info("[LoyaltyPoints] Default configuration file written: "
									+ name);
				} catch (final IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if (input != null)
							input.close();
					} catch (final IOException e) {
					}

					try {
						if (output != null)
							output.close();
					} catch (final IOException e) {
					}
				}
			}
		}
	}

	public int getCycleNumber() {
		return cycleNumber;
	}

	public int getIncrement() {
		return increment;
	}

	public int getUpdateTimer() {
		return updateTimer;
	}

	public void debug(final String txt) {

		if (debug == 1) {
			System.out.println(txt);
		}

	}

	public void startUpdateCheck() {
		version = VersionFormat(getDescription().getVersion());
		newestVersion = version;
		getNewestVersion();

		if (!upToDate()) {
			this.logger.info(colorize(pluginTag)
					+ " is not up to date, the new version:" + newVersion);
		} else {
			this.logger.info(colorize(pluginTag)
					+ " the plugin is up to date...");
		}
	}

	public void transformToSQL() {
		int total = 0;

		debug("type:" + pointType);
		if (pointType == 2) {
			debug(!sqlite.checkConnection() + " conn");
			debug("check" + sqlite.checkTable("users"));
			checkTable();

			mapFileConfig = YamlConfiguration.loadConfiguration(new File(
					getDataFolder(), "points.yml"));
			for (final String playerName : mapFileConfig.getKeys(false)) {
				final int points = mapFileConfig.getInt(playerName + ".points");
				int time = 0;
				time = mapFileConfig.getInt(playerName + ".time");
				final int totalTime = mapFileConfig.getInt(playerName
						+ ".totalTime");
				final LPUser user = new LPUser(this, playerName, points, time,
						totalTime, new Date().getTime());
				final String sql1 = "SELECT count(*) as c FROM  users WHERE username=\""
						+ playerName + "\"";
				debug(sql1);
				final ResultSet rs = sqlite.query(sql1);
				int c = 0;
				try {
					rs.next();
					c = rs.getInt("c");
					debug("before rs.next Count: " + c);
					rs.close();
				} catch (final SQLException e) {

					e.printStackTrace();
				}

				if (c == 0) {

					sqlite.query("INSERT INTO users VALUES (\""
							+ user.getName() + "\"," + "\"" + user.getPoint()
							+ "\",\"" + user.getTime() + "\",\""
							+ user.getTotalTime() + "\")");

					total++;
				}
				users.put(playerName, user);
			}

		} else if (pointType == 3) {
			debug("point type 3:");
			checkTable();

			mapFileConfig = YamlConfiguration.loadConfiguration(new File(
					getDataFolder(), "points.yml"));
			for (final String playerName : mapFileConfig.getKeys(false)) {
				final int points = mapFileConfig.getInt(playerName + ".points");
				int time = 0;
				time = mapFileConfig.getInt(playerName + ".time");
				final int totalTime = mapFileConfig.getInt(playerName
						+ ".totalTime");
				final LPUser user = new LPUser(this, playerName, points, time,
						totalTime, new Date().getTime());
				final String sql1 = "SELECT count(*) as c FROM  users WHERE username=\""
						+ playerName + "\"";
				debug(sql1);
				final ResultSet rs = mysql.query(sql1);
				int c = 0;
				try {
					rs.next();
					c = rs.getInt("c");
					debug("before rs.next " + c);
					rs.close();
				} catch (final SQLException e) {

					e.printStackTrace();
				}

				if (c == 0) {

					mysql.query("INSERT INTO users VALUES (\"" + user.getName()
							+ "\",\"" + user.getPoint() + "\",\""
							+ user.getTime() + "\",\"" + user.getTotalTime()
							+ "\")");

					total++;
				}
				users.put(playerName, user);
			}
		}

		logger.info(pluginTag + "the transform to sql is done we moved "
				+ total + " users");

	}

	public void getNewestVersion() {

		try {
			final URL url = new URL(
					"https://raw.github.com/franzmedia/LoyaltyPoints/master/version.txt");
			final URLConnection connection = url.openConnection();
			final BufferedReader in = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			newVersion = in.readLine();
			debug(newVersion);

			newestVersion = VersionFormat(newVersion);

			in.close();
		} catch (final MalformedURLException e) {
			this.logger
					.warning(pluginTag
							+ " there was a error while loading the newest version! Code MURLE");
		} catch (final IOException e) {
			this.logger
					.warning(pluginTag
							+ " there was a error while loading the newest version! Code: IO");
		}
	}

	private int VersionFormat(final String Version) {
		String NV = Version.replaceAll("\\.", "");
		debug("AFTER NV" + NV);
		final char arr[] = NV.toCharArray();
		NV = "";
		for (int i = 0; i < arr.length; i++) {
			NV = NV + arr[i];

		}
		final int miss = 4 - arr.length;

		for (int i = 0; i < miss; i++) {
			NV = NV + 0;

		}
		debug("newest:" + NV);
		return Integer.parseInt(NV);
	}

	public int getSaveTimer() {
		return SaveTimer;
	}

	public void insertUser(final LPUser user) {
		users.put(user.getName(), user);

	}

	public Map<String, LPUser> getUsers() {
		return users;
	}

	public boolean upToDate() {
		boolean returnstr = false;
		debug("uptoDATE" + newestVersion + ">" + version);
		if (newestVersion > version) {
			returnstr = false;
		} else {
			returnstr = true;
		}

		return returnstr;
	}

	public void save() {
		if (pointType == 2 || pointType == 3) {
			checkTable();

			for (final LPUser user : users.values()) {
				debug(user.getName());

				try {
					final String sql1 = "SELECT count(*) as c FROM  users WHERE username=\""
							+ user.getName() + "\"";
					debug(sql1);
					ResultSet rs;
					if (pointType == 2) {
						rs = sqlite.query(sql1);
					} else {
						rs = mysql.query(sql1);
					}
					rs.next();
					final int c = rs.getInt("c");
					debug("before rs.next " + c);
					rs.close();
					if (c != 0) {
						final String sql = "UPDATE users SET point = \""
								+ user.getPoint() + "\", " + "time = \""
								+ user.getTime() + "\", " + "totaltime = \""
								+ user.getTotalTime()
								+ "\" WHERE username = \"" + user.getName()
								+ "\"";
						debug("sql kode for " + user.getName() + "  " + sql);
						if (pointType == 2) {
							rs = sqlite.query(sql);
						} else {
							rs = mysql.query(sql);
						}

					} else {
						final String sql = "INSERT INTO users VALUES (\""
								+ user.getName() + "\",\"" + user.getPoint()
								+ "\"" + ",\"" + user.getTotalTime() + "\",\""
								+ user.getTime() + "\")";
						debug("else");
						if (pointType == 2) {
							rs = sqlite.query(sql);
						} else {
							rs = mysql.query(sql);
						}

					}

				} catch (final SQLException e) {

					e.printStackTrace();
					debug(e.getSQLState());
				}

			}
		} else {
			getLogger().info(pointType + "");
		}
	}

	private void checkTable() {
		if (pointType == 2) {
			debug("SQLITE" + sqlite.checkTable("users"));
			if (!sqlite.checkTable("users")) {
				status = 0;

				sqlite.createTable("CREATE TABLE users"
						+ "( 	username varchar(16) NOT NULL PRIMARY KEY,"
						+ "	point	 int(16)," + "	totaltime int(25),"
						+ "	time		int(10))");
				debug("SQLITE" + sqlite.checkTable("users"));
			}
		} else if (pointType == 3) {
			if (!mysql.checkTable("users")) {
				status = 0;
				mysql.createTable("CREATE TABLE users"
						+ "( 	username varchar(16) NOT NULL,"
						+ "	point	 int(16)," + "	totaltime int(25),"
						+ "	time		int(10), " + " primary key (username)"
						+ ")engine = innodb; ");

			}
		}
	}

	public File getMapFile() {

		mapFile = new File(this.getDataFolder(), "points.yml");

		return mapFile;
	}

	public boolean AfkTrackingSystem() {
		return afkTrackingSystem;
	}

}