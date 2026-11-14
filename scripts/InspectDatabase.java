import java.sql.*;
import java.util.Properties;

/** Read-only schema inspection. Never prints credentials or customer records. */
public class InspectDatabase {
    public static void main(String[] args) throws Exception {
        String host = System.getenv("PGHOST");
        String user = System.getenv("PGUSER");
        if (host == null || user == null) throw new IllegalArgumentException("PGHOST and PGUSER are required");
        Properties properties = new Properties();
        properties.setProperty("user", user);
        String password = System.getenv("PGPASSWORD");
        if (password == null && System.console() != null) {
            char[] secret = System.console().readPassword("Database password (hidden): ");
            if (secret != null) {
                password = new String(secret);
                java.util.Arrays.fill(secret, '\0');
            }
        }
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Database password is required");
        properties.setProperty("password", password);
        properties.setProperty("sslmode", System.getenv().getOrDefault("PGSSLMODE", "require"));
        properties.setProperty("channelBinding", "require");
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "20");
        String database = System.getenv().getOrDefault("PGDATABASE", "neondb");
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + host + ":5432/" + database, properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(20);
                try (ResultSet rows = statement.executeQuery("SELECT table_schema, table_name, column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema NOT IN ('pg_catalog', 'information_schema') ORDER BY table_schema, table_name, ordinal_position")) {
                    System.out.println("SCHEMA | TABLE | COLUMN | TYPE | NULLABLE");
                    while (rows.next()) System.out.println(rows.getString(1) + " | " + rows.getString(2) + " | " + rows.getString(3) + " | " + rows.getString(4) + " | " + rows.getString(5));
                }
            } finally {
                connection.rollback();
            }
        } catch (SQLException exception) {
            System.err.println("Database inspection failed (SQL state: " + exception.getSQLState() + "). Check connection settings and database access.");
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                System.err.println("Connection failure type: " + cause.getClass().getSimpleName());
            }
            System.exit(1);
        }
    }
}
