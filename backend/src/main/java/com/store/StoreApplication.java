package com.store;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StoreApplication {

    public static void main(String[] args) {
        // Store UTC, show IST (the frontend formats for display).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(StoreApplication.class, args);
    }
}
