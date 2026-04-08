package com.psd.smartcart_ecommerce;

import com.psd.smartcart_ecommerce.models.AppRole;
import com.psd.smartcart_ecommerce.models.Role;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.repositories.RoleRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Seeds the roles table with the required roles on startup if they are not already present.
 * This is necessary because the roles table is empty on a fresh database.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        for (AppRole appRole : AppRole.values()) {
            roleRepository.findByRoleName(appRole).orElseGet(() -> {
                Role role = new Role(appRole);
                return roleRepository.save(role);
            });
        }

        userRepository.findByUserName("admin").orElseGet(() -> {
            User admin = new User("admin", "admin@smartcart.local", passwordEncoder.encode("admin123"));
            User savedAdmin = userRepository.save(admin);

            Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(AppRole.ROLE_ADMIN)));

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            savedAdmin.setRoles(roles);

            return userRepository.save(savedAdmin);
        });

        System.out.println("Roles seeded: ROLE_USER, ROLE_SELLER, ROLE_ADMIN");
        System.out.println("Default admin ensured: username=admin");
    }
}

