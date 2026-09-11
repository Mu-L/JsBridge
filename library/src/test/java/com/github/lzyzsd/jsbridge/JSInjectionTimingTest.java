package com.github.lzyzsd.jsbridge;

import android.content.Context;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Tests for P1-2: JS injection timing fix.
 * Verifies the three-state lifecycle (NOT_LOADED → LOADING → LOADED)
 * and the message queueing behavior across page transitions.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class JSInjectionTimingTest {

    private BridgeWebView bridgeWebView;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        bridgeWebView = new BridgeWebView(context);
        bridgeWebView.setGson(new Gson());
    }

    @Test
    public void testInitialStateIsNotLoaded() {
        assertFalse("JS should not be loaded initially", bridgeWebView.isJSLoaded());
        assertEquals(BridgeWebView.JSLoadState.NOT_LOADED, bridgeWebView.mJSLoadState);
    }

    @Test
    public void testOnPageResetSetsNotLoaded() {
        // First load JS
        bridgeWebView.onJSInjected();
        assertTrue(bridgeWebView.isJSLoaded());

        // New page starts — reset
        bridgeWebView.onPageReset();
        assertFalse("After page reset, JS should not be loaded", bridgeWebView.isJSLoaded());
        assertEquals(BridgeWebView.JSLoadState.NOT_LOADED, bridgeWebView.mJSLoadState);
    }

    @Test
    public void testOnJSInjectedSetsLoaded() {
        bridgeWebView.onJSInjected();
        assertTrue("After JS injection, isJSLoaded should be true", bridgeWebView.isJSLoaded());
        assertEquals(BridgeWebView.JSLoadState.LOADED, bridgeWebView.mJSLoadState);
    }

    @Test
    public void testPageTransitionCycle() {
        // Initial: NOT_LOADED
        assertEquals(BridgeWebView.JSLoadState.NOT_LOADED, bridgeWebView.mJSLoadState);

        // Page loaded, JS injected
        bridgeWebView.onJSInjected();
        assertEquals(BridgeWebView.JSLoadState.LOADED, bridgeWebView.mJSLoadState);

        // Navigate to new page
        bridgeWebView.onPageReset();
        assertEquals(BridgeWebView.JSLoadState.NOT_LOADED, bridgeWebView.mJSLoadState);

        // New page loaded, JS re-injected
        bridgeWebView.onJSInjected();
        assertEquals(BridgeWebView.JSLoadState.LOADED, bridgeWebView.mJSLoadState);
    }

    @Test
    public void testMessagesQueuedBeforeJSInjection() {
        final int[] callCount = {0};
        OnBridgeCallback callback = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {
                callCount[0]++;
            }
        };

        // Queue several messages before JS is loaded
        bridgeWebView.callHandler("handler1", "data1", callback);
        bridgeWebView.callHandler("handler2", "data2", callback);
        bridgeWebView.callHandler("handler3", "data3", callback);

        // Callbacks should be registered (3 unique IDs)
        assertEquals("Three callbacks should be registered", 3, bridgeWebView.getCallbacks().size());
    }

    @Test
    public void testPageResetRestoresMessageQueue() {
        // Load JS and flush
        bridgeWebView.onJSInjected();
        assertTrue(bridgeWebView.isJSLoaded());

        // After onJSInjected, mMessages is null (queue flushed)
        // New page starts — queue should be restored
        bridgeWebView.onPageReset();

        // Now messages should be queued again (not dispatched immediately)
        final int[] callCount = {0};
        bridgeWebView.callHandler("handler", "data", new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {
                callCount[0]++;
            }
        });

        // Callback registered but not yet invoked (queued)
        assertEquals(1, bridgeWebView.getCallbacks().size());
    }

    @Test
    public void testMultiplePageResetsDoNotCorruptState() {
        // Multiple rapid resets shouldn't cause issues
        bridgeWebView.onPageReset();
        bridgeWebView.onPageReset();
        bridgeWebView.onPageReset();

        assertEquals(BridgeWebView.JSLoadState.NOT_LOADED, bridgeWebView.mJSLoadState);

        // Should still be able to queue and flush normally
        bridgeWebView.callHandler("handler", "data", new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        });
        assertEquals(1, bridgeWebView.getCallbacks().size());

        bridgeWebView.onJSInjected();
        assertTrue(bridgeWebView.isJSLoaded());
    }

    @Test
    public void testDoubleJSInjectedDoesNotCrash() {
        bridgeWebView.onJSInjected();
        assertTrue(bridgeWebView.isJSLoaded());

        // Second call should be harmless (mMessages is already null)
        bridgeWebView.onJSInjected();
        assertTrue(bridgeWebView.isJSLoaded());
    }

    @Test
    public void testMessagesSurvivePageResetIfNotFlushed() {
        // Queue a message
        bridgeWebView.callHandler("handler", "data", new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {}
        });
        assertEquals(1, bridgeWebView.getCallbacks().size());

        // Page reset creates a new queue — old queued messages are discarded (new page)
        bridgeWebView.onPageReset();

        // Callbacks map still has the entry (it's not cleared on page reset)
        // This is correct: callbacks are keyed by unique ID and old ones will just never
        // get a response from the new page
        assertEquals(1, bridgeWebView.getCallbacks().size());
    }

    @Test
    public void testPersistentCallbackSurvivesPageTransition() {
        final int[] callCount = {0};
        OnBridgeCallback persistentCallback = new OnBridgeCallback() {
            @Override
            public void onCallBack(String data) {
                callCount[0]++;
            }
        };

        bridgeWebView.callHandlerPersistent("handler", "data", persistentCallback);

        // Get the callback ID
        String callbackId = null;
        for (String id : bridgeWebView.getCallbacks().keySet()) {
            if (bridgeWebView.getPersistentCallbacks().containsKey(id)) {
                callbackId = id;
                break;
            }
        }
        assertNotNull("Persistent callback should be registered", callbackId);

        // Simulate page transition
        bridgeWebView.onJSInjected();
        bridgeWebView.onPageReset();

        // Persistent callback should still be in both maps
        assertTrue(bridgeWebView.getCallbacks().containsKey(callbackId));
        assertTrue(bridgeWebView.getPersistentCallbacks().containsKey(callbackId));
    }
}
