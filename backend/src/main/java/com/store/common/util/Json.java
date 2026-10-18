package com.store.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Thin wrapper so callers don't deal with checked Jackson exceptions. */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise " + value.getClass(), e);
        }
    }

    public JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON for " + type.getSimpleName(), e);
        }
    }

    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public JsonNode tree(Object value) {
        return mapper.valueToTree(value);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
