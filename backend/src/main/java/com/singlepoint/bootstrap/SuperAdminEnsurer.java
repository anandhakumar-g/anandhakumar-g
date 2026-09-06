package com.singlepoint.bootstrap;

import com.singlepoint.crypto.CryptoService;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Non-local environments (uat / cloud) have no demo seed. When {@code sp.bootstrap.super-admin-phone}
 * is set and no SUPER_ADMIN exists yet, create exactly one so the platform can be signed into.
 * Idempotent: a no-op on every boot after the first.
 */
@Component
@Profile("!local")
@ConditionalOnProperty(name = "sp.bootstrap.super-admin-phone")
@Order(0)
public class SuperAdminEnsurer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminEnsurer.class);

    private final AppUserRepository users;
    private final CryptoService crypto;
    private final String phone;
    private final String name;

    public SuperAdminEnsurer(AppUserRepository users, CryptoService crypto,
                             @org.springframework.beans.factory.annotation.Value("${sp.bootstrap.super-admin-phone:}") String phone,
                             @org.springframework.beans.factory.annotation.Value("${sp.bootstrap.super-admin-name:Platform Owner}") String name) {
        this.users = users;
        this.crypto = crypto;
        this.phone = phone == null ? "" : phone.trim();
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (phone.isEmpty()) {
            return;
        }
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            log.info("SuperAdminEnsurer: a SUPER_ADMIN already exists — nothing to do");
            return;
        }
        AppUser u = new AppUser();
        u.setRole(Role.SUPER_ADMIN);
        u.setName(name);
        u.setPhone(phone);
        u.setPhoneHash(crypto.lookupHash(phone));
        u.setProfileCompleted(true);
        users.save(u);
        log.info("SuperAdminEnsurer: created SUPER_ADMIN for {}", phone);
    }
}
