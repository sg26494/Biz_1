package com.store.user.dto;

import com.store.user.domain.User;
import com.store.user.domain.UserAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {}

    public record MeResponse(long id, String email, String name, String phone, String role, Instant createdAt) {
        public static MeResponse of(User u) {
            return new MeResponse(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole().name(),
                    u.getCreatedAt());
        }
    }

    public record UpdateMeRequest(
            @Size(min = 2, max = 80) String name,
            @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String phone) {}

    public record AddressRequest(
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String phone,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @Size(max = 120) String landmark,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$", message = "Enter a valid 6-digit pincode") String pincode,
            Boolean isDefault) {}

    public record AddressResponse(long id, String name, String phone, String line1, String line2, String landmark,
                                  String city, String state, String pincode, boolean isDefault) {
        public static AddressResponse of(UserAddress a) {
            return new AddressResponse(a.getId(), a.getName(), a.getPhone(), a.getLine1(), a.getLine2(),
                    a.getLandmark(), a.getCity(), a.getState(), a.getPincode(), a.isDefaultAddress());
        }
    }
}
