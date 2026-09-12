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

    // ---- Non-bridge URLs must NOT be intercepted ----

    @Test
    public void testNonBridgeUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("https://example.com/page"));
    }

    @Test
    public void testHttpUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("http://example.com/page?q=1"));
    }

    @Test
    public void testEmptyUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading(""));
    }

    @Test
    public void testJavascriptUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("javascript:void(0)"));
    }

    @Test
    public void testDataUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("data:text/html,<h1>hi</h1>"));
    }

    @Test
    public void testFileUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("file:///android_asset/demo.html"));
    }

    @Test
    public void testIntentUrl_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading(
                "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"));
    }

    @Test
    public void testNonBridgeUrl_withYyInPath_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("https://example.com/yy/page"));
    }

    @Test
    public void testNonBridgeUrl_withYyInQuery_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("https://example.com?scheme=yy://test"));
    }

    // ---- Issue #175: encoded query params must not be corrupted ----

    @Test
    public void testAlipayUrl_encodedQueryNotCorrupted() {
        // Original issue: %3d → = and %26 → & changed the parameter count
        String alipayUrl = "alipays://platformapi/startApp?appId=20000125"
                + "&orderSuffix=h5_route_token%3d%22RZ13%22%26is_h5_route%3d%22true%22";
        assertFalse(helper.shouldOverrideUrlLoading(alipayUrl));
    }

    @Test
    public void testUrlWithDoubleEncodedParams_notIntercepted() {
        // Double-encoded: %253d is %3d encoded again
        String url = "https://example.com/redirect?target=https%3A%2F%2Fother.com%2Fpage%253Fq%253D1";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    @Test
    public void testUrlWithEncodedChineseChars_notIntercepted() {
        // Chinese characters encoded in URL
        String url = "https://example.com/search?q=%E4%BD%A0%E5%A5%BD";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    @Test
    public void testUrlWithEncodedHash_notIntercepted() {
        String url = "https://example.com/page#section%20name%26anchor";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    @Test
    public void testUrlWithMultipleEncodedParams_notIntercepted() {
        String url = "https://pay.example.com/checkout"
                + "?amount=100"
                + "&callback=https%3A%2F%2Fmerchant.com%2Fpay%3Forder%3D123%26status%3Dsuccess"
                + "&sign=abc%3D%3D";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    @Test
    public void testUrlWithPlusSign_notIntercepted() {
        // '+' in query strings means space in form encoding; should not be touched
        String url = "https://example.com/search?q=hello+world&lang=zh";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    @Test
    public void testDeepLinkUrl_withEncodedParams_notIntercepted() {
        // WeChat-style deep link
        String url = "weixin://dl/business/?t=abc%3D123%26token%3Dxyz";
        assertFalse(helper.shouldOverrideUrlLoading(url));
    }

    // ---- Bridge URLs must be intercepted ----

    @Test
    public void testBridgeReturnUrl_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading("yy://return/_fetchQueue/"));
    }

    @Test
    public void testBridgeOverrideUrl_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading("yy://something"));
    }

    @Test
    public void testBridgeReturnUrl_withData_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading(
                "yy://return/callbackId/{\"data\":\"value\"}"));
    }

    @Test
    public void testBridgeReturnUrl_withEncodedData_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading(
                "yy://return/_fetchQueue/%7B%22key%22%3A%22val%22%7D"));
    }

    @Test
    public void testBridgeFetchQueueUrl_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading(BridgeUtil.YY_FETCH_QUEUE));
    }

    @Test
    public void testBridgeUrl_exactSchema_isIntercepted() {
        assertTrue(helper.shouldOverrideUrlLoading("yy://"));
    }

    // ---- Boundary / lookalike cases ----

    @Test
    public void testSimilarScheme_yyy_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("yyy://something"));
    }

    @Test
    public void testSimilarScheme_yyx_notIntercepted() {
        assertFalse(helper.shouldOverrideUrlLoading("yyx://return/data"));
    }

    @Test
    public void testUppercaseYY_notIntercepted() {
        // Bridge scheme is lowercase "yy://" — uppercase should not match
        assertFalse(helper.shouldOverrideUrlLoading("YY://return/data"));
    }

    // ---- Origin whitelist + URL decode interaction ----

    @Test
    public void testBridgeUrl_blockedByWhitelist_stillIntercepted() {
        // Even when origin is blocked, bridge URL should be intercepted (return true)
        // to prevent the WebView from navigating to it
        helper.addAllowedHost("example.com");
        assertTrue(helper.shouldOverrideUrlLoading("yy://return/_fetchQueue/"));
    }

    @Test
    public void testNonBridgeUrl_withWhitelist_notIntercepted() {
        helper.addAllowedHost("example.com");
        assertFalse(helper.shouldOverrideUrlLoading("https://evil.com/page"));
    }
}
