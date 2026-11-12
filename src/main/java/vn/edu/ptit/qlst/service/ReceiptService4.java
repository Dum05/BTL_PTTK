package vn.edu.ptit.qlst.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.qlst.dao.ShopDao4;
import vn.edu.ptit.qlst.model.Models4.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReceiptService4 {
    private final ShopDao4 dao;

    public ReceiptService4(ShopDao4 dao) {
        this.dao = dao;
    }

    public List<Supplier4> searchSuppliers(String name) {
        return dao.searchSuppliers(Checks4.text(name, 140, "Tên nhà cung cấp"));
    }

    public List<Product4> searchProducts(String name) {
        return dao.searchProducts(Checks4.text(name, 140, "Tên mặt hàng"));
    }

    public Supplier4 addSupplier(String name, String phone, String address) {
        name = Checks4.text(name, 140, "Tên nhà cung cấp");
        try {
            return dao.insertSupplier(name, Checks4.key(name), Checks4.phone(phone), Checks4.text(address, 250, "Địa chỉ"));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException4("Tên nhà cung cấp đã tồn tại.");
        }
    }

    public Product4 addProduct(String sku, String name) {
        sku = Checks4.text(sku, 30, "Mã SKU").toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9][A-Z0-9-]*")) {
            throw new BusinessException4("Mã SKU chỉ gồm chữ A–Z, số và dấu gạch nối.");
        }
        try {
            return dao.insertProduct(sku, Checks4.text(name, 140, "Tên mặt hàng"));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException4("Mã SKU đã tồn tại.");
        }
    }

    @Transactional
    public long receive(long employeeId, long supplierId, UUID requestKey,
                        List<String> products, List<String> quantities, List<String> prices) {
        List<ReceiptLine4> lines = validateLines(products, quantities, prices);
        dao.lockEmployee(employeeId);
        Optional<Long> existing = dao.findReceipt(employeeId, requestKey);
        if (existing.isPresent()) {
            GoodsReceiptView4 saved = dao.readInvoice(existing.get(), employeeId).orElseThrow();
            if (saved.supplier().id() != supplierId ||
                    !saved.lines().stream().map(InvoiceLine4::line).toList().equals(lines)) {
                throw new BusinessException4("Phiếu đã được lưu với nội dung khác. Mở phiếu mới để nhập tiếp.");
            }
            return saved.id();
        }
        if (!dao.supplierAvailable(supplierId)) {
            throw new BusinessException4("Nhà cung cấp không còn khả dụng.");
        }
        long receiptId = dao.insertReceipt(employeeId, supplierId, requestKey);
        for (ReceiptLine4 line : lines) {
            if (!dao.addStock(line)) {
                throw new BusinessException4("Có mặt hàng không còn khả dụng. Phiếu chưa được lưu.");
            }
            dao.insertLine(receiptId, line);
        }
        return receiptId;
    }

    private List<ReceiptLine4> validateLines(List<String> products, List<String> quantities, List<String> prices) {
        if (products.isEmpty() || products.size() > 100 ||
                products.size() != quantities.size() || products.size() != prices.size()) {
            throw new BusinessException4("Phiếu phải có từ 1 đến 100 dòng hợp lệ.");
        }
        List<ReceiptLine4> lines = new ArrayList<>();
        var used = new HashSet<Long>();
        for (int i = 0; i < products.size(); i++) {
            try {
                long productId = Long.parseLong(products.get(i));
                int quantity = Integer.parseInt(quantities.get(i));
                String price = prices.get(i);
                if (productId <= 0 || quantity < 1 || quantity > 1_000_000 ||
                        !price.matches("[1-9][0-9]{0,14}")) {
                    throw new NumberFormatException();
                }
                if (!used.add(productId)) {
                    throw new BusinessException4("Mỗi mặt hàng chỉ xuất hiện một lần trong phiếu.");
                }
                lines.add(new ReceiptLine4(productId, quantity, new BigDecimal(price)));
            } catch (NumberFormatException ex) {
                throw new BusinessException4("Số lượng hoặc đơn giá không hợp lệ tại dòng " + (i + 1) + ".");
            }
        }
        lines.sort(Comparator.comparingLong(ReceiptLine4::productId));
        return lines;
    }

    public Optional<GoodsReceiptView4> invoice(long receiptId, long employeeId) {
        return dao.readInvoice(receiptId, employeeId);
    }
}
