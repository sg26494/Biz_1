package com.store.user.service;

import com.store.common.error.ApiException;
import com.store.common.util.IndianStates;
import com.store.user.domain.User;
import com.store.user.domain.UserAddress;
import com.store.user.domain.UserAddressRepository;
import com.store.user.domain.UserRepository;
import com.store.user.dto.UserDtos.AddressRequest;
import com.store.user.dto.UserDtos.AddressResponse;
import com.store.user.dto.UserDtos.MeResponse;
import com.store.user.dto.UserDtos.UpdateMeRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_ADDRESSES = 10;

    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final Clock clock;

    public UserService(UserRepository users, UserAddressRepository addresses, Clock clock) {
        this.users = users;
        this.addresses = addresses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public MeResponse me(long userId) {
        return MeResponse.of(get(userId));
    }

    @Transactional
    public MeResponse updateMe(long userId, UpdateMeRequest req) {
        User u = get(userId);
        if (req.name() != null) {
            u.setName(req.name().trim());
        }
        if (req.phone() != null && !req.phone().equals(u.getPhone())) {
            users.findByPhone(req.phone()).ifPresent(other -> {
                throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "This mobile number is already registered",
                        Map.of("field", "phone"));
            });
            u.setPhone(req.phone());
        }
        return MeResponse.of(u);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(long userId) {
        return addresses.findActiveByUser(userId).stream().map(AddressResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UserAddress getAddress(long userId, long addressId) {
        return addresses.findActive(addressId, userId).orElseThrow(() -> ApiException.notFound("Address"));
    }

    @Transactional
    public AddressResponse addAddress(long userId, AddressRequest req) {
        List<UserAddress> existing = addresses.findActiveByUser(userId);
        if (existing.size() >= MAX_ADDRESSES) {
            throw ApiException.badRequest("ADDRESS_LIMIT", "You can save up to " + MAX_ADDRESSES + " addresses");
        }
        UserAddress a = new UserAddress();
        a.setUserId(userId);
        apply(a, req);
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(req.isDefault());
        if (makeDefault) {
            addresses.clearDefault(userId);
        }
        a.setDefaultAddress(makeDefault);
        return AddressResponse.of(addresses.save(a));
    }

    @Transactional
    public AddressResponse updateAddress(long userId, long addressId, AddressRequest req) {
        UserAddress a = getAddress(userId, addressId);
        apply(a, req);
        if (Boolean.TRUE.equals(req.isDefault()) && !a.isDefaultAddress()) {
            addresses.clearDefault(userId);
            addresses.flush();
            a.setDefaultAddress(true);
        }
        return AddressResponse.of(a);
    }

    /** Soft delete: past orders keep their own address snapshot anyway. */
    @Transactional
    public void deleteAddress(long userId, long addressId) {
        UserAddress a = getAddress(userId, addressId);
        a.setDeletedAt(clock.instant());
        a.setDefaultAddress(false);
    }

    private void apply(UserAddress a, AddressRequest req) {
        String state = IndianStates.canonical(req.state())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Choose a valid state",
                        Map.of("fields", Map.of("state", "Choose a valid state"))));
        a.setName(req.name().trim());
        a.setPhone(req.phone());
        a.setLine1(req.line1().trim());
        a.setLine2(blankToNull(req.line2()));
        a.setLandmark(blankToNull(req.landmark()));
        a.setCity(req.city().trim());
        a.setState(state);
        a.setPincode(req.pincode());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
