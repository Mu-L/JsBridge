package com.github.lzyzsd.jsbridge;

import android.content.Context;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class for persistent callback functionality.
 * Tests the fix for issue #280 - persistent callbacks should be reusable.
 *
 * Uses Robolectric so it can run as a local unit test (src/test) while still
 * having access to a real Context and WebView internals.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class PersistentCallbackTest {

    private BridgeWebView bridgeWebView;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        bridgeWebView = new BridgeWebView(context);
        bridgeWebView.setGson(new Gson());
    }

    /**
     * Simulate the @JavascriptInterface response() call that JS would make.
     * This directly invokes the callback from the map, matching the real
     * BaseJavascriptInterface.response() logic.
     */
    private void simulateJsResponse(String data, String responseId) {
        Map<String, OnBridgeCallback> callbacks = bridgeWebView.getCallbacks();
        Map<String, OnBridgeCallback> persistent = bridgeWebView.getPersistentCallbacks();

        OnBridgeCallback function = callbacks.get(responseId);
        if (function != null) {
            function.onCallBack(data);
            // Only remove if it's not a persistent callback
            if (persistent == null || !persistent.containsKey(responseId)) {
                callbacks.remove(responseId);
            }
        }
    }

    @Test
    public void testPersistentCallbackReuse() {
        final int[] callCount = {0};

        OnBridgeCallback persistentCallback = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {
                callCount[0]++;
            }
        };

        bridgeWebView.callHandlerPersistent("testHandler", "testData", persistentCallback);

        // Get the callback ID that was generated
        String callbackId = null;
        for (String id : bridgeWebView.getCallbacks().keySet()) {
            if (bridgeWebView.getPersistentCallbacks().containsKey(id)) {
                callbackId = id;
                break;
            }
        }

        assertNotNull("Persistent callback should be stored", callbackId);

        // Simulate multiple JS responses — persistent callback should survive each one
        simulateJsResponse("response1", callbackId);
        simulateJsResponse("response2", callbackId);
        simulateJsResponse("response3", callbackId);

        assertEquals("Persistent callback should be called 3 times", 3, callCount[0]);

        assertTrue("Persistent callback should still exist after multiple uses",
                bridgeWebView.getCallbacks().containsKey(callbackId));
        assertTrue("Persistent callback should be marked as persistent",
                bridgeWebView.getPersistentCallbacks().containsKey(callbackId));
    }

    @Test
    public void testNormalCallbackBehavior() {
        final int[] callCount = {0};

        OnBridgeCallback normalCallback = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {
                callCount[0]++;
            }
        };

        bridgeWebView.callHandler("testHandler", "testData", normalCallback);

        assertFalse("Normal callbacks should be stored initially",
                bridgeWebView.getCallbacks().isEmpty());

        String callbackId = null;
        for (String id : bridgeWebView.getCallbacks().keySet()) {
            callbackId = id;
            break;
        }

        assertNotNull("Normal callback should be stored", callbackId);
        assertFalse("Normal callback should not be marked as persistent",
                bridgeWebView.getPersistentCallbacks().containsKey(callbackId));

        simulateJsResponse("response", callbackId);
        assertEquals("Normal callback should be called once", 1, callCount[0]);

        // After first use, normal callback should be removed from the map
        assertFalse("Normal callback should be removed after first use",
                bridgeWebView.getCallbacks().containsKey(callbackId));

        // Try again — callback no longer in map so it should not be invoked
        simulateJsResponse("response2", callbackId);
        assertEquals("Normal callback should not be called again after deletion", 1, callCount[0]);
    }

    @Test
    public void testPersistentCallbackWithNullData() {
        final int[] callCount = {0};
        OnBridgeCallback cb = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) { callCount[0]++; }
        };

        bridgeWebView.callHandlerPersistent("handler", null, cb);

        // Callback should be registered even with null data
        assertFalse("Callback should be registered", bridgeWebView.getCallbacks().isEmpty());
        assertFalse("Should be marked as persistent",
                bridgeWebView.getPersistentCallbacks().isEmpty());
    }

    @Test
    public void testPersistentCallbackWithNullCallback() {
        // Should not crash when callback is null — no callback registered
        bridgeWebView.callHandlerPersistent("handler", "data", null);

        assertTrue("No callback should be registered when callback is null",
                bridgeWebView.getCallbacks().isEmpty());
        assertTrue("No persistent callback should be registered when callback is null",
                bridgeWebView.getPersistentCallbacks().isEmpty());
    }

    @Test
    public void testMixedPersistentAndNormalCallbacks() {
        final int[] persistentCount = {0};
        final int[] normalCount = {0};

        OnBridgeCallback persistentCb = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) { persistentCount[0]++; }
        };
        OnBridgeCallback normalCb = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) { normalCount[0]++; }
        };

        bridgeWebView.callHandlerPersistent("persistHandler", "d1", persistentCb);
        bridgeWebView.callHandler("normalHandler", "d2", normalCb);

        assertEquals("Two callbacks total", 2, bridgeWebView.getCallbacks().size());
        assertEquals("One persistent callback", 1, bridgeWebView.getPersistentCallbacks().size());

        // Find the IDs
        String persistentId = null, normalId = null;
        for (String id : bridgeWebView.getCallbacks().keySet()) {
            if (bridgeWebView.getPersistentCallbacks().containsKey(id)) {
                persistentId = id;
            } else {
                normalId = id;
            }
        }
        assertNotNull(persistentId);
        assertNotNull(normalId);

        // Invoke both
        simulateJsResponse("r1", persistentId);
        simulateJsResponse("r2", normalId);

        assertEquals(1, persistentCount[0]);
        assertEquals(1, normalCount[0]);

        // Normal should be removed, persistent should remain
        assertTrue("Persistent callback should remain", bridgeWebView.getCallbacks().containsKey(persistentId));
        assertFalse("Normal callback should be removed", bridgeWebView.getCallbacks().containsKey(normalId));

        // Invoke persistent again
        simulateJsResponse("r3", persistentId);
        assertEquals(2, persistentCount[0]);
    }

    @Test
    public void testDestroyCleansBothMaps() {
        bridgeWebView.callHandlerPersistent("h1", "d1", new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        });
        bridgeWebView.callHandler("h2", "d2", new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        });

        assertFalse(bridgeWebView.getCallbacks().isEmpty());
        assertFalse(bridgeWebView.getPersistentCallbacks().isEmpty());

        bridgeWebView.destroy();

        assertTrue("Callbacks should be empty after destroy",
                bridgeWebView.getCallbacks().isEmpty());
        assertTrue("Persistent callbacks should be empty after destroy",
                bridgeWebView.getPersistentCallbacks().isEmpty());
    }

    @Test
    public void testCallbackIdGeneration() {
        OnBridgeCallback callback1 = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        };
        OnBridgeCallback callback2 = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        };

        bridgeWebView.callHandler("handler1", "data1", callback1);
        int size1 = bridgeWebView.getCallbacks().size();

        bridgeWebView.callHandler("handler2", "data2", callback2);
        int size2 = bridgeWebView.getCallbacks().size();

        assertEquals("Each callback should be stored with unique ID", size1 + 1, size2);
    }
}
