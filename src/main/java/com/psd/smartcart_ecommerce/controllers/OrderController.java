package com.psd.smartcart_ecommerce.controllers;

import com.psd.smartcart_ecommerce.config.AppConstants;
import com.psd.smartcart_ecommerce.payload.*;
import com.psd.smartcart_ecommerce.services.OrderService;
import com.psd.smartcart_ecommerce.services.StripeService;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private StripeService stripeService;

    @PostMapping("/order/users/payments/{paymentMethod}")
    public ResponseEntity<OrderDTO> orderProducts(@PathVariable String paymentMethod, @RequestBody OrderRequestDTO orderRequestDTO) {
        String emailId = authUtil.loggedInEmail();
        System.out.println("orderRequestDTO DATA: " + orderRequestDTO);
        OrderDTO order = orderService.placeOrder(
                emailId,
                orderRequestDTO.getAddressId(),
                paymentMethod,
                orderRequestDTO.getPgName(),
                orderRequestDTO.getPgPaymentId(),
                orderRequestDTO.getPgStatus(),
                orderRequestDTO.getPgResponseMessage()
        );
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @PostMapping("/order/stripe-client-secret")
    public ResponseEntity<String> createStripeClientSecret(@RequestBody StripePaymentDto stripePaymentDto) throws StripeException {
        System.out.println("StripePaymentDTO Received " + stripePaymentDto);
        PaymentIntent paymentIntent = stripeService.paymentIntent(stripePaymentDto);
        return new ResponseEntity<>(paymentIntent.getClientSecret(), HttpStatus.CREATED);
    }

    @PostMapping("/order/stripe-checkout-session")
    public ResponseEntity<Map<String, String>> createStripeCheckoutSession(
            @RequestBody StripeCheckoutSessionDto dto
    ) throws StripeException {
        Session session = stripeService.createCheckoutSession(dto);
        Map<String, String> body = new HashMap<>();
        body.put("url", session.getUrl()); // frontend will redirect to this URL
        return new ResponseEntity<>(body, HttpStatus.CREATED);
    }

    @GetMapping("/order/users/orders")
    public ResponseEntity<OrderResponse> getUserOrders(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        OrderResponse orderResponse = orderService.getUserOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse, HttpStatus.OK);
    }

    @GetMapping("/order/users/orders/{orderId}")
    public ResponseEntity<OrderDTO> getUserOrderById(@PathVariable Long orderId) {
        OrderDTO order = orderService.getUserOrderById(orderId);
        return new ResponseEntity<OrderDTO>(order, HttpStatus.OK);
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<OrderResponse> getAllOrders(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        OrderResponse orderResponse = orderService.getAllOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse, HttpStatus.OK);
    }

    @GetMapping("/seller/orders")
    public ResponseEntity<OrderResponse> getAllSellerOrders(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        OrderResponse orderResponse = orderService.getAllSellerOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse, HttpStatus.OK);
    }

    @PutMapping("/admin/orders/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDto orderStatusUpdateDto) {
        OrderDTO order = orderService.updateOrder(orderId, orderStatusUpdateDto.getStatus());
        return new ResponseEntity<OrderDTO>(order, HttpStatus.OK);
    }

    @PutMapping("/seller/orders/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatusSeller(@PathVariable Long orderId,
                                                            @RequestBody OrderStatusUpdateDto orderStatusUpdateDto) {
        OrderDTO order = orderService.updateOrder(orderId, orderStatusUpdateDto.getStatus());
        return new ResponseEntity<OrderDTO>(order, HttpStatus.OK);
    }
}