package com.psd.smartcart_ecommerce.services;


import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.AddressDTO;

import java.util.List;

public interface AddressService {
    AddressDTO createAddress(AddressDTO addressDTO, User user);

    List<AddressDTO> getAddresses(User user);

    AddressDTO getAddressById(Long addressId, User user);

    List<AddressDTO> getUserAddresses(User user);

    AddressDTO updateAddress(Long addressId, AddressDTO addressDTO, User user);

    String deleteAddress(Long addressId, User user);
}
