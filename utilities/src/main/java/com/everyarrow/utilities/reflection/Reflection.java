/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.reflection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Reflection {
    /**
     * Retrieves the method with the given name from the specified class.
     *
     * @param className  The fully qualified name of the class (e.g., "java.util.ArrayList").
     * @param methodName The name of the method to retrieve.
     * @param paramTypes The parameter types of the method (varargs, optional).
     * @return The Method object if the method exists; otherwise, null.
     */
    public  static Method getMethod(String className, String methodName, Class<?>... paramTypes) {
        Method method = null;
        try {
            // Get the class dynamically using refactored getClass method
            Class<?> clazz = getClass(className);

            // Look for the method with the specified name and parameter types
            method = clazz.getMethod(methodName, paramTypes);
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found: " + className);
        } catch (NoSuchMethodException e) {
            System.out.println("Method not found: " + methodName);
        } catch (SecurityException e) {
            System.out.println("Access denied to the method: " + methodName);
        }

        // Return the method (null if not found or exception occurs)
        return method;
    }

    /**
     * Retrieves a method from a given class polymorphically.
     *
     * @param clazz        The Class object of the target class.
     * @param methodName   The name of the method to retrieve.
     * @param parameterTypes The parameter types of the method (optional for overloading).
     * @return The Method object if found, or null if no method is found.
     * @throws NoSuchMethodException if the method cannot be found.
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        if (clazz == null || methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("Class and method name must not be null or empty");
        }
        return clazz.getMethod(methodName, parameterTypes); // Uses reflection to find method
    }
    /**
     * Retrieves a Class object based on the fully qualified class name.
     *
     * @param className The fully qualified name of the class (e.g., "java.util.ArrayList").
     * @return The Class object if it exists; otherwise, null.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public  static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
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

    public static Object instantiateClass(String className, Object... args)  {
        try {
            Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());

            Class<?>[] argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i].getClass();
            }

            Constructor<?> constructor = clazz.getConstructor(argTypes);

            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Finds all classes within a package (non-recursive). */
    public static List<Class<?>> getClassesInPackage(String packageName) throws IOException, ClassNotFoundException {
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }

        List<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {

        List<Class<?>> classes = new ArrayList<>();

        try {


            if (!directory.exists()) return classes;

            File[] files = directory.listFiles();
            if (files == null) return classes;

            for (File file : files) {
                if (file.isDirectory()) {
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                        classes.add(Class.forName(className));
                }
            }


        } catch (ClassNotFoundException ex) {

            throw ex;


        }

        return classes;

    }
}