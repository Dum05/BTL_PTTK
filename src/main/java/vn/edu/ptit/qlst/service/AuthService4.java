package vn.edu.ptit.qlst.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.ptit.qlst.dao.ShopDao4;
import vn.edu.ptit.qlst.model.Models4.Role4;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService4 {
    public record Principal4(long id, String username, Role4 role) {}

    private final ShopDao4 dao;

    public AuthService4(ShopDao4 dao) {
        this.dao = dao;
    }

    public Optional<Principal4> authenticate(String username, String password) {
        if (username.length() > 50 || password.length() > 128) {
            return Optional.empty();
        }
        return dao.findAccount(username.strip().toLowerCase(Locale.ROOT)).filter(account -> {
            String[] parts = account.passwordHash().split(":");
            HexFormat hex = HexFormat.of();
            return MessageDigest.isEqual(hex.parseHex(parts[1]), derive(password, hex.parseHex(parts[0])));
        }).map(account -> new Principal4(account.id(), account.username(), account.role()));
    }

    private byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        } finally {
            spec.clearPassword();
        }
    }

    @Bean
    ApplicationRunner demoSeed(JdbcTemplate jdbc) {
        return args -> {
            seedUser(jdbc, "khachhang", Role4.CUSTOMER);
            seedUser(jdbc, "khachhang2", Role4.CUSTOMER);
            seedUser(jdbc, "nhanvienkho", Role4.WAREHOUSE);
            jdbc.update("""
                    INSERT INTO tblSupplier4(name,name_key,phone,address)
                    SELECT 'Điện máy An Phát','điện máy an phát','0243555123','Hà Nội'
                    WHERE NOT EXISTS (SELECT 1 FROM tblSupplier4 WHERE name_key='điện máy an phát')
                    """);
            jdbc.update("""
                    INSERT INTO tblProduct4(sku,name)
                    SELECT 'TV-55','Tivi Samsung 55 inch' WHERE NOT EXISTS (SELECT 1 FROM tblProduct4 WHERE sku='TV-55')
                    """);
            jdbc.update("""
                    INSERT INTO tblProduct4(sku,name)
                    SELECT 'TL-300','Tủ lạnh 300 lít' WHERE NOT EXISTS (SELECT 1 FROM tblProduct4 WHERE sku='TL-300')
                    """);
        };
    }

    private void seedUser(JdbcTemplate jdbc, String username, Role4 role) {
        if (dao.findAccount(username).isEmpty()) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            HexFormat hex = HexFormat.of();
            String hash = hex.formatHex(salt) + ":" + hex.formatHex(derive("Demo1234", salt));
            jdbc.update("INSERT INTO tblAccount4(username,password_hash,role) VALUES (?,?,?)", username, hash, role.name());
        }
    }
}
