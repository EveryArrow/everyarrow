/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.reflection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for handling resource files.
 */
public class ResourceUtils {
    // Private constructor to prevent instantiation of this utility class.
    private ResourceUtils() {
    }

    /**
     * Opens a resource file from the classpath and returns an InputStream.
     *
     * @param fileName The name of the file to be opened.
     * @return InputStream of the opened file.
     * @throws RuntimeException if the file cannot be found or opened.
     */
    public static InputStream openResourceFile(String fileName) {
        return Optional.ofNullable(ClassLoader.getSystemClassLoader().getResourceAsStream(fileName))
                .orElseThrow(() -> new RuntimeException("Could not find file " + fileName + " in resource path"));
    }

    /**
     * Reads a resource file from the classpath and converts it into a string.
     *
     * @param fileName The name of the file to read.
     * @return The contents of the file as a string.
     * @throws RuntimeException if there is an error reading the file.
     */
    public static String getResourceFileAsString(String fileName) {
        try (InputStream is = openResourceFile(fileName);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading resource file: " + fileName, e);
        }
    }

    /**
     * Checks if a resource file exists in the classpath.
     *
     * @param fileName The name of the file to check.
     * @return true if the file exists, false otherwise.
     */
    public static boolean resourceExists(String fileName) {
        return ClassLoader.getSystemClassLoader().getResource(fileName) != null;
    }
}