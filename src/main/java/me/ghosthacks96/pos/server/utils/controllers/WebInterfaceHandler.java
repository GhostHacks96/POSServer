package me.ghosthacks96.pos.server.utils.controllers;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
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
import java.io.PrintWriter;
import java.net.InetAddress;

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

            resp.setContentType("text/html");
            resp.setStatus(HttpServletResponse.SC_OK);

            PrintWriter out = resp.getWriter();
            out.println(generateDashboardHTML());
        }

        private String generateDashboardHTML() {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>POS System Dashboard</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body { font-family: Arial, sans-serif; background-color: #f5f5f5; }
                        .header { background-color: #2c3e50; color: white; padding: 1rem; text-align: center; }
                        .container { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }
                        .card { background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 2rem; padding: 1.5rem; }
                        .card h2 { color: #2c3e50; margin-bottom: 1rem; }
                        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1rem; }
                        .stat-card { background: #3498db; color: white; padding: 1.5rem; border-radius: 8px; text-align: center; }
                        .stat-number { font-size: 2rem; font-weight: bold; }
                        .stat-label { font-size: 0.9rem; opacity: 0.9; }
                        .button { background: #3498db; color: white; padding: 0.75rem 1.5rem; border: none; border-radius: 4px; cursor: pointer; text-decoration: none; display: inline-block; margin: 0.25rem; }
                        .button:hover { background: #2980b9; }
                        .status { padding: 0.5rem 1rem; border-radius: 4px; font-weight: bold; }
                        .status.online { background: #2ecc71; color: white; }
                        .status.offline { background: #e74c3c; color: white; }
                        .connection-info { background: #ecf0f1; padding: 1rem; border-radius: 4px; margin-top: 1rem; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>POS System Dashboard</h1>
                        <p>Point of Sale Management Interface</p>
                    </div>
                    
                    <div class="container">
                        <div class="card">
                            <h2>System Status</h2>
                            <p>Server Status: <span class="status online">Online</span></p>
                            <p>Database Connection: <span class="status online">Connected</span></p>
                            <p>Last Updated: <span id="timestamp"></span></p>
                            
                            <div class="connection-info">
                                <h3>Connection Test</h3>
                                <p>If you can see this page, the web interface is working correctly!</p>
                                <p>Server Time: <span id="server-time"></span></p>
                            </div>
                        </div>
                        
                        <div class="card">
                            <h2>Quick Stats</h2>
                            <div class="stats-grid">
                                <div class="stat-card">
                                    <div class="stat-number" id="connected-clients">0</div>
                                    <div class="stat-label">Connected Clients</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-number">0</div>
                                    <div class="stat-label">Today's Sales</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-number">0</div>
                                    <div class="stat-label">Active Transactions</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-number">0</div>
                                    <div class="stat-label">Total Products</div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="card">
                            <h2>Quick Actions</h2>
                            <a href="/api/status" class="button">View API Status</a>
                            <a href="/health" class="button">Health Check</a>
                            <a href="/api/products" class="button">Manage Products</a>
                            <a href="/api/transactions" class="button">View Transactions</a>
                            <a href="/api/reports" class="button">Generate Reports</a>
                        </div>
                    </div>
                    
                    <script>
                        function updateTimestamp() {
                            const now = new Date();
                            document.getElementById('timestamp').textContent = now.toLocaleString();
                            document.getElementById('server-time').textContent = now.toLocaleString();
                        }
                        
                        // Update timestamp immediately and every 30 seconds
                        updateTimestamp();
                        setInterval(updateTimestamp, 30000);
                        
                        // Simple connected clients counter (placeholder)
                        // In a real implementation, this would fetch from your server
                        setTimeout(() => {
                            // This is just a placeholder - you'd want to fetch real data
                            const clientCount = Math.floor(Math.random() * 5);
                            document.getElementById('connected-clients').textContent = clientCount;
                        }, 1000);
                    </script>
                </body>
                </html>
                """;
        }
    }

    /**
     * API servlet for handling REST endpoints
     */
    private static class ApiServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String pathInfo = req.getPathInfo();
            logger.info("API request: {} from {}", pathInfo, req.getRemoteAddr());

            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_OK);

            PrintWriter out = resp.getWriter();

            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/status")) {
                out.println("""
                    {
                        "status": "online",
                        "timestamp": "%s",
                        "version": "1.0-SNAPSHOT",
                        "server": "POS Web Interface",
                        "endpoints": [
                            "/api/status",
                            "/api/products",
                            "/api/transactions",
                            "/api/reports",
                            "/health"
                        ]
                    }
                    """.formatted(java.time.Instant.now()));
            } else {
                out.println("""
                    {
                        "error": "Endpoint not implemented yet",
                        "path": "%s",
                        "message": "This API endpoint is under development",
                        "timestamp": "%s"
                    }
                    """.formatted(pathInfo, java.time.Instant.now()));
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
            resp.setContentType("text/plain");
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().println("Static resource not found: " + req.getPathInfo());
        }
    }
}