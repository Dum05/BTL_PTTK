package vn.edu.ptit.qlst.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.ptit.qlst.dao.ShopDao;
import vn.edu.ptit.qlst.model.Models.Role;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    public record Principal(long id, String username, Role role) {}

    private final ShopDao dao;

    public AuthService(ShopDao dao) {
        this.dao = dao;
    }

    public Optional<Principal> authenticate(String username, String password) {
        if (username.length() > 50 || password.length() > 128) {
            return Optional.empty();
        }
        return dao.findAccount(username.strip().toLowerCase(Locale.ROOT)).filter(account -> {
            String[] parts = account.passwordHash().split(":");
            HexFormat hex = HexFormat.of();
            return MessageDigest.isEqual(hex.parseHex(parts[1]), derive(password, hex.parseHex(parts[0])));
        }).map(account -> new Principal(account.id(), account.username(), account.role()));
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
            seedUser(jdbc, "khachhang", Role.CUSTOMER);
            seedUser(jdbc, "khachhang2", Role.CUSTOMER);
            seedUser(jdbc, "nhanvienkho", Role.WAREHOUSE);
            jdbc.update("""
                    INSERT INTO supplier(name,name_key,phone,address)
                    SELECT 'Điện máy An Phát','điện máy an phát','0243555123','Hà Nội'
                    WHERE NOT EXISTS (SELECT 1 FROM supplier WHERE name_key='điện máy an phát')
                    """);
            jdbc.update("""
                    INSERT INTO product(sku,name)
                    SELECT 'TV-55','Tivi Samsung 55 inch' WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku='TV-55')
                    """);
            jdbc.update("""
                    INSERT INTO product(sku,name)
                    SELECT 'TL-300','Tủ lạnh 300 lít' WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku='TL-300')
                    """);
        };
    }

    private void seedUser(JdbcTemplate jdbc, String username, Role role) {
        if (dao.findAccount(username).isEmpty()) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            HexFormat hex = HexFormat.of();
            String hash = hex.formatHex(salt) + ":" + hex.formatHex(derive("Demo1234", salt));
            jdbc.update("INSERT INTO account(username,password_hash,role) VALUES (?,?,?)", username, hash, role.name());
        }
    }
}
