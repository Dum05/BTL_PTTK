package vn.edu.ptit.qlst.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Models {
    private Models() {}

    public enum Role { CUSTOMER, WAREHOUSE, MANAGER, CASHIER }

    public record Account(long id, String username, String passwordHash, Role role) {}

    public record MemberProfile(long id, long accountId, String fullName, LocalDate birthDate,
                                String phone, String email, String address, LocalDateTime registeredAt) {}

    public record Supplier(long id, String name, String phone, String address, boolean active) {}
    public record Product(long id, String sku, String name, long stock, boolean active) {}

    public record ReceiptLine(long productId, int quantity, BigDecimal unitPrice) {
        public BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record InvoiceLine(String sku, String productName, ReceiptLine line) {}

    public record GoodsReceipt(long id, Supplier supplier, String employee,
                               LocalDateTime receivedAt, List<InvoiceLine> lines) {
        public BigDecimal total() {
            return lines.stream().map(row -> row.line().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
