package me.ghosthacks96.pos.server.utils.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.Config;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class SettingsHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(SettingsHandler.class);
    private final Config config;

    public SettingsHandler() {
        this.config = POSServer.config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            switch (method) {
                case "GET":
                    handleGet(exchange, path);
                    break;
                case "POST":
                    handlePost(exchange, path);
                    break;
                default:
                    sendMethodNotAllowed(exchange);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error handling settings request: {}", e.getMessage(), e);
            sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/settings")) {
            getSettings(exchange);
        } else {
            sendNotFound(exchange);
        }
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        switch (path) {
            case "/api/settings":
                updateSettings(exchange);
                break;
            case "/api/test-connection":
                testConnection(exchange);
                break;
            case "/api/restart":
                restartServer(exchange);
                break;
            default:
                sendNotFound(exchange);
                break;
        }
    }

    private void getSettings(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> currentConfig = Config.getConfig();
            JSONObject response = new JSONObject();

            // Add current configuration
            if (currentConfig != null) {
                for (Map.Entry<String, Object> entry : currentConfig.entrySet()) {
                    response.put(entry.getKey(), entry.getValue());
                }
            }

            // Add system information
            response.put("systemVersion", "1.0.0");

            // Get config file modification time
            try {
                long lastModified = Files.getLastModifiedTime(Paths.get("config.yml")).toMillis();
                response.put("lastModified", lastModified);
            } catch (Exception e) {
                logger.warn("Could not get config file modification time: {}", e.getMessage());
            }

            sendJsonResponse(exchange, 200, response.toString());
            logger.debug("Settings retrieved successfully");

        } catch (Exception e) {
            logger.error("Error retrieving settings: {}", e.getMessage(), e);
            sendErrorResponse(exchange, 500, "Failed to retrieve settings: " + e.getMessage());
        }
    }

    private void updateSettings(HttpExchange exchange) throws IOException {
        try {
            // Read request body
            String requestBody = readRequestBody(exchange);
            JSONObject settingsJson = new JSONObject(requestBody);

            // Validate settings
            validateSettings(settingsJson);

            // Update each setting individually
            for (String key : settingsJson.keySet()) {
                Object value = settingsJson.get(key);
                config.setConfigOption(key, value);
                logger.debug("Updated setting: {} = {}", key, value);
            }

            // Send success response
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Settings updated successfully");

            sendJsonResponse(exchange, 200, response.toString());

            POSServer.console.printInfo("Settings updated via web interface");
            logger.info("Settings updated successfully via web interface");

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid settings provided: {}", e.getMessage());
            sendErrorResponse(exchange, 400, "Invalid settings: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating settings: {}", e.getMessage(), e);
            sendErrorResponse(exchange, 500, "Failed to update settings: " + e.getMessage());
        }
    }

    private void validateSettings(JSONObject settings) throws IllegalArgumentException {
        // Validate port
        if (settings.has("port")) {
            int port = settings.getInt("port");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
        }

        // Validate database file
        if (settings.has("db_file")) {
            String dbFile = settings.getString("db_file");
            if (dbFile == null || dbFile.trim().isEmpty()) {
                throw new IllegalArgumentException("Database file cannot be empty");
            }
        }

        // Validate remote host URL if remote access is enabled
        if (settings.has("db_getremote") && settings.getBoolean("db_getremote")) {
            if (!settings.has("db-r-host") || settings.getString("db-r-host").trim().isEmpty()) {
                throw new IllegalArgumentException("Remote host URL is required when remote database access is enabled");
            }

            String hostUrl = settings.getString("db-r-host");
            try {
                new URL(hostUrl); // Basic URL validation
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid remote host URL format");
            }
        }
    }

    private void testConnection(HttpExchange exchange) throws IOException {
        try {
            String requestBody = readRequestBody(exchange);
            JSONObject request = new JSONObject(requestBody);
            String hostUrl = request.getString("host");

            JSONObject response = new JSONObject();

            if (hostUrl == null || hostUrl.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Host URL is required");
                sendJsonResponse(exchange, 400, response.toString());
                return;
            }

            // Test connection
            boolean connectionSuccessful = testRemoteConnection(hostUrl);

            response.put("success", connectionSuccessful);
            response.put("message", connectionSuccessful ? "Connection successful" : "Connection failed");

            sendJsonResponse(exchange, 200, response.toString());

            logger.debug("Connection test for {}: {}", hostUrl, connectionSuccessful ? "SUCCESS" : "FAILED");

        } catch (Exception e) {
            logger.error("Error testing connection: {}", e.getMessage(), e);

            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Connection test failed: " + e.getMessage());

            sendJsonResponse(exchange, 200, response.toString());
        }
    }

    private boolean testRemoteConnection(String hostUrl) {
        try {
            URL url = new URL(hostUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 400;

        } catch (Exception e) {
            logger.debug("Connection test failed for {}: {}", hostUrl, e.getMessage());
            return false;
        }
    }

    private void restartServer(HttpExchange exchange) throws IOException {
        try {
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Server restart initiated");

            sendJsonResponse(exchange, 200, response.toString());

            POSServer.console.printInfo("Server restart requested via web interface");
            logger.info("Server restart requested via web interface");

            // Restart server in a separate thread to allow response to be sent first
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Give time for response to be sent
                    POSServer.shutdownSystem();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Server restart interrupted: {}", e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            logger.error("Error initiating server restart: {}", e.getMessage(), e);
            sendErrorResponse(exchange, 500, "Failed to restart server: " + e.getMessage());
        }
    }