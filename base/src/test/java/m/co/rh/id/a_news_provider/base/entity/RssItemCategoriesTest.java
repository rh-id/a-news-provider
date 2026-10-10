package m.co.rh.id.a_news_provider.base.entity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the rss_item.categories storage contract: delimiter joining,
 * term cleanup (trim, delimiter strip, empty filtering), case-insensitive dedupe
 * (before the cap) and the 10-term cap.
 */
public class RssItemCategoriesTest {

    private static final String DELIMITER = String.valueOf(RssItemCategories.DELIMITER);

    @Test
    public void serializeNullReturnsNull() {
        assertNull(RssItemCategories.serialize(null));
    }

    @Test
    public void serializeEmptyListReturnsNull() {
        assertNull(RssItemCategories.serialize(new ArrayList<>()));
    }

    @Test
    public void serializeDropsBlankAndNullTerms() {
        String stored = RssItemCategories.serialize(
                Arrays.asList("Tech", null, "   ", "", "News"));
        assertEquals("Tech" + DELIMITER + "News", stored);
    }

    @Test
    public void serializeStripsDelimiterFromTerms() {
        String stored = RssItemCategories.serialize(Arrays.asList(
                "Tech" + DELIMITER + "News", "World"));
        assertEquals("TechNews" + DELIMITER + "World", stored);
    }

    @Test
    public void serializeTrimsTerms() {
        String stored = RssItemCategories.serialize(Arrays.asList("  Tech  ", "\tNews\n"));
        assertEquals("Tech" + DELIMITER + "News", stored);
    }

    @Test
    public void serializeDedupesCaseInsensitiveKeepingFirstSpelling() {
        String stored = RssItemCategories.serialize(Arrays.asList(
                "Tech", "TECH", "  tech  ", "News"));
        assertEquals("Tech" + DELIMITER + "News", stored);
    }

    @Test
    public void serializeDedupesLocaleIndependently() {
        // dotted capital I vs lowercase i must dedupe via equalsIgnoreCase without
        // any default-locale toLowerCase (Turkish İ problem)
        String stored = RssItemCategories.serialize(Arrays.asList("DİĞER", "diğer"));
        assertEquals("DİĞER", stored);
    }

    @Test
    public void serializeCapsAtTenAfterDedupe() {
        // 12 raw terms where one duplicate sits INSIDE the first 10 positions and one
        // after them: only a dedupe-BEFORE-cap implementation keeps all 10 unique
        // terms - a premature cap would freeze the wasted duplicate slot and lose
        // cat-10, so this genuinely discriminates the ordering
        List<String> rawTerms = new ArrayList<>(Arrays.asList(
                "cat-1", "cat-2", "cat-3", "cat-4", "cat-5",
                "cat-6", "CAT-3", "cat-7", "cat-8", "cat-9",
                "cat-10", "cat-2"));
        String stored = RssItemCategories.serialize(rawTerms);
        List<String> parsed = RssItemCategories.parse(stored);
        assertEquals(10, parsed.size());
        assertEquals("cat-1", parsed.get(0));
        assertEquals("cat-10", parsed.get(9));
        // the duplicate is gone and its slot was reclaimed by a real term
        assertTrue(!parsed.contains("CAT-3"));
    }

    @Test
    public void serializeCapsAtTenWhenTwelveUniqueTerms() {
        List<String> rawTerms = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            rawTerms.add("cat-" + i);
        }
        String stored = RssItemCategories.serialize(rawTerms);
        assertEquals(10, RssItemCategories.parse(stored).size());
    }

    @Test
    public void parseNullReturnsEmptyList() {
        assertTrue(RssItemCategories.parse(null).isEmpty());
    }

    @Test
    public void parseEmptyStringReturnsEmptyList() {
        assertTrue(RssItemCategories.parse("").isEmpty());
    }

    @Test
    public void parseRoundTripsStoredTerms() {
        String stored = "Tech" + DELIMITER + "Tech News" + DELIMITER + "World";
        List<String> parsed = RssItemCategories.parse(stored);
        assertEquals(Arrays.asList("Tech", "Tech News", "World"), parsed);
        assertEquals(stored, RssItemCategories.serialize(parsed));
    }
}
