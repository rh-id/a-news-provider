package m.co.rh.id.a_news_provider.app.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for NotificationPermissionPolicy covering the SDK gate, the
 * notifications-enabled gate, the passive anti-nag budget, the permanent-denial
 * redirect and the grant-then-revoke case.
 */
public class NotificationPermissionPolicyTest {

    private static final int API_33 = 33;
    private static final int API_32 = 32;

    private static NotificationPermissionPolicy.Decision decide(boolean notificationsEnabled,
                                                                boolean deniedBefore,
                                                                int requestCount,
                                                                boolean rationaleAvailable,
                                                                boolean explicitRequest) {
        return NotificationPermissionPolicy.decide(API_33, notificationsEnabled, deniedBefore,
                requestCount, rationaleAvailable, explicitRequest);
    }

    @Test
    public void testBelowApi33AlwaysDoNothing() {
        // even a fresh install with notifications off and no asks yet
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                NotificationPermissionPolicy.decide(API_32, false, false, 0, false, false));
        // and an explicit request on the permanent-denial state
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                NotificationPermissionPolicy.decide(API_32, false, true, 5, false, true));
    }

    @Test
    public void testNotificationsEnabledDoNothing() {
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                decide(true, false, 0, false, false));
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                decide(true, true, 5, false, true));
    }

    @Test
    public void testFreshInstallPassiveAsk() {
        // count 0, never denied, rationale false (never-asked state)
        assertEquals(NotificationPermissionPolicy.Decision.ASK_DIALOG,
                decide(false, false, 0, false, false));
    }

    @Test
    public void testDeniedOnceWithRationalePassiveAsk() {
        // denied once (dialog still shows rationale), budget not yet exhausted
        assertEquals(NotificationPermissionPolicy.Decision.ASK_DIALOG,
                decide(false, true, 1, true, false));
    }

    @Test
    public void testPassiveBudgetExhaustedDoNothing() {
        // passive ask with count >= 2 is suppressed regardless of other flags
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                decide(false, false, 2, true, false));
        assertEquals(NotificationPermissionPolicy.Decision.DO_NOTHING,
                decide(false, true, 3, false, false));
    }

    @Test
    public void testExplicitRequestBypassesPassiveBudget() {
        // explicit toggle action bypasses ONLY the anti-nag gate
        assertEquals(NotificationPermissionPolicy.Decision.ASK_DIALOG,
                decide(false, false, 2, true, true));
        assertEquals(NotificationPermissionPolicy.Decision.ASK_DIALOG,
                decide(false, false, 99, true, true));
    }

    @Test
    public void testPermanentDenialShowsSettingsSnackbar() {
        // denied before and rationale gone: the dialog is a permanent dead end
        assertEquals(NotificationPermissionPolicy.Decision.SHOW_SETTINGS_SNACKBAR,
                decide(false, true, 1, false, false));
    }

    @Test
    public void testExplicitPermanentDenialShowsSettingsSnackbar() {
        // even an explicit request must not hammer the dead-end dialog
        assertEquals(NotificationPermissionPolicy.Decision.SHOW_SETTINGS_SNACKBAR,
                decide(false, true, 1, false, true));
    }

    @Test
    public void testGrantThenRevokeAsksDialog() {
        // granted once then revoked in settings: deniedBefore false, rationale false,
        // count 1 - the OS shows a fresh dialog, so asking is correct
        assertEquals(NotificationPermissionPolicy.Decision.ASK_DIALOG,
                decide(false, false, 1, false, false));
    }
}
