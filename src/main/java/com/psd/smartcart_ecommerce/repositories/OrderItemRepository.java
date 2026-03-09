package com.psd.smartcart_ecommerce.repositories;


import com.psd.smartcart_ecommerce.models.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

}