package com.github.lzyzsd.jsbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the Message class — factory methods, serialization, and edge cases.
 */
public class MessageTest {

    // --- createRequest factory ---

    @Test
    public void testCreateRequest_setsAllFields() {
        Message m = Message.createRequest("myHandler", "payload", "cb_1");
        assertEquals("myHandler", m.getHandlerName());
        assertEquals("payload", m.getData());
        assertEquals("cb_1", m.getCallbackId());
        assertNull(m.getResponseId());
        assertNull(m.getResponseData());
    }

    @Test
    public void testCreateRequest_allNullFields() {
        Message m = Message.createRequest(null, null, null);
        assertNull(m.getHandlerName());
        assertNull(m.getData());
        assertNull(m.getCallbackId());
        assertNull(m.getResponseId());
        assertNull(m.getResponseData());
    }

    @Test
    public void testCreateRequest_emptyStrings() {
        Message m = Message.createRequest("", "", "");
        assertEquals("", m.getHandlerName());
        assertEquals("", m.getData());
        assertEquals("", m.getCallbackId());
    }

    // --- createResponse factory ---

    @Test
    public void testCreateResponse_setsAllFields() {
        Message m = Message.createResponse("resp_1", "{\"ok\":true}");
        assertEquals("resp_1", m.getResponseId());
        assertEquals("{\"ok\":true}", m.getResponseData());
        assertNull(m.getHandlerName());
        assertNull(m.getData());
        assertNull(m.getCallbackId());
    }

    @Test
    public void testCreateResponse_nullFields() {
        Message m = Message.createResponse(null, null);
        assertNull(m.getResponseId());
        assertNull(m.getResponseData());
    }

    // --- toJson ---

    @Test
    public void testToJson_requestContainsCorrectFields() {
        Message m = Message.createRequest("handler", "data123", "cb_5");
        String json = m.toJson();
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("handler", obj.get("handlerName").getAsString());
        assertEquals("data123", obj.get("data").getAsString());
        assertEquals("cb_5", obj.get("callbackId").getAsString());
    }

    @Test
    public void testToJson_requestOmitsNullResponseFields() {
        Message m = Message.createRequest("h", "d", "c");
        String json = m.toJson();
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        // Gson default: null fields are omitted
        assertFalse("responseId should not appear", obj.has("responseId") && !obj.get("responseId").isJsonNull());
        assertFalse("responseData should not appear", obj.has("responseData") && !obj.get("responseData").isJsonNull());
    }

    @Test
    public void testToJson_responseOmitsNullRequestFields() {
        Message m = Message.createResponse("r1", "rdata");
        String json = m.toJson();
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("r1", obj.get("responseId").getAsString());
        assertEquals("rdata", obj.get("responseData").getAsString());
        assertFalse("handlerName should not appear", obj.has("handlerName") && !obj.get("handlerName").isJsonNull());
        assertFalse("callbackId should not appear", obj.has("callbackId") && !obj.get("callbackId").isJsonNull());
        assertFalse("data should not appear", obj.has("data") && !obj.get("data").isJsonNull());
    }

    @Test
    public void testToJson_allNullFieldsProducesValidJson() {
        Message m = Message.createRequest(null, null, null);
        String json = m.toJson();
        assertNotNull(json);
        // Should parse without error
        JsonParser.parseString(json);
    }

    // --- toArrayList ---

    @Test
    public void testToArrayList_validJsonArray() {
        String json = "[{\"handlerName\":\"h1\",\"data\":\"d1\",\"callbackId\":\"c1\"},"
                + "{\"responseId\":\"r2\",\"responseData\":\"rd2\"}]";
        List<Message> list = Message.toArrayList(json);
        assertNotNull(list);
        assertEquals(2, list.size());

        assertEquals("h1", list.get(0).getHandlerName());
        assertEquals("d1", list.get(0).getData());
        assertEquals("c1", list.get(0).getCallbackId());

        assertEquals("r2", list.get(1).getResponseId());
        assertEquals("rd2", list.get(1).getResponseData());
    }

    @Test
    public void testToArrayList_emptyArray() {
        List<Message> list = Message.toArrayList("[]");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test(expected = Exception.class)
    public void testToArrayList_malformedJson() {
        Message.toArrayList("{not valid[json");
    }

    @Test
    public void testToArrayList_singleElement() {
        String json = "[{\"handlerName\":\"test\"}]";
        List<Message> list = Message.toArrayList(json);
        assertEquals(1, list.size());
        assertEquals("test", list.get(0).getHandlerName());
    }

    // --- Special characters ---

    @Test
    public void testToJson_htmlTags() {
        Message m = Message.createRequest(null, "<script>alert('xss')</script>", null);
        String json = m.toJson();
        // Round-trip through Gson: parse back and verify content survives
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals("<script>alert('xss')</script>", parsed.getData());
    }

    @Test
    public void testToJson_newlinesAndTabs() {
        String data = "line1\nline2\ttab\rreturn";
        Message m = Message.createRequest(null, data, null);
        String json = m.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals(data, parsed.getData());
    }

    @Test
    public void testToJson_unicode() {
        String data = "你好世界 🌍 émojis ñ";
        Message m = Message.createRequest("unicodeHandler", data, null);
        String json = m.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals(data, parsed.getData());
        assertEquals("unicodeHandler", parsed.getHandlerName());
    }

    @Test
    public void testToJson_quotes() {
        String data = "He said \"hello\" and it's fine";
        Message m = Message.createRequest(null, data, null);
        String json = m.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals(data, parsed.getData());
    }

    @Test
    public void testToJson_backslashes() {
        String data = "path\\to\\file";
        Message m = Message.createRequest(null, data, null);
        String json = m.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals(data, parsed.getData());
    }

    // --- Long data ---

    @Test
    public void testToJson_veryLongData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("abcdefghij");
        }
        String longData = sb.toString(); // 100,000 chars
        Message m = Message.createRequest("bigHandler", longData, "cb_long");
        String json = m.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);
        assertEquals(longData, parsed.getData());
        assertEquals("bigHandler", parsed.getHandlerName());
        assertEquals("cb_long", parsed.getCallbackId());
    }

    // --- Setters / getters ---

    @Test
    public void testSettersOverrideFactoryValues() {
        Message m = Message.createRequest("original", "origData", "origCb");
        m.setHandlerName("updated");
        m.setData("newData");
        m.setCallbackId("newCb");
        m.setResponseId("rId");
        m.setResponseData("rData");

        assertEquals("updated", m.getHandlerName());
        assertEquals("newData", m.getData());
        assertEquals("newCb", m.getCallbackId());
        assertEquals("rId", m.getResponseId());
        assertEquals("rData", m.getResponseData());
    }

    // --- Round-trip serialization ---

    @Test
    public void testToJson_roundTrip_request() {
        Message original = Message.createRequest("myHandler", "payload123", "cb_42");
        String json = original.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);

        assertEquals(original.getHandlerName(), parsed.getHandlerName());
        assertEquals(original.getData(), parsed.getData());
        assertEquals(original.getCallbackId(), parsed.getCallbackId());
        assertNull(parsed.getResponseId());
        assertNull(parsed.getResponseData());
    }

    @Test
    public void testToJson_roundTrip_response() {
        Message original = Message.createResponse("resp_99", "{\"status\":\"ok\"}");
        String json = original.toJson();
        Message parsed = new Gson().fromJson(json, Message.class);

        assertEquals(original.getResponseId(), parsed.getResponseId());
        assertEquals(original.getResponseData(), parsed.getResponseData());
        assertNull(parsed.getHandlerName());
        assertNull(parsed.getData());
        assertNull(parsed.getCallbackId());
    }

    // --- toArrayList edge cases ---

    @Test
    public void testToArrayList_nullInput() {
        // Gson.fromJson(null, type) returns null
        List<Message> result = Message.toArrayList(null);
        assertNull(result);
    }

    @Test
    public void testToArrayList_emptyStringInput() {
        // Gson.fromJson("", type) returns null
        List<Message> result = Message.toArrayList("");
        assertNull(result);
    }
}
