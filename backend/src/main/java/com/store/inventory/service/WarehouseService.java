package com.store.inventory.service;

import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.util.IndianStates;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One pickup location at launch (address + GSTIN). Edited from admin settings. */
@Service
public class WarehouseService {

    public record Warehouse(long id, String code, String name, String pickupLocationName, String contactName,
                            String phone, String email, String line1, String line2, String city, String state,
                            String pincode, String gstin) {}

    public record WarehouseRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String pickupLocationName,
            @Size(max = 80) String contactName,
            @Pattern(regexp = "^[6-9]\\d{9}$") String phone,
            @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank String state,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$") String pincode,
            @Pattern(regexp = "^[0-9]{2}[A-Z0-9]{13}$", message = "Enter a valid 15-character GSTIN") String gstin) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public WarehouseService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Warehouse defaultWarehouse() {
        return jdbc.sql("""
                SELECT id, code, name, pickup_location_name, contact_name, phone, email, line1, line2, city, state,
                       pincode, gstin
                FROM warehouses WHERE is_default
                """).query(Warehouse.class).single();
    }

    @Transactional
    public Warehouse update(WarehouseRequest req, long actor) {
        Warehouse before = defaultWarehouse();
        String state = IndianStates.canonical(req.state())
                .orElseThrow(() -> ApiException.badRequest("INVALID_STATE", "Choose a valid state"));
        jdbc.sql("""
                UPDATE warehouses SET name = :name, pickup_location_name = :pickup, contact_name = :contact,
                       phone = :phone, email = :email, line1 = :line1, line2 = :line2, city = :city, state = :state,
                       pincode = :pincode, gstin = :gstin, updated_at = now()
                WHERE id = :id
                """)
                .param("name", req.name()).param("pickup", req.pickupLocationName())
                .param("contact", req.contactName()).param("phone", req.phone()).param("email", req.email())
                .param("line1", req.line1()).param("line2", req.line2()).param("city", req.city())
                .param("state", state).param("pincode", req.pincode()).param("gstin", req.gstin())
                .param("id", before.id())
                .update();
        Warehouse after = defaultWarehouse();
        audit.record(actor, "WAREHOUSE_UPDATED", "warehouse", before.id(), before, after, null);
        return after;
    }
}
