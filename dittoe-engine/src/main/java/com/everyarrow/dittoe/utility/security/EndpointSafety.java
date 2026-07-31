package com.everyarrow.dittoe.utility.security;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.utilities.environment.Environment;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;

public final class EndpointSafety {
    public static final String ALLOW_UNSAFE_ENDPOINT = "allowUnsafeEndpoint";
    public static final String ALLOW_UNSAFE_ENDPOINTS_CONFIG = "DITTOE_ALLOW_UNSAFE_ENDPOINT_OVERRIDES";

    private EndpointSafety() {
    }

    public static URI requirePublicHttpsEndpoint(String endpoint, Map<String, String> properties)
            throws ExecutionException {
        return requirePublicHttpsEndpoint(endpoint, properties, InetAddress::getAllByName);
    }

    static URI requirePublicHttpsEndpoint(
            String endpoint,
            Map<String, String> properties,
            HostResolver hostResolver) throws ExecutionException {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new ExecutionException("Endpoint URI is invalid.", e);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ExecutionException("Endpoint overrides must use HTTP or HTTPS.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ExecutionException("Endpoint override host cannot be blank.");
        }
        if (uri.getUserInfo() != null) {
            throw new ExecutionException("Endpoint overrides cannot contain embedded credentials.");
        }

        if (unsafeEndpointExplicitlyAllowed(properties)) {
            return uri;
        }

        if (!"https".equalsIgnoreCase(scheme)) {
            throw new ExecutionException("Endpoint overrides must use HTTPS by default.");
        }

        String normalizedHost = normalizeHost(host);
        if (isReservedHostName(normalizedHost)) {
            throw new ExecutionException("Endpoint override host is not public-safe by default: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(normalizedHost);
        } catch (UnknownHostException e) {
            throw new ExecutionException("Endpoint override host could not be resolved.", e);
        }
        if (addresses == null || addresses.length == 0) {
            throw new ExecutionException("Endpoint override host could not be resolved.");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new ExecutionException("Endpoint override host is not public-safe by default: " + host);
            }
        }

        return uri;
    }

    private static boolean unsafeEndpointExplicitlyAllowed(Map<String, String> properties) {
        boolean requestedByPipeline = Boolean.parseBoolean(
                properties == null ? "false" : properties.getOrDefault(ALLOW_UNSAFE_ENDPOINT, "false"));
        boolean allowedByOperator = Boolean.parseBoolean(
                Environment.getInstance().get(ALLOW_UNSAFE_ENDPOINTS_CONFIG, "false"));
        return requestedByPipeline && allowedByOperator;
    }

    private static String normalizeHost(String host) throws ExecutionException {
        String normalized = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        try {
            return IDN.toASCII(normalized).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new ExecutionException("Endpoint override host is invalid.", e);
        }
    }

    private static boolean isReservedHostName(String host) {
        if (host.equals("localhost") || host.equals("metadata") || host.equals("instance-data")) {
            return true;
        }
        if (!host.contains(".") && !host.contains(":")) {
            return true;
        }
        return host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".lan")
                || host.endsWith(".home")
                || host.endsWith(".svc")
                || host.endsWith(".test")
                || host.endsWith(".invalid");
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);

            return !(first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)
                    || first >= 224);
        }

        if (address instanceof Inet6Address && bytes.length == 16) {
            boolean uniqueLocal = (Byte.toUnsignedInt(bytes[0]) & 0xFE) == 0xFC;
            boolean documentation = Byte.toUnsignedInt(bytes[0]) == 0x20
                    && Byte.toUnsignedInt(bytes[1]) == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0D
                    && Byte.toUnsignedInt(bytes[3]) == 0xB8;
            return !uniqueLocal && !documentation;
        }

        return false;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
