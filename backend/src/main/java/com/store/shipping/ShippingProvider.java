package com.store.shipping;

/**
 * Courier aggregator behind one interface (Shiprocket in production, a stub locally).
 * Phase 6 adds createOrder / assignAwb / pickup / label / tracking / return orders here.
 */
public interface ShippingProvider {

    record Serviceability(boolean serviceable, Integer etaDays, Long ratePaise, String courierName) {}

    Serviceability checkServiceability(String pickupPincode, String deliveryPincode, int weightGrams);
}
