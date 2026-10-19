package m.co.rh.id.a_news_provider.app.ui;

import android.view.KeyEvent;

/**
 * Implemented by StatefulView pages that want to receive hardware key events
 * (e.g. volume-key article navigation, issue #60). MainActivity forwards
 * onKeyDown to the top-of-stack page when it implements this interface.
 * Called only after the view hierarchy declined the key (Activity.onKeyDown),
 * so focused text fields keep priority.
 */
public interface KeyEventHandler {
    /**
     * @return true if the event was consumed (stops further propagation /
     * suppresses default volume behavior), false to fall through.
     */
    boolean onKeyDown(int keyCode, KeyEvent event);
}
