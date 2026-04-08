package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.exceptions.APIException;
import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.CartItem;
import com.psd.smartcart_ecommerce.models.Product;
import com.psd.smartcart_ecommerce.payload.CartDTO;
import com.psd.smartcart_ecommerce.payload.CartItemRequest;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.repositories.CartItemRepository;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService{
    private static final int MAX_SYNC_ITEMS = 50;
    private static final int MAX_REQUESTED_ITEM_QUANTITY = 25;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    ModelMapper modelMapper;

    private Cart getOrCreateCurrentUserCart() {
        Cart existingCart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (existingCart != null) {
            return existingCart;
        }

        Cart cart = new Cart();
        cart.setTotalPrice(0.00);
        cart.setUser(authUtil.loggedInUser());
        return cartRepository.save(cart);
    }

    private void recalculateCartTotal(Cart cart) {
        double total = cart.getCartItems().stream()
                .mapToDouble(item -> item.getProductPrice() * item.getQuantity())
                .sum();
        cart.setTotalPrice(total);
        cartRepository.save(cart);
    }

    private CartDTO buildCartDTO(Cart cart) {
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<ProductDTO> products = cart.getCartItems().stream().map(item -> {
            ProductDTO productDTO = modelMapper.map(item.getProduct(), ProductDTO.class);
            productDTO.setQuantity(item.getQuantity());
            return productDTO;
        }).toList();

        cartDTO.setProducts(products);
        cartDTO.setTotalPrice(cart.getTotalPrice());
        return cartDTO;
    }

    private int normalizeRequestedQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new APIException("Quantity must be greater than zero");
        }

        return Math.min(quantity, MAX_REQUESTED_ITEM_QUANTITY);
    }

    private Product getProductForCart(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
    }

    private CartItem getCartItem(Long cartId, Long productId) {
        return cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);
    }

    private void applyAbsoluteQuantity(Cart cart, Product product, int targetQuantity) {
        CartItem cartItem = getCartItem(cart.getCartId(), product.getProductId());

        if (targetQuantity <= 0) {
            if (cartItem != null) {
                cartItemRepository.delete(cartItem);
            }
            recalculateCartTotal(cart);
            return;
        }

        if (product.getQuantity() == 0) {
            throw new APIException(product.getProductName() + " is not available");
        }

        if (targetQuantity > product.getQuantity()) {
            throw new APIException("Please, make an order of the " + product.getProductName()
                    + " less than or equal to the quantity " + product.getQuantity() + ".");
        }

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setProduct(product);
            cartItem.setCart(cart);
        }

        cartItem.setQuantity(targetQuantity);
        cartItem.setDiscount(product.getDiscount());
        cartItem.setProductPrice(product.getSpecialPrice());
        cartItemRepository.save(cartItem);
        recalculateCartTotal(cart);
    }

    @Override
    public CartDTO addProductToCart(Long productId, Integer quantity) {
        Cart cart = getOrCreateCurrentUserCart();
        Product product = getProductForCart(productId);
        int requestedQuantity = normalizeRequestedQuantity(quantity);

        CartItem existingItem = getCartItem(cart.getCartId(), productId);
        int currentQuantity = existingItem != null ? existingItem.getQuantity() : 0;
        applyAbsoluteQuantity(cart, product, currentQuantity + requestedQuantity);

        return buildCartDTO(cart);
    }

    @Override
    public List<CartDTO> getAllCarts() {
        List<Cart> carts = cartRepository.findAll();

        if (carts.size() == 0) {
            throw new APIException("No cart exists");
        }

        List<CartDTO> cartDTOs = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

            List<ProductDTO> products = cart.getCartItems().stream().map(cartItem -> {
                ProductDTO productDTO = modelMapper.map(cartItem.getProduct(), ProductDTO.class);
                productDTO.setQuantity(cartItem.getQuantity()); // Set the quantity from CartItem
                return productDTO;
            }).collect(Collectors.toList());


            cartDTO.setProducts(products);

            return cartDTO;

        }).collect(Collectors.toList());

        return cartDTOs;
    }

    @Override
    public CartDTO getCurrentUserCart() {
        Cart cart = getOrCreateCurrentUserCart();
        return buildCartDTO(cart);
    }

    @Override
    public CartDTO syncCartItems(List<CartItemRequest> items) {
        Cart cart = getOrCreateCurrentUserCart();

        if (items == null || items.isEmpty()) {
            return buildCartDTO(cart);
        }

        if (items.size() > MAX_SYNC_ITEMS) {
            throw new APIException("Too many cart items in a single sync request");
        }

        Map<Long, Integer> normalizedItems = new LinkedHashMap<>();
        for (CartItemRequest item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }

            normalizedItems.merge(
                    item.getProductId(),
                    Math.min(item.getQuantity(), MAX_REQUESTED_ITEM_QUANTITY),
                    Integer::sum
            );
        }

        for (Map.Entry<Long, Integer> entry : normalizedItems.entrySet()) {
            Product product = getProductForCart(entry.getKey());
            CartItem existingItem = getCartItem(cart.getCartId(), entry.getKey());
            int currentQuantity = existingItem != null ? existingItem.getQuantity() : 0;
            int mergedQuantity = Math.min(currentQuantity + entry.getValue(), MAX_REQUESTED_ITEM_QUANTITY);
            applyAbsoluteQuantity(cart, product, mergedQuantity);
        }

        return buildCartDTO(cart);
    }

    @Override
    public CartDTO getCart(String emailId, Long cartId) {
        Cart cart = cartRepository.findCartByEmailAndCartId(emailId, cartId);
        if (cart == null){
            throw new ResourceNotFoundException("Cart", "cartId", cartId);
        }
        return buildCartDTO(cart);
    }

    @Transactional
    @Override
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity) {

        Cart cart = getOrCreateCurrentUserCart();
        Product product = getProductForCart(productId);
        CartItem cartItem = getCartItem(cart.getCartId(), productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        int newQuantity = cartItem.getQuantity() + quantity;

        if (newQuantity < 0) {
            throw new APIException("The resulting quantity cannot be negative.");
        }

        applyAbsoluteQuantity(cart, product, newQuantity);

        return buildCartDTO(cart);
    }


    @Transactional
    @Override
    public String deleteCurrentUserProductFromCart(Long productId) {
        Cart cart = getOrCreateCurrentUserCart();
        return deleteProductFromCart(cart.getCartId(), productId);
    }


    @Transactional
    @Override
    public String deleteProductFromCart(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }

        cartItemRepository.deleteCartItemByProductIdAndCartId(cartId, productId);
        recalculateCartTotal(cart);

        return "Product " + cartItem.getProduct().getProductName() + " removed from the cart !!!";
    }


    @Override
    public void updateProductInCarts(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        double cartPrice = cart.getTotalPrice()
                - (cartItem.getProductPrice() * cartItem.getQuantity());

        cartItem.setProductPrice(product.getSpecialPrice());

        cart.setTotalPrice(cartPrice
                + (cartItem.getProductPrice() * cartItem.getQuantity()));

        cartItem = cartItemRepository.save(cartItem);
    }

}
