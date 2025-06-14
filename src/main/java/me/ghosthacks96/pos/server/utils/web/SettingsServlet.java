package me.ghosthacks96.pos.server.utils.web;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.Config;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

public class SettingsServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SettingsServlet.class);
    private final Config config;

    public SettingsServlet() {
        this.config = POSServer.config;
    }

    // Alternative constructor for dependency injection
    public SettingsServlet(Config config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        // Debug logging
        logger.debug("GET request - PathInfo: '{}', RequestURI: '{}'", pathInfo, request.getRequestURI());

        try {
            // Handle API endpoints first to avoid confusion
            if ("/settings".equals(pathInfo)) {
                // API endpoint to get settings data
                getSettings(response);
            } else if (pathInfo == null || pathInfo.equals("/")) {
                // Serve the settings HTML page
                serveSettingsPage(response);
            } else {
                logger.debug("Path not found: {}", pathInfo);
                sendNotFound(response);
            }
        } catch (Exception e) {
            logger.error("Error handling GET settings request: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        // Debug logging
        logger.debug("POST request - PathInfo: '{}', RequestURI: '{}'", pathInfo, request.getRequestURI());

        try {
            if (pathInfo == null) pathInfo = "/";

            switch (pathInfo) {
                case "/settings":
                    updateSettings(request, response);
                    break;
                case "/test-connection":
                    testConnection(request, response);
                    break;
                case "/restart":
                    restartServer(response);
                    break;
                default:
                    logger.debug("POST path not found: {}", pathInfo);
                    sendNotFound(response);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error handling POST settings request: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void serveSettingsPage(HttpServletResponse response) throws IOException {
        try {
            // Load the HTML template from resources
            String htmlContent = loadHtmlTemplate();

            response.setContentType("text/html");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);

            try (PrintWriter writer = response.getWriter()) {
                writer.write(htmlContent);
                writer.flush();
            }

            logger.debug("Settings page served successfully");

        } catch (Exception e) {
            logger.error("Error serving settings page: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Failed to load settings page: " + e.getMessage());
        }
    }

    private String loadHtmlTemplate() throws IOException {
        // Try to load from classpath resources first
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("web/templates/settings.html")) {

            if (inputStream != null) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.warn("Could not load settings.html from classpath: {}", e.getMessage());
        }

        // Fallback to file system
        try {
            return Files.readString(Paths.get("src/main/resources/web/templates/settings.html"),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Could not load settings.html from file system: {}", e.getMessage());
        }

        // Last resort - return a basic HTML page
        return createBasicSettingsPage();
    }

    private String createBasicSettingsPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Settings - POS System</title>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .error { color: red; padding: 10px; background: #ffe6e6; border: 1px solid red; }
                </style>
            </head>
            <body>
                <h1>Settings - POS System</h1>
                <div class="error">
                    <p>Settings template could not be loaded. Please ensure settings.html is available in the resources.</p>
                    <p>API endpoints are still available at:</p>
                    <ul>
                        <li>GET /settings/api/settings - Get current settings</li>
                        <li>POST /settings/api/settings - Update settings</li>
                        <li>POST /settings/api/test-connection - Test database connection</li>
                        <li>POST /settings/api/restart - Restart server</li>
                    </ul>
                </div>
            </body>
            </html>
            """;
    }

    private void getSettings(HttpServletResponse response) throws IOException {
        logger.debug("Processing getSettings API call");

        try {
            Map<String, Object> currentConfig = Config.getConfig();
            JSONObject jsonResponse = new JSONObject();

            // Add current configuration
            if (currentConfig != null) {
                for (Map.Entry<String, Object> entry : currentConfig.entrySet()) {
                    jsonResponse.put(entry.getKey(), entry.getValue());
                }
            } else {
                logger.warn("Config.getConfig() returned null, using defaults");
                // Add some default values if config is null
                jsonResponse.put("port", 666);
                jsonResponse.put("auto-start", true);
                jsonResponse.put("debug", true);
                jsonResponse.put("db_file", "pos.db");
                jsonResponse.put("db_getremote", false);
                jsonResponse.put("db-r-host", "");
            }

            // Add system information
            jsonResponse.put("systemVersion", "1.0.0");

            // Get config file modification time
            try {
                long lastModified = Files.getLastModifiedTime(Paths.get("config.yml")).toMillis();
                jsonResponse.put("lastModified", lastModified);
            } catch (Exception e) {
                logger.warn("Could not get config file modification time: {}", e.getMessage());
                jsonResponse.put("lastModified", System.currentTimeMillis());
            }

            String jsonString = jsonResponse.toString();
            logger.debug("Sending JSON response: {}", jsonString);

            sendJsonResponse(response, 200, jsonString);
            logger.debug("Settings retrieved successfully");

        } catch (Exception e) {
            logger.error("Error retrieving settings: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Failed to retrieve settings: " + e.getMessage());
        }
    }

    private void updateSettings(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.debug("Processing updateSettings API call");

        try {
            // Read request body
            String requestBody = readRequestBody(request);
            logger.debug("Request body: {}", requestBody);

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
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("success", true);
            jsonResponse.put("message", "Settings updated successfully");

            sendJsonResponse(response, 200, jsonResponse.toString());

            POSServer.console.printInfo("Settings updated via web interface");
            logger.info("Settings updated successfully via web interface");

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid settings provided: {}", e.getMessage());
            sendErrorResponse(response, 400, "Invalid settings: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating settings: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Failed to update settings: " + e.getMessage());
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

    private void testConnection(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.debug("Processing testConnection API call");

        try {
            String requestBody = readRequestBody(request);
            JSONObject requestJson = new JSONObject(requestBody);
            String hostUrl = requestJson.getString("host");

            JSONObject jsonResponse = new JSONObject();

            if (hostUrl == null || hostUrl.trim().isEmpty()) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "Host URL is required");
                sendJsonResponse(response, 400, jsonResponse.toString());
                return;
            }

            // Test connection
            boolean connectionSuccessful = testRemoteConnection(hostUrl);

            jsonResponse.put("success", connectionSuccessful);
            jsonResponse.put("message", connectionSuccessful ? "Connection successful" : "Connection failed");

            sendJsonResponse(response, 200, jsonResponse.toString());

            logger.debug("Connection test for {}: {}", hostUrl, connectionSuccessful ? "SUCCESS" : "FAILED");

        } catch (Exception e) {
            logger.error("Error testing connection: {}", e.getMessage(), e);

            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Connection test failed: " + e.getMessage());

            sendJsonResponse(response, 200, jsonResponse.toString());
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

    private void restartServer(HttpServletResponse response) throws IOException {
        logger.debug("Processing restartServer API call");

        try {
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("success", true);
            jsonResponse.put("message", "Server restart initiated");

            sendJsonResponse(response, 200, jsonResponse.toString());

            POSServer.console.printInfo("Server restart requested via web interface");
            logger.info("Server restart requested via web interface");

            // Restart server in a separate thread to allow response to be sent first
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Give time for response to be sent
                    POSServer.shutdownServer();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Server restart interrupted: {}", e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            logger.error("Error initiating server restart: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Failed to restart server: " + e.getMessage());
        }
    }

    private String readRequestBody(HttpServletRequest request) throws IOException {
        return request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
    }

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJsonResponse(HttpServletResponse response, int statusCode, String jsonResponse) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        setCorsHeaders(response);
        response.setStatus(statusCode);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(jsonResponse);
            writer.flush();
        }

        logger.debug("Sent JSON response with status {}: {}", statusCode, jsonResponse);
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("success", false);
        errorResponse.put("error", errorMessage);

        sendJsonResponse(response, statusCode, errorResponse.toString());
    }

    private void sendNotFound(HttpServletResponse response) throws IOException {
        sendErrorResponse(response, 404, "Endpoint not found");
    }
}