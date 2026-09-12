package com.github.lzyzsd.jsbridge;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BridgeConfigTest {

    private BridgeConfig config;

    @Before
    public void setUp() {
        config = new BridgeConfig();
    }

    @Test
    public void testEmptyWhitelistAllowsAllUrls() {
        assertTrue(config.isUrlAllowed("https://example.com/page"));
        assertTrue(config.isUrlAllowed("https://evil.com/hack"));
        assertTrue(config.isUrlAllowed("http://anything.org"));
        assertTrue(config.isUrlAllowed("file:///android_asset/demo.html"));
    }

    @Test
    public void testAddedHostAllowsMatchingUrl() {
        config.addAllowedHost("example.com");
        assertTrue(config.isUrlAllowed("https://example.com/page"));
        assertTrue(config.isUrlAllowed("http://example.com"));
        assertTrue(config.isUrlAllowed("https://example.com/path?q=1"));
    }

    @Test
    public void testAddedHostBlocksNonMatchingUrl() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed("https://evil.com/page"));
        assertFalse(config.isUrlAllowed("https://other.org"));
    }

    @Test
    public void testCaseInsensitiveHostMatching() {
        config.addAllowedHost("Example.COM");
        assertTrue(config.isUrlAllowed("https://example.com/page"));
        assertTrue(config.isUrlAllowed("https://EXAMPLE.COM/page"));
        assertTrue(config.isUrlAllowed("https://Example.Com/page"));
    }

    @Test
    public void testWildcardMatchesSubdomain() {
        config.addAllowedHost("*.example.com");
        assertTrue(config.isUrlAllowed("https://sub.example.com/page"));
        assertTrue(config.isUrlAllowed("https://api.example.com"));
    }

    @Test
    public void testWildcardMatchesBaseDomain() {
        config.addAllowedHost("*.example.com");
        assertTrue(config.isUrlAllowed("https://example.com/page"));
    }

    @Test
    public void testWildcardDoesNotMatchSimilarDomain() {
        config.addAllowedHost("*.example.com");
        assertFalse(config.isUrlAllowed("https://notexample.com/page"));
        assertFalse(config.isUrlAllowed("https://fakeexample.com"));
    }

    @Test
    public void testWildcardMatchesDeepSubdomain() {
        config.addAllowedHost("*.example.com");
        assertTrue(config.isUrlAllowed("https://deep.sub.example.com/page"));
        assertTrue(config.isUrlAllowed("https://a.b.c.example.com"));
    }

    @Test
    public void testNullUrlReturnsFalseWhenWhitelistSet() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed(null));
    }

    @Test
    public void testEmptyStringUrlReturnsFalseWhenWhitelistSet() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed(""));
    }

    @Test
    public void testUrlWithNoHostReturnsFalse() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed("javascript:void(0)"));
        assertFalse(config.isUrlAllowed("data:text/html,<h1>hi</h1>"));
    }

    @Test
    public void testRemoveAllowedHostRestoresAllowAll() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed("https://evil.com"));

        config.removeAllowedHost("example.com");
        // Now empty again — allow all
        assertTrue(config.isUrlAllowed("https://evil.com"));
        assertTrue(config.isUrlAllowed("https://example.com"));
    }

    @Test
    public void testSetAllowedHostsReplacesPreviousHosts() {
        config.addAllowedHost("old.com");
        assertTrue(config.isUrlAllowed("https://old.com"));

        Set<String> newHosts = new HashSet<>(Arrays.asList("new.com", "another.com"));
        config.setAllowedHosts(newHosts);

        assertFalse(config.isUrlAllowed("https://old.com"));
        assertTrue(config.isUrlAllowed("https://new.com"));
        assertTrue(config.isUrlAllowed("https://another.com"));
    }

    @Test
    public void testSetAllowedHostsWithNullClearsHosts() {
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed("https://evil.com"));

        config.setAllowedHosts(null);
        // Back to allow-all
        assertTrue(config.isUrlAllowed("https://evil.com"));
        assertTrue(config.isUrlAllowed("https://example.com"));
    }

    @Test
    public void testMultipleHostsFirstMatchesSecondDoesNot() {
        config.addAllowedHost("allowed.com");
        config.addAllowedHost("also-allowed.com");

        assertTrue(config.isUrlAllowed("https://allowed.com/page"));
        assertTrue(config.isUrlAllowed("https://also-allowed.com/page"));
        assertFalse(config.isUrlAllowed("https://blocked.com/page"));
    }

    @Test
    public void testGetAllowedHostsReturnsUnmodifiableSet() {
        config.addAllowedHost("example.com");
        Set<String> hosts = config.getAllowedHosts();

        try {
            hosts.add("evil.com");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        // Original set should be unchanged
        assertEquals(1, config.getAllowedHosts().size());
        assertTrue(config.getAllowedHosts().contains("example.com"));
    }

    @Test
    public void testFileUrlHandling() {
        // file:// URLs have no host — should be blocked when whitelist is set
        config.addAllowedHost("example.com");
        assertFalse(config.isUrlAllowed("file:///android_asset/demo.html"));

        // But allowed when whitelist is empty
        config.removeAllowedHost("example.com");
        assertTrue(config.isUrlAllowed("file:///android_asset/demo.html"));
    }

    @Test
    public void testHttpAndHttpsUrls() {
        config.addAllowedHost("example.com");
        assertTrue(config.isUrlAllowed("http://example.com/page"));
        assertTrue(config.isUrlAllowed("https://example.com/page"));
        assertTrue(config.isUrlAllowed("https://example.com:8080/page"));
    }

    @Test
    public void testAddNullHostIsIgnored() {
        config.addAllowedHost(null);
        // Should still be empty — allow all
        assertTrue(config.getAllowedHosts().isEmpty());
        assertTrue(config.isUrlAllowed("https://anything.com"));
    }

    @Test
    public void testRemoveNullHostIsIgnored() {
        config.addAllowedHost("example.com");
        config.removeAllowedHost(null);
        // Should still have example.com
        assertEquals(1, config.getAllowedHosts().size());
    }

    @Test
    public void testSetAllowedHostsSkipsNullEntries() {
        Set<String> hosts = new HashSet<>();
        hosts.add("example.com");
        hosts.add(null);
        config.setAllowedHosts(hosts);

        assertEquals(1, config.getAllowedHosts().size());
        assertTrue(config.isUrlAllowed("https://example.com"));
    }

    // ---- Port handling ----

    @Test
    public void testPortInUrl_matchesHostWithoutPort() {
        // URI host extraction strips the port — "example.com:8080" → host "example.com"
        config.addAllowedHost("example.com");
        assertTrue("URL with non-standard port should match host",
                config.isUrlAllowed("https://example.com:8080/page"));
        assertTrue("URL with standard port should match host",
                config.isUrlAllowed("https://example.com:443/page"));
    }

    // ---- Subdomain exact match ----

    @Test
    public void testSubdomainExactMatch_onlyMatchesExact() {
        config.addAllowedHost("sub.example.com");
        assertTrue("Exact subdomain should match",
                config.isUrlAllowed("https://sub.example.com/page"));
        assertFalse("Different subdomain should NOT match",
                config.isUrlAllowed("https://other.example.com/page"));
        assertFalse("Base domain should NOT match with subdomain-only config",
                config.isUrlAllowed("https://example.com/page"));
    }

    // ---- Empty string host ----

    @Test
    public void testEmptyStringHost_isIgnoredAsPattern() {
        config.addAllowedHost("");
        // An empty string in the set should not accidentally match everything
        // It will match hosts that are also empty, but real URLs always have a non-empty host
        assertFalse("Empty host should not match real URLs",
                config.isUrlAllowed("https://example.com/page"));
    }
}
