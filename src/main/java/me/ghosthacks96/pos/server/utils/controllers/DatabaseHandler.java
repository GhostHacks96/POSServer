package me.ghosthacks96.pos.server.utils.controllers;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import me.ghosthacks96.pos.server.utils.models.*;
import me.ghosthacks96.pos.server.utils.perms.PermissionCategory;
import me.ghosthacks96.pos.server.utils.perms.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class DatabaseHandler {

    // SQLite database file path
    private static String DB_URL = "jdbc:sqlite:";

    // Connection pool (simple implementation)
    private final Map<Thread, Connection> connectionPool = new ConcurrentHashMap<>();
    private final Object poolLock = new Object();

    // SQL Queries (SQLite syntax)
    private static final String CREATE_USERS_TABLE = """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            is_admin INTEGER DEFAULT 0,
            is_active INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            last_login DATETIME
        )
    """;

    private static final String CREATE_PERMISSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS permissions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            permission_id TEXT UNIQUE NOT NULL,
            name TEXT NOT NULL,
            description TEXT,
            category TEXT NOT NULL,
            level TEXT NOT NULL,
            dependencies TEXT,
            is_active INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """;

    private static final String CREATE_USER_PERMISSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS user_permissions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            permission_id TEXT NOT NULL,
            granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE,
            UNIQUE (user_id, permission_id)
        )
    """;

    private static final String CREATE_TRANSACTIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            transaction_id TEXT UNIQUE NOT NULL,
            customer_id TEXT,
            employee_id TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            subtotal REAL NOT NULL,
            tax_amount REAL DEFAULT 0.00,
            discount_amount REAL DEFAULT 0.00,
            total_amount REAL NOT NULL,
            payment_method TEXT NOT NULL,
            status TEXT NOT NULL
        )
    """;
    private static final String CREATE_PRODUCTS_TABLE = """
        CREATE TABLE IF NOT EXISTS products (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            description TEXT,
            price REAL NOT NULL,
            stock INTEGER DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """;

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHandler.class);

    public DatabaseHandler(String dbFile) {
        DB_URL += dbFile;
        ConsoleHandler.printInfo("Initializing SQLite database at: " + DB_URL);
        if (POSServer.config != null && POSServer.console.DEBUG) logger.debug("Initializing SQLite database handler at {}", DB_URL);
        initializeDatabase();
    }

    /**
     * Initialize database connection and create tables
     */
    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            // Test connection
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                if (POSServer.config != null && POSServer.console.DEBUG) logger.debug("SQLite connection established successfully");
                // Create tables
                createTables(conn);
                // Insert default permissions if they don't exist
                insertDefaultPermissions(conn);
                // Create default admin user if it doesn't exist
                createDefaultAdminUser(conn);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize SQLite database: {}", e.getMessage(), e);
            System.err.println("Failed to initialize SQLite database: " + e.getMessage());
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
                // Only create a new connection if none exists for the thread
                conn = DriverManager.getConnection(DB_URL);
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
                if (POSServer.config != null && POSServer.console.DEBUG) logger.debug("Closed SQLite connection for thread {}", currentThread.getId());
            } catch (SQLException e) {
                System.err.println("Error closing SQLite connection: " + e.getMessage());
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
            stmt.execute(CREATE_PRODUCTS_TABLE);
            if (POSServer.config != null && POSServer.console.DEBUG) logger.debug("SQLite tables created successfully");
        }
    }

    /**
     * Insert default permissions into database
     */
    private void insertDefaultPermissions(Connection conn) throws SQLException {
        String insertPermissionSQL = """
            INSERT OR IGNORE INTO permissions (permission_id, name, description, category, level, dependencies, is_active)
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
            if (POSServer.config != null && POSServer.console.DEBUG) logger.debug("Default permissions inserted successfully");
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
        stmt.setInt(7, isActive ? 1 : 0);
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
                    VALUES (?, ?, ?, 1, 1)
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
            WHERE username = ? 
        """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    int id = rs.getInt("id");
                    boolean isAdmin = rs.getInt("is_admin") == 1;
                    boolean isActive = rs.getInt("is_active") == 1;
                    if (verifyPassword(password, storedHash, salt)) {
                        try (PreparedStatement updateStmt = conn.prepareStatement(
                                "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?")) {
                            updateStmt.setInt(1, id);
                            updateStmt.executeUpdate();
                        }
                        // Create user model
                        LocalDateTime last_login = rs.getTimestamp("last_login") != null ?
                                rs.getTimestamp("last_login").toLocalDateTime() : null;
                        Set<PermissionModel> permissions = getUserPermissions(conn, id);

                        UserModel user = new UserModel(username, "", isAdmin, permissions);
                        user.setActive(isActive);
                        user.setLastLogin(last_login);

                        return user;
                    }
                }else{
                    logger.warn("User not found: {}", username);
                }
            } catch (SQLException e) {
                logger.error("Error authenticating user: {}", e.getMessage(), e);
                e.printStackTrace();
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
    private Set<PermissionModel> getUserPermissions(Connection conn, int userId) {
        Set<PermissionModel> permissions = new HashSet<>();
        String sql = """
        SELECT p.permission_id, p.name, p.description, p.category, p.level, 
               p.dependencies, p.is_active, p.created_at, p.updated_at
        FROM permissions p
        JOIN user_permissions up ON p.permission_id = up.permission_id
        WHERE up.user_id = ? AND p.is_active = 1
    """;
        try ( // New connection per call
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Set<String> deps = parseDependencies(rs.getString("dependencies"));
                    PermissionModel permission = new PermissionModel(
                            rs.getString("permission_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            PermissionCategory.valueOf(rs.getString("category")),
                            PermissionLevel.valueOf(rs.getString("level")),
                            deps,
                            rs.getInt("is_active") == 1,
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
     * Create a new user
     */
    public boolean createUser(String username, String password, boolean isAdmin, Set<String> permissionIds) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String salt = generateSalt();
                String hashedPassword = hashPassword(password, salt);
                String insertUserSQL = """
                    INSERT INTO users (username, password_hash, salt, is_admin, is_active)
                    VALUES (?, ?, ?, ?, 1)
                """;
                int userId;
                try (PreparedStatement stmt = conn.prepareStatement(insertUserSQL, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, username.trim());
                    stmt.setString(2, hashedPassword);
                    stmt.setString(3, salt);
                    stmt.setInt(4, isAdmin ? 1 : 0);
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
                String username = rs.getString("username");
                boolean isAdmin = rs.getBoolean("is_admin");
                boolean isActive = rs.getBoolean("is_active");
                LocalDateTime timestamp = rs.getTimestamp("created_at") != null ?
                        rs.getTimestamp("created_at").toLocalDateTime() : null;

                Set<PermissionModel> permissions = getUserPermissions(conn,rs.getInt("id"));

                UserModel user = new UserModel(username, "", isAdmin, permissions);
                user.setActive(isActive);
                user.setLastLogin(timestamp);

                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Error getting all users: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    /**
     * Get all products
     */
    public List<Map<String, Object>> getAllProducts() {
        List<Map<String, Object>> products = new ArrayList<>();
        String sql = "SELECT id, name, description, price, stock FROM products";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                product.put("id", rs.getInt("id"));
                product.put("name", rs.getString("name"));
                product.put("description", rs.getString("description"));
                product.put("price", rs.getBigDecimal("price"));
                product.put("stock", rs.getInt("stock"));
                products.add(product);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving products: {}", e.getMessage(), e);
        }
        return products;
    }

    /**
     * Get a transaction by its transaction_id
     */
    public Map<String, Object> getTransactionById(String transactionId) {
        Map<String, Object> transaction = new HashMap<>();
        String sql = "SELECT * FROM transactions WHERE transaction_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnName(i);
                        Object value = rs.getObject(i);
                        transaction.put(columnName, value);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving transaction by id {}: {}", transactionId, e.getMessage(), e);
        }
        return transaction;
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

