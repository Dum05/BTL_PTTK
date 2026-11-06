package vn.edu.ptit.qlst.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.qlst.dao.ShopDao;
import vn.edu.ptit.qlst.model.Models.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReceiptService {
    private final ShopDao dao;

    public ReceiptService(ShopDao dao) {
        this.dao = dao;
    }

    public List<Supplier> searchSuppliers(String name) {
        return dao.searchSuppliers(Checks.text(name, 140, "Tên nhà cung cấp"));
    }

    public List<Product> searchProducts(String name) {
        return dao.searchProducts(Checks.text(name, 140, "Tên mặt hàng"));
    }

    public Supplier addSupplier(String name, String phone, String address) {
        name = Checks.text(name, 140, "Tên nhà cung cấp");
        try {
            return dao.insertSupplier(name, Checks.key(name), Checks.phone(phone), Checks.text(address, 250, "Địa chỉ"));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("Tên nhà cung cấp đã tồn tại.");
        }
    }

    public Product addProduct(String sku, String name) {
        sku = Checks.text(sku, 30, "Mã SKU").toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9][A-Z0-9-]*")) {
            throw new BusinessException("Mã SKU chỉ gồm chữ A–Z, số và dấu gạch nối.");
        }
        try {
            return dao.insertProduct(sku, Checks.text(name, 140, "Tên mặt hàng"));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("Mã SKU đã tồn tại.");
        }
    }

    @Transactional
    public long receive(long employeeId, long supplierId, UUID requestKey,
                        List<String> products, List<String> quantities, List<String> prices) {
        List<ReceiptLine> lines = validateLines(products, quantities, prices);
        dao.lockEmployee(employeeId);
        Optional<Long> existing = dao.findReceipt(employeeId, requestKey);
        if (existing.isPresent()) {
            GoodsReceipt saved = dao.readInvoice(existing.get(), employeeId).orElseThrow();
            if (saved.supplier().id() != supplierId ||
                    !saved.lines().stream().map(InvoiceLine::line).toList().equals(lines)) {
                throw new BusinessException("Phiếu đã được lưu với nội dung khác. Mở phiếu mới để nhập tiếp.");
            }
            return saved.id();
        }
        if (!dao.supplierAvailable(supplierId)) {
            throw new BusinessException("Nhà cung cấp không còn khả dụng.");
        }
        long receiptId = dao.insertReceipt(employeeId, supplierId, requestKey);
        for (ReceiptLine line : lines) {
            if (!dao.addStock(line)) {
                throw new BusinessException("Có mặt hàng không còn khả dụng. Phiếu chưa được lưu.");
            }
            dao.insertLine(receiptId, line);
        }
        return receiptId;
    }

    private List<ReceiptLine> validateLines(List<String> products, List<String> quantities, List<String> prices) {
        if (products.isEmpty() || products.size() > 100 ||
                products.size() != quantities.size() || products.size() != prices.size()) {
            throw new BusinessException("Phiếu phải có từ 1 đến 100 dòng hợp lệ.");
        }
        List<ReceiptLine> lines = new ArrayList<>();
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
                    throw new BusinessException("Mỗi mặt hàng chỉ xuất hiện một lần trong phiếu.");
                }
                lines.add(new ReceiptLine(productId, quantity, new BigDecimal(price)));
            } catch (NumberFormatException ex) {
                throw new BusinessException("Số lượng hoặc đơn giá không hợp lệ tại dòng " + (i + 1) + ".");
            }
        }
        lines.sort(Comparator.comparingLong(ReceiptLine::productId));
        return lines;
    }

    public Optional<GoodsReceipt> invoice(long receiptId, long employeeId) {
        return dao.readInvoice(receiptId, employeeId);
    }
}
