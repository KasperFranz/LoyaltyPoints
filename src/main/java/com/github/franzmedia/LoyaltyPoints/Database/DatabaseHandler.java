package com.github.franzmedia.LoyaltyPoints.Database;

import com.github.franzmedia.LoyaltyPoints.LPUser;
import com.github.franzmedia.LoyaltyPoints.LoyaltyPoints;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class DatabaseHandler {

    private int type;
    private Logger log;
    private String hostname, portnmbr, database, username, password;
    private MySQL mysql;
    private SQLite sqlite;
    private LoyaltyPoints plugin;

    public DatabaseHandler(LoyaltyPoints plugin, Logger log, int type,
            String hostname, String portnmbr, String database, String username,
            String password) {

        this.type = type;
        this.plugin = plugin;
        this.log = log;
        this.hostname = hostname;
        this.portnmbr = portnmbr;
        this.database = database;
        this.username = username;
        this.password = password;
        openConn();

    }

    private void openConn() {
        switch (type) {
            case 1:

                mysql = new MySQL(log, "[LoyaltyPoints]", hostname, portnmbr,
                        database, username, password);
                plugin.debug("MYSQL STARTED");
                mysql.open();
                break;
            default:
                sqlite = new SQLite(log, "[LoyaltyPoints]", "lp",
                        plugin.getDataFolder());
                plugin.debug("SQLITE STARTED");
                sqlite.open();
                break;
        }
        if (!checkTable("users")) {
            createTable("CREATE TABLE users"
                    + "( 	username varchar(16) NOT NULL PRIMARY KEY,"
                    + "	point	 int(16)," + "	totaltime int(25),"
                    + "	time		int(10))");
        }
    }

    private boolean checkTable(String table) {
        checkOpen();
        boolean tableBoolean;

        switch (type) {
            case 1:
                tableBoolean = mysql.checkTable(table);
                break;
            default:
                tableBoolean = sqlite.checkTable(table);
                break;
        }

        return tableBoolean;
    }

    private boolean checkConnection() {
        boolean connectionBoolean;

        switch (type) {
            case 1:
                connectionBoolean = mysql.checkConnection();
                break;
            default:
                connectionBoolean = sqlite.checkConnection();
                break;
        }

        return connectionBoolean;
    }

    private void checkOpen() {
        boolean conn = checkConnection();
        if (!conn) {
            openConn();
        }
    }

    public void saveUser(LPUser user) {
        checkOpen();
        String query = "UPDATE users SET point = \"" + user.getPoint() + "\", "
                + "time = \"" + user.getTime() + "\", " + "totaltime = \""
                + user.getTotalTime() + "\" WHERE username = \""
                + user.getName() + "\"";
        plugin.debug(query);

        switch (type) {
            case 1:
                mysql.query(query);
                break;
            default:
                sqlite.query(query);
                break;
        }

    }

    public boolean checkUser(String username) {
        checkOpen();
        boolean returnboolean = false;
        ResultSet rs;
        int c = 0;
        String query = "SELECT count(*) as c FROM  users WHERE username=\""
                + username + "\"";

        switch (type) {
            case 1:
                rs = mysql.query(query);
                break;
            default:
                rs = sqlite.query(query);
                break;
        }

        try {
            rs.next();
            c = rs.getInt("c");
            rs.close();
        } catch (SQLException e) {
            log.warning("SQL ERROR ON CHECK USER:" + e.toString());
        }
        if (c > 0) {
            returnboolean = true;
        }
        plugin.debug(returnboolean + "" + c + " type:" + type + " " + query);
        return returnboolean;
    }

    public void saveUsers(LPUser[] users) {
        checkOpen();
        if (type == 1) {
            for (int i = 0; i < users.length; i++) {
                String sql = "UPDATE users SET point = \""
                        + users[i].getPoint() + "\", " + "time = \""
                        + users[i].getTime() + "\", " + "totaltime = \""
                        + users[i].getTotalTime() + "\" WHERE username = \""
                        + users[i].getName() + "\"";
                plugin.debug(sql);
                ResultSet rs = mysql.query(sql);
                try {
                    if (!rs.rowUpdated()) {
                        insertUser(users[i]);
                    }
                    rs.close();
                } catch (SQLException e) {
                    log.warning("SQL ERROR ON SAVE USERS:" + e.toString());
                }
            }
        } else {

            for (int i = 0; i < users.length; i++) {

                // TODO: MAKE IT SO IT DOES IT LOKE WITH THE MYSQL!
                String sql = "UPDATE users SET point = \""
                        + users[i].getPoint() + "\", " + "time = \""
                        + users[i].getTime() + "\", " + "totaltime = \""
                        + users[i].getTotalTime() + "\" WHERE username = \""
                        + users[i].getName() + "\"";
                sqlite.query(sql);

            }
        }
    }

    public void insertUser(LPUser user) {
        checkOpen();
        if (!checkUser(user.getName())) {


            String sql = "INSERT INTO users VALUES (\"" + user.getName()
                    + "\",\"" + user.getPoint() + "\"" + ",\""
                    + user.getTotalTime() + "\",\"" + user.getTime() + "\")";
            plugin.debug(sql);

            switch (type) {
                case 1:
                    plugin.debug(type + "mysql");
                    mysql.query(sql);
                    break;
                default:
                    sqlite.query(sql);
            }

        }

    }

    public LPUser GetUser(String username) {
        checkOpen();
        LPUser user = null;
        long now = new Date().getTime();

        int last = 0;

        ResultSet rs;
        String query = "SELECT count(*) as c FROM users WHERE username = '"
                + username + "'";

        switch (type) {
            case 1:
                rs = mysql.query(query);
                break;
            default:
                rs = sqlite.query(query);
                break;
        }

        try {
            rs.next();

            last = rs.getInt("c");
            rs.close();
        } catch (SQLException e) {
            log.warning("SQL ERROR ON GET USER:" + e.toString());
        }
        if (last != 0) {
            query = "SELECT * from users where username = '" + username + "'";

            if (type == 1) {
                rs = mysql.query(query);
            } else {
                rs = sqlite.query(query);
            }
            try {
                rs.next();

                plugin.debug("OLD ONE");
                // old one
                user = new LPUser(plugin, rs.getString("username"),
                        rs.getInt("point"), rs.getInt("time"),
                        rs.getInt("totaltime"), now);
                rs.close();

            } catch (SQLException e) {
                log.warning("SQL ERROR ON GET USER:" + e.toString());
            }
        } else {
            // NEW USER!!!!;
            user = new LPUser(username, plugin);
            insertUser(user);

        }

        return user;
    }

    public LPUser[] getTop(final int from, final int to) {
        checkOpen();
        LPUser[] users = null;

        String query = "SELECT username,point,totaltime, time FROM users order by point desc LIMIT "
                + from + "," + to;
        System.out.println(query);
        plugin.debug(query);
        ResultSet rs;
        switch (type) {
            case 1:
                rs = mysql.query(query);
                break;
            default:
                rs = sqlite.query(query);
                break;
        }

        plugin.debug("before try");
        try {
            users = new LPUser[to];
            plugin.debug("Size: " + users.length);
            Long now = new Date().getTime();

            for (int i = 0; rs.next(); i++) {

                if (rs.getString("username") != null) {
                    users[i] = new LPUser(plugin, rs.getString("username"),
                            rs.getInt("point"), rs.getInt("time"),
                            rs.getInt("totaltime"), now);
                    plugin.debug(i + " " + rs.getString("username") + " " + users[i].getName().isEmpty());
                }
            }

            int maxUsers = 0;

            for (int i = 0; i < users.length; i++) {
                try {

                    if (users[i] != null) {
                        maxUsers++;
                        plugin.debug(maxUsers + "");
                    }
                } catch (NullPointerException e) {
                    plugin.debug(e.getMessage());
                }
            }

            LPUser[] newusers = new LPUser[maxUsers];
            System.arraycopy(users, 0, newusers, 0, maxUsers);


            plugin.debug(newusers.length + " " + users.length);
            users = newusers;
            rs.close();
            plugin.debug(users.length + "");

        } catch (SQLException e) {
            plugin.debug(e.getSQLState() + "" + e.getMessage() + " "
                    + e.toString());
        }
        return users;
    }

    private void createTable(String query) {
checkOpen();
        switch (type) {
            case 1:
                mysql.createTable(query);
                break;
            default:
                sqlite.createTable(query);
                break;
        }
    }

    public void close() {

        switch (type) {
            case 1:
                mysql.close();
                break;
            default:
                sqlite.close();
                break;
        }
    }

    public String transformToSQL() {
        int total = 0;
        checkOpen();
        checkTable("users");

        FileConfiguration mapFileConfig = YamlConfiguration
                .loadConfiguration(new File(plugin.getDataFolder(),
                "points.yml"));
        for (final String playerName : mapFileConfig.getKeys(false)) {
            plugin.debug(playerName);
            final LPUser user = new LPUser(plugin, playerName,
                    mapFileConfig.getInt(playerName + ".points"),
                    mapFileConfig.getInt(playerName + ".time"),
                    mapFileConfig.getInt(playerName + ".totalTime"),
                    new Date().getTime());
            if (!checkUser(user.getName())) {
                total++;
                insertUser(user);
            }
        }

        return plugin.getLptext().getTransformAmount()
                .replace("%TOTAL%", total + "");

    }
}
