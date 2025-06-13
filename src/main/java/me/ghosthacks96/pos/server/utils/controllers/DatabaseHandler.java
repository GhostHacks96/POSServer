package me.ghosthacks96.pos.server.utils.controllers;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import me.ghosthacks96.pos.server.utils.models.*;
import me.ghosthacks96.pos.server.utils.perms.PermissionCategory;
import me.ghosthacks96.pos.server.utils.perms.PermissionLevel;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class DatabaseHandler {

    // Database connection details
    private static String DB_URL = "jdbc:mysql://";
    private static String DB_USER = "";
    private static String DB_PASSWORD = "pos_password";
    private static int DB_PORT = 3306; // Default MySQL port

    // Connection pool (simple implementation)
    private final Map<Thread, Connection> connectionPool = new ConcurrentHashMap<>();
    private final Object poolLock = new Object();

    // SQL Queries
    private static final String CREATE_USERS_TABLE = """
        CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            username VARCHAR(50) UNIQUE NOT NULL,
            password_hash VARCHAR(255) NOT NULL,
            salt VARCHAR(255) NOT NULL,
            is_admin BOOLEAN DEFAULT FALSE,
            is_active BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_login TIMESTAMP NULL,
            INDEX idx_username (username),
            INDEX idx_active (is_active)
        )
    """;

    private static final String CREATE_PERMISSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS permissions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            permission_id VARCHAR(50) UNIQUE NOT NULL,
            name VARCHAR(100) NOT NULL,
            description TEXT,
            category VARCHAR(50) NOT NULL,
            level VARCHAR(20) NOT NULL,
            dependencies JSON,
            is_active BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_permission_id (permission_id),
            INDEX idx_category (category)
        )
    """;

    private static final String CREATE_USER_PERMISSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS user_permissions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            permission_id VARCHAR(50) NOT NULL,
            granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE,
            UNIQUE KEY unique_user_permission (user_id, permission_id)
        )
    """;

    private static final String CREATE_TRANSACTIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS transactions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            transaction_id VARCHAR(50) UNIQUE NOT NULL,
            customer_id VARCHAR(50),
            employee_id VARCHAR(50) NOT NULL,
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            subtotal DECIMAL(10,2) NOT NULL,
            tax_amount DECIMAL(10,2) DEFAULT 0.00,
            discount_amount DECIMAL(10,2) DEFAULT 0.00,
            total_amount DECIMAL(10,2) NOT NULL,
            payment_method VARCHAR(20) NOT NULL,
            status VARCHAR(20) NOT NULL,
            INDEX idx_transaction_id (transaction_id),
            INDEX idx_employee_id (employee_id),
            INDEX idx_timestamp (timestamp),
            INDEX idx_status (status)
        )
    """;

    public DatabaseHandler(String dbUrl, String dbUser, String dbPassword,int dbPort) {
        DB_PORT = dbPort;
        DB_URL = DB_URL+ dbUrl + ":" + DB_PORT + "/pos_db"; // Assuming pos_db is the database name
        DB_USER = dbUser;
        DB_PASSWORD = dbPassword;

        ConsoleHandler.printInfo("Initializing database connection with URL: " + DB_URL + ", User: " + DB_USER + ", Port: " + DB_PORT);
        initializeDatabase();
    }

    /**
     * Initialize database connection and create tables
     */
    private void initializeDatabase() {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Test connection
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connection established successfully");

                // Create tables
                createTables(conn);

                // Insert default permissions if they don't exist
                insertDefaultPermissions(conn);

                // Create default admin user if it doesn't exist
                createDefaultAdminUser(conn);

            }
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            POSServer.shutdownSystem();
        }
    }

    /**
     * Get database connection for current thread
     */
    private Connection getConnection() throws SQLException {
        Thread currentThread = Thread.currentThread();
        Connection conn = connectionPool.get(currentThread);

        if (conn == null || conn.isClosed()) {
            synchronized (poolLock) {
                conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                connectionPool.put(currentThread, conn);
            }
        }

        return conn;
    }

    /**
     * Close connection for current thread
     */
    public void closeConnection() {
        Thread currentThread = Thread.currentThread();
        Connection conn = connectionPool.get(currentThread);

        if (conn != null) {
            try {
                conn.close();
                connectionPool.remove(currentThread);
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    /**
     * Create database tables
     */
    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_USERS_TABLE);
            stmt.execute(CREATE_PERMISSIONS_TABLE);
            stmt.execute(CREATE_USER_PERMISSIONS_TABLE);
            stmt.execute(CREATE_TRANSACTIONS_TABLE);
            System.out.println("Database tables created successfully");
        }
    }

    /**
     * Insert default permissions into database
     */
    private void insertDefaultPermissions(Connection conn) throws SQLException {
        String insertPermissionSQL = """
            INSERT IGNORE INTO permissions (permission_id, name, description, category, level, dependencies, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(insertPermissionSQL)) {
            // You would get these from your PosPermissions class
            insertPermission(stmt, "SALES_PROCESS", "Process Sale", "Process customer transactions and sales", "SALES", "WRITE", "[]", true);
            insertPermission(stmt, "SALES_VOID", "Void Transaction", "Void or cancel transactions", "SALES", "DELETE", "[\"SALES_PROCESS\"]", true);
            insertPermission(stmt, "DISCOUNT_APPLY", "Apply Discount", "Apply discounts to items or transactions", "DISCOUNT", "WRITE", "[\"SALES_PROCESS\"]", true);
            insertPermission(stmt, "INVENTORY_VIEW", "View Inventory", "View current inventory levels and product information", "INVENTORY", "READ", "[]", true);
            insertPermission(stmt, "INVENTORY_MANAGE", "Manage Inventory", "Add, edit, and manage inventory items", "INVENTORY", "WRITE", "[\"INVENTORY_VIEW\"]", true);
            insertPermission(stmt, "REPORTS_VIEW", "View Reports", "View sales and business reports", "REPORTS", "READ", "[]", true);
            insertPermission(stmt, "REPORTS_GENERATE", "Generate Reports", "Generate custom reports and export data", "REPORTS", "WRITE", "[\"REPORTS_VIEW\"]", true);
            insertPermission(stmt, "USER_MANAGE", "Manage Users", "Create, edit, and manage user accounts", "USER_MANAGEMENT", "WRITE", "[]", true);
            insertPermission(stmt, "PERMISSION_MANAGE", "Manage Permissions", "Assign and manage user permissions", "USER_MANAGEMENT", "ADMIN", "[\"USER_MANAGE\"]", true);
            insertPermission(stmt, "SYSTEM_SETTINGS", "System Settings", "Access and modify system settings", "SYSTEM", "ADMIN", "[]", true);

            System.out.println("Default permissions inserted successfully");
        }
    }

    private void insertPermission(PreparedStatement stmt, String permissionId, String name, String description,
                                  String category, String level, String dependencies, boolean isActive) throws SQLException {
        stmt.setString(1, permissionId);
        stmt.setString(2, name);
        stmt.setString(3, description);
        stmt.setString(4, category);
        stmt.setString(5, level);
        stmt.setString(6, dependencies);
        stmt.setBoolean(7, isActive);
        stmt.addBatch();
        stmt.executeBatch();
        stmt.clearBatch();
    }

    /**
     * Create default admin user
     */
    private void createDefaultAdminUser(Connection conn) throws SQLException {
        String checkAdminSQL = "SELECT COUNT(*) FROM users WHERE username = 'admin'";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkAdminSQL)) {

            if (rs.next() && rs.getInt(1) == 0) {
                // Create default admin user
                String salt = generateSalt();
                String hashedPassword = hashPassword("admin123", salt);

                String insertAdminSQL = """
                    INSERT INTO users (username, password_hash, salt, is_admin, is_active)
                    VALUES (?, ?, ?, TRUE, TRUE)
                """;

                try (PreparedStatement insertStmt = conn.prepareStatement(insertAdminSQL)) {
                    insertStmt.setString(1, "admin");
                    insertStmt.setString(2, hashedPassword);
                    insertStmt.setString(3, salt);
                    insertStmt.executeUpdate();

                    System.out.println("Default admin user created (username: admin, password: admin123)");
                }
            }
        }
    }

    /**
     * Authenticate user login
     */
    public UserModel authenticateUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return null;
        }

        String sql = """
            SELECT id, username, password_hash, salt, is_admin, is_active, created_at, last_login
            FROM users 
            WHERE username = ? AND is_active = TRUE
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");

                    // Verify password
                    if (verifyPassword(password, storedHash, salt)) {
                        // Update last login
                        updateLastLogin(conn, rs.getInt("id"));

                        // Create user model
                        boolean isAdmin = rs.getBoolean("is_admin");
                        Set<PermissionModel> permissions = getUserPermissions(rs.getInt("id"));

                        UserModel user = new UserModel(username, "", isAdmin, permissions);
                        user.setActive(rs.getBoolean("is_active"));
                        user.setLastLogin(rs.getTimestamp("last_login") != null ?
                                rs.getTimestamp("last_login").toLocalDateTime() : null);

                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get user permissions from database
     */
    private Set<PermissionModel> getUserPermissions(int userId) {
        Set<PermissionModel> permissions = new HashSet<>();

        String sql = """
            SELECT p.permission_id, p.name, p.description, p.category, p.level, 
                   p.dependencies, p.is_active, p.created_at, p.updated_at
            FROM permissions p
            JOIN user_permissions up ON p.permission_id = up.permission_id
            WHERE up.user_id = ? AND p.is_active = TRUE
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Parse dependencies JSON (simplified - you might want to use a proper JSON library)
                    String dependenciesJson = rs.getString("dependencies");
                    Set<String> dependencies = parseDependencies(dependenciesJson);

                    PermissionModel permission = new PermissionModel(
                            rs.getString("permission_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            PermissionCategory.valueOf(rs.getString("category")),
                            PermissionLevel.valueOf(rs.getString("level")),
                            dependencies,
                            rs.getBoolean("is_active"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getTimestamp("updated_at").toLocalDateTime()
                    );

                    permissions.add(permission);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user permissions: " + e.getMessage());
            e.printStackTrace();
        }

        return permissions;
    }

    /**
     * Parse dependencies JSON string (simple implementation)
     */
    private Set<String> parseDependencies(String dependenciesJson) {
        Set<String> dependencies = new HashSet<>();
        if (dependenciesJson != null && !dependenciesJson.equals("[]")) {
            // Simple JSON parsing - replace with proper JSON library in production
            dependenciesJson = dependenciesJson.replace("[", "").replace("]", "").replace("\"", "");
            if (!dependenciesJson.trim().isEmpty()) {
                String[] deps = dependenciesJson.split(",");
                for (String dep : deps) {
                    dependencies.add(dep.trim());
                }
            }
        }
        return dependencies;
    }

    /**
     * Update user's last login timestamp
     */
    private void updateLastLogin(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Create a new user
     */
    public boolean createUser(String username, String password, boolean isAdmin, Set<String> permissionIds) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert user
                String salt = generateSalt();
                String hashedPassword = hashPassword(password, salt);

                String insertUserSQL = """
                    INSERT INTO users (username, password_hash, salt, is_admin, is_active)
                    VALUES (?, ?, ?, ?, TRUE)
                """;

                int userId;
                try (PreparedStatement stmt = conn.prepareStatement(insertUserSQL, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, username.trim());
                    stmt.setString(2, hashedPassword);
                    stmt.setString(3, salt);
                    stmt.setBoolean(4, isAdmin);

                    int affected = stmt.executeUpdate();
                    if (affected == 0) {
                        conn.rollback();
                        return false;
                    }

                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            userId = generatedKeys.getInt(1);
                        } else {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // Insert user permissions
                if (permissionIds != null && !permissionIds.isEmpty()) {
                    String insertPermissionSQL = "INSERT INTO user_permissions (user_id, permission_id) VALUES (?, ?)";

                    try (PreparedStatement stmt = conn.prepareStatement(insertPermissionSQL)) {
                        for (String permissionId : permissionIds) {
                            stmt.setInt(1, userId);
                            stmt.setString(2, permissionId);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Generate salt for password hashing
     */
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hash password with salt
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Verify password against stored hash
     */
    private boolean verifyPassword(String password, String storedHash, String salt) {
        String hashedInput = hashPassword(password, salt);
        return hashedInput.equals(storedHash);
    }

    /**
     * Check if user exists
     */
    public boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if user exists: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Get all users (for admin purposes)
     */
    public List<UserModel> getAllUsers() {
        List<UserModel> users = new ArrayList<>();

        String sql = """
            SELECT id, username, is_admin, is_active, created_at, last_login
            FROM users
            ORDER BY username
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Set<PermissionModel> permissions = getUserPermissions(rs.getInt("id"));

                UserModel user = new UserModel(rs.getString("username"), "", rs.getBoolean("is_admin"), permissions);
                user.setActive(rs.getBoolean("is_active"));
                user.setLastLogin(rs.getTimestamp("last_login") != null ?
                        rs.getTimestamp("last_login").toLocalDateTime() : null);

                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Error getting all users: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    /**
     * Close all connections and cleanup
     */
    public void shutdown() {
        for (Connection conn : connectionPool.values()) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection during shutdown: " + e.getMessage());
            }
        }
        connectionPool.clear();
        System.out.println("Database handler shutdown complete");
    }
}