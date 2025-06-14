package me.ghosthacks96.pos.server.utils.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import me.ghosthacks96.pos.server.utils.web.TemplateLoader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebInterfaceHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebInterfaceHandler.class);

    private Server server;
    private int port;
    private boolean isRunning = false;

    public WebInterfaceHandler() {
        this(6666); // Default port
    }

    public WebInterfaceHandler(int port) {
        this.port = port;
    }

    /**
     * Starts the Jetty web server
     */
    public void start() {
        logger.info("Starting Jetty web server on port {}...", port);
        POSServer.console.printInfo("Starting Jetty web server on port " + port + "...");

        try {
            server = new Server();

            // Configure the server connector with explicit host binding
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            connector.setHost("0.0.0.0"); // Bind to all interfaces
            connector.setIdleTimeout(30000); // 30 second timeout
            server.addConnector(connector);

            // Create servlet context handler
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);

            // Add servlets
            addServlets(context);

            // Start the server
            server.start();
            isRunning = true;

            // Get the actual bound port (useful if port was 0)
            int actualPort = connector.getLocalPort();

            ConsoleHandler.printInfo("POS Web Interface started successfully!");
            ConsoleHandler.printInfo("Server bound to:"+ connector.getHost()+":"+ actualPort);
            ConsoleHandler.printInfo("Access the web interface at:");
            ConsoleHandler.printInfo("  - http://localhost:"+ actualPort);
            ConsoleHandler.printInfo("  - http://127.0.0.1:"+ actualPort);

            try {
                String localIP = InetAddress.getLocalHost().getHostAddress();
                logger.info("  - http://{}:{}", localIP, actualPort);
            } catch (Exception e) {
                logger.debug("Could not determine local IP: {}", e.getMessage());
            }

            POSServer.console.printInfo("Web interface accessible at http://localhost:" + actualPort);

        } catch (Exception e) {
            logger.error("Failed to start web interface on port {}", port, e);

            // Check if port is already in use
            if (e.getMessage().contains("Address already in use") ||
                    e.getMessage().contains("bind")) {
                logger.error("Port {} appears to be already in use. Try a different port.", port);
                POSServer.console.printError("Port " + port + " is already in use!");
            }

            throw new RuntimeException("Failed to start web interface", e);
        }
    }

    /**
     * Stops the Jetty web server
     */
    public void stop() {
        logger.info("Stopping Jetty web server...");
        POSServer.console.printInfo("Stopping Jetty web server...");

        if (server != null && isRunning) {
            try {
                server.stop();
                server.destroy();
                isRunning = false;
                logger.info("POS Web Interface stopped");
            } catch (Exception e) {
                logger.error("Error stopping web interface", e);
            }
        }
    }

    /**
     * Joins the server thread (blocks until server stops)
     */
    public void join() {
        if (server != null) {
            try {
                server.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Server join interrupted", e);
            }
        }
    }

    /**
     * Adds servlets to the context handler
     */
    private void addServlets(ServletContextHandler context) {
        // Main dashboard servlet
        context.addServlet(new ServletHolder(new DashboardServlet()), "/");
        context.addServlet(new ServletHolder(new DashboardServlet()), "/dashboard");

        // API endpoints
        context.addServlet(new ServletHolder(new ApiServlet()), "/api/*");

        // Static resources (CSS, JS, images)
        context.addServlet(new ServletHolder(new StaticResourceServlet()), "/static/*");

        // Health check endpoint
        context.addServlet(new ServletHolder(new HealthCheckServlet()), "/health");
    }

    /**
     * Check if the server is running
     */
    public boolean isRunning() {
        boolean running = isRunning && server != null && server.isRunning();
        logger.debug("Web server running status: {}", running);
        return running;
    }

    /**
     * Get the current port
     */
    public int getPort() {
        if (server != null && server.isStarted()) {
            ServerConnector connector = (ServerConnector) server.getConnectors()[0];
            return connector.getLocalPort();
        }
        return port;
    }

    /**
     * Set a new port (only works if server is not running)
     */
    public void setPort(int port) {
        if (!isRunning) {
            this.port = port;
        } else {
            logger.warn("Cannot change port while server is running");
        }
    }

    // Inner servlet classes

    /**
     * Health check servlet for testing connectivity
     */
    private static class HealthCheckServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_OK);

            PrintWriter out = resp.getWriter();
            out.println("""
                {
                    "status": "healthy",
                    "timestamp": "%s",
                    "server": "POS Web Interface"
                }
                """.formatted(java.time.Instant.now()));
        }
    }

    /**
     * Main dashboard servlet
     */
    private static class DashboardServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            logger.info("DashboardServlet GET request from {}", req.getRemoteAddr());
            POSServer.console.printInfo("DashboardServlet GET request from " + req.getRemoteAddr());

            resp.setContentType("text/html; charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);

            // Prepare template data
            Map<String, String> templateData = new HashMap<>();
            templateData.put("title", "Dashboard");
            templateData.put("pageTitle", "System Dashboard");
            templateData.put("version", "1.0-SNAPSHOT");
            templateData.put("serverTime", Instant.now().toString());
            templateData.put("lastUpdated", Instant.now().toString());

            // System status data (you'd get this from your actual system)
            templateData.put("statusClass", "online");
            templateData.put("serverStatus", "online");
            templateData.put("serverStatusText", "Online");
            templateData.put("dbStatus", "online");
            templateData.put("dbStatusText", "Connected");

            // Statistics data (placeholder - replace with real data)
            templateData.put("todaySales", "$1,234.56");
            templateData.put("todayTransactions", "42");
            templateData.put("connectedClients", "3");
            templateData.put("totalProducts", "156");

            // Recent activity (you'd generate this from your transaction log)
            templateData.put("recentActivity", generateRecentActivityHTML());

            // Load and render template
            String html = TemplateLoader.loadTemplate("dashboard.html", templateData);

            PrintWriter out = resp.getWriter();
            out.println(html);
        }

        private String generateRecentActivityHTML() {
            // This would come from your actual transaction/activity log
            return """
            <div class="activity-item">
                <div class="activity-time">2 minutes ago</div>
                <div class="activity-description">New transaction completed - $45.67</div>
            </div>
            <div class="activity-item">
                <div class="activity-time">5 minutes ago</div>
                <div class="activity-description">Product updated: Coffee Beans</div>
            </div>
            <div class="activity-item">
                <div class="activity-time">10 minutes ago</div>
                <div class="activity-description">New client connected from 192.168.1.105</div>
            </div>
            """;
        }
    }

    /**
     * API servlet for handling REST endpoints
     */
    private static class ApiServlet extends HttpServlet {
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            handleRequest(req, resp, "GET");
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            handleRequest(req, resp, "POST");
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            handleRequest(req, resp, "PUT");
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            handleRequest(req, resp, "DELETE");
        }

        private void handleRequest(HttpServletRequest req, HttpServletResponse resp, String method)
                throws ServletException, IOException {
            String pathInfo = req.getPathInfo();
            logger.info("API {} request: {} from {}", method, pathInfo, req.getRemoteAddr());

            resp.setContentType("application/json; charset=UTF-8");
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

            try {
                String response = routeRequest(method, pathInfo, req);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(response);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } catch (Exception e) {
                logger.error("API error for {} {}", method, pathInfo, e);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Internal server error: " + e.getMessage());
            }
        }

        private String routeRequest(String method, String pathInfo, HttpServletRequest req)
                throws IOException {
            if (pathInfo == null) pathInfo = "/";

            String[] pathParts = pathInfo.split("/");
            String resource = pathParts.length > 1 ? pathParts[1] : "";
            String id = pathParts.length > 2 ? pathParts[2] : null;

            return switch (resource) {
                case "", "status" -> handleStatus();
                case "products" -> handleProducts(method, id, req);
                case "transactions" -> handleTransactions(method, id, req);
                case "reports" -> handleReports(req);
                case "stats" -> handleStats();
                case "clients" -> handleClients();
                default -> createErrorResponse("Unknown endpoint: " + pathInfo);
            };
        }

        private String handleStatus() {
            Map<String, Object> status = new HashMap<>();
            status.put("status", "online");
            status.put("timestamp", Instant.now().toString());
            status.put("version", "1.0-SNAPSHOT");
            status.put("server", "POS Web Interface");
            status.put("uptime", getUptime());
            status.put("endpoints", Arrays.asList(
                    "/api/status", "/api/products", "/api/transactions",
                    "/api/reports", "/api/stats", "/api/clients"
            ));

            return toJson(status);
        }

        private String handleProducts(String method, String id, HttpServletRequest req)
                throws IOException {
            return switch (method) {
                case "GET" -> id != null ? getProduct(id) : getAllProducts();
                case "POST" -> createProduct(req);
                case "PUT" -> updateProduct(id, req);
                case "DELETE" -> deleteProduct(id);
                default -> createErrorResponse("Method not allowed");
            };
        }

        private String getAllProducts() {
            // In a real implementation, this would query your database
            List<Map<String, Object>> products = Arrays.asList(
                    createProductMap("1", "Coffee Beans", "Premium coffee beans", 12.99, 50),
                    createProductMap("2", "Tea Bags", "Earl Grey tea bags", 8.50, 30),
                    createProductMap("3", "Muffin", "Blueberry muffin", 3.25, 25)
            );

            Map<String, Object> response = new HashMap<>();
            response.put("products", products);
            response.put("total", products.size());
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String getProduct(String id) {
            // Mock product lookup
            Map<String, Object> product = createProductMap(id, "Coffee Beans",
                    "Premium coffee beans", 12.99, 50);
            return toJson(product);
        }

        private String createProduct(HttpServletRequest req) throws IOException {
            String body = req.getReader().lines().collect(Collectors.joining());
            Map<String, Object> productData = fromJson(body);

            // Validate required fields
            if (!productData.containsKey("name") || !productData.containsKey("price")) {
                throw new IllegalArgumentException("Name and price are required");
            }

            // In real implementation, save to database and return created product
            String newId = String.valueOf(System.currentTimeMillis());
            Map<String, Object> response = new HashMap<>();
            response.put("id", newId);
            response.put("message", "Product created successfully");
            response.put("product", productData);
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String updateProduct(String id, HttpServletRequest req) throws IOException {
            if (id == null) {
                throw new IllegalArgumentException("Product ID is required for update");
            }

            String body = req.getReader().lines().collect(Collectors.joining());
            Map<String, Object> productData = fromJson(body);

            // In real implementation, update in database
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("message", "Product updated successfully");
            response.put("product", productData);
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String deleteProduct(String id) {
            if (id == null) {
                throw new IllegalArgumentException("Product ID is required for deletion");
            }

            // In real implementation, delete from database
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("message", "Product deleted successfully");
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String handleTransactions(String method, String id, HttpServletRequest req)
                throws IOException {
            return switch (method) {
                case "GET" -> id != null ? getTransaction(id) : getAllTransactions();
                case "POST" -> createTransaction(req);
                default -> createErrorResponse("Method not allowed for transactions");
            };
        }

        private String getAllTransactions() {
            List<Map<String, Object>> transactions = Arrays.asList(
                    createTransactionMap("1", "COMPLETED", 45.67, Instant.now().minusSeconds(300)),
                    createTransactionMap("2", "COMPLETED", 23.45, Instant.now().minusSeconds(600)),
                    createTransactionMap("3", "PENDING", 67.89, Instant.now().minusSeconds(120))
            );

            Map<String, Object> response = new HashMap<>();
            response.put("transactions", transactions);
            response.put("total", transactions.size());
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String getTransaction(String id) {
            Map<String, Object> transaction = createTransactionMap(id, "COMPLETED",
                    45.67, Instant.now().minusSeconds(300));
            return toJson(transaction);
        }

        private String createTransaction(HttpServletRequest req) throws IOException {
            String body = req.getReader().lines().collect(Collectors.joining());
            Map<String, Object> transactionData = fromJson(body);

            // Validate transaction data
            if (!transactionData.containsKey("items") || !transactionData.containsKey("total")) {
                throw new IllegalArgumentException("Items and total are required");
            }

            String newId = String.valueOf(System.currentTimeMillis());
            Map<String, Object> response = new HashMap<>();
            response.put("id", newId);
            response.put("status", "COMPLETED");
            response.put("message", "Transaction processed successfully");
            response.put("transaction", transactionData);
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        private String handleReports(HttpServletRequest req) {
            String reportType = req.getParameter("type");
            if (reportType == null) reportType = "daily";

            Map<String, Object> report = new HashMap<>();
            report.put("type", reportType);
            report.put("period", getPeriodForReportType(reportType));
            report.put("totalSales", 1234.56);
            report.put("totalTransactions", 42);
            report.put("averageTransaction", 29.39);
            report.put("topProducts", Arrays.asList(
                    Map.of("name", "Coffee Beans", "sales", 456.78, "quantity", 23),
                    Map.of("name", "Tea Bags", "sales", 234.56, "quantity", 15)
            ));
            report.put("timestamp", Instant.now().toString());

            return toJson(report);
        }

        private String handleStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("connectedClients", getConnectedClientsCount());
            stats.put("todaySales", 1234.56);
            stats.put("todayTransactions", 42);
            stats.put("totalProducts", 156);
            stats.put("serverUptime", getUptime());
            stats.put("memoryUsage", getMemoryUsage());
            stats.put("timestamp", Instant.now().toString());

            return toJson(stats);
        }

        private String handleClients() {
            // Mock connected clients data
            List<Map<String, Object>> clients = Arrays.asList(
                    Map.of("id", "client-1", "ip", "192.168.1.100", "connected", Instant.now().minusSeconds(1800).toString()),
                    Map.of("id", "client-2", "ip", "192.168.1.101", "connected", Instant.now().minusSeconds(3600).toString())
            );

            Map<String, Object> response = new HashMap<>();
            response.put("clients", clients);
            response.put("total", clients.size());
            response.put("timestamp", Instant.now().toString());

            return toJson(response);
        }

        // Helper methods
        private Map<String, Object> createProductMap(String id, String name, String description,
                                                     double price, int stock) {
            Map<String, Object> product = new HashMap<>();
            product.put("id", id);
            product.put("name", name);
            product.put("description", description);
            product.put("price", price);
            product.put("stock", stock);
            product.put("category", "Beverages");
            product.put("createdAt", Instant.now().toString());
            return product;
        }

        private Map<String, Object> createTransactionMap(String id, String status,
                                                         double total, Instant timestamp) {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("id", id);
            transaction.put("status", status);
            transaction.put("total", total);
            transaction.put("items", Arrays.asList(
                    Map.of("productId", "1", "name", "Coffee Beans", "quantity", 2, "price", 12.99)
            ));
            transaction.put("timestamp", timestamp.toString());
            return transaction;
        }

        private String getPeriodForReportType(String type) {
            return switch (type) {
                case "daily" -> LocalDate.now().toString();
                case "weekly" -> "Week of " + LocalDate.now().with(DayOfWeek.MONDAY);
                case "monthly" -> YearMonth.now().toString();
                default -> "Custom period";
            };
        }

        private int getConnectedClientsCount() {
            // In real implementation, get from connection manager
            return 3;
        }

        private String getUptime() {
            // Mock uptime calculation
            return "2 hours 34 minutes";
        }

        private Map<String, Object> getMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            Map<String, Object> memory = new HashMap<>();
            memory.put("total", totalMemory / (1024 * 1024) + " MB");
            memory.put("used", usedMemory / (1024 * 1024) + " MB");
            memory.put("free", freeMemory / (1024 * 1024) + " MB");
            return memory;
        }

        private void sendErrorResponse(HttpServletResponse resp, int status, String message)
                throws IOException {
            resp.setStatus(status);
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", message);
            error.put("status", status);
            error.put("timestamp", Instant.now().toString());
            resp.getWriter().println(toJson(error));
        }

        private String createErrorResponse(String message) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", message);
            error.put("timestamp", Instant.now().toString());
            return toJson(error);
        }

        private String toJson(Object obj) {
            try {
                return objectMapper.writeValueAsString(obj);
            } catch (Exception e) {
                logger.error("JSON serialization error", e);
                return "{\"error\": \"JSON serialization failed\"}";
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> fromJson(String json) {
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                logger.error("JSON deserialization error", e);
                throw new IllegalArgumentException("Invalid JSON format");
            }
        }
    }

    /**
     * Static resource servlet for CSS, JS, images
     */
    private static class StaticResourceServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Remove leading slash and prepend web/static/
            String resourcePath = "/web/static" + pathInfo;

            try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    // Set content type based on file extension
                    String contentType = getContentType(pathInfo);
                    resp.setContentType(contentType);
                    resp.setStatus(HttpServletResponse.SC_OK);

                    // Copy file content to response
                    inputStream.transferTo(resp.getOutputStream());
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().println("Static resource not found: " + pathInfo);
                }
            } catch (Exception e) {
                logger.error("Error serving static resource: {}", pathInfo, e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain";
        }
    }

}