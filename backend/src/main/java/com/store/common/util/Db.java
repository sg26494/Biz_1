package com.store.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** JDBC parameter helpers (pgjdbc binds OffsetDateTime, not Instant). */
public final class Db {

    private Db() {}

    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
