package com.psd.smartcart_ecommerce.services;


import com.psd.smartcart_ecommerce.payload.OrderDTO;
import com.psd.smartcart_ecommerce.payload.OrderResponse;
import jakarta.transaction.Transactional;

public interface OrderService {
    @Transactional
    OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage);

    OrderResponse getUserOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO getUserOrderById(Long orderId);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder(Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
