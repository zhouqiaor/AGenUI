package com.amap.agenuiplayground.widget;

import com.amap.agenuiplayground.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for all widget templates — the single source of truth.
 *
 * <p>Adding a new template requires only adding one entry here; all other
 * classes (preloader, prerenderer, button wiring, template bar) read from
 * this registry automatically.
 *
 * <h3>Previous problem (P0)</h3>
 * Adding a template required modifying 5+ files:
 * <ol>
 *   <li>{@code WidgetProtocolTemplates.AVAILABLE_TEMPLATES} array</li>
 *   <li>{@code WidgetProtocolTemplates.TEMPLATE_BUTTON_IDS} array</li>
 *   <li>{@code WidgetTemplatePreloader} preload list</li>
 *   <li>Layout XML (add a button)</li>
 *   <li>{@code strings.xml} (add template name)</li>
 * </ol>
 *
 * <h3>Solution</h3>
 * A single immutable list of {@link TemplateEntry} objects. Each entry declares:
 * <ul>
 *   <li>{@code name} — template file name (e.g. "weather")</li>
 *   <li>{@code buttonId} — R.id for the template bar button (0 if none)</li>
 *   <li>{@code displayNameRes} — string resource for display name</li>
 *   <li>{@code category} — logical grouping (weather, productivity, etc.)</li>
 * </ul>
 *
 * <p>{@link WidgetProtocolTemplates} now delegates to this registry for
 * {@link WidgetProtocolTemplates#AVAILABLE_TEMPLATES} and
 * {@link WidgetProtocolTemplates#TEMPLATE_BUTTON_IDS}.
 */
public final class WidgetTemplateRegistry {

    private WidgetTemplateRegistry() { } // utility class

    /**
     * Logical category for a template, used for filtering and UI grouping.
     */
    public enum Category {
        WEATHER,
        PRODUCTIVITY,
        EDUCATION,
        MEETING,
        UTILITY
    }

    /**
     * Immutable description of a registered widget template.
     */
    public static final class TemplateEntry {
        private final String name;
        private final int buttonId;
        private final int displayNameRes;
        private final Category category;

        /**
         * @param name           Template file name (without .json extension)
         * @param buttonId       R.id for template bar button, or 0 if no button
         * @param displayNameRes String resource ID for display name
         * @param category       Logical category
         */
        public TemplateEntry(String name, int buttonId, int displayNameRes, Category category) {
            this.name = name;
            this.buttonId = buttonId;
            this.displayNameRes = displayNameRes;
            this.category = category;
        }

        public String getName() { return name; }
        public int getButtonId() { return buttonId; }
        public int getDisplayNameRes() { return displayNameRes; }
        public Category getCategory() { return category; }
        public boolean hasButton() { return buttonId != 0; }
    }

    // ===== Registry data =====
    // To add a new template: add one entry here. That's it.
    // The template JSON file must exist in assets/widget_templates/.
    // The button id must exist in the layout XML (if hasButton() is true).
    // The display name string must exist in strings.xml.

    private static final List<TemplateEntry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new TemplateEntry("weather",    R.id.btnTemplateWeather,    R.string.widget_template_weather,    Category.WEATHER),
            new TemplateEntry("agenda",    R.id.btnTemplateAgenda,     R.string.widget_template_agenda,     Category.PRODUCTIVITY),
            new TemplateEntry("todo",      R.id.btnTemplateTodo,       R.string.widget_template_todo,       Category.PRODUCTIVITY),
            new TemplateEntry("meeting",   R.id.btnTemplateMeeting,    R.string.widget_template_meeting,     Category.MEETING),
            new TemplateEntry("poll",      R.id.btnTemplatePoll,       R.string.widget_template_poll,        Category.MEETING),
            new TemplateEntry("classroom", R.id.btnTemplateClassroom,   R.string.widget_template_classroom,   Category.EDUCATION),
            new TemplateEntry("flashcard", R.id.btnTemplateFlashcard,  R.string.widget_template_flashcard,   Category.EDUCATION),
            new TemplateEntry("calendar",  0,                           R.string.widget_template_calendar,   Category.PRODUCTIVITY),
            new TemplateEntry("note",      0,                           R.string.widget_template_note,       Category.UTILITY),
            new TemplateEntry("notecard", 0,                           R.string.widget_template_notecard,    Category.EDUCATION)
    ));

    // ===== Derived views (computed once, cached) =====

    private static final String[] TEMPLATE_NAMES;
    private static final int[] TEMPLATE_BUTTON_IDS;
    private static final String DEFAULT_TEMPLATE;

    static {
        List<String> names = new ArrayList<>(ENTRIES.size());
        List<Integer> buttonIds = new ArrayList<>();
        for (TemplateEntry e : ENTRIES) {
            names.add(e.getName());
            if (e.hasButton()) {
                buttonIds.add(e.getButtonId());
            }
        }
        TEMPLATE_NAMES = names.toArray(new String[0]);
        TEMPLATE_BUTTON_IDS = new int[buttonIds.size()];
        for (int i = 0; i < buttonIds.size(); i++) {
            TEMPLATE_BUTTON_IDS[i] = buttonIds.get(i);
        }
        DEFAULT_TEMPLATE = ENTRIES.get(0).getName(); // "weather"
    }

    /**
     * @return All registered template entries (immutable).
     */
    public static List<TemplateEntry> getEntries() {
        return ENTRIES;
    }

    /**
     * @return Array of all template names (convenience for legacy code).
     */
    public static String[] getTemplateNames() {
        return TEMPLATE_NAMES;
    }

    /**
     * @return Array of button IDs for templates that have buttons (convenience for legacy code).
     */
    public static int[] getButtonIds() {
        return TEMPLATE_BUTTON_IDS;
    }

    /**
     * @return The default template name.
     */
    public static String getDefaultTemplate() {
        return DEFAULT_TEMPLATE;
    }

    /**
     * @return The entry for the given template name, or null if not found.
     */
    public static TemplateEntry getEntry(String templateName) {
        for (TemplateEntry e : ENTRIES) {
            if (e.getName().equals(templateName)) return e;
        }
        return null;
    }

    /**
     * @return All template names in the given category.
     */
    public static List<String> getByCategory(Category category) {
        List<String> result = new ArrayList<>();
        for (TemplateEntry e : ENTRIES) {
            if (e.getCategory() == category) result.add(e.getName());
        }
        return result;
    }

    /**
     * @return The next template name in the registry order, wrapping around.
     */
    public static String getNextTemplate(String current) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).getName().equals(current)) {
                int next = (i + 1) % ENTRIES.size();
                return ENTRIES.get(next).getName();
            }
        }
        return DEFAULT_TEMPLATE;
    }
}
