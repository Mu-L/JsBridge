package com.github.lzyzsd.jsbridge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.lzyzsd.library.BuildConfig;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressLint("SetJavaScriptEnabled")
public class BridgeWebView extends WebView implements WebViewJavascriptBridge, BridgeWebViewClient.OnLoadJSListener {

    private static final String TAG = "BridgeWebView";
    private final int URL_MAX_CHARACTER_NUM = 2097152;
    private Map<String, OnBridgeCallback> mCallbacks = new ConcurrentHashMap<>();
    private Map<String, OnBridgeCallback> mPersistentCallbacks = new ConcurrentHashMap<>();

    // P1-1: Unified message model — was List<Object>
    private List<Message> mMessages = new ArrayList<>();

    private BridgeWebViewClient mClient;
    private long mUniqueId = 0;

    // P1-2: JS injection state management (replaces boolean mJSLoaded)
    enum JSLoadState { NOT_LOADED, LOADING, LOADED }
    volatile JSLoadState mJSLoadState = JSLoadState.NOT_LOADED;

    private Gson mGson;

    // P1-3: Domain whitelist configuration
    private BridgeConfig mBridgeConfig;

    public BridgeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BridgeWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public BridgeWebView(Context context) {
        super(context);
        init();
    }

    private void init() {
        clearCache(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        mClient = new BridgeWebViewClient(this);
        super.setWebViewClient(mClient);
    }

    public void setGson(Gson gson) {
        mGson = gson;
    }

    public boolean isJSLoaded() {
        return mJSLoadState == JSLoadState.LOADED;
    }

    public Map<String, OnBridgeCallback> getCallbacks() {
        return mCallbacks;
    }

    public Map<String, OnBridgeCallback> getPersistentCallbacks() {
        return mPersistentCallbacks;
    }

    // --- P1-3: Domain whitelist API ---

    public void setBridgeConfig(BridgeConfig config) {
        mBridgeConfig = config;
    }

    public BridgeConfig getBridgeConfig() {
        return mBridgeConfig;
    }

    /**
     * Set allowed hosts for the bridge. Only pages from these hosts can call
     * native methods. Pass null or empty to allow all (default).
     */
    public void setAllowedHosts(Set<String> hosts) {
        if (mBridgeConfig == null) {
            mBridgeConfig = new BridgeConfig();
        }
        mBridgeConfig.setAllowedHosts(hosts);
    }

    /**
     * Add a single allowed host (e.g. "example.com" or "*.example.com").
     */
    public void addAllowedHost(String host) {
        if (mBridgeConfig == null) {
            mBridgeConfig = new BridgeConfig();
        }
        mBridgeConfig.addAllowedHost(host);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient.setWebViewClient(client);
    }

    // --- P1-2: JS injection state callbacks ---

    @Override
    public void onPageReset() {
        mJSLoadState = JSLoadState.NOT_LOADED;
        // Restore message queue so messages sent before JS is ready are queued
        mMessages = new ArrayList<>();
    }

    @Override
    public void onJSInjected() {
        mJSLoadState = JSLoadState.LOADED;
        if (mMessages != null) {
            for (Message message : mMessages) {
                dispatchMessage(message);
            }
            mMessages = null;
        }
    }

    // --- WebViewJavascriptBridge implementation ---

    @Override
    public void sendToWeb(String data) {
        sendToWeb(data, (OnBridgeCallback) null);
    }

    @Override
    public void sendToWeb(String data, OnBridgeCallback responseCallback) {
        doSend(null, data, responseCallback);
    }

    /**
     * call javascript registered handler
     */
    public void callHandler(String handlerName, String data, OnBridgeCallback callBack) {
        doSend(handlerName, data, callBack);
    }

    /**
     * call javascript registered handler with persistent callback
     */
    public void callHandlerPersistent(String handlerName, String data, OnBridgeCallback callBack) {
        doSendPersistent(handlerName, data, callBack);
    }

    @Override
    public void sendToWeb(String function, Object... values) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            String jsCommand = String.format(function, values);
            jsCommand = String.format(BridgeUtil.JAVASCRIPT_STR, jsCommand);
            loadUrl(jsCommand);
        }
    }

    @Override
    public void responseFromWeb(String data, String callbackId) {
        sendResponse(data, callbackId);
    }

    // --- P1-1: Unified message model (Message replaces JSRequest/JSResponse) ---

    private void doSend(String handlerName, Object data, OnBridgeCallback responseCallback) {
        if (!(data instanceof String) && mGson == null) {
            return;
        }
        String dataStr = data instanceof String ? (String) data : mGson.toJson(data);
        String callbackId = null;
        if (responseCallback != null) {
            callbackId = String.format(BridgeUtil.CALLBACK_ID_FORMAT,
                    (++mUniqueId) + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
            mCallbacks.put(callbackId, responseCallback);
        }
        Message message = Message.createRequest(
                TextUtils.isEmpty(handlerName) ? null : handlerName,
                dataStr,
                callbackId
        );
        queueMessage(message);
    }

    private void doSendPersistent(String handlerName, Object data, OnBridgeCallback responseCallback) {
        if (!(data instanceof String) && mGson == null) {
            return;
        }
        String dataStr = data instanceof String ? (String) data : mGson.toJson(data);
        String callbackId = null;
        if (responseCallback != null) {
            callbackId = String.format(BridgeUtil.CALLBACK_ID_FORMAT,
                    (++mUniqueId) + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
            mCallbacks.put(callbackId, responseCallback);
            mPersistentCallbacks.put(callbackId, responseCallback);
        }
        Message message = Message.createRequest(
                TextUtils.isEmpty(handlerName) ? null : handlerName,
                dataStr,
                callbackId
        );
        queueMessage(message);
    }

    private void queueMessage(Message message) {
        if (mMessages != null) {
            mMessages.add(message);
        } else {
            dispatchMessage(message);
        }
    }

    /**
     * Dispatch a message to JS. Uses Message.toJson() directly — no Gson dependency.
     */
    private void dispatchMessage(Message message) {
        String messageJson = message.toJson();
        messageJson = JSONObject.quote(messageJson);
        String javascriptCommand = String.format(BridgeUtil.JS_HANDLE_MESSAGE_FROM_JAVA, messageJson);
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    && javascriptCommand.length() >= URL_MAX_CHARACTER_NUM) {
                this.evaluateJavascript(javascriptCommand, null);
            } else {
                this.loadUrl(javascriptCommand);
            }
        }
    }

    public void sendResponse(Object data, String callbackId) {
        if (!(data instanceof String) && mGson == null) {
            return;
        }
        if (!TextUtils.isEmpty(callbackId)) {
            String responseData = data instanceof String ? (String) data : mGson.toJson(data);
            final Message response = Message.createResponse(callbackId, responseData);
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                dispatchMessage(response);
            } else {
                post(new Runnable() {
                    @Override
                    public void run() {
                        dispatchMessage(response);
                    }
                });
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        mCallbacks.clear();
        mPersistentCallbacks.clear();
    }

    // --- P1-3: BaseJavascriptInterface with origin check ---

    public static abstract class BaseJavascriptInterface {

        private Map<String, OnBridgeCallback> mCallbacks;
        private Map<String, OnBridgeCallback> mPersistentCallbacks;
        private BridgeConfig mBridgeConfig;
        private WebView mWebView;

        public BaseJavascriptInterface(Map<String, OnBridgeCallback> callbacks) {
            this(callbacks, null, null, null);
        }

        public BaseJavascriptInterface(Map<String, OnBridgeCallback> callbacks,
                                       Map<String, OnBridgeCallback> persistentCallbacks) {
            this(callbacks, persistentCallbacks, null, null);
        }

        /**
         * Constructor with domain whitelist support.
         *
         * @param callbacks           callback map
         * @param persistentCallbacks persistent callback map (nullable)
         * @param bridgeConfig        bridge config with allowed hosts (nullable = allow all)
         * @param webView             the WebView, used to read the current URL for origin checks
         */
        public BaseJavascriptInterface(Map<String, OnBridgeCallback> callbacks,
                                       Map<String, OnBridgeCallback> persistentCallbacks,
                                       BridgeConfig bridgeConfig,
                                       WebView webView) {
            mCallbacks = callbacks;
            mPersistentCallbacks = persistentCallbacks;
            mBridgeConfig = bridgeConfig;
            mWebView = webView;
        }

        private boolean isOriginAllowed() {
            if (mBridgeConfig == null) return true;
            String url = (mWebView != null) ? mWebView.getUrl() : null;
            return mBridgeConfig.isUrlAllowed(url);
        }

        @JavascriptInterface
        public String send(String data, String callbackId) {
            if (!isOriginAllowed()) {
                Log.w(TAG, "Bridge send blocked: origin not in allowed hosts");
                return "";
            }
            Log.d(TAG, data + ", callbackId: " + callbackId + " " + Thread.currentThread().getName());
            return send(data);
        }

        @JavascriptInterface
        public void response(String data, String responseId) {
            if (!isOriginAllowed()) {
                Log.w(TAG, "Bridge response blocked: origin not in allowed hosts");
                return;
            }
            Log.d(TAG, data + ", responseId: " + responseId + " " + Thread.currentThread().getName());
            if (!TextUtils.isEmpty(responseId)) {
                OnBridgeCallback function = mCallbacks.get(responseId);
                if (function != null) {
                    function.onCallBack(data);
                    // Only remove if it's not a persistent callback
                    if (mPersistentCallbacks == null || !mPersistentCallbacks.containsKey(responseId)) {
                        mCallbacks.remove(responseId);
                    }
                }
            }
        }

        public abstract String send(String data);

        private static final String TAG = "BaseJavascriptInterface";
    }
}
