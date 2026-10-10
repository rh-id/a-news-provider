package m.co.rh.id.a_news_provider.app.ui.model;

import java.util.List;

public class RssItemModel {
    public CharSequence pubDate;
    public CharSequence title;
    public CharSequence description;
    public List<String> categories;
    public boolean isRead;
    public boolean isFavorite;
    public Long id;
}
