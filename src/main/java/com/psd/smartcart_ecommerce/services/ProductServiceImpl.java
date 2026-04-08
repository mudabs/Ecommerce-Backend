package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.exceptions.APIException;
import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.Category;
import com.psd.smartcart_ecommerce.models.Product;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.CartDTO;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.payload.ProductResponse;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.CategoryRepository;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
@Service
@Transactional
public class ProductServiceImpl implements ProductService {
    @Autowired
    private CartRepository cartRepository;


    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    AuthUtil authUtil;

    @Value("${project.image}")
    private String path;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    private double calculateSpecialPrice(double price, double discount) {
        return price - ((discount * 0.01) * price);
    }

    private Pageable buildProductPageDetails(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        return PageRequest.of(pageNumber, pageSize, sortByAndOrder);
    }

    private ProductResponse mapProductResponse(Page<Product> pageProducts) {
        List<ProductDTO> productDTOS = pageProducts.getContent().stream()
                .map(product -> {
                    ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
                    productDTO.setImage(constructImageUrl(product.getImage()));
                    return productDTO;
                })
                .toList();

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        boolean isProductNotPresent = true;

        List<Product> products = category.getProducts();
        for (Product value : products) {
            if (value.getProductName().equals(productDTO.getProductName())) {
                isProductNotPresent = false;
                break;
            }
        }

        if (isProductNotPresent) {
            Product product = modelMapper.map(productDTO, Product.class);
            product.setImage("default.png");
            product.setCategory(category);
            product.setUser(authUtil.loggedInUser());
            Double requestedSpecialPrice = productDTO.getSpecialPrice();
            double specialPrice = requestedSpecialPrice != null
                ? requestedSpecialPrice
                : calculateSpecialPrice(product.getPrice(), product.getDiscount());
            product.setSpecialPrice(specialPrice);
            Product savedProduct = productRepository.save(product);
            return modelMapper.map(savedProduct, ProductDTO.class);
        } else {
            throw new APIException("Product already exist!!");
        }
    }

    @Override
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);

        Page<Product> pageProducts = productRepository.findAll(pageDetails);

        return mapProductResponse(pageProducts);
    }

    @Override
    public ProductResponse getAllProducts(String keyword, String category, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedCategory = category == null ? "" : category.trim();

        boolean hasKeyword = !normalizedKeyword.isEmpty();
        boolean hasCategory = !normalizedCategory.isEmpty();

        Page<Product> pageProducts;

        if (hasKeyword && hasCategory) {
            pageProducts = productRepository.findByProductNameContainingIgnoreCaseAndCategory_CategoryNameIgnoreCase(
                    normalizedKeyword,
                    normalizedCategory,
                    pageDetails
            );
        } else if (hasKeyword) {
            pageProducts = productRepository.findByProductNameContainingIgnoreCase(normalizedKeyword, pageDetails);
        } else if (hasCategory) {
            pageProducts = productRepository.findByCategory_CategoryNameIgnoreCase(normalizedCategory, pageDetails);
        } else {
            pageProducts = productRepository.findAll(pageDetails);
        }

        return mapProductResponse(pageProducts);
    }

    @Override
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);
        Page<Product> pageProducts = productRepository.findAll(pageDetails);

        return mapProductResponse(pageProducts);
    }

    @Override
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);

        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, pageDetails);

        return mapProductResponse(pageProducts);
    }

    private String constructImageUrl(String imageName) {
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }

    @Override
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category, pageDetails);

        return mapProductResponse(pageProducts);
    }

    @Override
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = buildProductPageDetails(pageNumber, pageSize, sortBy, sortOrder);
        Page<Product> pageProducts = productRepository.findByProductNameContainingIgnoreCase(keyword, pageDetails);

        return mapProductResponse(pageProducts);
    }

    @Override
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {
        Product productFromDb = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        productFromDb.setProductName(productDTO.getProductName());
        productFromDb.setDescription(productDTO.getDescription());
        productFromDb.setQuantity(productDTO.getQuantity());
        productFromDb.setDiscount(productDTO.getDiscount());
        productFromDb.setPrice(productDTO.getPrice());

        Double requestedSpecialPrice = productDTO.getSpecialPrice();
        double specialPrice = requestedSpecialPrice != null
            ? requestedSpecialPrice
            : calculateSpecialPrice(productDTO.getPrice(), productDTO.getDiscount());
        productFromDb.setSpecialPrice(specialPrice);

        Product savedProduct = productRepository.save(productFromDb);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);

        List<CartDTO> cartDTOs = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

            List<ProductDTO> products = cart.getCartItems().stream()
                    .map(p -> modelMapper.map(p.getProduct(), ProductDTO.class)).collect(Collectors.toList());

            cartDTO.setProducts(products);

            return cartDTO;

        }).collect(Collectors.toList());

        cartDTOs.forEach(cart -> cartService.updateProductInCarts(cart.getCartId(), productId));

        return modelMapper.map(savedProduct, ProductDTO.class);
    }

    @Override
    public ProductDTO deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        // DELETE
        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(), productId));

        productRepository.delete(product);
        return modelMapper.map(product, ProductDTO.class);
    }

    @Override
    public ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException {
        Product productFromDb = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        String fileName = fileService.uploadImage(path, image);
        productFromDb.setImage(fileName);

        Product updatedProduct = productRepository.save(productFromDb);
        return modelMapper.map(updatedProduct, ProductDTO.class);
    }


}