package app.stayput;

import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

final class CheckInChecks {
  private static final String START = "Start a 25-minute cycle";

  static void run(SmokeInstrumentation test, MainActivity screen) throws Exception {
    String setting = Settings.System.SCREEN_OFF_TIMEOUT;
    int originalTimeout = Settings.System.getInt(screen.getContentResolver(), setting, 30_000);
    test.shell("settings put system " + setting + " 600000");
    try {
      exerciseCheckIn(test, screen);
    } finally {
      test.shell("settings put system " + setting + " " + originalTimeout);
    }
  }

  private static void exerciseCheckIn(SmokeInstrumentation test, MainActivity screen)
      throws Exception {
    UiAutomation automation =
        test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    screen.startActivity(
        new Intent(screen, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    test.until(() -> MainActivity.isVisible, "Stayput is open for check-in setup");
    UiChecks.main(test, () -> Store.set(screen, "minutes", 25));
    test.until(() -> setting(test, "Check-in") != null, "Check-in is available in the tab bar");
    test.check(
        setting(test, "Check-in").performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "The Check-in tab opens from the tab bar");
    test.until(
        () -> UiChecks.text(test, "Scroll check-in") != null, "Check-in has its own screen");
    test.capture("page-check-in");
    AccessibilityNodeInfo toggle = setting(test, "Show check-ins.");
    test.check(toggle != null && !toggle.isChecked(), "Scroll check-in is off by default");
    test.check(
        toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Scroll check-in can be enabled");
    test.check(
        setting(test, "Apps to watch.").performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Check-in app picker opens");
    test.until(
        () -> {
          AccessibilityNodeInfo save = UiChecks.text(test, "Save apps");
          return save != null && save.isEnabled();
        },
        "Check-in app picker loads");
    UiChecks.setSearch(test, "Settings");
    test.until(() -> settingsChoice(test) != null, "Settings appears as a selectable app");
    test.check(
        settingsChoice(test).performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Settings can be selected for check-ins");
    test.until(
        () -> {
          AccessibilityNodeInfo choice = settingsChoice(test);
          return choice != null && choice.isChecked();
        },
        "The app picker shows Settings as selected");
    UiChecks.click(test, "Save apps");
    test.check(Store.checkInApps(screen).contains("com.android.settings"), "Chosen app is saved");
    test.check(
        setting(test, "Time before asking.").performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Check-in delay picker opens");
    UiChecks.click(test, "1 minute");

    openSettings(screen);
    test.until(() -> !MainActivity.isVisible, "Selected app opens outside Stayput");
    Thread.sleep(4_000);
    test.shell("input keyevent KEYCODE_HOME");
    Thread.sleep(1_000);
    long reopenedAt = SystemClock.elapsedRealtime();
    openSettings(screen);
    test.until(
        () -> overlayAction(automation, START) != null,
        "Check-in appears over the selected app",
        75_000);
    test.check(
        SystemClock.elapsedRealtime() - reopenedAt >= 59_000,
        "Leaving the app resets its check-in timer");
    test.capture("04-scroll-check-in");

    tap(test, overlayAction(automation, "Keep going"));
    long continuedAt = SystemClock.elapsedRealtime();
    test.until(() -> overlayAction(automation, START) == null, "Keep going dismisses the check-in");
    test.check(!Store.active(screen) && !MainActivity.isVisible, "Keep going leaves the selected app open");
    test.until(
        () -> overlayAction(automation, START) != null, "Check-in returns after continuing", 75_000);
    test.check(
        SystemClock.elapsedRealtime() - continuedAt >= 59_000,
        "Keep going grants a full new interval");

    PowerManager power = screen.getSystemService(PowerManager.class);
    KeyguardManager keyguard = screen.getSystemService(KeyguardManager.class);
    test.shell("input keyevent KEYCODE_SLEEP");
    test.until(() -> !power.isInteractive(), "Screen turns off during a check-in");
    test.shell("input keyevent KEYCODE_WAKEUP");
    test.shell("wm dismiss-keyguard");
    test.until(() -> power.isInteractive() && !keyguard.isKeyguardLocked(), "Screen is unlocked");
    long unlockedAt = SystemClock.elapsedRealtime();
    test.until(
        () -> overlayAction(automation, START) == null, "Locking the screen clears the check-in", 5_000);
    test.until(
        () -> overlayAction(automation, START) != null, "Check-in resumes after unlocking", 75_000);
    test.check(
        SystemClock.elapsedRealtime() - unlockedAt >= 58_000,
        "Unlocking starts a full new interval");

    tap(test, overlayAction(automation, START));
    test.until(
        () -> MainActivity.isVisible && Store.active(screen),
        "Start cycle opens Stayput and starts focus");
    test.check(Store.duration(screen) == 25 * 60_000L, "Check-in uses the chosen focus duration");
    test.check(overlayAction(automation, START) == null, "Check-in is gone during the session");
  }

  private static AccessibilityNodeInfo setting(SmokeInstrumentation test, String prefix) {
    return UiChecks.node(
        test,
        node -> node.getContentDescription() != null
            && node.getContentDescription().toString().startsWith(prefix));
  }

  private static AccessibilityNodeInfo settingsChoice(SmokeInstrumentation test) {
    return UiChecks.node(
        test, node -> node.isCheckable() && "Settings".equals(node.getContentDescription()));
  }

  private static AccessibilityNodeInfo overlayAction(UiAutomation automation, String label) {
    for (AccessibilityWindowInfo window : automation.getWindows()) {
      if (window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
      AccessibilityNodeInfo match =
          UiChecks.node(window.getRoot(), node -> node.getText() != null
              && label.contentEquals(node.getText()));
      if (match != null) return match;
    }
    return null;
  }

  private static void openSettings(MainActivity screen) {
    screen.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private static void tap(SmokeInstrumentation test, AccessibilityNodeInfo node) throws Exception {
    Rect bounds = new Rect();
    node.getBoundsInScreen(bounds);
    test.shell("input tap " + bounds.centerX() + " " + bounds.centerY());
  }
}
