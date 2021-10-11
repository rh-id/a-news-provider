package m.co.rh.id.a_news_provider.app.model;

import java.io.Serializable;
import java.util.ArrayList;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

public class RssModel implements Serializable {
    private RssChannel rssChannel;
    private ArrayList<RssItem> rssItems;

    public RssModel(RssChannel rssChannel, ArrayList<RssItem> rssItems) {
        this.rssChannel = rssChannel;
        this.rssItems = rssItems;
    }

    public RssChannel getRssChannel() {
        return rssChannel;
    }

    public ArrayList<RssItem> getRssItems() {
        return rssItems;
    }

    @Override
    public String toString() {
        return "RssModel{" +
                "rssChannel=" + rssChannel +
                ", rssItems=" + rssItems +
                '}';
    }
}
