package me.ghosthacks96.pos.server.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionModel(
        String transactionId,
        String customerId,
        String employeeId,
        LocalDateTime timestamp,
        List<TransactionItem> items,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        TransactionStatus status
) {

    // Validation constructor
    public TransactionModel {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Transaction must have at least one item");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative");
        }

        // Make items immutable
        items = List.copyOf(items);
    }

    // Convenience method to calculate item count
    public int getItemCount() {
        return items.size();
    }

    // Check if transaction is completed
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    // Get total quantity of all items
    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(TransactionItem::quantity)
                .sum();
    }

    // Get item by product ID
    public TransactionItem getItemByProductId(String productId) {
        return items.stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    // Check if transaction contains a specific product
    public boolean containsProduct(String productId) {
        return items.stream()
                .anyMatch(item -> item.productId().equals(productId));
    }

    // Get all unique product IDs in the transaction
    public List<String> getProductIds() {
        return items.stream()
                .map(TransactionItem::productId)
                .distinct()
                .toList();
    }
}

// Supporting record for transaction items
record TransactionItem(
        String productId,
        String productName,
        String productCategory,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        BigDecimal discountAmount
) {
    public TransactionItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
        if (lineTotal == null || lineTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Line total cannot be negative");
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
    }

    // Calculate line total before discount
    public BigDecimal getLineSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Check if item has discount applied
    public boolean hasDiscount() {
        return discountAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}

// Enums for payment method and status
enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    MOBILE_PAYMENT,
    CHECK,
    GIFT_CARD
}

enum TransactionStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}