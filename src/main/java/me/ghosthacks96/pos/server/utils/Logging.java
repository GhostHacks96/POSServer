package me.ghosthacks96.pos.server.utils;


import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Logging {

    private static String LOG_DIR ;
    private static final String CURRENT_LOG_FILE = LOG_DIR + "latest.log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ExecutorService executor;
    private static ScheduledExecutorService scheduler;

    ConsoleHandler console;

    public Logging(Config config) {
        console = POSServer.console;
        LOG_DIR = config.getConfig().get("dir") != null ? (String) config.getConfig().get("dir") : "logs/";

        executor = Executors.newSingleThreadExecutor();
        ensureLogDirectoryExists();
        setupMidnightLogZipTask();
    }

    public void logInfo(String message) {
        log("INFO", message);
    }

    public void logWarning(String message) {
        log("WARNING", message);
    }

    public void logError(String message) {
        log("ERROR", message);
    }

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);
        System.out.print(logEntry);
        executor.submit(() -> appendToLogFile(logEntry));
    }

    private void appendToLogFile(String logEntry) {
        try (FileWriter writer = new FileWriter(CURRENT_LOG_FILE, true)) {
            writer.write(logEntry);
        } catch (IOException e) {
            console.printError(e.getMessage());
        }
    }

    private void ensureLogDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            console.printError(e.getMessage());
            throw new RuntimeException("Failed to create log directory: " + e.getMessage());
        }
    }

    public void onShutdown() {
        try {
            System.out.println("Shutting down logging...");
            rotateLogOnShutdown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void rotateLogOnShutdown() {
        String newLogFileName = LOG_DIR + LocalDateTime.now().format(DATE_FORMAT) + ".log";
        try {
            Files.move(Paths.get(CURRENT_LOG_FILE), Paths.get(newLogFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            console.printError(e.getMessage());
        }
    }

    private void setupMidnightLogZipTask() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduler.scheduleAtFixedRate(() -> {
            if (isFirstDayOfMonth()) {
                zipMonthlyLogs();
            }
        }, computeInitialDelay(), 24, TimeUnit.HOURS);
    }

    private long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
        return java.time.Duration.between(now, midnight).toSeconds();
    }

    private boolean isFirstDayOfMonth() {
        return LocalDateTime.now().getDayOfMonth() == 1;
    }

    private void zipMonthlyLogs() {
        String month = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MM-yyyy")); // Previous month
        String zipFileName = LOG_DIR + "logs-" + month + ".zip";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            Files.list(Paths.get(LOG_DIR))
                    .filter(path -> isMonthlyLog(path, month))
                    .forEach(path -> {
                        try {
                            zos.putNextEntry(new ZipEntry(path.getFileName().toString()));
                            Files.copy(path, zos);
                            zos.closeEntry();
                            Files.delete(path); // Delete the log file after zipping
                        } catch (IOException e) {
                            console.printError(e.getMessage());
                        }
                    });
        } catch (Exception e) {
            console.printError(e.getMessage());
        }
    }

    private boolean isMonthlyLog(Path path, String month) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".log") && fileName.contains("-" + month);
    }
}
