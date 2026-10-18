package com.store.shipping;

import com.store.catalog.domain.ProductVariant;
import com.store.catalog.domain.ProductVariantRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery")
public class DeliveryController {

    private final DeliveryService delivery;
    private final ProductVariantRepository variants;

    public DeliveryController(DeliveryService delivery, ProductVariantRepository variants) {
        this.delivery = delivery;
        this.variants = variants;
    }

    /** GET /delivery/check?pincode=302001&variantId=12 */
    @GetMapping("/check")
    public DeliveryService.DeliveryCheck check(@RequestParam String pincode,
                                               @RequestParam(required = false) Long variantId) {
        int weight = variantId == null ? 1000
                : variants.findById(variantId).map(ProductVariant::getWeightGrams).orElse(1000);
        return delivery.check(pincode, weight);
    }
}
