package vn.edu.ptit.qlst.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Models4 {
    private Models4() {}

    public enum Role4 { CUSTOMER, WAREHOUSE, MANAGER, CASHIER }

    public record Account4(long id, String username, String passwordHash, Role4 role) {}

    public record MemberProfile4(long id, long accountId, String fullName, LocalDate birthDate,
                                String phone, String email, String address, LocalDateTime registeredAt) {}

    public record Supplier4(long id, String name, String phone, String address, boolean active) {}
    public record Product4(long id, String sku, String name, long stock, boolean active) {}

    public record ReceiptLine4(long productId, int quantity, BigDecimal unitPrice) {
        public BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record InvoiceLine4(String sku, String productName, ReceiptLine4 line) {}

    public record GoodsReceiptView4(long id, Supplier4 supplier, String employee,
                               LocalDateTime receivedAt, List<InvoiceLine4> lines) {
        public BigDecimal total() {
            return lines.stream().map(row -> row.line().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
