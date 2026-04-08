package com.psd.smartcart_ecommerce.repositories;

import com.psd.smartcart_ecommerce.models.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
	List<Address> findAllByUserUserId(Long userId);

	Optional<Address> findByAddressIdAndUserUserId(Long addressId, Long userId);
}