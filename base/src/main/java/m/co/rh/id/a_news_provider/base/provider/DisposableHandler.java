package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import m.co.rh.id.aprovider.ProviderDisposable;

public class DisposableHandler extends Handler implements ProviderDisposable {

    public DisposableHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void dispose(Context context) {
        removeCallbacksAndMessages(null);
    }
}
