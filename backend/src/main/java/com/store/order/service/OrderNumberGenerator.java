package com.store.order.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ORD-YYMMDD-XXXX: IST date plus a per-day counter (row-locked upsert, so numbers never collide). */
@Component
public class OrderNumberGenerator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcClient jdbc;
    private final Clock clock;

    public OrderNumberGenerator(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        int seq = jdbc.sql("""
                INSERT INTO order_number_counters (day, last_seq) VALUES (:d, 1)
                ON CONFLICT (day) DO UPDATE SET last_seq = order_number_counters.last_seq + 1
                RETURNING last_seq
                """).param("d", today).query(Integer.class).single();
        // 4 digits covers 9,999 orders a day; beyond that the number simply grows to 5 digits.
        return "ORD-" + FMT.format(today) + "-" + String.format("%04d", seq);
    }
}
