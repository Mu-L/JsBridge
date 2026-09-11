package com.github.lzyzsd.jsbridge;

import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ValueCallback;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JsBridge辅助类,帮助集成JsBridge功能.
 *
 * @author ZhengAn
 * @date 2019-06-30
 */
public class BridgeHelper implements WebViewJavascriptBridge {

    private static final String TAG = "BridgeHelper";

    private static final String BRIDGE_JS = "WebViewJavascriptBridge.js";
    private Map<String, OnBridgeCallback> responseCallbacks = new ConcurrentHashMap<>();
    private Map<String, BridgeHandler> messageHandlers = new ConcurrentHashMap<>();
    private BridgeHandler defaultHandler = new DefaultHandler();

    private List<Message> startupMessage = new ArrayList<>();

    private long uniqueId = 0;

    private IWebView webView;

    // P1-2: Guard against duplicate JS injection
    private boolean mInjecting = false;

    // P1-3: Domain whitelist
    private BridgeConfig bridgeConfig;

    public BridgeHelper(IWebView webView) {
        this.webView = webView;
    }

    // PLACEHOLDER_REST

    // --- P1-3: Domain whitelist API ---

    public void setBridgeConfig(BridgeConfig config) {
        this.bridgeConfig = config;
    }

    public BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    public void setAllowedHosts(Set<String> hosts) {
        if (bridgeConfig == null) {
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.setAllowedHosts(hosts);
    }

    public void addAllowedHost(String host) {
        if (bridgeConfig == null) {
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.addAllowedHost(host);
    }

    private boolean isOriginAllowed() {
        if (bridgeConfig == null) return true;
        String url = webView.getWebView().getUrl();
        return bridgeConfig.isUrlAllowed(url);
    }

    /**
     * @param handler default handler,handle messages send by js without assigned handler name,
     *                if js message has handler name, it will be handled by named handlers registered by native
     */
    public void setDefaultHandler(BridgeHandler handler) {
        this.defaultHandler = handler;
    }

    /**
     * 获取到CallBackFunction data执行调用并且从数据集移除
     */
    private void handlerReturnData(String url) {
        String functionName = BridgeUtil.getFunctionFromReturnUrl(url);
        OnBridgeCallback f = responseCallbacks.get(functionName);
        String data = BridgeUtil.getDataFromReturnUrl(url);
        if (f != null) {
            f.onCallBack(data);
            responseCallbacks.remove(functionName);
        }
    }

    /**
     * 保存message到消息队列
     */
    private void doSend(String handlerName, String data, OnBridgeCallback responseCallback) {
        Message m = new Message();
        if (!TextUtils.isEmpty(data)) {
            m.setData(data);
        }
        if (responseCallback != null) {
            String callbackStr = String.format(BridgeUtil.CALLBACK_ID_FORMAT,
                    ++uniqueId + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
            responseCallbacks.put(callbackStr, responseCallback);
            m.setCallbackId(callbackStr);
        }
        if (!TextUtils.isEmpty(handlerName)) {
            m.setHandlerName(handlerName);
        }
        queueMessage(m);
    }

    private void queueMessage(Message m) {
        if (startupMessage != null) {
            startupMessage.add(m);
        } else {
            dispatchMessage(m);
        }
    }

    /**
     * 分发message 必须在主线程才分发成功
     */
    private void dispatchMessage(Message m) {
        String messageJson = m.toJson();
        messageJson = JSONObject.quote(messageJson);
        String javascriptCommand = String.format(BridgeUtil.JS_HANDLE_MESSAGE_FROM_JAVA, messageJson);
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            this.loadUrl(javascriptCommand);
        }
    }

    /**
     * 刷新消息队列
     */
    private void flushMessageQueue() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            loadUrl(BridgeUtil.JS_FETCH_QUEUE_FROM_JAVA, new OnBridgeCallback() {

                @Override
                public void onCallBack(String data) {
                    List<Message> list = null;
                    try {
                        list = Message.toArrayList(data);
                    } catch (Exception e) {
                        Log.w(TAG, e);
                        return;
                    }
                    if (list == null || list.isEmpty()) {
                        return;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        Message m = list.get(i);
                        String responseId = m.getResponseId();
                        if (!TextUtils.isEmpty(responseId)) {
                            OnBridgeCallback function = responseCallbacks.get(responseId);
                            String responseData = m.getResponseData();
                            if (function != null) {
                                function.onCallBack(responseData);
                            }
                            responseCallbacks.remove(responseId);
                        } else {
                            OnBridgeCallback responseFunction = null;
                            final String callbackId = m.getCallbackId();
                            if (!TextUtils.isEmpty(callbackId)) {
                                responseFunction = new OnBridgeCallback() {
                                    @Override
                                    public void onCallBack(String data) {
                                        Message responseMsg = new Message();
                                        responseMsg.setResponseId(callbackId);
                                        responseMsg.setResponseData(data);
                                        queueMessage(responseMsg);
                                    }
                                };
                            } else {
                                responseFunction = new OnBridgeCallback() {
                                    @Override
                                    public void onCallBack(String data) {
                                        // do nothing
                                    }
                                };
                            }
                            BridgeHandler handler;
                            if (!TextUtils.isEmpty(m.getHandlerName())) {
                                handler = messageHandlers.get(m.getHandlerName());
                            } else {
                                handler = defaultHandler;
                            }
                            if (handler != null) {
                                handler.handler(m.getData(), responseFunction);
                            }
                        }
                    }
                }
            });
        }
    }

    private void loadUrl(String jsUrl, OnBridgeCallback returnCallback) {
        this.loadUrl(jsUrl);
        responseCallbacks.put(BridgeUtil.parseFunctionName(jsUrl), returnCallback);
    }

    private void loadUrl(String jsUrl) {
        webView.loadUrl(jsUrl);
    }

    public void registerHandler(String handlerName, BridgeHandler handler) {
        if (handler != null) {
            messageHandlers.put(handlerName, handler);
        }
    }

    public void unregisterHandler(String handlerName) {
        if (handlerName != null) {
            messageHandlers.remove(handlerName);
        }
    }

    public void callHandler(String handlerName, String data, OnBridgeCallback callBack) {
        doSend(handlerName, data, callBack);
    }

    // --- P1-2: Timing-safe page lifecycle ---

    /**
     * Call when a new page starts loading. Resets injection state and restores
     * the message queue so messages are queued until JS is injected again.
     */
    public void onPageStarted() {
        mInjecting = false;
        startupMessage = new ArrayList<>();
    }

    /**
     * Call when the page finishes loading. Injects the bridge JS using
     * evaluateJavascript (with a completion callback) and then flushes
     * queued messages only after injection is confirmed.
     */
    public void onPageFinished() {
        if (mInjecting) return; // debounce multiple onPageFinished calls
        mInjecting = true;

        String jsContent = BridgeUtil.assetFile2Str(webView.getContext(), BRIDGE_JS);
        if (jsContent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.getWebView().evaluateJavascript(jsContent, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    flushStartupMessages();
                }
            });
        } else {
            // Fallback for API < 19
            if (jsContent != null) {
                loadUrl("javascript:" + jsContent);
            }
            flushStartupMessages();
        }
    }

    private void flushStartupMessages() {
        if (startupMessage != null) {
            for (Message m : startupMessage) {
                dispatchMessage(m);
            }
            startupMessage = null;
        }
    }

    // P1-3: Origin check in URL interception
    public boolean shouldOverrideUrlLoading(String url) {
        try {
            String replacedUrl = url.replaceAll("%(?![0-9a-fA-F]{2})", "%25").replaceAll("\\+", "%2B");
            url = URLDecoder.decode(replacedUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, e);
        }

        if (url.startsWith(BridgeUtil.YY_RETURN_DATA)) {
            if (!isOriginAllowed()) return true; // block silently
            handlerReturnData(url);
            return true;
        } else if (url.startsWith(BridgeUtil.YY_OVERRIDE_SCHEMA)) {
            if (!isOriginAllowed()) return true; // block silently
            flushMessageQueue();
            return true;
        }
        return false;
    }

    @Override
    public void sendToWeb(String data) {
        sendToWeb(data, (OnBridgeCallback) null);
    }

    @Override
    public void sendToWeb(String data, OnBridgeCallback responseCallback) {
        doSend(null, data, responseCallback);
    }

    @Override
    public void sendToWeb(String function, Object... values) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            String jsCommand = String.format(function, values);
            jsCommand = String.format(BridgeUtil.JAVASCRIPT_STR, jsCommand);
            loadUrl(jsCommand);
        }
    }

    public void sendResponse(Object data, String callbackId) {
        if (!TextUtils.isEmpty(callbackId)) {
            final Message response = new Message();
            response.responseId = callbackId;
            response.responseData = data instanceof String ? (String) data : new Gson().toJson(data);
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                dispatchMessage(response);
            } else {
                webView.getWebView().post(new Runnable() {
                    @Override
                    public void run() {
                        dispatchMessage(response);
                    }
                });
            }
        }
    }

    @Override
    public void responseFromWeb(String data, String callbackId) {
        sendResponse(data, callbackId);
    }

    public Map<String, OnBridgeCallback> getCallbacks() {
        return responseCallbacks;
    }

    // Visible for testing
    List<Message> getStartupMessage() {
        return startupMessage;
    }
}
