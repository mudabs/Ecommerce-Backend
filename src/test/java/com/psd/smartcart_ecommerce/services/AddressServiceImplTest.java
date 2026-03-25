package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Address;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.AddressDTO;
import com.psd.smartcart_ecommerce.repositories.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepo;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    @Test
    void getAddressesReturnsOnlyAddressesOwnedByLoggedInUser() {
        User user = new User();
        user.setUserId(1L);

        Address address = new Address();
        address.setAddressId(10L);

        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setAddressId(10L);

        when(addressRepo.findAllByUser_UserId(1L)).thenReturn(List.of(address));
        when(modelMapper.map(address, AddressDTO.class)).thenReturn(addressDTO);

        List<AddressDTO> addresses = addressService.getAddresses(user);

        assertEquals(1, addresses.size());
        assertEquals(10L, addresses.getFirst().getAddressId());
        verify(addressRepo).findAllByUser_UserId(1L);
        verify(addressRepo, never()).findAll();
    }

    @Test
    void getAddressByIdThrowsWhenAddressDoesNotBelongToLoggedInUser() {
        User user = new User();
        user.setUserId(7L);

        when(addressRepo.findByAddressIdAndUser_UserId(99L, 7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> addressService.getAddressById(99L, user));

        verify(addressRepo).findByAddressIdAndUser_UserId(99L, 7L);
    }
}

