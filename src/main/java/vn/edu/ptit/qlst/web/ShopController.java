package vn.edu.ptit.qlst.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.qlst.model.Models.Role;
import vn.edu.ptit.qlst.service.AuthService;
import vn.edu.ptit.qlst.service.AuthService.Principal;
import vn.edu.ptit.qlst.service.BusinessException;
import vn.edu.ptit.qlst.service.MemberService;
import vn.edu.ptit.qlst.service.ReceiptService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ShopController {
    private final AuthService auth;
    private final MemberService members;
    private final ReceiptService receipts;

    public ShopController(AuthService auth, MemberService members, ReceiptService receipts) {
        this.auth = auth;
        this.members = members;
        this.receipts = receipts;
    }

    @GetMapping("/")
    public String home(Model model, @SessionAttribute(name = "user", required = false) Principal user) {
        model.addAttribute("user", user);
        return "home";
    }

    @PostMapping("/login")
    public String login(String username, String password, HttpServletRequest request, Model model) {
        var user = auth.authenticate(username == null ? "" : username, password == null ? "" : password);
        if (user.isEmpty()) {
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng.");
            return "home";
        }
        request.changeSessionId();
        request.getSession().setAttribute("user", user.get());
        request.getSession().setAttribute("csrf", UUID.randomUUID().toString());
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/member")
    public String member(@SessionAttribute("user") Principal user, Model model) {
        model.addAttribute("profile", members.profile(user.id()).orElse(null));
        return "member";
    }

    @PostMapping("/member")
    public String register(@SessionAttribute("user") Principal user, @RequestParam Map<String, String> form, Model model) {
        try {
            members.register(user.id(), form.get("fullName"), form.getOrDefault("birthDate", ""),
                    form.get("phone"), form.get("email"), form.get("address"));
            return "redirect:/member";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("error", "Chưa lưu được hồ sơ. Vui lòng thử lại.");
        }
        model.addAttribute("form", form);
        return "member";
    }

    @GetMapping("/warehouse")
    public String warehouse(Model model) {
        model.addAttribute("requestKey", UUID.randomUUID());
        return "warehouse";
    }

    @GetMapping("/warehouse/suppliers")
    @ResponseBody
    public Object suppliers(@RequestParam String name) {
        return receipts.searchSuppliers(name);
    }

    @PostMapping("/warehouse/suppliers")
    @ResponseBody
    public Object addSupplier(@RequestParam String name, @RequestParam String phone, @RequestParam String address) {
        return receipts.addSupplier(name, phone, address);
    }

    @GetMapping("/warehouse/products")
    @ResponseBody
    public Object products(@RequestParam String name) {
        return receipts.searchProducts(name);
    }

    @PostMapping("/warehouse/products")
    @ResponseBody
    public Object addProduct(@RequestParam String sku, @RequestParam String name) {
        return receipts.addProduct(sku, name);
    }

    @PostMapping("/warehouse/receipts")
    @ResponseBody
    public Map<String, String> receive(@SessionAttribute("user") Principal user, @RequestParam long supplierId,
                                      @RequestParam UUID requestKey, @RequestParam List<String> productId,
                                      @RequestParam List<String> quantity, @RequestParam List<String> unitPrice) {
        long id = receipts.receive(user.id(), supplierId, requestKey, productId, quantity, unitPrice);
        return Map.of("invoiceUrl", "/warehouse/invoices/" + id);
    }

    @GetMapping("/warehouse/invoices/{id}")
    public String invoice(@SessionAttribute("user") Principal user, @PathVariable long id, Model model) {
        var invoice = receipts.invoice(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("invoice", invoice);
        model.addAttribute("date", invoice.receivedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        return "invoice";
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalid(BusinessException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> databaseError() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Chưa hoàn thành thao tác. Vui lòng thử lại."));
    }
}
