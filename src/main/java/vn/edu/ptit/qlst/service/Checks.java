package vn.edu.ptit.qlst.service;

import java.util.Locale;

final class Checks {
    private Checks() {}

    static String text(String value, int max, String field) {
        String cleaned = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty() || cleaned.length() > max || cleaned.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(field + " phải có từ 1 đến " + max + " ký tự hợp lệ.");
        }
        return cleaned;
    }

    static String phone(String value) {
        String number = value == null ? "" : value.strip();
        if (!number.matches("0[0-9]{9}")) {
            throw new BusinessException("Số điện thoại phải có 10 chữ số và bắt đầu bằng 0.");
        }
        return number;
    }

    static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
