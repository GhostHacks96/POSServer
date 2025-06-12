package me.ghosthacks96.pos.server.utils.models;

import me.ghosthacks96.pos.server.utils.perms.PermissionCategory;
import me.ghosthacks96.pos.server.utils.perms.PermissionLevel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PermissionModel(
        String permissionId,
        String name,
        String description,
        PermissionCategory category,
        PermissionLevel level,
        Set<String> dependencies,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // Validation constructor
    public PermissionModel {
        if (permissionId == null || permissionId.isBlank()) {
            throw new IllegalArgumentException("Permission ID cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Permission name cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Permission category cannot be null");
        }
        if (level == null) {
            throw new IllegalArgumentException("Permission level cannot be null");
        }

        // Make dependencies immutable
        dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of();
    }

    // Check if this permission has dependencies
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    // Check if this permission depends on another specific permission
    public boolean dependsOn(String permissionId) {
        return dependencies.contains(permissionId);
    }

    // Get the permission as a simple string for backwards compatibility
    public String getPermissionString() {
        return category.name().toLowerCase() + "." + name.toLowerCase().replace(" ", "_");
    }
}

// Utility class for common POS permissions
class PosPermissions {

    // Sales permissions
    public static final PermissionModel PROCESS_SALE = new PermissionModel(
            "SALES_PROCESS",
            "Process Sale",
            "Process customer transactions and sales",
            PermissionCategory.SALES,
            PermissionLevel.WRITE,
            Set.of(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    public static final PermissionModel VOID_TRANSACTION = new PermissionModel(
            "SALES_VOID",
            "Void Transaction",
            "Void or cancel transactions",
            PermissionCategory.SALES,
            PermissionLevel.DELETE,
            Set.of("SALES_PROCESS"),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    public static final PermissionModel APPLY_DISCOUNT = new PermissionModel(
            "DISCOUNT_APPLY",
            "Apply Discount",
            "Apply discounts to items or transactions",
            PermissionCategory.DISCOUNT,
            PermissionLevel.WRITE,
            Set.of("SALES_PROCESS"),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    // Inventory permissions
    public static final PermissionModel VIEW_INVENTORY = new PermissionModel(
            "INVENTORY_VIEW",
            "View Inventory",
            "View current inventory levels and product information",
            PermissionCategory.INVENTORY,
            PermissionLevel.READ,
            Set.of(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    public static final PermissionModel MANAGE_INVENTORY = new PermissionModel(
            "INVENTORY_MANAGE",
            "Manage Inventory",
            "Add, edit, and manage inventory items",
            PermissionCategory.INVENTORY,
            PermissionLevel.WRITE,
            Set.of("INVENTORY_VIEW"),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    // Reports permissions
    public static final PermissionModel VIEW_REPORTS = new PermissionModel(
            "REPORTS_VIEW",
            "View Reports",
            "View sales and business reports",
            PermissionCategory.REPORTS,
            PermissionLevel.READ,
            Set.of(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    public static final PermissionModel GENERATE_REPORTS = new PermissionModel(
            "REPORTS_GENERATE",
            "Generate Reports",
            "Generate custom reports and export data",
            PermissionCategory.REPORTS,
            PermissionLevel.WRITE,
            Set.of("REPORTS_VIEW"),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    // User management permissions
    public static final PermissionModel MANAGE_USERS = new PermissionModel(
            "USER_MANAGE",
            "Manage Users",
            "Create, edit, and manage user accounts",
            PermissionCategory.USER_MANAGEMENT,
            PermissionLevel.WRITE,
            Set.of(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    public static final PermissionModel MANAGE_PERMISSIONS = new PermissionModel(
            "PERMISSION_MANAGE",
            "Manage Permissions",
            "Assign and manage user permissions",
            PermissionCategory.USER_MANAGEMENT,
            PermissionLevel.ADMIN,
            Set.of("USER_MANAGE"),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    // System permissions
    public static final PermissionModel SYSTEM_SETTINGS = new PermissionModel(
            "SYSTEM_SETTINGS",
            "System Settings",
            "Access and modify system settings",
            PermissionCategory.SYSTEM,
            PermissionLevel.ADMIN,
            Set.of(),
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    // Get all predefined permissions
    public static List<PermissionModel> getAllPermissions() {
        return List.of(
                PROCESS_SALE, VOID_TRANSACTION, APPLY_DISCOUNT,
                VIEW_INVENTORY, MANAGE_INVENTORY,
                VIEW_REPORTS, GENERATE_REPORTS,
                MANAGE_USERS, MANAGE_PERMISSIONS,
                SYSTEM_SETTINGS
        );
    }
}