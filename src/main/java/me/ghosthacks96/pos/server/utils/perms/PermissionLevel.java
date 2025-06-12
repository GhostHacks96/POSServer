package me.ghosthacks96.pos.server.utils.perms;

// Enum for permission levels
public enum PermissionLevel {
    READ(1, "View Only"),
    WRITE(2, "Create/Edit"),
    DELETE(3, "Delete"),
    ADMIN(4, "Full Control");

    private final int priority;
    private final String description;

    PermissionLevel(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHigherThan(PermissionLevel other) {
        return this.priority > other.priority;
    }
}
