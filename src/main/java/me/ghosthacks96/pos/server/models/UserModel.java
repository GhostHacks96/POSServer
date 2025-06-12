package me.ghosthacks96.pos.server.models;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalDateTime;

public class UserModel {
    private String username;
    private String password;
    private boolean isAdmin;
    private Set<PermissionModel> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private boolean isActive;

    public UserModel(String username, String password, boolean isAdmin, Set<PermissionModel> permissions) {
        this.username = username;
        this.password = password;
        this.isAdmin = isAdmin;
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public Set<PermissionModel> getPermissions() {
        return Set.copyOf(permissions); // Return immutable copy
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public boolean isActive() {
        return isActive;
    }

    // Setters
    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public void setPermissions(Set<PermissionModel> permissions) {
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    // Permission checking methods using PermissionModel
    public boolean hasPermission(PermissionModel permission) {
        if (!isActive) {
            return false;
        }

        if (isAdmin) {
            return true; // Admins have all permissions
        }

        return permissions.contains(permission);
    }

    // Check permission by ID
    public boolean hasPermissionById(String permissionId) {
        if (!isActive) {
            return false;
        }

        if (isAdmin) {
            return true;
        }

        return permissions.stream()
                .anyMatch(perm -> perm.permissionId().equals(permissionId));
    }

    // Backwards compatibility - check by permission string
    public boolean hasPermission(String permissionString) {
        if (!isActive) {
            return false;
        }

        if (isAdmin) {
            return true;
        }

        return permissions.stream()
                .anyMatch(perm -> perm.getPermissionString().equals(permissionString));
    }

    // Check if user has permission with specific level or higher
    public boolean hasPermissionLevel(PermissionCategory category, PermissionLevel requiredLevel) {
        if (!isActive) {
            return false;
        }

        if (isAdmin) {
            return true;
        }

        return permissions.stream()
                .filter(perm -> perm.category() == category)
                .anyMatch(perm -> perm.level().getPriority() >= requiredLevel.getPriority());
    }

    // Get all permissions for a specific category
    public Set<PermissionModel> getPermissionsByCategory(PermissionCategory category) {
        return permissions.stream()
                .filter(perm -> perm.category() == category)
                .collect(java.util.stream.Collectors.toSet());
    }

    // Check if user can perform action (validates dependencies)
    public boolean canPerformAction(PermissionModel permission) {
        if (!isActive) {
            return false;
        }

        if (isAdmin) {
            return true;
        }

        // Check if user has the permission
        if (!hasPermission(permission)) {
            return false;
        }

        // Check if user has all required dependencies
        for (String dependencyId : permission.dependencies()) {
            if (!hasPermissionById(dependencyId)) {
                return false;
            }
        }

        return true;
    }

    // Permission management methods
    public void addPermission(PermissionModel permission) {
        if (permission != null && permission.isActive()) {
            permissions.add(permission);
        }
    }

    public void removePermission(PermissionModel permission) {
        permissions.remove(permission);
    }

    public void removePermissionById(String permissionId) {
        permissions.removeIf(perm -> perm.permissionId().equals(permissionId));
    }

    // Utility methods
    public int getPermissionCount() {
        return permissions.size();
    }

    public boolean hasAnyPermissions() {
        return !permissions.isEmpty() || isAdmin;
    }

    // Get all permission IDs as strings (for serialization/logging)
    public Set<String> getPermissionIds() {
        return permissions.stream()
                .map(PermissionModel::permissionId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // Get all permission strings (backwards compatibility)
    public Set<String> getPermissionStrings() {
        return permissions.stream()
                .map(PermissionModel::getPermissionString)
                .collect(java.util.stream.Collectors.toSet());
    }

    // Check if user has any admin-level permissions
    public boolean hasAdminPermissions() {
        if (isAdmin) {
            return true;
        }

        return permissions.stream()
                .anyMatch(perm -> perm.level() == PermissionLevel.ADMIN);
    }

    @Override
    public String toString() {
        return String.format("UserModel{username='%s', isAdmin=%s, permissions=%d, isActive=%s}",
                username, isAdmin, permissions.size(), isActive);
    }
}