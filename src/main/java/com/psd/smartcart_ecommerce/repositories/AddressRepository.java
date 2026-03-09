package com.psd.smartcart_ecommerce.repositories;

import com.psd.smartcart_ecommerce.models.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}