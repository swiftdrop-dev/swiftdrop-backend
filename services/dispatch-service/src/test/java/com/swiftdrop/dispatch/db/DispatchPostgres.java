package com.swiftdrop.dispatch.db;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Disposable local PostgreSQL cluster. It is not the shared or Neon database. */
public final class DispatchPostgres {

    private static final String BIN = locateBin();
    private static final Path DATA_DIR;
    private static final int PORT;

    public static final String UPGRADE_URL;
    public static final String RUNTIME_URL;

    static {
        try {
            DATA_DIR = Files.createTempDirectory("swiftdrop-dispatch-pg-");
            PORT = freePort();
            run(new ProcessBuilder(
                    BIN + "/initdb",
                    "-D", DATA_DIR.toString(),
                    "--username=swiftdrop",
                    "--locale=C",
                    "--encoding=UTF8",
                    "--auth-local=trust",
                    "--auth-host=trust"));
            Files.writeString(DATA_DIR.resolve("postgresql.conf"), """
                    listen_addresses = '127.0.0.1'
                    port = %d
                    unix_socket_directories = '%s'
                    """.formatted(PORT, DATA_DIR));
            run(new ProcessBuilder(
                    BIN + "/pg_ctl",
                    "-D", DATA_DIR.toString(),
                    "-l", DATA_DIR.resolve("server.log").toString(),
                    "-w",
                    "-t", "30",
                    "start"));
            Runtime.getRuntime().addShutdownHook(new Thread(DispatchPostgres::stop));
            String adminUrl = url("postgres");
            try (Connection connection = DriverManager.getConnection(adminUrl, username(), password());
                 Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("CREATE DATABASE dispatch_upgrade");
                statement.execute("CREATE DATABASE dispatch_runtime");
            }
            UPGRADE_URL = url("dispatch_upgrade");
            RUNTIME_URL = url("dispatch_runtime");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start disposable PostgreSQL", exception);
        }
    }

    private DispatchPostgres() {
    }

    /**
     * Prefer an explicit {@code DISPATCH_PG_BIN}, then PostgreSQL 16, then the newest
     * installed server under {@code /usr/lib/postgresql}. CI installs that package
     * because GitHub runners do not ship the server binaries.
     */
    private static String locateBin() {
        String override = System.getenv("DISPATCH_PG_BIN");
        if (isServerBin(override)) {
            return override;
        }
        Path root = Path.of("/usr/lib/postgresql");
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("PostgreSQL server binaries were not found under " + root);
        }
        try (var versions = Files.list(root)) {
            List<Path> bins = versions
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("bin"))
                    .filter(DispatchPostgres::isServerBin)
                    .toList();
            return bins.stream()
                    .filter(bin -> "16".equals(bin.getParent().getFileName().toString()))
                    .findFirst()
                    .or(() -> bins.stream().max(Comparator.comparing(bin -> bin.getParent().getFileName().toString())))
                    .map(Path::toString)
                    .orElseThrow(() -> new IllegalStateException(
                            "PostgreSQL server binaries were not found under " + root));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not locate PostgreSQL server binaries", exception);
        }
    }

    private static boolean isServerBin(String directory) {
        return directory != null && !directory.isBlank() && isServerBin(Path.of(directory));
    }

    private static boolean isServerBin(Path directory) {
        return Files.isExecutable(directory.resolve("initdb")) && Files.isExecutable(directory.resolve("pg_ctl"));
    }

    public static String username() {
        return "swiftdrop";
    }

    public static String password() {
        return "";
    }

    private static String url(String database) {
        return "jdbc:postgresql://127.0.0.1:" + PORT + "/" + database;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void run(ProcessBuilder builder) throws IOException, InterruptedException {
        builder.environment().putAll(Map.of("LC_ALL", "C", "LANG", "C"));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("PostgreSQL command failed: " + builder.command().get(0)
                    + " exit=" + (process.isAlive() ? "timeout" : process.exitValue())
                    + " output=" + new String(output));
        }
    }

    private static void stop() {
        try {
            new ProcessBuilder(BIN + "/pg_ctl", "-D", DATA_DIR.toString(), "-m", "fast", "-w", "stop")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // The test JVM is exiting; the temporary cluster is not a shared database.
        }
        try {
            Files.walk(DATA_DIR)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup of the temporary data directory.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup of the temporary data directory.
        }
    }
}
