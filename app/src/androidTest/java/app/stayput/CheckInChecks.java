package app.stayput;

import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

final class CheckInChecks {
  private static final String START = "Start a 25-minute cycle";
  private static final String FIXTURE_LABEL = "Reel test feed";

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
    test.check(
        setting(test, "Check-in intensity. Gentle") != null, "Check-in intensity defaults to Gentle");
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
    UiChecks.setSearch(test, FIXTURE_LABEL);
    test.until(() -> feedChoice(test) != null, "Reel test feed appears as a selectable app");
    test.check(
        feedChoice(test).performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Reel test feed can be selected for check-ins");
    test.until(
        () -> {
          AccessibilityNodeInfo choice = feedChoice(test);
          return choice != null && choice.isChecked();
        },
        "The app picker shows the test feed as selected");
    UiChecks.click(test, "Save apps");
    test.check(
        Store.checkInApps(screen).contains(test.getContext().getPackageName()), "Chosen app is saved");
    // Skip the clock for tests of classification and per-visit behavior.
    UiChecks.main(test, () -> Store.set(screen, ScrollCheckIn.MINUTES, 0));
    openFeed(test, false);
    test.until(() -> !MainActivity.isVisible, "Selected app opens outside Stayput");
    test.until(() -> visible(test, "Feed item 1"), "The watched app opens its regular feed");
    automation.waitForIdle(500, 5_000);
    swipe(automation);
    test.until(() -> !visible(test, "Feed item 1"), "A swipe moves the regular feed", 5_000);
    swipeWithoutPrompt(automation, 8);
    test.check(overlayAction(automation, START) == null, "Ordinary feed scrolling stays quiet");

    openFeed(test, true);
    test.until(() -> visible(test, "Clip 1"), "The watched app opens its clip pager");
    automation.waitForIdle(500, 5_000);
    swipe(automation);
    test.until(
        () -> visible(test, "Clip 2") && !visible(test, "Clip 1"),
        "An upward swipe snaps to the next clip",
        5_000);
    awaitPagingPrompt(test, automation, 0);
    test.capture("04-scroll-check-in");

    swipe(automation);
    test.until(() -> overlayAction(automation, START) == null, "An upward swipe dismisses the alert");
    test.check(!Store.active(screen) && !MainActivity.isVisible, "Swipe dismissal leaves the feed open");
    swipeWithoutPrompt(automation, 8);
    test.check(overlayAction(automation, START) == null, "Gentle stays quiet for the rest of the visit");

    test.shell("am start -W -n app.stayput/.MainActivity");
    test.until(() -> MainActivity.isVisible, "Leaving the feed ends the visit");
    openFeed(test, true);
    awaitPagingPrompt(test, automation, 0);
    tap(test, overlayAction(automation, "Keep going"));
    test.until(() -> overlayAction(automation, START) == null, "Gentle checks in on a new visit");

    chooseIntensity(test, "Repeat · After each interval");
    test.check(
        setting(test, "Time before asking.").performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Check-in delay picker opens");
    UiChecks.click(test, "1 minute");
    openFeed(test, true);
    awaitPagingPrompt(test, automation, 60_000);
    tap(test, overlayAction(automation, "Keep going"));
    test.until(() -> overlayAction(automation, START) == null, "Keep going dismisses the check-in");
    awaitPagingPrompt(test, automation, 60_000);

    chooseIntensity(test, "Firm · On the next reel");
    UiChecks.main(test, () -> Store.set(screen, ScrollCheckIn.MINUTES, 0));
    openFeed(test, true);
    awaitPagingPrompt(test, automation, 0);
    swipe(automation, false);
    test.until(() -> overlayAction(automation, START) == null, "A downward swipe dismisses the alert");
    Thread.sleep(600);
    test.check(overlayAction(automation, START) == null, "Firm waits for a fresh reel advance");

    swipe(automation);
    test.until(() -> overlayAction(automation, START) != null, "Firm returns after one reel", 5_000);
    automation.waitForIdle(500, 5_000);
    tap(test, overlayAction(automation, "Keep going"));
    test.until(() -> overlayAction(automation, START) == null, "Keep going also dismisses Firm");
    swipe(automation);
    test.until(() -> overlayAction(automation, START) != null, "Firm checks in on the next reel again", 5_000);

    PowerManager power = screen.getSystemService(PowerManager.class);
    KeyguardManager keyguard = screen.getSystemService(KeyguardManager.class);
    test.shell("input keyevent KEYCODE_SLEEP");
    test.until(() -> !power.isInteractive(), "Screen turns off");
    test.shell("input keyevent KEYCODE_WAKEUP");
    test.shell("wm dismiss-keyguard");
    test.until(() -> power.isInteractive() && !keyguard.isKeyguardLocked(), "Screen is unlocked");
    test.until(() -> overlayAction(automation, START) == null, "Locking the phone clears the alert");
    swipeWithoutPrompt(automation, 1);
    awaitPagingPrompt(test, automation, 0);

    tap(test, overlayAction(automation, START));
    test.until(
        () -> MainActivity.isVisible && Store.active(screen),
        "Start cycle opens Stayput and starts focus");
    test.check(Store.duration(screen) == 25 * 60_000L, "Check-in uses the chosen focus duration");
    test.check(overlayAction(automation, START) == null, "Check-in is gone during the session");
  }

  private static void chooseIntensity(SmokeInstrumentation test, String label) throws Exception {
    test.shell("am start -W -n app.stayput/.MainActivity");
    test.until(() -> MainActivity.isVisible, "Stayput opens to change check-in intensity");
    AccessibilityNodeInfo row = setting(test, "Check-in intensity.");
    if (row == null) {
      test.until(() -> setting(test, "Check-in") != null, "Check-in tab is available");
      setting(test, "Check-in").performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    test.until(() -> setting(test, "Check-in intensity.") != null, "Check-in intensity is available");
    test.check(
        setting(test, "Check-in intensity.").performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Check-in intensity picker opens");
    test.capture("check-in-intensity");
    UiChecks.click(test, label);
    test.until(() -> setting(test, "Check-in intensity. " + label) != null, "Chosen intensity is saved");
  }

  private static AccessibilityNodeInfo setting(SmokeInstrumentation test, String prefix) {
    return UiChecks.node(
        test,
        node -> node.getContentDescription() != null
            && node.getContentDescription().toString().startsWith(prefix));
  }

  private static AccessibilityNodeInfo feedChoice(SmokeInstrumentation test) {
    return UiChecks.node(
        test, node -> node.isCheckable() && FIXTURE_LABEL.equals(node.getContentDescription()));
  }

  private static AccessibilityNodeInfo overlayAction(UiAutomation automation, String label) {
    for (AccessibilityWindowInfo window : automation.getWindows()) {
      if (window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
      AccessibilityNodeInfo match =
          UiChecks.node(window.getRoot(), node -> node.getText() != null && label.contentEquals(node.getText()));
      if (match != null) return match;
    }
    return null;
  }

  private static void openFeed(SmokeInstrumentation test, boolean paged) throws Exception {
    test.shell(
        "am start -W -f 0x10008000 -n " + test.getContext().getPackageName() + "/"
            + ReelFeedActivity.class.getName() + " --ez " + ReelFeedActivity.PAGED + " " + paged);
  }

  private static boolean visible(SmokeInstrumentation test, String label) {
    return UiChecks.node(
        test, node -> node.isVisibleToUser() && label.contentEquals(
            node.getText() == null ? "" : node.getText())) != null;
  }

  private static void swipe(UiAutomation automation) throws Exception {
    swipe(automation, true);
  }

  private static void swipe(UiAutomation automation, boolean up) throws Exception {
    Rect bounds = new Rect();
    AccessibilityNodeInfo root = automation.getRootInActiveWindow();
    if (root == null) throw new AssertionError("The test feed window is unavailable");
    root.getBoundsInScreen(bounds);
    long downAt = SystemClock.uptimeMillis();
    float startY = bounds.top + bounds.height() * (up ? .75f : .25f);
    float travel = bounds.height() * (up ? -.5f : .5f);
    int steps = 12;
    for (int step = 0; step <= steps; step++) {
      int action = step == 0 ? MotionEvent.ACTION_DOWN
          : step == steps ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
      float y = startY + travel * step / steps;
      MotionEvent event = MotionEvent.obtain(
          downAt, SystemClock.uptimeMillis(), action, bounds.centerX(), y, 0);
      event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      try {
        if (!automation.injectInputEvent(event, true)) {
          throw new AssertionError("Android rejected the swipe at step " + step);
        }
      } finally {
        event.recycle();
      }
      Thread.sleep(25);
    }
  }

  private static void swipeWithoutPrompt(UiAutomation automation, int count) throws Exception {
    for (int i = 0; i < count; i++) {
      swipe(automation);
      Thread.sleep(650);
      if (overlayAction(automation, START) != null) {
        throw new AssertionError("Check-in appeared while the feed should stay quiet");
      }
    }
  }

  private static void awaitPagingPrompt(
      SmokeInstrumentation test, UiAutomation automation, long delay) throws Exception {
    test.until(
        () -> UiChecks.node(test, node -> node.isVisibleToUser() && node.getText() != null
            && node.getText().toString().startsWith("Clip ")) != null,
        "The watched app is ready for paging");
    automation.waitForIdle(500, 5_000);
    long started = SystemClock.elapsedRealtime();
    long deadline = started + delay + 45_000;
    long betweenSwipes = delay == 0 ? 2_000 : 7_500;
    while (SystemClock.elapsedRealtime() < deadline) {
      swipe(automation);
      long nextSwipe = Math.min(deadline, SystemClock.elapsedRealtime() + betweenSwipes);
      do {
        if (overlayAction(automation, START) != null) {
          test.check(
              SystemClock.elapsedRealtime() - started >= delay - 1_000,
              "Repeated page swipes trigger a check-in only after the configured interval");
          automation.waitForIdle(500, 5_000);
          return;
        }
        Thread.sleep(250);
      } while (SystemClock.elapsedRealtime() < nextSwipe);
    }
    throw new AssertionError("Repeated vertical page swipes did not trigger a check-in");
  }

  private static void tap(SmokeInstrumentation test, AccessibilityNodeInfo node) throws Exception {
    Rect bounds = new Rect();
    node.getBoundsInScreen(bounds);
    test.shell("input tap " + bounds.centerX() + " " + bounds.centerY());
  }
}
