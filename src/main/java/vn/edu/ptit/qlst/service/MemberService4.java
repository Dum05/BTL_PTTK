package vn.edu.ptit.qlst.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vn.edu.ptit.qlst.dao.ShopDao4;
import vn.edu.ptit.qlst.model.Models4.MemberProfile4;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class MemberService4 {
    private final ShopDao4 dao;

    public MemberService4(ShopDao4 dao) {
        this.dao = dao;
    }

    public Optional<MemberProfile4> profile(long accountId) {
        return dao.findProfile(accountId);
    }

    public void register(long accountId, String name, String birthDate, String phone, String email, String address) {
        name = Checks4.text(name, 120, "Họ và tên");
        LocalDate date;
        try {
            date = LocalDate.parse(birthDate);
        } catch (DateTimeParseException ex) {
            throw new BusinessException4("Ngày sinh không hợp lệ.");
        }
        if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now())) {
            throw new BusinessException4("Ngày sinh phải từ năm 1900 đến hôm nay.");
        }
        phone = Checks4.phone(phone);
        email = Checks4.key(Checks4.text(email, 150, "Email"));
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new BusinessException4("Email không hợp lệ.");
        }
        address = Checks4.text(address, 250, "Địa chỉ");
        try {
            dao.insertProfile(accountId, name, date, phone, email, address);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException4("Tài khoản, số điện thoại hoặc email đã có hồ sơ thành viên.");
        }
    }
}
