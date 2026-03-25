package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.payload.AuthenticationResult;
import com.psd.smartcart_ecommerce.payload.UserResponse;
import com.psd.smartcart_ecommerce.security.request.LoginRequest;
import com.psd.smartcart_ecommerce.security.request.SignupRequest;
import com.psd.smartcart_ecommerce.security.response.MessageResponse;
import com.psd.smartcart_ecommerce.security.response.UserInfoResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

public interface AuthService {

    AuthenticationResult login(LoginRequest loginRequest);

    ResponseEntity<MessageResponse> register(SignupRequest signUpRequest);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    ResponseCookie logoutUser();

    AuthenticationResult refreshToken(Authentication authentication);

    UserResponse getAllSellers(Pageable pageable);
}