package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.FeedHealthItem;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.DeleteRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PauseRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.component.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * One feed-health dashboard row (issue #42): renders the derived
 * {@link FeedHealthItem} status (badge), the sync timeline (last attempt /
 * last success), the unread/total counts and - when present - the recorded
 * error and the active backoff schedule, with sync-now / pause / delete
 * actions. Read-only-plus-actions by design: the row shows the persisted sync
 * state and edits only it through commands, it never opens the feed.
 * <p>
 * Deliberate display rules (issue #42):
 * <ul>
 * <li>a channel that was never attempted shows the never-synced string for
 * the last-attempt line and hides the last-success line - for those rows
 * updated_date_time equals the ADD time and must never masquerade as a
 * success;</li>
 * <li>the backoff line renders the failure counter as a SCHEDULE ("retries
 * after ..."), never as a literal failure count (the counter is
 * Retry-After-encoded, issue #50), and only while the quiet window is still
 * active - a stale past date would be nonsense;</li>
 * <li>the sync-now button (and its accessibility action) is hidden on paused
 * rows: RssSyncWorker silently skips paused channels and the user would get
 * zero feedback for a dead button.</li>
 * </ul>
 * The delete action asks for confirmation first (a deliberate new UX on this
 * page: the home channel list deletes without one, but there the row sits
 * behind a long-press edit menu, while this dashboard exposes delete as a
 * first-class button).
 */
public class FeedHealthItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    /**
     * Same date rendering as the item lists (RssItemSV): short weekday, day,
     * month name, year.
     */
    private static final String DATE_FORMAT = "E, d MMM yyyy";

    /**
     * The currently bound row, kept NON-transient so it survives the
     * StatefulView's serialized state (FeedHealthItem is Serializable) - same
     * rationale as RssItemSV's mRssItemSubject. Created once in the
     * constructor, never completed: the RxDisposer owns the subscription's
     * lifecycle, the wrapper outlives it.
     */
    private SerialBehaviorSubject<FeedHealthItem> mFeedHealthItemSubject;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient SyncChannelCmd mSyncChannelCmd;
    private transient PauseRssChannelCmd mPauseRssChannelCmd;
    private transient DeleteRssChannelCmd mDeleteRssChannelCmd;
    private transient int mPauseUnpauseAccessibilityActionId;
    private transient int mSyncAccessibilityActionId;

    public FeedHealthItemSV() {
        mFeedHealthItemSubject = new SerialBehaviorSubject<>();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mSyncChannelCmd = mSvProvider.get(SyncChannelCmd.class);
        mPauseRssChannelCmd = mSvProvider.get(PauseRssChannelCmd.class);
        mDeleteRssChannelCmd = mSvProvider.get(DeleteRssChannelCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.item_feed_health, container, false);
        TextView textName = view.findViewById(R.id.text_name);
        TextView textStatus = view.findViewById(R.id.text_status);
        TextView textLastAttempt = view.findViewById(R.id.text_last_attempt);
        TextView textLastSuccess = view.findViewById(R.id.text_last_success);
        TextView textCounts = view.findViewById(R.id.text_counts);
        TextView textError = view.findViewById(R.id.text_error);
        TextView textBackoff = view.findViewById(R.id.text_backoff);
        Button buttonSyncNow = view.findViewById(R.id.button_sync_now);
        Button buttonPause = view.findViewById(R.id.button_pause);
        Button buttonDelete = view.findViewById(R.id.button_delete);

        buttonSyncNow.setOnClickListener(this);
        buttonPause.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        addRowAccessibilityActions(view);

        mRxDisposer.add("feedHealthItemUiChange", mFeedHealthItemSubject
                .getSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feedHealthItem -> {
                    renderRow(activity, view, feedHealthItem, textName, textStatus,
                            textLastAttempt, textLastSuccess, textCounts, textError,
                            textBackoff, buttonSyncNow, buttonPause);
                }, throwable -> {
                    // leave blank: the subject only emits dashboard rows, an
                    // error here cannot happen by construction
                }));
        return view;
    }

    /**
     * Renders the current row state (issue #42). Runs on the main thread via
     * the createView subscription.
     */
    private void renderRow(Activity activity, View rowView, FeedHealthItem feedHealthItem,
                           TextView textName, TextView textStatus, TextView textLastAttempt,
                           TextView textLastSuccess, TextView textCounts, TextView textError,
                           TextView textBackoff, Button buttonSyncNow, Button buttonPause) {
        RssChannel rssChannel = feedHealthItem.rssChannel;
        Context context = rowView.getContext();
        textName.setText(feedHealthItem.getDisplayName());

        // status badge: OK green / PAUSED grey / ERROR red. Every pair here is
        // chosen ABOVE the WCAG contrast threshold the instrumented a11y gate
        // (TextContrastCheck, ERROR-level) enforces on the rendered row: the
        // grey pair is the home list's paused badge convention, the OK badge
        // fixes black-on-green (mode-independent), and the ERROR badge resolves
        // Material's designed colorError/colorOnError pair for day/night
        switch (feedHealthItem.getStatus()) {
            case OK:
                textStatus.setText(R.string.status_ok);
                textStatus.setBackgroundColor(
                        ContextCompat.getColor(activity, R.color.green_500));
                textStatus.setTextColor(
                        ContextCompat.getColor(activity, R.color.black));
                break;
            case PAUSED:
                textStatus.setText(R.string.paused);
                textStatus.setBackgroundColor(
                        ContextCompat.getColor(activity, R.color.daynight_gray_300_gray_600));
                textStatus.setTextColor(
                        ContextCompat.getColor(activity, R.color.daynight_black_white));
                break;
            default:
                textStatus.setText(R.string.status_error);
                textStatus.setBackgroundColor(
                        UiUtils.getColorFromAttribute(activity, R.attr.colorError));
                textStatus.setTextColor(
                        UiUtils.getColorFromAttribute(activity, R.attr.colorOnError));
                break;
        }

        // last attempt / last success: a never-attempted channel shows the
        // never-synced string and NO success line - its updated_date_time is
        // the add time, never a success (issue #42)
        if (rssChannel.lastSyncAttemptDateTime == null) {
            textLastAttempt.setText(R.string.feed_health_never_synced);
            textLastSuccess.setVisibility(View.GONE);
        } else {
            textLastAttempt.setText(context.getString(R.string.feed_health_last_attempt,
                    formatDate(rssChannel.lastSyncAttemptDateTime)));
            if (rssChannel.updatedDateTime != null) {
                textLastSuccess.setText(context.getString(R.string.feed_health_last_success,
                        formatDate(rssChannel.updatedDateTime)));
                textLastSuccess.setVisibility(View.VISIBLE);
            } else {
                textLastSuccess.setVisibility(View.GONE);
            }
        }

        textCounts.setText(context.getString(R.string.feed_health_counts,
                feedHealthItem.unreadItems, feedHealthItem.totalItems));

        // error line: the honest failure record - HTTP status when the failure
        // had one, error message otherwise, joined when both exist
        boolean hasError = rssChannel.lastHttpStatus != null
                || (rssChannel.lastError != null && !rssChannel.lastError.isEmpty());
        if (hasError) {
            StringBuilder errorLine = new StringBuilder();
            if (rssChannel.lastHttpStatus != null) {
                errorLine.append(context.getString(R.string.feed_health_http_status,
                        rssChannel.lastHttpStatus));
            }
            if (rssChannel.lastError != null && !rssChannel.lastError.isEmpty()) {
                if (errorLine.length() > 0) {
                    errorLine.append(" - ");
                }
                errorLine.append(rssChannel.lastError);
            }
            textError.setText(errorLine.toString());
            textError.setVisibility(View.VISIBLE);
        } else {
            textError.setVisibility(View.GONE);
        }

        // backoff schedule (issue #50 counter rendered as a schedule, issue #42):
        // only while the quiet window is still active - a past "retries after"
        // date would be noise, and a paused channel's stale window hides too
        long backoffUntilMillis =
                feedHealthItem.getBackoffUntilMillis(System.currentTimeMillis());
        if (backoffUntilMillis > 0) {
            textBackoff.setText(context.getString(R.string.feed_health_backoff_until,
                    formatDate(new Date(backoffUntilMillis))));
            textBackoff.setVisibility(View.VISIBLE);
        } else {
            textBackoff.setVisibility(View.GONE);
        }

        // paused rows must not offer "Sync now": the worker silently skips
        // paused channels and the user would get zero feedback (issue #42)
        buttonSyncNow.setVisibility(rssChannel.isPaused ? View.GONE : View.VISIBLE);
        buttonPause.setText(rssChannel.isPaused ? R.string.unpause : R.string.pause);
        // no row grey-out on purpose: a 50%-alpha row would composite its text
        // below the contrast threshold the a11y gate enforces, and the badge
        // already carries the paused state
        // re-register the state-correct sync/pause accessibility actions
        addSyncAccessibilityAction(rowView);
        addPauseAccessibilityAction(rowView);
    }

    /**
     * Registers the row's always-available accessibility actions (delete) and
     * the state-correct sync/pause ones.
     */
    private void addRowAccessibilityActions(View rowView) {
        ViewCompat.addAccessibilityAction(rowView,
                rowView.getContext().getString(R.string.delete),
                (view, arguments) -> {
                    confirmDelete(view);
                    return true;
                });
        addSyncAccessibilityAction(rowView);
        addPauseAccessibilityAction(rowView);
    }

    /**
     * Registers the row's sync-now accessibility action with a state-correct
     * existence: REMOVED on paused rows (the worker would silently skip the
     * sync), re-registered otherwise, replacing any previously registered
     * sync action.
     */
    private void addSyncAccessibilityAction(View rowView) {
        if (mSyncAccessibilityActionId != 0) {
            ViewCompat.removeAccessibilityAction(rowView, mSyncAccessibilityActionId);
            mSyncAccessibilityActionId = 0;
        }
        FeedHealthItem feedHealthItem = mFeedHealthItemSubject.getValue();
        boolean isPaused = feedHealthItem != null && feedHealthItem.rssChannel.isPaused;
        if (!isPaused) {
            mSyncAccessibilityActionId = ViewCompat.addAccessibilityAction(rowView,
                    rowView.getContext().getString(R.string.sync_now),
                    (view, arguments) -> {
                        syncNow();
                        return true;
                    });
        }
    }

    /**
     * Registers the row's pause/unpause accessibility action with the
     * state-correct label (the channel state at invocation time decides the
     * label), replacing any previously registered pause/unpause action.
     */
    private void addPauseAccessibilityAction(View rowView) {
        if (mPauseUnpauseAccessibilityActionId != 0) {
            ViewCompat.removeAccessibilityAction(rowView, mPauseUnpauseAccessibilityActionId);
            mPauseUnpauseAccessibilityActionId = 0;
        }
        FeedHealthItem feedHealthItem = mFeedHealthItemSubject.getValue();
        boolean isPaused = feedHealthItem != null && feedHealthItem.rssChannel.isPaused;
        mPauseUnpauseAccessibilityActionId = ViewCompat.addAccessibilityAction(rowView,
                rowView.getContext().getString(isPaused ?
                        R.string.unpause : R.string.pause),
                (view, arguments) -> {
                    togglePause();
                    return true;
                });
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_sync_now) {
            syncNow();
        } else if (viewId == R.id.button_pause) {
            togglePause();
        } else if (viewId == R.id.button_delete) {
            confirmDelete(view);
        }
    }

    /**
     * Requests the targeted single-channel sync (issue #42) through
     * SyncChannelCmd. Only reachable on non-paused rows (button/action hidden
     * while paused).
     */
    private void syncNow() {
        FeedHealthItem feedHealthItem = mFeedHealthItemSubject.getValue();
        if (feedHealthItem != null && feedHealthItem.rssChannel.id != null) {
            mSyncChannelCmd.execute(feedHealthItem.rssChannel.id);
        }
    }

    /**
     * Toggles the paused state of the current channel. When the write lands
     * the updatedRssChannel event triggers the dashboard reload and this row
     * rebinds (badge, grey-out, button and accessibility action swap).
     */
    private void togglePause() {
        FeedHealthItem feedHealthItem = mFeedHealthItemSubject.getValue();
        if (feedHealthItem != null && feedHealthItem.rssChannel.id != null) {
            mPauseRssChannelCmd.execute(feedHealthItem.rssChannel.id,
                    !feedHealthItem.rssChannel.isPaused);
        }
    }

    /**
     * Asks for confirmation before deleting (deliberate new UX, issue #42:
     * this dashboard exposes delete as a first-class button). The delete
     * itself runs through DeleteRssChannelCmd exactly like the home channel
     * list's delete.
     */
    private void confirmDelete(View rowView) {
        FeedHealthItem feedHealthItem = mFeedHealthItemSubject.getValue();
        if (feedHealthItem == null || feedHealthItem.rssChannel.id == null) {
            return;
        }
        Context context = rowView.getContext();
        MaterialAlertDialogBuilder materialAlertDialogBuilder =
                new MaterialAlertDialogBuilder(context);
        materialAlertDialogBuilder.setTitle(R.string.feed_health_delete_confirm_title);
        materialAlertDialogBuilder.setMessage(R.string.feed_health_delete_confirm_message);
        materialAlertDialogBuilder.setPositiveButton(R.string.delete, (dialog, which) -> {
            mDeleteRssChannelCmd.execute(feedHealthItem.rssChannel);
            dialog.dismiss();
        });
        materialAlertDialogBuilder.setNegativeButton(R.string.cancel,
                (dialog, which) -> dialog.dismiss());
        materialAlertDialogBuilder.create().show();
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat(DATE_FORMAT).format(date);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        // the subject itself is deliberately left untouched (RssItemSV
        // pattern): it is non-transient row state, and the RxDisposer already
        // disposed the subscription
    }

    public void setFeedHealthItem(FeedHealthItem feedHealthItem) {
        mFeedHealthItemSubject.onNext(feedHealthItem);
    }
}
