package m.co.rh.id.a_news_provider.app.util;

/**
 * Pure-java decision policy for the runtime {@code POST_NOTIFICATIONS} permission
 * (Android 13 and above). Contains no android imports so it runs on the JVM for
 * unit tests; the SDK level is passed in as a plain int.
 * <p>
 * Semantics of the inputs:
 * <ul>
 *     <li>{@code sdkInt} - device SDK level, 33 is the level that introduced the
 *     runtime notification permission (Android 13, Tiramisu).</li>
 *     <li>{@code notificationsEnabled} - current app notification enable state
 *     (covers granted permission AND user enabled channels).</li>
 *     <li>{@code deniedBefore} - {@code true} ONLY when a previously shown system
 *     dialog was answered with "deny". Never inferred from the request count.</li>
 *     <li>{@code requestCount} - number of times the system dialog was actually
 *     shown (both passive and explicit asks increment it).</li>
 *     <li>{@code rationaleAvailable} - result of
 *     {@code shouldShowRequestPermissionRationale()}. Note it returns {@code false}
 *     in ALL of: never asked, permanently denied, currently granted and
 *     grant-then-revoked-in-settings. Hence {@code deniedBefore} above is mandatory
 *     to distinguish permanent denial from the grant-revoke case.</li>
 *     <li>{@code explicitRequest} - {@code true} when the ask comes from an explicit
 *     user action (e.g. enabling periodic sync in settings). Explicit requests
 *     bypass the passive anti-nag gate only.</li>
 * </ul>
 * <p>
 * Decision order (order is load-bearing):
 * <ol>
 *     <li>{@code sdkInt < 33} - the permission does not exist, DO_NOTHING.</li>
 *     <li>{@code notificationsEnabled} - nothing to ask for, DO_NOTHING.</li>
 *     <li>passive (non-explicit) ask with {@code requestCount >= 2} - anti-nag gate
 *     exhausted, DO_NOTHING. Explicit requests bypass ONLY this rule.</li>
 *     <li>{@code deniedBefore && !rationaleAvailable} - the dialog is a permanent
 *     dead end (OS silently no-ops further requests), redirect the user to the
 *     system settings instead: SHOW_SETTINGS_SNACKBAR.</li>
 *     <li>otherwise ASK_DIALOG. This includes the grant-then-revoke case
 *     ({@code deniedBefore == false}, {@code rationaleAvailable == false},
 *     {@code requestCount == 1}): the OS will show a fresh dialog for it, so
 *     asking is the correct action.</li>
 * </ol>
 */
public final class NotificationPermissionPolicy {

    /**
     * Possible outcomes of {@link #decide(int, boolean, boolean, int, boolean, boolean)}.
     */
    public enum Decision {
        /**
         * Show the system permission dialog now (caller must increment the request
         * count BEFORE calling requestPermissions).
         */
        ASK_DIALOG,
        /**
         * The system dialog would be a dead end; show a snackbar with an action
         * that deep-links to the app notification settings instead.
         */
        SHOW_SETTINGS_SNACKBAR,
        /**
         * Do not bother the user at all.
         */
        DO_NOTHING
    }

    private NotificationPermissionPolicy() {
    }

    /**
     * Decides what the app should do about the notification permission.
     *
     * @param sdkInt               device SDK level (33 introduced POST_NOTIFICATIONS)
     * @param notificationsEnabled true if notifications are currently enabled
     * @param deniedBefore         true only if a shown dialog was denied before
     * @param requestCount         number of times the dialog was actually shown
     * @param rationaleAvailable   result of shouldShowRequestPermissionRationale()
     * @param explicitRequest      true if the ask comes from an explicit user action
     * @return the decision, never null
     */
    public static Decision decide(int sdkInt, boolean notificationsEnabled, boolean deniedBefore,
                                  int requestCount, boolean rationaleAvailable, boolean explicitRequest) {
        // 33 = Build.VERSION_CODES.TIRAMISU (Android 13), first level with the
        // runtime notification permission. Literal keeps this class JVM-testable.
        if (sdkInt < 33) {
            return Decision.DO_NOTHING;
        }
        if (notificationsEnabled) {
            return Decision.DO_NOTHING;
        }
        if (!explicitRequest && requestCount >= 2) {
            // passive anti-nag gate: at most 2 unprompted asks, ever
            return Decision.DO_NOTHING;
        }
        if (deniedBefore && !rationaleAvailable) {
            // permanent denial: further dialogs would silently no-op
            return Decision.SHOW_SETTINGS_SNACKBAR;
        }
        return Decision.ASK_DIALOG;
    }
}
