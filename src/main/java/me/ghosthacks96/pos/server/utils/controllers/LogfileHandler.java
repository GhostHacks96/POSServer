package me.ghosthacks96.pos.server.utils.controllers;


import java.io.*;
import java.nio.file.*;
import java.nio.file.DirectoryStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.zip.*;

public class LogfileHandler {

    private static final String LOG_DIR = "logs/";
    private static final String CURRENT_LOG_FILE = LOG_DIR + "latest.log";
    private static final String LOG_PATTERN = "latest.log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final ExecutorService executor;
    private static ScheduledExecutorService scheduler;

    public LogfileHandler() {
        executor = Executors.newSingleThreadExecutor();
        ensureLogDirectoryExists();
        setupMonthlyLogZipTask();
    }

    private void ensureLogDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private void setupMonthlyLogZipTask() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        long initialDelay = computeInitialDelayToFirstOfMonth();
        // Schedule to run at 12:01 AM on the first of every month
        scheduler.scheduleAtFixedRate(this::zipMonthlyLogs, initialDelay, 30 * 24 * 60 * 60, TimeUnit.SECONDS);
    }

    private long computeInitialDelayToFirstOfMonth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextFirst = now.withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(1).withSecond(0).withNano(0);
        return java.time.Duration.between(now, nextFirst).getSeconds();
    }

    private void zipMonthlyLogs() {
        String month = LocalDateTime.now().minusMonths(1).format(MONTH_FORMAT); // Zip last month's logs
        String zipFileName = LOG_DIR + "logs-" + month + ".zip";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(LOG_DIR), LOG_PATTERN);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            for (Path entry : stream) {
                // Only zip logs from the previous month (by last modified time)
                File file = entry.toFile();
                LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(entry).toInstant(),
                    java.time.ZoneId.systemDefault()
                );
                if (lastModified.format(MONTH_FORMAT).equals(month)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    Files.copy(entry, zos);
                    zos.closeEntry();
                    Files.delete(entry); // Remove the log after zipping
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}