package com.github.lzyzsd.jsbridge;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowWebView;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * Tests for BridgeWebView.init() — Android 11+ compatibility (issue #283).
 * Verifies that WebSettings are configured correctly after construction:
 * - Security defaults (file access disabled)
 * - DOM storage enabled
 * - No forced cache clearing
 * - JavaScript enabled
 * - WebViewClient set
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BridgeWebViewInitTest {

    private BridgeWebView webView;
    private WebSettings settings;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        webView = new BridgeWebView(context);
        settings = webView.getSettings();
    }

    // ---- Core settings ----

    @Test
    public void testJavaScriptEnabled() {
        assertTrue("JavaScript must be enabled for the bridge to work",
                settings.getJavaScriptEnabled());
    }

    @Test
    public void testUseWideViewPort() {
        assertTrue("Wide viewport should be enabled",
                settings.getUseWideViewPort());
    }

    @Test
    public void testDomStorageEnabled() {
        assertTrue("DOM storage should be enabled by default",
                settings.getDomStorageEnabled());
    }

    // ---- Android 11+ security defaults ----

    @SuppressWarnings("deprecation")
    @Test
    public void testFileAccessFromFileUrlsDisabled() {
        assertFalse("File access from file:// URLs should be disabled for security",
                settings.getAllowFileAccessFromFileURLs());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testUniversalAccessFromFileUrlsDisabled() {
        assertFalse("Universal access from file:// URLs should be disabled for security",
                settings.getAllowUniversalAccessFromFileURLs());
    }

    // ---- Cache policy not forced ----

    @Test
    public void testCacheModeNotForced() {
        // init() no longer sets LOAD_NO_CACHE — cache policy is left to the host app
        assertNotEquals("Cache mode should NOT be forced to LOAD_NO_CACHE",
                WebSettings.LOAD_NO_CACHE, settings.getCacheMode());
    }

    // ---- WebViewClient is set ----

    @Test
    public void testWebViewClientIsSet() {
        ShadowWebView shadow = shadowOf(webView);
        assertNotNull("BridgeWebViewClient should be set during init",
                shadow.getWebViewClient());
    }
}
