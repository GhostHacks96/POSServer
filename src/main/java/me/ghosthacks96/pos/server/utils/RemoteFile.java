package me.ghosthacks96.pos.server.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class RemoteFile {


    private String fileURL;
    public RemoteFile(String fileURL) {
        if (fileURL == null || fileURL.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or blank");
        }
        this.fileURL = fileURL;
    }

    public void download() {
        String dbFileName = Config.getConfig().get("dbfile")+"";
        if (dbFileName == null || dbFileName.isBlank()) {
            throw new IllegalStateException("Database file name is not set in config");
        }
        try {
            // Download remote file to a temp file
            File tempFile = File.createTempFile("remotedb", ".tmp");
            try (InputStream in = new URL(fileURL).openStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            File localFile = new File(dbFileName);
            boolean shouldReplace = false;
            if (!localFile.exists()) {
                shouldReplace = true;
            } else {
                byte[] localBytes = Files.readAllBytes(localFile.toPath());
                byte[] remoteBytes = Files.readAllBytes(tempFile.toPath());
                if (!Arrays.equals(localBytes, remoteBytes)) {
                    shouldReplace = true;
                }
            }
            if (shouldReplace) {
                Files.copy(tempFile.toPath(), localFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile.delete();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download or update file: " + e.getMessage(), e);
        }
    }
}
