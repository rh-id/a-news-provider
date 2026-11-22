package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialOptionalBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.model.RssItemModel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

/**
 * The CARD (magazine) list row layout variant (issue #58): an article thumbnail
 * from {@code RssItem.mediaImage} on top, the source feed (icon + name) above a
 * two-line title, and a meta line with the date and the favorite star.
 * <p>
 * Shared row behavior (RssItem stream + mapping, self-heal, read/favorite toggles,
 * tap-to-detail, long-press read toggle, duplicate-story badge, TalkBack state
 * description + custom actions, dispose contract) lives in {@link BaseRssItemSV};
 * this class adds the card's image and channel bindings:
 * <ul>
 *     <li>the thumbnail loads through Volley's {@link NetworkImageView} +
 *     {@link ImageLoader} exactly like the channel rows do
 *     ({@code SerialOptionalBehaviorSubject} + short debounce + main-thread
 *     {@code setImageUrl}); it is deliberately INDEPENDENT of the
 *     {@code downloadImage} setting - that preference gates the article detail
 *     page's media (issue #36), while the card layout was requested to always show
 *     the feed-provided thumbnail (issue #58 user decision); when the item has no
 *     {@code mediaImage} the whole thumbnail collapses (same hide-when-null pattern
 *     as {@code RssChannelItemSV}'s icon),</li>
 *     <li>the feed name/icon come from the shared model mapping (executor-resolved,
 *     memoized per pass in {@link BaseRssItemSV}), and a
 *     {@code RssChangeNotifier.updatedRssChannel} self-heal subscription refreshes
 *     both when the channel is renamed/unpaused by {@code RenameRssFeedCmd} /
 *     {@code PauseRssChannelCmd} - the DiffUtil diff runs on RssItem objects, so a
 *     channel change alone would never rebind this row.</li>
 * </ul>
 * The card background is a plain square full-width surface (no rounded corners, no
 * outer margins) so the swipe helper's full-bounds square background rect
 * ({@code RssItemSwipeHelperCallback.onChildDraw}) never peeks around rounded card
 * corners. Swipe actions, mark-read-on-tap and the long-press read toggle come from
 * the base unchanged; the root id is {@code root_layout} like every other variant.
 */
public class RssItemCardSV extends BaseRssItemSV {

    /**
     * RxDisposer key of the thumbnail url load. Repeated binds reuse the same key,
     * so a bind while a load is queued replaces the previous subscription instead
     * of stacking stale renders.
     */
    private static final String KEY_THUMBNAIL_URL = "thumbnailUrl";

    /**
     * RxDisposer key of the feed-icon url load (same debounced pattern as the
     * thumbnail, just for the 24dp source icon).
     */
    private static final String KEY_CHANNEL_ICON_URL = "channelIconUrl";

    /**
     * RxDisposer key of the channel self-heal subscription: renames/unpauses of the
     * row's feeding channel re-render the name and icon without a list reload.
     */
    private static final String KEY_ON_RSS_CHANNEL_UPDATED = "createView_onRssChannelUpdated";

    /**
     * Debounce for the image-url subjects, mirroring the channel row's
     * {@code RssChannelItemSV} (a RecyclerView fling fires many binds; only the
     * last queued url per subject should reach Volley).
     */
    private static final long IMAGE_URL_DEBOUNCE_MILLIS = 100L;

    private transient SerialOptionalBehaviorSubject<String> mThumbnailUrlSubject;
    private transient SerialOptionalBehaviorSubject<String> mChannelIconUrlSubject;
    private transient NetworkImageView mNetworkImageViewThumbnail;
    private transient NetworkImageView mNetworkImageViewChannelIcon;
    private transient TextView mTextChannelName;
    private transient TextView mTextTitle;
    private transient TextView mTextDate;

    public RssItemCardSV() {
        super();
        mThumbnailUrlSubject = new SerialOptionalBehaviorSubject<>();
        mChannelIconUrlSubject = new SerialOptionalBehaviorSubject<>();
    }

    @Override
    protected int provideLayoutRes() {
        return R.layout.view_rss_item_card;
    }

    @Override
    protected void onRowViewCreated(Activity activity, View rowView) {
        mNetworkImageViewThumbnail = rowView.findViewById(R.id.network_image_view_thumbnail);
        // daynight-aware while-loading/error placeholder set from code - this
        // Volley version exposes no defaultImage/errorImage XML attrs (same
        // code path as the detail page's setErrorImageResId), and a size-less
        // shape survives the centerCrop scaleType on every API level
        mNetworkImageViewThumbnail.setDefaultImageResId(
                R.drawable.bg_image_thumbnail_daynight);
        mNetworkImageViewThumbnail.setErrorImageResId(
                R.drawable.bg_image_thumbnail_daynight);
        mNetworkImageViewChannelIcon = rowView.findViewById(R.id.network_image_view_channel_icon);
        mTextChannelName = rowView.findViewById(R.id.text_channel_name);
        mTextTitle = rowView.findViewById(R.id.text_title);
        mTextDate = rowView.findViewById(R.id.text_date);
        mRxDisposer.add(KEY_THUMBNAIL_URL,
                mThumbnailUrlSubject.getSubject()
                        .debounce(IMAGE_URL_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(imageUrlOpt -> imageUrlOpt.ifPresent(s ->
                                mNetworkImageViewThumbnail.setImageUrl(s,
                                        mSvProvider.get(ImageLoader.class)))));
        mRxDisposer.add(KEY_CHANNEL_ICON_URL,
                mChannelIconUrlSubject.getSubject()
                        .debounce(IMAGE_URL_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(imageUrlOpt -> imageUrlOpt.ifPresent(s ->
                                mNetworkImageViewChannelIcon.setImageUrl(s,
                                        mSvProvider.get(ImageLoader.class)))));
        // channel renames/unpauses never touch the bound RssItem, so DiffUtil will
        // not rebind this row: self-heal the name/icon here when the row's channel
        // emits an update (the display name fallback is the shared base logic)
        mRxDisposer.add(KEY_ON_RSS_CHANNEL_UPDATED,
                mRssChangeNotifier.updatedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional -> rssChannelOptional.ifPresent(rssChannel -> {
                            RssItem currentRssItem = getRssItem();
                            if (currentRssItem != null && rssChannel.id != null
                                    && rssChannel.id.equals(currentRssItem.channelId)) {
                                mTextChannelName.setText(resolveChannelDisplayName(rssChannel));
                                bindChannelIcon(rssChannel.imageUrl);
                            }
                        })));
    }

    @Override
    protected void bindRowView(Activity activity, View rowView, RssItemModel rssItemModel) {
        mTextDate.setText(rssItemModel.pubDate);
        mTextTitle.setText(rssItemModel.title);
        mTextTitle.setTypeface(rssItemModel.isRead ?
                Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        mTextChannelName.setText(rssItemModel.channelName);
        // thumbnail visibility is bound from the model on EVERY bind so a recycled
        // card never shows the previous item's image; the actual Volley load is
        // debounced through the subject (empty-string and null collapse to no-op)
        if (rssItemModel.mediaImage != null && !rssItemModel.mediaImage.isEmpty()) {
            mThumbnailUrlSubject.onNext(rssItemModel.mediaImage);
            mNetworkImageViewThumbnail.setVisibility(View.VISIBLE);
        } else {
            mThumbnailUrlSubject.onNext(null);
            mNetworkImageViewThumbnail.setVisibility(View.GONE);
        }
        bindChannelIcon(rssItemModel.channelIconUrl);
    }

    /**
     * Routes the feed icon into the debounced Volley loader and toggles the icon's
     * visibility (hidden entirely when the channel has no image url - same
     * hide-when-null pattern as {@code RssChannelItemSV}'s icon binding).
     *
     * @param imageUrl the channel image url, may be null or empty
     */
    private void bindChannelIcon(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            mChannelIconUrlSubject.onNext(imageUrl);
            mNetworkImageViewChannelIcon.setVisibility(View.VISIBLE);
        } else {
            mChannelIconUrlSubject.onNext(null);
            mNetworkImageViewChannelIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mThumbnailUrlSubject = null;
        mChannelIconUrlSubject = null;
        mNetworkImageViewThumbnail = null;
        mNetworkImageViewChannelIcon = null;
        mTextChannelName = null;
        mTextTitle = null;
        mTextDate = null;
    }
}
