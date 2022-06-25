package m.co.rh.id.a_news_provider.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * RSS Feed {@link ContentProvider} will provide read access to RSS feeds and articles.
 * <p>
 * URI is like this:
 * <ul>
 *     <li>/feeds - will return a list of feeds, each of which is a pair of an id and a name.</li>
 *     <li>/articles - will return a list of all articles regardless of feed.</li>
 *     <li>/articles/<feedid> - will return a list of articles, each of which has an id, a title and a text.</li>
 * </ul>
 */
public abstract class AbstractRSSContentProvider extends ContentProvider {

    /**
     * The column names in the response to the "feeds" URI.
     */
    private final String[] FEED_COL_NAMES = {"id", "title"};

    /**
     * The column names in the response to the "articles/*" URI.
     */
    private final String[] ARTICLE_COL_NAMES = {"id", "title", "text"};


    private static final int URI_FEED_LIST = 1;
    private static final int URI_ARTICLE_LIST = 2;
    private static final int URI_ALL_ARTICLES = 3;

    private final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);


    public AbstractRSSContentProvider() {
        uriMatcher.addURI(getAuthority(), "feeds", URI_FEED_LIST);
        uriMatcher.addURI(getAuthority(), "articles/*", URI_ARTICLE_LIST);
        uriMatcher.addURI(getAuthority(), "articles", URI_ALL_ARTICLES);
    }


    /**
     * Returns the name of the {@link ContentProvider}'s authority for use in the URL parser. This has to be the same as the
     * <code>android:authorities</code> attribute of the provider declaration in the manifest.
     *
     * @return the name of the authority, which is never null
     */
    @NonNull
    protected abstract String getAuthority();


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not implemented");
    }


    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case URI_FEED_LIST: {
                return "vnd.android.cursor.dir/vnd.rssprovider.feeds";
            }
            case URI_ARTICLE_LIST: {
                return "vnd.android.cursor.dir/vnd.rssprovider.items";
            }
            case URI_ALL_ARTICLES: {
                return "vnd.android.cursor.dir/vnd.rssprovider.items";
            }
        }
        return null;
    }


    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not implemented");
    }


    @Override
    public boolean onCreate() {
        return false;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        switch (uriMatcher.match(uri)) {
            case URI_FEED_LIST: {
                return feedsCursor();
            }
            case URI_ARTICLE_LIST: {
                return articleCursor(uri.getLastPathSegment());
            }
            case URI_ALL_ARTICLES: {
                return articleCursor(null);
            }
        }

        return null;
    }


    private Cursor feedsCursor() {
        List<Feed> feeds = getFeeds();

        return new AbstractCursor() {


            @Override
            public int getCount() {
                return feeds.size();
            }


            @Override
            public String[] getColumnNames() {
                return FEED_COL_NAMES;
            }


            @Override
            public String getString(int column) {
                final Feed feed = feeds.get(getPosition());
                switch (column) {
                    case 0: {
                        return feed.id;
                    }
                    case 1: {
                        return feed.title;
                    }
                }
                return null;
            }


            @Override
            public short getShort(int column) {
                return 0;
            }


            @Override
            public int getInt(int column) {
                return 0;
            }


            @Override
            public long getLong(int column) {
                return 0;
            }


            @Override
            public float getFloat(int column) {
                return 0.0f;
            }


            @Override
            public double getDouble(int column) {
                return 0.0d;
            }


            @Override
            public boolean isNull(int column) {
                return column < FEED_COL_NAMES.length;
            }
        };
    }


    private Cursor articleCursor(String feedId) {
        List<Article> articles = getArticles(feedId);

        return new AbstractCursor() {


            @Override
            public int getCount() {
                return articles.size();
            }


            @Override
            public String[] getColumnNames() {
                return ARTICLE_COL_NAMES;
            }


            @Override
            public String getString(int column) {
                final Article item = articles.get(getPosition());
                switch (column) {
                    case 0: {
                        return item.id;
                    }
                    case 1: {
                        return item.title;
                    }

                    case 2: {
                        return item.text;
                    }
                }
                return null;
            }


            @Override
            public short getShort(int column) {
                return 0;
            }


            @Override
            public int getInt(int column) {
                return 0;
            }


            @Override
            public long getLong(int column) {
                return 0;
            }


            @Override
            public float getFloat(int column) {
                return 0.0f;
            }


            @Override
            public double getDouble(int column) {
                return 0.0d;
            }


            @Override
            public boolean isNull(int column) {
                return column < ARTICLE_COL_NAMES.length;
            }
        };
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not implemented");
    }


    /**
     * Returns the list of feeds.
     *
     * @return the list of feeds, which is never null.
     */
    @NonNull
    protected abstract List<Feed> getFeeds();

    /**
     * Returns the list of articles for the feed whose ID is given.
     *
     * @param feedId the ID of the feed for which to return the articles, or null, if all articles are to be returned
     *
     * @return the list of articles
     */
    @NonNull
    protected abstract List<Article> getArticles(String feedId);


    /**
     * Convenience method to create a Feed object.
     *
     * @param id    the ID
     * @param title the name of the feed
     *
     * @return the Feed object, which is never null
     */
    @NonNull
    protected static Feed feed(String id, String title) {
        return new Feed(id, title);
    }


    /**
     * Convenience method to create an Article object.
     *
     * @param id    the ID
     * @param title the title
     * @param text  the text
     *
     * @return the Article object, which is never null
     */
    @NonNull
    protected static Article article(String id, String title, String text) {
        return new Article(id, title, text);
    }


    protected static class Feed {
        public final String id;
        public final String title;


        private Feed(String id, String title) {
            this.id = id;
            this.title = title;
        }


        @Override
        @NonNull
        public String toString() {
            return "Feed{id='" + id + "', title='" + title + "'}";
        }
    }

    protected static class Article {
        public final String id;
        public final String title;
        public final String text;


        protected Article(String id, String title, String text) {
            this.id = id;
            this.title = title;
            this.text = text;
        }


        @Override
        @NonNull
        public String toString() {
            return "Article{id=" + id + " title='" + title + "', text='" + text + "'}";
        }
    }

}
