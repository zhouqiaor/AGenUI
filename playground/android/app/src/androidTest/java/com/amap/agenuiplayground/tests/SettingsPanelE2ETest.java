package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenuiplayground.base.AGenUIBaseTest;
import com.amap.agenuiplayground.base.TestFixtureLoader;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Settings Panel E2E 测试
 *
 * <p>验证设置面板的核心功能：
 * <ul>
 *   <li>01: Modal+Card+Column 基本结构 — 标题栏 + 内容 + 底部</li>
 *   <li>02: 两栏布局 — 左导航 + 右内容区 + List 动态模板渲染 switch 项</li>
 *   <li>03: Slider 列表 — List 动态模板渲染带滑块的设置项</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class SettingsPanelE2ETest extends AGenUIBaseTest {

    private TestFixtureLoader loader;

    @Override
    public void setUp() {
        super.setUp();
        loader = new TestFixtureLoader(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    // ==================== Test 01: Basic Modal+Card Structure ====================

    /**
     * 01_settings_basic: 验证 Modal+Card+Column 标题栏结构
     * 12 个组件：root(Column) + trigger-btn(Button) + trigger-text(Text) + modal(Modal)
     * + modal-body(Card) + dialog-body(Column) + title-bar(Row) + title-text(Text)
     * + close-btn(Button) + close-icon(Icon) + content-text(Text) + footer-text(Text)
     */
    @Test
    public void testSettings_01_basicStructure() throws Exception {
        String fixturePath = "settings_panel/01_settings_basic.json";
        JSONObject expect = loader.getExpect(fixturePath);

        // 使用逐条发送避免流式截断，然后用 sendAndWaitForRender 轮询等待组件稳定
        String surfaceId = loader.getSurfaceId(fixturePath);
        org.json.JSONArray messages = loader.getMessages(fixturePath);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            sb.append(messages.get(i).toString());
        }
        Surface surface = sendAndWaitForRender(sb.toString(), surfaceId);
        assertNotNull("Surface should be created: " + fixturePath, surface);

        // 验证组件数量
        int expectedCount = expect.getInt("componentCount");
        assertEquals("Component count should be " + expectedCount,
                expectedCount, surface.getComponentCount());

        // 验证核心组件类型
        A2UIComponent root = surface.getComponent("root");
        assertNotNull("root should exist", root);
        assertEquals("root type should be Column", "Column", root.getComponentType());

        A2UIComponent modal = surface.getComponent("modal-dialog");
        assertNotNull("modal-dialog should exist", modal);
        assertEquals("modal-dialog type should be Modal", "Modal", modal.getComponentType());

        A2UIComponent card = surface.getComponent("modal-body");
        assertNotNull("modal-body should exist", card);
        assertEquals("modal-body type should be Card", "Card", card.getComponentType());

        // 验证标题栏组件
        A2UIComponent titleBar = surface.getComponent("title-bar");
        assertNotNull("title-bar should exist", titleBar);
        assertEquals("title-bar type should be Row", "Row", titleBar.getComponentType());

        A2UIComponent titleText = surface.getComponent("title-text");
        assertNotNull("title-text should exist", titleText);
        assertEquals("title-text type should be Text", "Text", titleText.getComponentType());

        // 验证关闭按钮
        A2UIComponent closeBtn = surface.getComponent("close-btn");
        assertNotNull("close-btn should exist", closeBtn);
        assertEquals("close-btn type should be Button", "Button", closeBtn.getComponentType());
    }

    // ==================== Test 02: Two-Pane with Category Nav + List ====================

    /**
     * 02_settings_two_pane: 验证两栏布局 + List 动态 switch 项模板
     * 19 个组件（不含数据展开后的 List 项副本）
     */
    @Test
    public void testSettings_02_twoPaneWithList() throws Exception {
        String fixturePath = "settings_panel/02_settings_two_pane.json";
        JSONObject expect = loader.getExpect(fixturePath);

        String surfaceId = loader.getSurfaceId(fixturePath);
        org.json.JSONArray messages = loader.getMessages(fixturePath);
        Surface surface = sendMessagesAndWaitForRender(messages, surfaceId);
        assertNotNull("Surface should be created: " + fixturePath, surface);

        // 组件数量应 >= 期望值（List 数据项会额外创建组件实例）
        int expectedCount = expect.getInt("componentCount");
        int actualCount = surface.getComponentCount();
        assertTrue("Component count should be >= " + expectedCount
                        + " (template definitions + data-expanded items), actual=" + actualCount,
                actualCount >= expectedCount);

        // 验证两栏布局核心组件
        A2UIComponent twoPane = surface.getComponent("two-pane");
        assertNotNull("two-pane should exist", twoPane);
        assertEquals("two-pane type should be Row", "Row", twoPane.getComponentType());

        A2UIComponent leftNav = surface.getComponent("left-nav");
        assertNotNull("left-nav should exist", leftNav);
        assertEquals("left-nav type should be Column", "Column", leftNav.getComponentType());

        A2UIComponent rightContent = surface.getComponent("right-content");
        assertNotNull("right-content should exist", rightContent);
        assertEquals("right-content type should be Column", "Column", rightContent.getComponentType());

        // 验证分类导航项存在
        assertNotNull("cat-1 should exist", surface.getComponent("cat-1"));
        assertNotNull("cat-2 should exist", surface.getComponent("cat-2"));
        assertNotNull("cat-3 should exist", surface.getComponent("cat-3"));

        // 验证 List 组件存在
        A2UIComponent list = surface.getComponent("settings-list");
        assertNotNull("settings-list should exist", list);
        assertEquals("settings-list type should be List", "List", list.getComponentType());

        // 验证 switch-item-template 定义存在
        A2UIComponent template = surface.getComponent("switch-item-template");
        assertNotNull("switch-item-template should exist", template);
    }

    // ==================== Test 03: Slider List with Dynamic Data ====================

    /**
     * 03_settings_slider_items: 验证 List + Slider 动态模板
     * 9 个组件（不含数据展开的 List 项）
     */
    @Test
    public void testSettings_03_sliderList() throws Exception {
        String fixturePath = "settings_panel/03_settings_slider_items.json";
        JSONObject expect = loader.getExpect(fixturePath);

        String surfaceId = loader.getSurfaceId(fixturePath);
        org.json.JSONArray messages = loader.getMessages(fixturePath);
        Surface surface = sendMessagesAndWaitForRender(messages, surfaceId);
        assertNotNull("Surface should be created: " + fixturePath, surface);

        // 组件数量应 >= 期望值
        int expectedCount = expect.getInt("componentCount");
        int actualCount = surface.getComponentCount();
        assertTrue("Component count should be >= " + expectedCount
                        + " (template + data-expanded items), actual=" + actualCount,
                actualCount >= expectedCount);

        // 验证 Slider 模板定义存在
        A2UIComponent sliderControl = surface.getComponent("slider-item-control");
        assertNotNull("slider-item-control should exist", sliderControl);
        assertEquals("slider-item-control type should be Slider", "Slider",
                sliderControl.getComponentType());

        // 验证 List 组件存在
        A2UIComponent list = surface.getComponent("slider-list");
        assertNotNull("slider-list should exist", list);
        assertEquals("slider-list type should be List", "List", list.getComponentType());

        // 验证组件树完整性
        Map<String, A2UIComponent> tree = surface.getComponentTree();
        assertNotNull("Component tree should not be null", tree);
        assertNotNull("slider-item-template should be in tree", tree.get("slider-item-template"));
    }

    // ==================== Test 04: Component Tree Integrity ====================

    /**
     * 验证 settings 基本结构的完整组件树
     */
    @Test
    public void testSettings_04_componentTreeIntegrity() throws Exception {
        String fixturePath = "settings_panel/01_settings_basic.json";

        String surfaceId = loader.getSurfaceId(fixturePath);
        org.json.JSONArray messages = loader.getMessages(fixturePath);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            sb.append(messages.get(i).toString());
        }
        Surface surface = sendAndWaitForRender(sb.toString(), surfaceId);

        Map<String, A2UIComponent> tree = surface.getComponentTree();
        assertNotNull("Component tree should not be null", tree);

        // 验证所有关键组件都在树中
        String[] expectedIds = {"root", "trigger-btn", "trigger-btn-text", "modal-dialog",
                "modal-body", "dialog-body", "title-bar", "title-text",
                "close-btn", "close-icon", "content-text", "footer-text"};

        for (String id : expectedIds) {
            assertNotNull("Component '" + id + "' should be in tree", tree.get(id));
        }
    }

    // ==================== Test 05: Category Switch (Multi-Message DataModel Update) ====================

    /**
     * 04_settings_category_switch: 验证多消息 DataModel 更新（模拟 selectCategory）
     * 4 条消息：createSurface + updateComponents + updateDataModel(enterprise) + updateDataModel(sound)
     * 8 个模板组件 + 多次 DataModel 更新
     */
    @Test
    public void testSettings_05_categorySwitch() throws Exception {
        String fixturePath = "settings_panel/04_settings_category_switch.json";
        JSONObject expect = loader.getExpect(fixturePath);

        String surfaceId = loader.getSurfaceId(fixturePath);
        org.json.JSONArray messages = loader.getMessages(fixturePath);
        // Use sendMessagesAndWaitForRender: sends each message independently then polls for stability
        Surface surface = sendMessagesAndWaitForRender(messages, surfaceId);
        assertNotNull("Surface should be created: " + fixturePath, surface);

        // Verify component count
        int expectedCount = expect.getInt("componentCount");
        int actualCount = surface.getComponentCount();
        assertTrue("Component count should be >= " + expectedCount
                        + " (template + data), actual=" + actualCount,
                actualCount >= expectedCount);

        // Verify category buttons exist
        assertNotNull("cat-enterprise should exist", surface.getComponent("cat-enterprise"));
        assertNotNull("cat-sound should exist", surface.getComponent("cat-sound"));

        // Verify content area exists
        assertNotNull("right-content should exist", surface.getComponent("right-content"));
        assertNotNull("content-title should exist", surface.getComponent("content-title"));

        // Verify component tree integrity
        Map<String, A2UIComponent> tree = surface.getComponentTree();
        assertNotNull("Component tree should not be null", tree);

        org.json.JSONArray expectedIds = expect.getJSONArray("componentIds");
        for (int i = 0; i < expectedIds.length(); i++) {
            String id = expectedIds.getString(i);
            assertNotNull("Component '" + id + "' should be in tree", tree.get(id));
        }
    }
}
