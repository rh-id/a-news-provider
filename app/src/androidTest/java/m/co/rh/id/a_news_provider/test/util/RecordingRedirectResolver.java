package m.co.rh.id.a_news_provider.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Redirect-probe test double that records every probed URL so tests can assert
 * whether the redirect probe ran (and how often). Defaults to never redirecting
 * ({@code url -> null}); individual tests install their own resolution via
 * {@link #setResolution(Function)}. The call list is thread-safe because the
 * bulk add probes genuinely new URLs on concurrent executor threads.
 * <p>
 * IMPORTANT: {@link #setResolution(Function)} must be called before work is
 * submitted - the field is intentionally non-volatile and relies on
 * setResolution happening-before task submission.
 */
public class RecordingRedirectResolver implements Function<String, String> {
    private final List<String> mCalls = Collections.synchronizedList(new ArrayList<>());
    private Function<String, String> mResolution = url -> null;

    public void setResolution(Function<String, String> resolution) {
        mResolution = resolution;
    }

    public List<String> getCalls() {
        // snapshot so callers never iterate the live list while probe tasks append to it
        return new ArrayList<>(mCalls);
    }

    @Override
    public String apply(String url) {
        mCalls.add(url);
        return mResolution.apply(url);
    }
}
