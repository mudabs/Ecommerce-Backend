package com.psd.smartcart_ecommerce.payload;


import java.util.HashSet;
import java.util.Set;

import com.psd.smartcart_ecommerce.models.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long userId;
    private String username;
    private String email;
    private String password;
    private Set<Role> roles = new HashSet<>();
    private AddressDTO address;
    private CartDTO cart;
}