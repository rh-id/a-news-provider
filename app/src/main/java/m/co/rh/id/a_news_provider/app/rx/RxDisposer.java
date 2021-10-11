package m.co.rh.id.a_news_provider.app.rx;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer {
    private Map<String, Disposable> disposableMap;

    public RxDisposer() {
        disposableMap = new HashMap<>();
    }

    public void add(String uniqueKey, Disposable disposable) {
        Disposable existing = disposableMap.remove(uniqueKey);
        if (existing != null) {
            existing.dispose();
        }
        disposableMap.put(uniqueKey, disposable);
    }

    public void dispose() {
        if (!disposableMap.isEmpty()) {
            for (Map.Entry<String, Disposable> entry : disposableMap.entrySet()) {
                entry.getValue().dispose();
            }
            disposableMap.clear();
        }
    }
}
