package com.store.common.error;

import java.util.Map;

/** The one error format for every endpoint. */
public record ErrorResponse(String code, String message, Map<String, Object> details) {}
