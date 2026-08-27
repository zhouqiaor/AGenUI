package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetTemplateRegistry;
import com.amap.agenuiplayground.widget.WidgetTemplateRegistry.Category;
import com.amap.agenuiplayground.widget.WidgetTemplateRegistry.TemplateEntry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * R181-R185: WidgetTemplateRegistry unit tests.
 *
 * Tests the central registry — single source of truth for all widget templates.
 * Validates: entry count, categories, rotation, lookup, default template, button mapping.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetTemplateRegistryTest {

    // ===== Entry count and basic properties =====

    @Test
    public void TR01_registry_hasAtLeast10Entries() {
        List<TemplateEntry> entries = WidgetTemplateRegistry.getEntries();
        assertNotNull(entries);
        assertTrue("Registry should have at least 10 entries, got " + entries.size(),
                entries.size() >= 10);
    }

    @Test
    public void TR02_allEntries_haveNonEmptyName() {
        for (TemplateEntry e : WidgetTemplateRegistry.getEntries()) {
            assertNotNull("Entry name should not be null", e.getName());
            assertFalse("Entry name should not be empty", e.getName().isEmpty());
        }
    }

    @Test
    public void TR03_allEntries_haveCategory() {
        for (TemplateEntry e : WidgetTemplateRegistry.getEntries()) {
            assertNotNull("Entry " + e.getName() + " should have a category", e.getCategory());
        }
    }

    @Test
    public void TR04_entriesAreImmutable() {
        List<TemplateEntry> entries = WidgetTemplateRegistry.getEntries();
        try {
            entries.add(new TemplateEntry("hacked", 0, 0, Category.UTILITY));
            fail("Registry entries list should be immutable");
        } catch (UnsupportedOperationException e) {
            // Expected — list is immutable
        }
    }

    // ===== Template names array =====

    @Test
    public void TR05_templateNamesArray_matchesEntries() {
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        List<TemplateEntry> entries = WidgetTemplateRegistry.getEntries();
        assertEquals("Names array length should match entries size",
                entries.size(), names.length);
        for (int i = 0; i < names.length; i++) {
            assertEquals("Name at index " + i + " should match",
                    entries.get(i).getName(), names[i]);
        }
    }

    @Test
    public void TR06_templateNames_containsWeather() {
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        boolean hasWeather = false;
        for (String n : names) {
            if ("weather".equals(n)) hasWeather = true;
        }
        assertTrue("Registry should contain 'weather' template", hasWeather);
    }

    @Test
    public void TR07_templateNames_containsAllExpected() {
        String[] expected = {"weather", "agenda", "todo", "meeting", "poll",
                "classroom", "flashcard", "calendar", "note", "notecard"};
        String[] actual = WidgetTemplateRegistry.getTemplateNames();
        for (String e : expected) {
            boolean found = false;
            for (String a : actual) {
                if (e.equals(a)) { found = true; break; }
            }
            assertTrue("Registry should contain template: " + e, found);
        }
    }

    // ===== Default template =====

    @Test
    public void TR08_defaultTemplate_isWeather() {
        assertEquals("Default template should be 'weather'",
                "weather", WidgetTemplateRegistry.getDefaultTemplate());
    }

    // ===== getEntry lookup =====

    @Test
    public void TR09_getEntry_returnsEntryForValidName() {
        TemplateEntry entry = WidgetTemplateRegistry.getEntry("weather");
        assertNotNull("Should find entry for 'weather'", entry);
        assertEquals("weather", entry.getName());
        assertEquals(Category.WEATHER, entry.getCategory());
    }

    @Test
    public void TR10_getEntry_returnsNullForUnknown() {
        assertNull("Should return null for unknown template",
                WidgetTemplateRegistry.getEntry("nonexistent_template"));
    }

    @Test
    public void TR11_getEntry_returnsNullForNull() {
        assertNull("Should return null for null input",
                WidgetTemplateRegistry.getEntry(null));
    }

    @Test
    public void TR12_getEntry_returnsNullForEmpty() {
        assertNull("Should return null for empty string",
                WidgetTemplateRegistry.getEntry(""));
    }

    // ===== Category filtering =====

    @Test
    public void TR13_getByCategory_weatherContainsWeather() {
        List<String> weatherTemplates = WidgetTemplateRegistry.getByCategory(Category.WEATHER);
        assertTrue("Weather category should contain 'weather'",
                weatherTemplates.contains("weather"));
    }

    @Test
    public void TR14_getByCategory_educationHasClassroomAndFlashcard() {
        List<String> edu = WidgetTemplateRegistry.getByCategory(Category.EDUCATION);
        assertTrue("Education should contain 'classroom'", edu.contains("classroom"));
        assertTrue("Education should contain 'flashcard'", edu.contains("flashcard"));
    }

    @Test
    public void TR15_getByCategory_meetingHasMeetingAndPoll() {
        List<String> meeting = WidgetTemplateRegistry.getByCategory(Category.MEETING);
        assertTrue("Meeting should contain 'meeting'", meeting.contains("meeting"));
        assertTrue("Meeting should contain 'poll'", meeting.contains("poll"));
    }

    @Test
    public void TR16_getByCategory_productivityHasAgendaAndTodo() {
        List<String> prod = WidgetTemplateRegistry.getByCategory(Category.PRODUCTIVITY);
        assertTrue("Productivity should contain 'agenda'", prod.contains("agenda"));
        assertTrue("Productivity should contain 'todo'", prod.contains("todo"));
    }

    @Test
    public void TR17_getByCategory_emptyForNonExistentCategory() {
        // All defined categories should return non-empty lists
        for (Category c : Category.values()) {
            List<String> templates = WidgetTemplateRegistry.getByCategory(c);
            assertFalse("Category " + c + " should have at least one template",
                    templates.isEmpty());
        }
    }

    // ===== Template rotation =====

    @Test
    public void TR18_getNextTemplate_weatherReturnsAgenda() {
        assertEquals("agenda", WidgetTemplateRegistry.getNextTemplate("weather"));
    }

    @Test
    public void TR19_getNextTemplate_lastWrapsToFirst() {
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        String lastName = names[names.length - 1];
        String next = WidgetTemplateRegistry.getNextTemplate(lastName);
        assertEquals("Next after last should wrap to first",
                names[0], next);
    }

    @Test
    public void TR20_getNextTemplate_unknownReturnsDefault() {
        assertEquals("Unknown template should return default",
                WidgetTemplateRegistry.getDefaultTemplate(),
                WidgetTemplateRegistry.getNextTemplate("nonexistent"));
    }

    @Test
    public void TR21_getNextTemplate_fullCycle() {
        // Full cycle should return to start
        String start = WidgetTemplateRegistry.getDefaultTemplate();
        String current = start;
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        for (int i = 0; i < names.length; i++) {
            current = WidgetTemplateRegistry.getNextTemplate(current);
        }
        assertEquals("Full cycle should return to start", start, current);
    }

    // ===== Button IDs =====

    @Test
    public void TR22_buttonIds_nonEmpty() {
        int[] ids = WidgetTemplateRegistry.getButtonIds();
        assertTrue("Should have at least some button IDs", ids.length > 0);
    }

    @Test
    public void TR23_entriesWithButton_haveNonZeroId() {
        for (TemplateEntry e : WidgetTemplateRegistry.getEntries()) {
            if (e.hasButton()) {
                assertTrue("Entry " + e.getName() + " has button but ID is 0",
                        e.getButtonId() != 0);
            }
        }
    }

    @Test
    public void TR24_calendarAndNote_haveNoButton() {
        TemplateEntry calendar = WidgetTemplateRegistry.getEntry("calendar");
        if (calendar != null) {
            assertFalse("Calendar should not have a button", calendar.hasButton());
        }
        TemplateEntry note = WidgetTemplateRegistry.getEntry("note");
        if (note != null) {
            assertFalse("Note should not have a button", note.hasButton());
        }
    }

    // ===== No duplicate names =====

    @Test
    public void TR25_noDuplicateNames() {
        String[] names = WidgetTemplateRegistry.getTemplateNames();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String n : names) {
            assertFalse("Duplicate template name: " + n, seen.contains(n));
            seen.add(n);
        }
    }

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }
}
