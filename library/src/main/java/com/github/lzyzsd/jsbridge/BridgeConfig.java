package com.github.lzyzsd.jsbridge;

import android.net.Uri;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Configuration for the JsBridge, including domain whitelist.
 * <p>
 * When the allowed hosts set is empty (default), all origins are permitted
 * for backward compatibility. Once any host is added, only pages whose host
 * matches an entry in the set are allowed to use the bridge.
 * <p>
 * Wildcard subdomain matching is supported: "*.example.com" matches
 * "sub.example.com" and "example.com" itself.
 */
public class BridgeConfig {

    private final Set<String> allowedHosts = new CopyOnWriteArraySet<>();

    /**
     * Replace the entire allowed-hosts set.
     *
     * @param hosts set of host patterns (e.g. "example.com", "*.example.com").
     *              Pass null or empty to allow all hosts (backward-compatible default).
     */
    public void setAllowedHosts(Set<String> hosts) {
        allowedHosts.clear();
        if (hosts != null) {
            for (String host : hosts) {
                if (host != null) {
                    allowedHosts.add(host.toLowerCase(Locale.US));
                }
            }
        }
    }

    /**
     * Add a single host to the allowed set.
     *
     * @param host e.g. "example.com" or "*.example.com"
     */
    public void addAllowedHost(String host) {
        if (host != null) {
            allowedHosts.add(host.toLowerCase(Locale.US));
        }
    }

    /**
     * Remove a single host from the allowed set.
     *
     * @param host the host pattern to remove
     */
    public void removeAllowedHost(String host) {
        if (host != null) {
            allowedHosts.remove(host.toLowerCase(Locale.US));
        }
    }

    /**
     * Return an unmodifiable snapshot of the current allowed hosts.
     */
    public Set<String> getAllowedHosts() {
        return Collections.unmodifiableSet(allowedHosts);
    }

    /**
     * Check whether a URL's host is in the allowed set.
     * <p>
     * Returns {@code true} if:
     * <ul>
     *   <li>The allowed set is empty (backward-compatible default), or</li>
     *   <li>The URL's host matches an entry exactly, or</li>
     *   <li>The URL's host matches a wildcard entry (e.g. "*.example.com"
     *       matches "sub.example.com" and "example.com").</li>
     * </ul>
     * Returns {@code false} for null/empty URLs or when the host doesn't match.
     *
     * @param url the full URL to check
     * @return true if the origin is allowed
     */
    public boolean isUrlAllowed(String url) {
        if (allowedHosts.isEmpty()) {
            return true; // backward compatible: no whitelist = allow all
        }
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            host = host.toLowerCase(Locale.US);

            for (String pattern : allowedHosts) {
                if (pattern.startsWith("*.")) {
                    // Wildcard: *.example.com matches sub.example.com and example.com
                    String suffix = pattern.substring(1); // ".example.com"
                    String baseDomain = pattern.substring(2); // "example.com"
                    if (host.equals(baseDomain) || host.endsWith(suffix)) {
                        return true;
                    }
                } else {
                    if (host.equals(pattern)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
