package m.co.rh.id.a_news_provider.component.network.parser;

class RssMedia {
    static final int TYPE_IMAGE = 1;
    static final int TYPE_VIDEO = 2;
    String url;
    int type;

    boolean isImage() {
        return type == TYPE_IMAGE;
    }

    boolean isVideo() {
        return type == TYPE_VIDEO;
    }
}