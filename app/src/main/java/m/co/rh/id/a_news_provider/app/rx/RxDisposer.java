package m.co.rh.id.a_news_provider.app.rx;

import android.content.Context;

import co.rh.id.lib.rx3_utils.disposable.UniqueKeyDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer implements ProviderDisposable {
    private UniqueKeyDisposable uniqueKeyDisposable;

    public RxDisposer() {
        uniqueKeyDisposable = new UniqueKeyDisposable();
    }

    public void add(String uniqueKey, Disposable disposable) {
        uniqueKeyDisposable.add(uniqueKey, disposable);
    }

    @Override
    public void dispose(Context context) {
        uniqueKeyDisposable.dispose();
    }
}
