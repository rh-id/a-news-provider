package m.co.rh.id.a_news_provider.test;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;

/**
 * Test page used to test SV component individually
 */
public class TestPage extends StatefulView<Activity> {

    @NavInject
    private StatefulView mTestSv;

    public TestPage(StatefulView testSv) {
        mTestSv = testSv;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        FrameLayout frameLayout = new FrameLayout(activity);
        FrameLayout.LayoutParams layoutparams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.setLayoutParams(layoutparams);
        frameLayout.addView(mTestSv.buildView(activity, frameLayout));
        return frameLayout;
    }
}
