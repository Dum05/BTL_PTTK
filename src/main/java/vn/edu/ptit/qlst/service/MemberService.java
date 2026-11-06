package vn.edu.ptit.qlst.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vn.edu.ptit.qlst.dao.ShopDao;
import vn.edu.ptit.qlst.model.Models.MemberProfile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class MemberService {
    private final ShopDao dao;

    public MemberService(ShopDao dao) {
        this.dao = dao;
    }

    public Optional<MemberProfile> profile(long accountId) {
        return dao.findProfile(accountId);
    }

    public void register(long accountId, String name, String birthDate, String phone, String email, String address) {
        name = Checks.text(name, 120, "Họ và tên");
        LocalDate date;
        try {
            date = LocalDate.parse(birthDate);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Ngày sinh không hợp lệ.");
        }
        if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now())) {
            throw new BusinessException("Ngày sinh phải từ năm 1900 đến hôm nay.");
        }
        phone = Checks.phone(phone);
        email = Checks.key(Checks.text(email, 150, "Email"));
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new BusinessException("Email không hợp lệ.");
        }
        address = Checks.text(address, 250, "Địa chỉ");
        try {
            dao.insertProfile(accountId, name, date, phone, email, address);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("Tài khoản, số điện thoại hoặc email đã có hồ sơ thành viên.");
        }
    }
}
