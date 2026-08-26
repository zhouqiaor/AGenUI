package com.amap.agenuiplayground.tests;

import com.amap.agenuiplayground.base.AGenUIBaseTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Edge case tests for built-in components (R76-80).
 * Tests Modal, Tabs, Carousel, RichText, Table rendering.
 */
public class ComponentEdgeCaseTest extends AGenUIBaseTest {

    private static final String SURFACE_ID = "edge-case-test";

    private JSONArray createSurfaceAndComponents(String suffix, JSONArray components) throws Exception {
        JSONArray messages = new JSONArray();
        JSONObject create = new JSONObject();
        create.put("type", "createSurface");
        create.put("surfaceId", SURFACE_ID + suffix);
        create.put("catalogId", "test");
        create.put("sendDataModel", false);
        create.put("animated", false);
        messages.put(create);

        JSONObject update = new JSONObject();
        update.put("type", "updateComponents");
        update.put("surfaceId", SURFACE_ID + suffix);
        update.put("components", components);
        messages.put(update);

        return messages;
    }

    @Test
    public void testModalComponent_BasicRender() throws Exception {
        JSONArray components = new JSONArray();
        JSONObject modal = new JSONObject();
        modal.put("id", "test-modal");
        modal.put("type", "Modal");
        JSONObject props = new JSONObject();
        props.put("visible", true);
        props.put("styles", new JSONObject().put("width", "300px").put("height", "200px"));
        modal.put("properties", props);
        modal.put("children", new JSONArray()
            .put(new JSONObject().put("id", "modal-text").put("type", "Text")
                .put("properties", new JSONObject().put("text", "Modal content"))));
        components.put(modal);

        JSONArray messages = createSurfaceAndComponents("-modal", components);
        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-modal");
        assertNotNull(surface);
        assertTrue(surface.getComponentCount() >= 1);
    }

    @Test
    public void testTabsComponent_BasicRender() throws Exception {
        JSONArray components = new JSONArray();
        JSONObject tabs = new JSONObject();
        tabs.put("id", "test-tabs");
        tabs.put("type", "Tabs");
        JSONObject props = new JSONObject();
        props.put("selectedIndex", 0);
        JSONArray tabsData = new JSONArray()
            .put(new JSONObject().put("title", "Tab 1"))
            .put(new JSONObject().put("title", "Tab 2"));
        props.put("tabs", tabsData);
        props.put("styles", new JSONObject().put("width", "100%").put("height", "300px"));
        tabs.put("properties", props);
        components.put(tabs);

        JSONArray messages = createSurfaceAndComponents("-tabs", components);
        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-tabs");
        assertNotNull(surface);
        assertTrue(surface.getComponentCount() >= 1);
    }

    @Test
    public void testCarouselComponent_BasicRender() throws Exception {
        JSONArray components = new JSONArray();
        JSONObject carousel = new JSONObject();
        carousel.put("id", "test-carousel");
        carousel.put("type", "Carousel");
        JSONObject props = new JSONObject();
        props.put("autoPlay", false);
        props.put("styles", new JSONObject().put("width", "100%").put("height", "200px"));
        carousel.put("properties", props);
        carousel.put("children", new JSONArray()
            .put(new JSONObject().put("id", "slide-1").put("type", "Text")
                .put("properties", new JSONObject().put("text", "Slide 1")))
            .put(new JSONObject().put("id", "slide-2").put("type", "Text")
                .put("properties", new JSONObject().put("text", "Slide 2"))));
        components.put(carousel);

        JSONArray messages = createSurfaceAndComponents("-carousel", components);
        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-carousel");
        assertNotNull(surface);
        assertTrue(surface.getComponentCount() >= 2);
    }

    @Test
    public void testRichTextComponent_BasicRender() throws Exception {
        JSONArray components = new JSONArray();
        JSONObject richText = new JSONObject();
        richText.put("id", "test-richtext");
        richText.put("type", "RichText");
        JSONObject props = new JSONObject();
        JSONObject segments = new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", "Hello "))
            .put(new JSONObject().put("type", "link").put("text", "World").put("href", "https://example.com"));
        props.put("segments", segments);
        richText.put("properties", props);
        components.put(richText);

        JSONArray messages = createSurfaceAndComponents("-richtext", components);
        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-richtext");
        assertNotNull(surface);
        assertTrue(surface.getComponentCount() >= 1);
    }

    @Test
    public void testTableComponent_BasicRender() throws Exception {
        JSONArray components = new JSONArray();
        JSONObject table = new JSONObject();
        table.put("id", "test-table");
        table.put("type", "Table");
        JSONObject props = new JSONObject();
        JSONArray columns = new JSONArray()
            .put(new JSONObject().put("key", "name").put("title", "Name"))
            .put(new JSONObject().put("key", "age").put("title", "Age"));
        props.put("columns", columns);
        props.put("styles", new JSONObject().put("width", "100%").put("height", "200px"));
        table.put("properties", props);
        components.put(table);

        JSONArray messages = createSurfaceAndComponents("-table", components);
        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-table");
        assertNotNull(surface);
        assertTrue(surface.getComponentCount() >= 1);
    }
}
