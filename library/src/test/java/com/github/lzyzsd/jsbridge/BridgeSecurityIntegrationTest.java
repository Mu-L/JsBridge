package com.github.lzyzsd.jsbridge;

import android.content.Context;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Tests for P1-3: BaseJavascriptInterface origin check integration.
 * <p>
 * Uses a plain WebView (not BridgeWebView) so we can control getUrl()
 * via Robolectric's ShadowWebView without triggering BridgeWebViewClient
 * side effects.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BridgeSecurityIntegrationTest {

    private WebView webView;
    private Map<String, OnBridgeCallback> callbacks;
    private Map<String, OnBridgeCallback> persistentCallbacks;

    /** Concrete subclass that tracks whether the abstract send(String) was reached. */
    private static class TestJavascriptInterface extends BridgeWebView.BaseJavascriptInterface {
        int sendCount = 0;
        String lastSendData = null;

        TestJavascriptInterface(Map<String, OnBridgeCallback> callbacks) {
            super(callbacks);
        }

        TestJavascriptInterface(Map<String, OnBridgeCallback> callbacks,
                                Map<String, OnBridgeCallback> persistent) {
            super(callbacks, persistent);
        }

        TestJavascriptInterface(Map<String, OnBridgeCallback> callbacks,
                                Map<String, OnBridgeCallback> persistent,
                                BridgeConfig config,
                                WebView webView) {
            super(callbacks, persistent, config, webView);
        }

        @Override
        public String send(String data) {
            sendCount++;
            lastSendData = data;
            return "ok";
        }
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        webView = new WebView(context);
        callbacks = new ConcurrentHashMap<>();
        persistentCallbacks = new ConcurrentHashMap<>();
    }

    /**
     * Set the URL that webView.getUrl() returns via Robolectric's shadow.
     * ShadowWebView.loadUrl(String) stores the URL in originalUrl,
     * and getUrl() returns originalUrl.
     */
    private void setWebViewUrl(String url) {
        webView.loadUrl(url);
    }

    // ---- No config / empty whitelist: backward compatible ----

    @Test
    public void testNoConfig_sendAllowed() {
        TestJavascriptInterface iface = new TestJavascriptInterface(callbacks);
        String result = iface.send("hello", "cb1");
        assertEquals(1, iface.sendCount);
        assertEquals("ok", result);
    }

    @Test
    public void testNoConfig_responseAllowed() {
        final int[] callCount = {0};
        callbacks.put("resp1", new OnBridgeCallback() {
            @Override public void onCallBack(String data) { callCount[0]++; }
        });

        TestJavascriptInterface iface = new TestJavascriptInterface(callbacks);
        iface.response("data", "resp1");
        assertEquals("Callback should be invoked", 1, callCount[0]);
        assertFalse("Callback should be removed (non-persistent)", callbacks.containsKey("resp1"));
    }

    @Test
    public void testEmptyWhitelist_sendAllowed() {
        BridgeConfig config = new BridgeConfig();
        // Empty whitelist = allow all
        setWebViewUrl("https://any-domain.com/page");

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("hello", "cb1");
        assertEquals(1, iface.sendCount);
        assertEquals("ok", result);
    }

    @Test
    public void testEmptyWhitelist_responseAllowed() {
        BridgeConfig config = new BridgeConfig();
        setWebViewUrl("https://any-domain.com/page");

        final int[] callCount = {0};
        callbacks.put("resp1", new OnBridgeCallback() {
            @Override public void onCallBack(String data) { callCount[0]++; }
        });

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        iface.response("data", "resp1");
        assertEquals(1, callCount[0]);
    }

    // ---- Whitelist with allowed host ----

    @Test
    public void testAllowedHost_sendSucceeds() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://example.com/page");

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("hello", "cb1");
        assertEquals("Abstract send should be called", 1, iface.sendCount);
        assertEquals("ok", result);
    }

    @Test
    public void testAllowedHost_responseInvokesCallback() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://example.com/page");

        final int[] callCount = {0};
        callbacks.put("resp1", new OnBridgeCallback() {
            @Override public void onCallBack(String data) { callCount[0]++; }
        });

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        iface.response("data", "resp1");
        assertEquals(1, callCount[0]);
        assertFalse("Non-persistent callback removed", callbacks.containsKey("resp1"));
    }

    // ---- Whitelist with blocked host ----

    @Test
    public void testBlockedHost_sendReturnsEmpty() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://evil.com/attack");

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("payload", "cb1");
        assertEquals("Abstract send should NOT be called", 0, iface.sendCount);
        assertEquals("Blocked send should return empty string", "", result);
    }

    @Test
    public void testBlockedHost_responseDoesNotInvokeCallback() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://evil.com/attack");

        final int[] callCount = {0};
        callbacks.put("resp1", new OnBridgeCallback() {
            @Override public void onCallBack(String data) { callCount[0]++; }
        });

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        iface.response("data", "resp1");
        assertEquals("Callback should NOT be invoked for blocked host", 0, callCount[0]);
    }

    @Test
    public void testBlockedHost_responsePreservesCallbackInMap() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://evil.com/attack");

        callbacks.put("resp1", new OnBridgeCallback() {
            @Override public void onCallBack(String data) {}
        });

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        iface.response("data", "resp1");

        assertTrue("Callback should still be in map (not consumed by blocked response)",
                callbacks.containsKey("resp1"));
    }

    // ---- Persistent callback with allowed host ----

    @Test
    public void testAllowedHost_persistentCallbackNotRemoved() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        setWebViewUrl("https://example.com/page");

        final int[] callCount = {0};
        OnBridgeCallback cb = new OnBridgeCallback() {
            @Override public void onCallBack(String data) { callCount[0]++; }
        };
        callbacks.put("resp1", cb);
        persistentCallbacks.put("resp1", cb);

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);

        iface.response("data1", "resp1");
        assertEquals(1, callCount[0]);
        assertTrue("Persistent callback should remain in map", callbacks.containsKey("resp1"));

        iface.response("data2", "resp1");
        assertEquals(2, callCount[0]);
        assertTrue("Persistent callback still in map after second call", callbacks.containsKey("resp1"));
    }

    // ---- Old constructors (no config) always allow ----

    @Test
    public void testOldConstructorSingleArg_alwaysAllows() {
        TestJavascriptInterface iface = new TestJavascriptInterface(callbacks);
        String result = iface.send("data", "cb1");
        assertEquals(1, iface.sendCount);
        assertEquals("ok", result);
    }

    @Test
    public void testOldConstructorTwoArg_alwaysAllows() {
        TestJavascriptInterface iface = new TestJavascriptInterface(callbacks, persistentCallbacks);
        String result = iface.send("data", "cb1");
        assertEquals(1, iface.sendCount);
        assertEquals("ok", result);
    }

    // ---- Null URL handling ----

    @Test
    public void testNullUrl_blockedWhenWhitelistNonEmpty() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");
        // Don't set any URL on webView — getUrl() will return null

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("data", "cb1");
        assertEquals("Send should be blocked for null URL", 0, iface.sendCount);
        assertEquals("", result);
    }

    @Test
    public void testNullWebView_blockedWhenWhitelistNonEmpty() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("example.com");

        // Pass null webView
        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, null);
        String result = iface.send("data", "cb1");
        assertEquals("Send should be blocked when webView is null", 0, iface.sendCount);
        assertEquals("", result);
    }

    // ---- Wildcard host integration ----

    @Test
    public void testWildcardHost_subdomainSendSucceeds() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("*.example.com");
        setWebViewUrl("https://api.example.com/v1/data");

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("hello", "cb1");
        assertEquals("Wildcard subdomain should allow send", 1, iface.sendCount);
        assertEquals("ok", result);
    }

    @Test
    public void testWildcardHost_wrongDomainBlocked() {
        BridgeConfig config = new BridgeConfig();
        config.addAllowedHost("*.example.com");
        setWebViewUrl("https://evil.com/page");

        TestJavascriptInterface iface = new TestJavascriptInterface(
                callbacks, persistentCallbacks, config, webView);
        String result = iface.send("payload", "cb1");
        assertEquals("Non-matching domain should be blocked", 0, iface.sendCount);
        assertEquals("", result);
    }
}
