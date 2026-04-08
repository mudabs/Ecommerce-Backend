package com.psd.smartcart_ecommerce.repositories;


import com.psd.smartcart_ecommerce.models.AppRole;
import com.psd.smartcart_ecommerce.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(AppRole appRole);
}
