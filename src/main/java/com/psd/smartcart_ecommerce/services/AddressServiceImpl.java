package com.psd.smartcart_ecommerce.services;


import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Address;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.AddressDTO;
import com.psd.smartcart_ecommerce.repositories.AddressRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class AddressServiceImpl implements AddressService{
    @Autowired
    private AddressRepository addressRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public AddressDTO createAddress(AddressDTO addressDTO, User user) {
        Address address = modelMapper.map(addressDTO, Address.class);
        User managedUser = userRepo.findById(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", user.getUserId()));
        address.setUser(managedUser);
        Address savedAddress = addressRepo.save(address);
        return modelMapper.map(savedAddress, AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getAddresses(User user) {
        List<Address> addresses = addressRepo.findAllByUserUserId(user.getUserId());
        return addresses.stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();
    }

    @Override
    public AddressDTO getAddressesById(Long addressId, User user) {
        Address address = getManagedUserAddress(addressId, user);
        return modelMapper.map(address, AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getUserAddresses(User user) {
        List<Address> addresses = addressRepo.findAllByUserUserId(user.getUserId());
        return addresses.stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();
    }

    @Override
    public AddressDTO updateAddress(Long addressId, AddressDTO addressDTO, User user) {
        Address addressFromDatabase = getManagedUserAddress(addressId, user);

        addressFromDatabase.setCity(addressDTO.getCity());
        addressFromDatabase.setPincode(addressDTO.getPincode());
        addressFromDatabase.setState(addressDTO.getState());
        addressFromDatabase.setCountry(addressDTO.getCountry());
        addressFromDatabase.setStreet(addressDTO.getStreet());
        addressFromDatabase.setBuildingName(addressDTO.getBuildingName());

        Address updatedAddress = addressRepo.save(addressFromDatabase);

        return modelMapper.map(updatedAddress, AddressDTO.class);
    }

    @Override
    public String deleteAddress(Long addressId, User user) {
        Address addressFromDatabase = getManagedUserAddress(addressId, user);

        addressRepo.delete(addressFromDatabase);

        return "Address deleted successfully with addressId: " + addressId;
    }

    private Address getManagedUserAddress(Long addressId, User user) {
        return addressRepo.findByAddressIdAndUserUserId(addressId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
    }

}

