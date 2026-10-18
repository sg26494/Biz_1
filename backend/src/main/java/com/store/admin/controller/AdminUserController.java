package com.store.admin.controller;

import com.store.admin.service.AdminUserService;
import com.store.admin.service.AdminUserService.CustomerDetail;
import com.store.admin.service.AdminUserService.CustomerRow;
import com.store.auth.security.CurrentUser;
import com.store.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    public record BlockRequest(@NotNull Boolean blocked, @NotBlank @Size(max = 500) String reason) {}

    public record AddAdminRequest(@NotBlank @Email String email) {}

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    public PageResponse<CustomerRow> customers(@RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return service.customers(q, Math.max(0, page), PageResponse.clampSize(size, 100));
    }

    @GetMapping("/customers/{id}")
    public CustomerDetail customer(@PathVariable long id) {
        return service.customer(id);
    }

    @PostMapping("/customers/{id}/block")
    public CustomerRow block(@PathVariable long id, @Valid @RequestBody BlockRequest req) {
        return service.setBlocked(id, req.blocked(), req.reason(), CurrentUser.id());
    }

    @GetMapping("/admins")
    public List<CustomerRow> admins() {
        return service.admins();
    }

    @PostMapping("/admins")
    public CustomerRow addAdmin(@Valid @RequestBody AddAdminRequest req) {
        return service.addAdmin(req.email(), CurrentUser.id());
    }

    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Void> removeAdmin(@PathVariable long id) {
        service.removeAdmin(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
