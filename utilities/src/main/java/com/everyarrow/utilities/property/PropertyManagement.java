/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.property;

import java.util.*;

public class PropertyManagement {

    /**
     * Validates that all specified properties are present in the provided map.
     *
     * @param map        A map of string keys and values.
     * @param properties Array of strings representing the keys to check in the map.
     * @throws RuntimeException if any specified key is missing from the map.
     */
    public static void validateProperties(Map<String, String> map, String... properties) {
        List<String> missing = new ArrayList<>();

        for (String key : properties) {
            if (!map.containsKey(key)) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            throw new RuntimeException("Properties missing: " + Arrays.toString(missing.toArray()));
        }
    }

    private static Map<String, String> asStringMap(Properties properties) {
        Map<String, String> map = new HashMap<>(properties.size());
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return Map.copyOf(map);
    }
}
