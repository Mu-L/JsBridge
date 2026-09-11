package com.github.lzyzsd.jsbridge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class Message {
    public String responseId;
    public String responseData;
    public String callbackId;
    public String data;
    public String handlerName;

    /**
     * Create a request message to send to JS.
     *
     * @param handlerName JS handler name (nullable)
     * @param data        data payload (nullable)
     * @param callbackId  callback ID for response (nullable)
     * @return a new Message configured as a request
     */
    public static Message createRequest(String handlerName, String data, String callbackId) {
        Message m = new Message();
        m.handlerName = handlerName;
        m.data = data;
        m.callbackId = callbackId;
        return m;
    }

    /**
     * Create a response message to send back to JS.
     *
     * @param responseId   the original callback ID this is responding to
     * @param responseData the response payload
     * @return a new Message configured as a response
     */
    public static Message createResponse(String responseId, String responseData) {
        Message m = new Message();
        m.responseId = responseId;
        m.responseData = responseData;
        return m;
    }

    public static List<Message> toArrayList(String data) {
        return new Gson().fromJson(data, new TypeToken<List<Message>>(){}.getType());
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getResponseData() {
        return responseData;
    }

    public void setResponseData(String responseData) {
        this.responseData = responseData;
    }

    public String getCallbackId() {
        return callbackId;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setCallbackId(String callbackStr) {
        this.callbackId = callbackStr;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
