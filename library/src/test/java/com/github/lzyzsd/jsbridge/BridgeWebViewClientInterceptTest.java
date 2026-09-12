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
 * Integration tests for BridgeWebViewClient.shouldOverrideUrlLoading.
 * Verifies that:
 * 1. Bridge URLs (yy://) are intercepted and return true
 * 2. Non-bridge URLs pass through and return false
 * 3. Non-bridge URLs with encoded params are NOT decoded (fix for #175)
 * 4. Custom WebViewClient delegation works correctly
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class BridgeWebViewClientInterceptTest {

    private BridgeWebViewClient client;
    private WebView webView;
    private boolean pageResetCalled;
    private boolean jsInjectedCalled;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        webView = new WebView(context);
        pageResetCalled = false;
        jsInjectedCalled = false;

        client = new BridgeWebViewClient(new BridgeWebViewClient.OnLoadJSListener() {
            @Override
            public void onPageReset() { pageResetCalled = true; }
            @Override
            public void onJSInjected() { jsInjectedCalled = true; }
        });
    }

    // ---- Bridge URLs should be intercepted ----

    @Test
    public void testBridgeReturnUrl_intercepted() {
        assertTrue(client.shouldOverrideUrlLoading(webView, "yy://return/_fetchQueue/"));
    }

    @Test
    public void testBridgeOverrideUrl_intercepted() {
        assertTrue(client.shouldOverrideUrlLoading(webView, "yy://__QUEUE_MESSAGE__"));
    }

    @Test
    public void testBridgeFetchQueue_intercepted() {
        assertTrue(client.shouldOverrideUrlLoading(webView, BridgeUtil.YY_FETCH_QUEUE));
    }

    @Test
    public void testBridgeUrl_withEncodedPayload_intercepted() {
        assertTrue(client.shouldOverrideUrlLoading(webView,
                "yy://return/cb1/%7B%22data%22%3A%22hello%22%7D"));
    }

    @Test
    public void testBridgeUrl_exactSchema_intercepted() {
        assertTrue(client.shouldOverrideUrlLoading(webView, "yy://"));
    }

    // ---- Non-bridge URLs should NOT be intercepted ----

    @Test
    public void testHttpsUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "https://example.com/page"));
    }

    @Test
    public void testHttpUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "http://example.com/page"));
    }

    @Test
    public void testFileUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "file:///android_asset/demo.html"));
    }

    @Test
    public void testJavascriptUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "javascript:alert(1)"));
    }

    @Test
    public void testDataUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView,
                "data:text/html;charset=utf-8,<h1>test</h1>"));
    }

    @Test
    public void testIntentUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView,
                "intent://scan/#Intent;scheme=zxing;end"));
    }

    @Test
    public void testEmptyUrl_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, ""));
    }

    // ---- Issue #175: encoded query params must NOT be decoded ----

    @Test
    public void testAlipayDeepLink_encodedQueryPreserved() {
        // The original bug: URLDecoder.decode turned %3d into = and %26 into &,
        // splitting a single query value into multiple parameters
        String alipayUrl = "alipays://platformapi/startApp?appId=20000125"
                + "&orderSuffix=h5_route_token%3d%22RZ13%22%26is_h5_route%3d%22true%22";
        assertFalse("Alipay URL must not be intercepted as a bridge URL",
                client.shouldOverrideUrlLoading(webView, alipayUrl));
    }

    @Test
    public void testWeChatDeepLink_encodedQueryPreserved() {
        String wechatUrl = "weixin://dl/business/?t=abc%3D123%26token%3Dxyz";
        assertFalse(client.shouldOverrideUrlLoading(webView, wechatUrl));
    }

    @Test
    public void testCallbackUrl_doubleEncoded_notIntercepted() {
        // redirect URL where the target is itself URL-encoded
        String url = "https://auth.example.com/callback"
                + "?redirect=https%3A%2F%2Fapp.com%2Fdone%3Fstatus%3Dok%26id%3D42";
        assertFalse(client.shouldOverrideUrlLoading(webView, url));
    }

    @Test
    public void testChineseEncodedUrl_notIntercepted() {
        String url = "https://example.com/search?q=%E6%B5%8B%E8%AF%95&page=1";
        assertFalse(client.shouldOverrideUrlLoading(webView, url));
    }

    @Test
    public void testPlusSignInQuery_notIntercepted() {
        String url = "https://example.com/search?q=hello+world";
        assertFalse(client.shouldOverrideUrlLoading(webView, url));
    }

    @Test
    public void testUrlWithHashFragment_encodedChars_notIntercepted() {
        String url = "https://example.com/page#section%20with%26special";
        assertFalse(client.shouldOverrideUrlLoading(webView, url));
    }

    // ---- Lookalike schemes should NOT be intercepted ----

    @Test
    public void testSimilarScheme_yyy_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "yyy://return/data"));
    }

    @Test
    public void testUppercaseYY_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "YY://return/data"));
    }

    @Test
    public void testYyInHttpsHost_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "https://yy.com/page"));
    }

    @Test
    public void testYyInHttpsPath_notIntercepted() {
        assertFalse(client.shouldOverrideUrlLoading(webView, "https://example.com/yy://fake"));
    }

    // ---- Custom WebViewClient delegation ----

    @Test
    public void testBridgeUrl_interceptedEvenWithCustomClient() {
        final boolean[] customCalled = {false};
        client.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                customCalled[0] = true;
                return false;
            }
        });

        // Bridge URL should be intercepted by bridge client, not delegated
        assertTrue(client.shouldOverrideUrlLoading(webView, "yy://return/_fetchQueue/"));
        assertFalse("Custom client should NOT be called for bridge URLs", customCalled[0]);
    }

    @Test
    public void testNonBridgeUrl_delegatedToCustomClient() {
        final boolean[] customCalled = {false};
        client.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                customCalled[0] = true;
                return true;
            }
        });

        // Non-bridge URL should be delegated to custom client
        boolean result = client.shouldOverrideUrlLoading(webView, "https://example.com/page");
        assertTrue("Custom client should be called for non-bridge URLs", customCalled[0]);
        assertTrue("Custom client's return value should be forwarded", result);
    }
}
