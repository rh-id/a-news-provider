package m.co.rh.id.a_news_provider.app.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;

import m.co.rh.id.a_news_provider.R;

/**
 * The mini-player bar strip (issue #69 phase 2): title + play/pause + close,
 * docked to the bottom of the activity's content by
 * {@link MiniPlayerBarController}. Passive view only - all interaction is
 * forwarded to the controller through {@link Listener}; every visible control
 * carries a contentDescription from the layout (accessibility audit
 * requirement), the play/pause description follows the current visual state.
 */
public class MiniPlayerBarView extends LinearLayout {

    private TextView mTitleText;
    private AppCompatImageButton mPlayPauseButton;
    private AppCompatImageButton mCloseButton;
    private Listener mListener;

    public MiniPlayerBarView(Context context) {
        super(context);
    }

    public MiniPlayerBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MiniPlayerBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitleText = findViewById(R.id.text_mini_player_title);
        mPlayPauseButton = findViewById(R.id.button_mini_player_play_pause);
        mCloseButton = findViewById(R.id.button_mini_player_close);
        if (mTitleText != null) {
            mTitleText.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onTitleClicked();
                }
            });
        }
        if (mPlayPauseButton != null) {
            mPlayPauseButton.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onPlayPauseClicked();
                }
            });
        }
        if (mCloseButton != null) {
            mCloseButton.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onCloseClicked();
                }
            });
        }
    }

    /**
     * Binds what is playing. The raw item title is shown as plain text (the
     * detail page renders the rich variant).
     *
     * @param title the loaded item's title, may be null
     */
    public void setTitleText(String title) {
        if (mTitleText == null) {
            return;
        }
        mTitleText.setText(title == null ? "" : title);
        // marquee only runs while the view is selected
        mTitleText.setSelected(true);
    }

    /**
     * Reflects the play state onto the play/pause button: the icon AND its
     * contentDescription always describe the CURRENT state (the button plays
     * when paused and pauses when playing).
     *
     * @param isPlaying true to show the pause icon/label, false play
     */
    public void setPlayPauseVisual(boolean isPlaying) {
        if (mPlayPauseButton == null) {
            return;
        }
        mPlayPauseButton.setImageResource(isPlaying
                ? R.drawable.ic_pause_white
                : R.drawable.ic_play_arrow_white);
        mPlayPauseButton.setContentDescription(getContext().getString(isPlaying
                ? R.string.fab_pause_audio
                : R.string.fab_play_audio));
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // the view can outlive its listeners (controller swap) - never keep
        // callbacks into a detached controller
        mListener = null;
    }

    /**
     * Interaction callbacks, implemented by {@link MiniPlayerBarController}.
     */
    public interface Listener {
        /**
         * The title was tapped: re-open the playing item's detail page.
         */
        void onTitleClicked();

        /**
         * The play/pause button was tapped.
         */
        void onPlayPauseClicked();

        /**
         * The close button was tapped: stop playback entirely.
         */
        void onCloseClicked();
    }
}
