package com.store.shipping;

import com.store.common.config.AppProperties;
import com.store.common.util.Json;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShippingConfig {

    /** SHIPROCKET_ENABLED=false (local/dev) uses a stub that treats every valid pincode as serviceable. */
    @Bean
    public ShippingProvider shippingProvider(AppProperties props, HttpClient http, Json json, Clock clock) {
        if (props.shiprocket() != null && props.shiprocket().enabled()) {
            return new ShiprocketProvider(http, json, props.shiprocket(), clock);
        }
        return (pickup, delivery, weight) -> new ShippingProvider.Serviceability(true, null, null, "Stub courier");
    }
}
