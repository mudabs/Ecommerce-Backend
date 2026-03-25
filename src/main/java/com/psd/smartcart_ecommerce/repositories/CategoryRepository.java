package com.psd.smartcart_ecommerce.repositories;

import com.psd.smartcart_ecommerce.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CategoryRepository extends JpaRepository<Category , Long> {
    Category findByCategoryName(String categoryName);
}
