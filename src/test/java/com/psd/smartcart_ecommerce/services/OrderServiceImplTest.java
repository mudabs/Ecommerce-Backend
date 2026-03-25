package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.repositories.AddressRepository;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.OrderItemRepository;
import com.psd.smartcart_ecommerce.repositories.OrderRepository;
import com.psd.smartcart_ecommerce.repositories.PaymentRepository;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CartService cartService;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuthUtil authUtil;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void placeOrderRejectsAddressOwnedByAnotherUser() {
        Cart cart = new Cart();
        cart.setTotalPrice(150.0);

        when(cartRepository.findCartByEmail("user@example.com")).thenReturn(cart);
        when(authUtil.loggedInUserId()).thenReturn(5L);
        when(addressRepository.findByAddressIdAndUser_UserId(44L, 5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.placeOrder(
                "user@example.com",
                44L,
                "CARD",
                "stripe",
                "pg-1",
                "SUCCESS",
                "ok"
        ));

        verify(addressRepository).findByAddressIdAndUser_UserId(44L, 5L);
        verifyNoInteractions(orderRepository, paymentRepository, orderItemRepository, productRepository, cartService, modelMapper);
    }
}

