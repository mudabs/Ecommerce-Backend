package com.psd.smartcart_ecommerce.repositories;


import com.psd.smartcart_ecommerce.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>{

}