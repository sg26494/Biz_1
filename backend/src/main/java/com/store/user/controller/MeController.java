package com.store.user.controller;

import com.store.auth.security.CurrentUser;
import com.store.user.dto.UserDtos.AddressRequest;
import com.store.user.dto.UserDtos.AddressResponse;
import com.store.user.dto.UserDtos.MeResponse;
import com.store.user.dto.UserDtos.UpdateMeRequest;
import com.store.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService users;

    public MeController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public MeResponse me() {
        return users.me(CurrentUser.id());
    }

    @PatchMapping
    public MeResponse update(@Valid @RequestBody UpdateMeRequest req) {
        return users.updateMe(CurrentUser.id(), req);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses() {
        return users.listAddresses(CurrentUser.id());
    }

    @PostMapping("/addresses")
    public AddressResponse add(@Valid @RequestBody AddressRequest req) {
        return users.addAddress(CurrentUser.id(), req);
    }

    @PatchMapping("/addresses/{id}")
    public AddressResponse update(@PathVariable long id, @Valid @RequestBody AddressRequest req) {
        return users.updateAddress(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        users.deleteAddress(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
