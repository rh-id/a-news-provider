package m.co.rh.id.a_news_provider.base.ui;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SwipeGestureDetector implements View.OnTouchListener {
    private final GestureDetector gestureDetector;
    private final int swipeThreshold;
    private final int swipeVelocityThreshold;

    public SwipeGestureDetector(Context ctx) {
        this(ctx, 100, 100);
    }

    public SwipeGestureDetector(Context ctx, int swipeThreshold, int swipeVelocityThreshold) {
        gestureDetector = new GestureDetector(ctx, new GestureListener());
        this.swipeThreshold = swipeThreshold;
        this.swipeVelocityThreshold = swipeVelocityThreshold;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        v.performClick();
        return gestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > swipeThreshold && Math.abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
                            onSwipeLeft();
                        }
                        result = true;
                    }
                } else if (Math.abs(diffY) > swipeThreshold && Math.abs(velocityY) > swipeVelocityThreshold) {
                    if (diffY > 0) {
                        onSwipeBottom();
                    } else {
                        onSwipeTop();
                    }
                    result = true;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }
    }

    public void onSwipeRight() {
        // Leave blank
    }

    public void onSwipeLeft() {
        // Leave blank
    }

    public void onSwipeTop() {
        // Leave blank
    }

    public void onSwipeBottom() {
        // Leave blank
    }
}
