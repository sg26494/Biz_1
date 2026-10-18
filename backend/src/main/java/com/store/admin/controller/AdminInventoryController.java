package com.store.admin.controller;

import com.store.auth.security.CurrentUser;
import com.store.common.settings.SettingsService;
import com.store.common.web.PageResponse;
import com.store.inventory.service.InventoryService;
import com.store.inventory.service.InventoryService.AdjustReason;
import com.store.inventory.service.InventoryService.InventoryRow;
import com.store.inventory.service.InventoryService.MovementRow;
import com.store.inventory.service.InventoryService.StockView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    public record AdjustRequest(@NotNull Long variantId, @NotNull AdjustReason reason, @NotNull Integer delta,
                                @Size(max = 500) String note) {}

    public record ThresholdRequest(@NotNull Long variantId, @Min(0) Integer threshold) {}

    private final InventoryService inventory;
    private final SettingsService settings;

    public AdminInventoryController(InventoryService inventory, SettingsService settings) {
        this.inventory = inventory;
        this.settings = settings;
    }

    @GetMapping
    public List<InventoryRow> list(@RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "false") boolean lowOnly) {
        return inventory.list(q, lowOnly, settings.getInt(SettingsService.LOW_STOCK_DEFAULT, 3));
    }

    /** Reason code required: PURCHASE (new stock), DAMAGE, CORRECTION. */
    @PostMapping("/adjust")
    public StockView adjust(@Valid @RequestBody AdjustRequest req) {
        return inventory.adjust(req.variantId(), req.reason(), req.delta(), req.note(), CurrentUser.id());
    }

    @GetMapping("/movements")
    public List<MovementRow> movements(@RequestParam(required = false) Long variantId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return inventory.movements(variantId, Math.max(0, page), PageResponse.clampSize(size, 200));
    }
}
