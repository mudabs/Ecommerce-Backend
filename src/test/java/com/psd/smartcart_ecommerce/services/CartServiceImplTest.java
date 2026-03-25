package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.CartDTO;
import com.psd.smartcart_ecommerce.repositories.CartItemRepository;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AuthUtil authUtil;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void createCartForCurrentUserCreatesCartWhenMissing() {
        User user = new User();
        user.setUserId(7L);
        user.setUserName("buyer");

        Cart savedCart = new Cart();
        savedCart.setCartId(12L);
        savedCart.setUser(user);
        savedCart.setTotalPrice(0.0);

        CartDTO cartDTO = new CartDTO();

        when(authUtil.loggedInEmail()).thenReturn("buyer@example.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(cartRepository.findCartByEmail("buyer@example.com")).thenReturn(null);
        when(cartRepository.save(any(Cart.class))).thenReturn(savedCart);
        when(modelMapper.map(savedCart, CartDTO.class)).thenReturn(cartDTO);

        CartDTO result = cartService.createCartForCurrentUser();

        assertSame(cartDTO, result);
        verify(cartRepository).save(any(Cart.class));
        verify(modelMapper).map(savedCart, CartDTO.class);
    }
}

