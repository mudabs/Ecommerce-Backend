package com.psd.smartcart_ecommerce.repositories;


import com.psd.smartcart_ecommerce.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

}