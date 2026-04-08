package com.psd.smartcart_ecommerce.repositories;

import com.psd.smartcart_ecommerce.models.Category;
import com.psd.smartcart_ecommerce.models.Product;
import com.psd.smartcart_ecommerce.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByCategoryOrderByPriceAsc(Category category, Pageable pageDetails);

    Page<Product> findByProductNameContainingIgnoreCase(String keyword, Pageable pageDetails);

    Page<Product> findByCategory_CategoryNameIgnoreCase(String categoryName, Pageable pageDetails);

    Page<Product> findByProductNameContainingIgnoreCaseAndCategory_CategoryNameIgnoreCase(String keyword, String categoryName, Pageable pageDetails);

    Page<Product> findByUser(User user, Pageable pageDetails);
}
