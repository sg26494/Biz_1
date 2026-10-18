package com.store.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.store.user.dto.UserDtos.MeResponse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record GoogleLoginRequest(@NotBlank String idToken) {}

    public record OtpRequest(@NotBlank @Email @Size(max = 320) String email) {}

    public record OtpVerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {}

    public record SignupCompleteRequest(
            @NotBlank String signupToken,
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String phone,
            @AssertTrue(message = "Please accept the terms") boolean acceptTerms) {}

    public enum LoginStatus { LOGGED_IN, SIGNUP_REQUIRED }

    /**
     * LOGGED_IN: accessToken + user (refresh token is set as an HttpOnly cookie).
     * SIGNUP_REQUIRED: signupToken + prefilled email/name; nothing has been created yet.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
            LoginStatus status,
            String accessToken,
            Long expiresIn,
            MeResponse user,
            String signupToken,
            String email,
            String name) {

        public static LoginResponse loggedIn(String accessToken, long expiresIn, MeResponse user) {
            return new LoginResponse(LoginStatus.LOGGED_IN, accessToken, expiresIn, user, null, null, null);
        }

        public static LoginResponse signupRequired(String signupToken, String email, String name) {
            return new LoginResponse(LoginStatus.SIGNUP_REQUIRED, null, null, null, signupToken, email, name);
        }
    }

    /** Internal result: the response body plus the raw refresh token for the cookie. */
    public record AuthResult(LoginResponse body, String refreshToken) {}
}
