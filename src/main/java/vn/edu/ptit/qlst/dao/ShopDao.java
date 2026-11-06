package vn.edu.ptit.qlst.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.qlst.model.Models.*;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ShopDao {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Supplier> SUPPLIER = (rs, row) ->
            new Supplier(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                    rs.getString("address"), rs.getBoolean("active"));
    private static final RowMapper<Product> PRODUCT = (rs, row) ->
            new Product(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getLong("stock"), rs.getBoolean("active"));

    public ShopDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> findAccount(String username) {
        return jdbc.query("SELECT * FROM account WHERE username = ?", (rs, i) ->
                new Account(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), Role.valueOf(rs.getString("role"))), username)
                .stream().findFirst();
    }

    public Optional<MemberProfile> findProfile(long accountId) {
        return jdbc.query("SELECT * FROM member_profile WHERE account_id = ?", (rs, i) ->
                new MemberProfile(rs.getLong("id"), accountId, rs.getString("full_name"),
                        rs.getDate("birth_date").toLocalDate(), rs.getString("phone"), rs.getString("email"),
                        rs.getString("address"), rs.getTimestamp("registered_at").toLocalDateTime()), accountId)
                .stream().findFirst();
    }

    public void insertProfile(long accountId, String name, LocalDate date, String phone,
                              String email, String address) {
        jdbc.update("""
                INSERT INTO member_profile(account_id, full_name, birth_date, phone, email, address)
                VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, name, date, phone, email, address);
    }

    private String searchPattern(String keyword) {
        return "%" + keyword.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    public List<Supplier> searchSuppliers(String name) {
        return jdbc.query("""
                SELECT * FROM supplier WHERE active AND name_key LIKE ? ESCAPE '!'
                ORDER BY name, id FETCH FIRST 30 ROWS ONLY
                """, SUPPLIER, searchPattern(name));
    }

    public List<Product> searchProducts(String name) {
        return jdbc.query("""
                SELECT * FROM product WHERE active AND LOWER(name) LIKE ? ESCAPE '!'
                ORDER BY name, id FETCH FIRST 30 ROWS ONLY
                """, PRODUCT, searchPattern(name));
    }

    public Supplier insertSupplier(String name, String key, String phone, String address) {
        long id = insert("INSERT INTO supplier(name, name_key, phone, address) VALUES (?, ?, ?, ?)",
                name, key, phone, address);
        return new Supplier(id, name, phone, address, true);
    }

    public Product insertProduct(String sku, String name) {
        long id = insert("INSERT INTO product(sku, name) VALUES (?, ?)", sku, name);
        return new Product(id, sku, name, 0, true);
    }

    public void lockEmployee(long employeeId) {
        jdbc.queryForObject("SELECT id FROM account WHERE id = ? FOR UPDATE", Long.class, employeeId);
    }

    public Optional<Long> findReceipt(long employeeId, UUID requestKey) {
        return jdbc.query("SELECT id FROM goods_receipt WHERE employee_id = ? AND request_key = ?",
                (rs, i) -> rs.getLong(1), employeeId, requestKey).stream().findFirst();
    }

    public boolean supplierAvailable(long id) {
        return !jdbc.query("SELECT id FROM supplier WHERE id = ? AND active FOR UPDATE",
                (rs, i) -> rs.getLong(1), id).isEmpty();
    }

    public long insertReceipt(long employeeId, long supplierId, UUID requestKey) {
        return insert("INSERT INTO goods_receipt(employee_id, supplier_id, request_key) VALUES (?, ?, ?)",
                employeeId, supplierId, requestKey);
    }

    public boolean addStock(ReceiptLine line) {
        return jdbc.update("UPDATE product SET stock = stock + ? WHERE id = ? AND active",
                line.quantity(), line.productId()) == 1;
    }

    public void insertLine(long receiptId, ReceiptLine line) {
        jdbc.update("""
                INSERT INTO goods_receipt_line(receipt_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """, receiptId, line.productId(), line.quantity(), line.unitPrice());
    }

    public Optional<GoodsReceipt> readInvoice(long receiptId, long employeeId) {
        return jdbc.query("""
                SELECT g.id AS receipt_id, g.received_at, s.*, a.username
                FROM goods_receipt g JOIN supplier s ON s.id = g.supplier_id
                JOIN account a ON a.id = g.employee_id WHERE g.id = ? AND g.employee_id = ?
                """, (rs, i) -> new GoodsReceipt(rs.getLong("receipt_id"), SUPPLIER.mapRow(rs, i),
                rs.getString("username"), rs.getTimestamp("received_at").toLocalDateTime(),
                List.of()), receiptId, employeeId).stream().findFirst().map(receipt ->
                new GoodsReceipt(receipt.id(), receipt.supplier(), receipt.employee(), receipt.receivedAt(),
                        jdbc.query("""
                                SELECT p.id, p.sku, p.name, l.quantity, l.unit_price
                                FROM goods_receipt_line l JOIN product p ON p.id = l.product_id
                                WHERE l.receipt_id = ? ORDER BY p.id
                                """, (rs, i) -> new InvoiceLine(rs.getString("sku"), rs.getString("name"),
                                new ReceiptLine(rs.getLong("id"), rs.getInt("quantity"),
                                        rs.getBigDecimal("unit_price"))), receiptId)));
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, key);
        return ((Number) key.getKeys().get("ID")).longValue();
    }
}
