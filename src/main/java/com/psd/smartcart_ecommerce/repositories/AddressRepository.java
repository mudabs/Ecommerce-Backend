package com.psd.smartcart_ecommerce.repositories;

import com.psd.smartcart_ecommerce.models.Address;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
	@EntityGraph(attributePaths = "user")
	List<Address> findAllByUser_UserId(Long userId);

	@EntityGraph(attributePaths = "user")
	Optional<Address> findByAddressIdAndUser_UserId(Long addressId, Long userId);
}