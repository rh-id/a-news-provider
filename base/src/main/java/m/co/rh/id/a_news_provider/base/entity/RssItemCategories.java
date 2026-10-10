package m.co.rh.id.a_news_provider.base.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage contract for {@link RssItem#categories}: feed-provided category terms
 * are joined with {@link #DELIMITER} (the unit separator, chosen because it can
 * never appear inside a real term) into a single nullable TEXT column.
 * <p>
 * The delimiter constant plus the parse/serialize helpers live here in the base
 * module next to the entity, so no other module re-implements the contract.
 */
public final class RssItemCategories {

    /**
     * Delimiter used to join category terms inside {@link RssItem#categories}.
     * Written as an escape so no control character ever lands in the source.
     */
    public static final char DELIMITER = '\u001F';

    /**
     * Maximum number of category terms stored per rss item.
     */
    public static final int MAX_CATEGORY_COUNT = 10;

    private RssItemCategories() {
    }

    /**
     * Serializes the given raw category terms into the stored representation.
     * Each term is trimmed, stripped of the {@link #DELIMITER} and dropped when
     * empty after trimming; duplicates are removed case-insensitively (keeping
     * the first occurrence) and the result is capped at
     * {@link #MAX_CATEGORY_COUNT} after the dedupe.
     *
     * @param categories raw category terms as read from the feed, may be null
     * @return the joined terms, or null when no term survives the cleanup
     */
    public static String serialize(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        List<String> cleanedCategories = new ArrayList<>();
        for (String category : categories) {
            if (category == null) {
                continue;
            }
            String term = category.replace(String.valueOf(DELIMITER), "").trim();
            if (term.isEmpty()) {
                continue;
            }
            boolean duplicated = false;
            for (String existing : cleanedCategories) {
                if (existing.equalsIgnoreCase(term)) {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated) {
                cleanedCategories.add(term);
            }
        }
        if (cleanedCategories.isEmpty()) {
            return null;
        }
        if (cleanedCategories.size() > MAX_CATEGORY_COUNT) {
            cleanedCategories = cleanedCategories.subList(0, MAX_CATEGORY_COUNT);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < cleanedCategories.size(); i++) {
            if (i > 0) {
                stringBuilder.append(DELIMITER);
            }
            stringBuilder.append(cleanedCategories.get(i));
        }
        return stringBuilder.toString();
    }

    /**
     * Parses the stored representation back into the individual category terms.
     *
     * @param stored the stored joined terms, may be null
     * @return the parsed terms in stored order, empty when stored is null/empty
     */
    public static List<String> parse(String stored) {
        List<String> categories = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return categories;
        }
        String[] splitTerms = stored.split(String.valueOf(DELIMITER));
        for (String splitTerm : splitTerms) {
            String term = splitTerm.trim();
            if (!term.isEmpty()) {
                categories.add(term);
            }
        }
        return categories;
    }
}
