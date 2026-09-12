package com.github.lzyzsd.jsbridge;

import android.content.Context;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Tests for BridgeHelper.shouldOverrideUrlLoading — specifically the fix for #175
 * (URL decode must not corrupt non-bridge URLs).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BridgeHelperUrlDecodeTest {

    private BridgeHelper helper;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        WebView webView = new WebView(context);
        IWebView iWebView = new IWebView() {
            @Override public void loadUrl(String url) { webView.loadUrl(url); }
            @Override public Context getContext() { return context; }
            @Override public WebView getWebView() { return webView; }
        };
        helper = new BridgeHelper(iWebView);
    }

    @Test
    public void testNonBridgeUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("https://example.com/page"));
    }

    @Test
    public void testNonBridgeUrl_withEncodedParams_notIntercepted() {
        // Issue #175: Alipay-style URL with encoded query params must NOT be decoded
        String alipayUrl = "alipays://platformapi/startApp?appId=20000125"
                + "&orderSuffix=h5_route_token%3d%22RZ13%22%26is_h5_route%3d%22true%22";
        assertFalse(helper.shouldOverrideUrlLoading(alipayUrl));
    }

    @Test
    public void testBridgeReturnUrl_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading("yy://return/_fetchQueue/"));
    }

    @Test
    public void testBridgeOverrideUrl_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading("yy://something"));
    }

    @Test
    public void testNonBridgeUrl_withYyInPath_notIntercepted() {
        // A normal URL that happens to contain "yy" should not be intercepted
        assertFalse(helper.shouldOverrideUrlLoading("https://example.com/yy/page"));
    }

    @Test
    public void testEmptyUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading(""));
    }

    @Test
    public void testJavascriptUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("javascript:void(0)"));
    }
}
