package com.psd.smartcart_ecommerce.controllers;

import com.psd.smartcart_ecommerce.payload.CartDTO;
import com.psd.smartcart_ecommerce.payload.CartItemRequest;
import com.psd.smartcart_ecommerce.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping("/carts/products/{productId}/quantity/{quantity}")
    public ResponseEntity<CartDTO> addProductToCart(@PathVariable Long productId,
                                                    @PathVariable Integer quantity){
        CartDTO cartDTO = cartService.addProductToCart(productId, quantity);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.CREATED);
    }

    @GetMapping("/carts")
    public ResponseEntity<List<CartDTO>> getCarts() {
        List<CartDTO> cartDTOs = cartService.getAllCarts();
        return new ResponseEntity<List<CartDTO>>(cartDTOs, HttpStatus.FOUND);
    }

    @GetMapping("/carts/users/cart")
    public ResponseEntity<CartDTO> getCartById(){
        CartDTO cartDTO = cartService.getCurrentUserCart();
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }

    @PostMapping("/carts/users/cart/sync")
    public ResponseEntity<CartDTO> syncUserCart(@RequestBody List<CartItemRequest> items) {
        CartDTO cartDTO = cartService.syncCartItems(items);
        return new ResponseEntity<>(cartDTO, HttpStatus.OK);
    }

    @PutMapping("/cart/products/{productId}/quantity/{operation}")
    public ResponseEntity<CartDTO> updateCartProduct(@PathVariable Long productId,
                                                     @PathVariable String operation) {

        CartDTO cartDTO = cartService.updateProductQuantityInCart(productId,
                operation.equalsIgnoreCase("delete") ? -1 : 1);

        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }

    @DeleteMapping("/carts/users/cart/product/{productId}")
    public ResponseEntity<String> deleteCurrentUserProductFromCart(@PathVariable Long productId) {
        String status = cartService.deleteCurrentUserProductFromCart(productId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    @DeleteMapping("/carts/{cartId}/product/{productId}")
    public ResponseEntity<String> deleteProductFromCart(@PathVariable Long cartId,
                                                        @PathVariable Long productId) {
        String status = cartService.deleteCurrentUserProductFromCart(productId);

        return new ResponseEntity<String>(status, HttpStatus.OK);
    }
}

