package com.psd.smartcart_ecommerce.repositories;


import com.psd.smartcart_ecommerce.models.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByEmail(String email, Pageable pageable);

    Optional<Order> findByOrderIdAndEmail(Long orderId, String email);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    Double getTotalRevenue();
}