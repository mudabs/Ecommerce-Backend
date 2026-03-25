package com.psd.smartcart_ecommerce.security;

import com.psd.smartcart_ecommerce.models.AppRole;
import com.psd.smartcart_ecommerce.models.Role;
import com.psd.smartcart_ecommerce.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebSecurityConfigTest {

    @Test
    void initDataCreatesMissingRolesOnly() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName(AppRole.ROLE_SELLER)).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName(AppRole.ROLE_ADMIN)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebSecurityConfig webSecurityConfig = new WebSecurityConfig();
        CommandLineRunner runner = webSecurityConfig.initData(roleRepository);

        runner.run();

        verify(roleRepository).findByRoleName(AppRole.ROLE_USER);
        verify(roleRepository).findByRoleName(AppRole.ROLE_SELLER);
        verify(roleRepository).findByRoleName(AppRole.ROLE_ADMIN);
        verify(roleRepository, times(3)).save(any(Role.class));
        verifyNoMoreInteractions(roleRepository);
    }

    @Test
    void initDataDoesNotCreateRolesThatAlreadyExist() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.of(new Role(AppRole.ROLE_USER)));
        when(roleRepository.findByRoleName(AppRole.ROLE_SELLER)).thenReturn(Optional.of(new Role(AppRole.ROLE_SELLER)));
        when(roleRepository.findByRoleName(AppRole.ROLE_ADMIN)).thenReturn(Optional.of(new Role(AppRole.ROLE_ADMIN)));

        WebSecurityConfig webSecurityConfig = new WebSecurityConfig();
        CommandLineRunner runner = webSecurityConfig.initData(roleRepository);

        runner.run();

        verify(roleRepository).findByRoleName(AppRole.ROLE_USER);
        verify(roleRepository).findByRoleName(AppRole.ROLE_SELLER);
        verify(roleRepository).findByRoleName(AppRole.ROLE_ADMIN);
        verify(roleRepository, never()).save(any(Role.class));
        verifyNoMoreInteractions(roleRepository);
    }
}

