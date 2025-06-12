package me.ghosthacks96.pos.server.utils.perms;

// Enum for permission categories
public enum PermissionCategory {
    SALES("Sales Operations"),
    INVENTORY("Inventory Management"),
    REPORTS("Reports and Analytics"),
    USER_MANAGEMENT("User Management"),
    SYSTEM("System Administration"),
    PAYMENT("Payment Processing"),
    CUSTOMER("Customer Management"),
    DISCOUNT("Discount and Promotions");

    private final String displayName;

    PermissionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
