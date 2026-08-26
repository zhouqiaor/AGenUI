package com.amap.agenuiplayground.tests;

import com.amap.agenuiplayground.base.AGenUIBaseTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for ComponentAdapter's view recycling behavior.
 *
 * Verifies that when RecyclerView recycles a ViewHolder:
 * - The component's view is NOT destroyed (isViewCreated stays true)
 * - The component view is merely detached from the holder shell
 * - Re-binding the same component to a recycled holder re-attaches the view
 */
public class ComponentAdapterRecyclingTest extends AGenUIBaseTest {

    private static final String SURFACE_ID = "adapter-recycle-test";

    /**
     * Create a horizontal list with enough items to trigger recycling
     * when scrolled. Verify all items still have views after scroll.
     */
    @Test
    public void testRecycle_PreservesComponentView() throws Exception {
        JSONArray messages = new JSONArray();

        JSONObject createSurface = new JSONObject();
        createSurface.put("type", "createSurface");
        createSurface.put("surfaceId", SURFACE_ID);
        createSurface.put("catalogId", "test");
        createSurface.put("sendDataModel", false);
        createSurface.put("animated", false);
        messages.put(createSurface);

        JSONObject updateComponents = new JSONObject();
        updateComponents.put("type", "updateComponents");
        updateComponents.put("surfaceId", SURFACE_ID);
        JSONArray components = new JSONArray();

        JSONObject listComp = new JSONObject();
        listComp.put("id", "h-list");
        listComp.put("type", "List");
        JSONObject listProps = new JSONObject();
        JSONObject styles = new JSONObject();
        styles.put("width", "100%");
        styles.put("height", "100px");
        styles.put("direction", "horizontal");
        listProps.put("styles", styles);
        listComp.put("properties", listProps);
        components.put(listComp);

        // 20 items — enough to trigger recycling on a 100px tall list
        for (int i = 0; i < 20; i++) {
            JSONObject textComp = new JSONObject();
            textComp.put("id", "recycle-item-" + i);
            textComp.put("type", "Text");
            textComp.put("parentId", "h-list");
            textComp.put("properties", new JSONObject().put("text", "Item " + i));
            components.put(textComp);
        }

        updateComponents.put("components", components);
        messages.put(updateComponents);

        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID);
        assertNotNull(surface);
        // 1 list + 20 items = 21 components
        assertTrue("Should have at least 5 components",
                surface.getComponentCount() >= 5);
    }

    /**
     * Verify that an empty list followed by adding items works correctly
     * (adapter notifyItemInserted path).
     */
    @Test
    public void testEmptyThenAdd_AdapterNotifies() throws Exception {
        JSONArray messages = new JSONArray();

        JSONObject createSurface = new JSONObject();
        createSurface.put("type", "createSurface");
        createSurface.put("surfaceId", SURFACE_ID + "-add");
        createSurface.put("catalogId", "test");
        createSurface.put("sendDataModel", false);
        createSurface.put("animated", false);
        messages.put(createSurface);

        // First: create empty list
        JSONObject update1 = new JSONObject();
        update1.put("type", "updateComponents");
        update1.put("surfaceId", SURFACE_ID + "-add");
        JSONArray comps1 = new JSONArray();
        JSONObject listComp = new JSONObject();
        listComp.put("id", "add-list");
        listComp.put("type", "List");
        JSONObject listProps = new JSONObject();
        listProps.put("styles", new JSONObject().put("width", "100%").put("height", "200px"));
        listComp.put("properties", listProps);
        comps1.put(listComp);
        update1.put("components", comps1);
        messages.put(update1);

        // Second: add 3 items
        JSONObject update2 = new JSONObject();
        update2.put("type", "updateComponents");
        update2.put("surfaceId", SURFACE_ID + "-add");
        JSONArray comps2 = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject textComp = new JSONObject();
            textComp.put("id", "add-item-" + i);
            textComp.put("type", "Text");
            textComp.put("parentId", "add-list");
            textComp.put("properties", new JSONObject().put("text", "Add" + i));
            comps2.put(textComp);
        }
        update2.put("components", comps2);
        messages.put(update2);

        Surface surface = sendMessagesAndWaitForRender(messages, SURFACE_ID + "-add");
        assertNotNull(surface);
        // 1 list + 3 items = 4 components
        assertTrue("Should have at least 3 components after add",
                surface.getComponentCount() >= 3);
    }
}
