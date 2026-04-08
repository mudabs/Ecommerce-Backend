package com.psd.smartcart_ecommerce.util;

import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.security.services.UserDetailsImpl;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    @Autowired
    UserRepository userRepository;

    private User resolveAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UsernameNotFoundException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userRepository.findByUserName(userDetails.getUsername())
                    .or(() -> userRepository.findByEmail(userDetails.getEmail()))
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found for principal: " + userDetails.getUsername()
                    ));
        }

        String identity = authentication.getName();
        if (identity == null || identity.isBlank() || "anonymousUser".equalsIgnoreCase(identity)) {
            throw new UsernameNotFoundException("No authenticated user found");
        }

        return userRepository.findByUserName(identity)
                .or(() -> userRepository.findByEmail(identity))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username or email: " + identity
                ));
    }

    public String loggedInEmail(){
        return resolveAuthenticatedUser().getEmail();
    }

    public Long loggedInUserId(){
        return resolveAuthenticatedUser().getUserId();
    }

    public User loggedInUser(){
        return resolveAuthenticatedUser();
    }


}