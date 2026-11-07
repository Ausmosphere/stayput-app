package app.stayput;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/**
 * Device integration checks. Run only on a disposable emulator after granting setup permissions.
 */
public class SmokeInstrumentation extends Instrumentation {
  static Set<String> set(String... values) {
    return new HashSet<>(Arrays.asList(values));
  }

  int checks = 0;
  StringBuilder report = new StringBuilder();

  void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
    report.append("PASS ").append(message).append('\n');
    Bundle progress = new Bundle();
    progress.putString("stream", "\nPASS " + message + "\n");
    sendStatus(0, progress);
  }

  void until(BooleanSupplier condition, String message) throws Exception {
    long end = SystemClock.elapsedRealtime() + 60000;
    while (SystemClock.elapsedRealtime() < end) {
      if (condition.getAsBoolean()) {
        check(true, message);
        return;
      }
      Thread.sleep(250);
    }
    throw new AssertionError(message + " timed out");
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    int status = Activity.RESULT_CANCELED;
    Context c = getTargetContext();
    android.content.SharedPreferences prefs = c.getSharedPreferences("stayput", 0);
    Map<String, ?> saved = new HashMap<>(prefs.getAll());
    Bundle result = new Bundle();
    try {
      runOnMainSync(
          () -> {
            c.stopService(new Intent(c, BlockService.class));
          });
      prefs.edit().clear().commit();
      getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
      Store.set(c, "feedback", false);
      Store.set(c, "ambient", "none");
      Activity activity =
          startActivitySync(
              new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      // Instrumentation force-stops the target package, which leaves Android's old binding dead.
      // Reconnect the enabled service on this disposable emulator before testing the real flow.
      shell("settings put secure enabled_accessibility_services ''");
      shell(
          "settings put secure enabled_accessibility_services"
              + " app.stayput/app.stayput.FocusAccessibilityService");
      shell("settings put secure accessibility_enabled 1");
      until(
          () -> FocusAccessibilityService.getConnected() != null,
          "Accessibility service connected");
      check(
          Permissions.accessibility(c) && Permissions.overlay(c),
          "Blocking and overlay permissions granted");
      check(Permissions.ready(c), "Session readiness requires a live blocking service");
      shell("settings put secure enabled_accessibility_services ''");
      until(
          () -> FocusAccessibilityService.getConnected() == null,
          "Disconnected accessibility service is released");
      check(!Permissions.ready(c), "Disconnected service cannot report session readiness");
      shell(
          "settings put secure enabled_accessibility_services"
              + " app.stayput/app.stayput.FocusAccessibilityService");
      shell("settings put secure accessibility_enabled 1");
      until(
          () -> FocusAccessibilityService.getConnected() != null && Permissions.ready(c),
          "Accessibility service reconnects after release");
      capture("01-focus");
      MainActivity screen = (MainActivity) activity;
      UiChecks.run(this, screen);
      for (int page : new int[] {4, 1, 2, 3}) {
        runOnMainSync(
            () -> {
              screen.tab = page;
              screen.render();
            });
        capture("page-" + page);
      }
      runOnMainSync(
          () -> {
            screen.tab = 0;
            screen.render();
          });
      check(
          Store.begin(c, 5, set("com.android.settings"), "Integration session"), "Session starts");
      until(
          () ->
              "true".equals(persisted(c).get("active"))
                  && Long.toString(Store.duration(c)).equals(persisted(c).get("duration")),
          "Session start is written to disk");
      UiChecks.active(this, screen);
      check(!Store.begin(c, 60, set(), "Duplicate"), "Overlapping sessions rejected");
      Store.allowed(c, set("com.android.contacts"));
      check(
          Store.sessionAllowed(c).equals(set("com.android.settings")),
          "Active whitelist is a stable snapshot");
      long before = Store.remaining(c);
      Store.extend(c);
      check(
          Math.abs(Store.remaining(c) - before - 900000) < 2000,
          "Extension adds exactly fifteen minutes");
      until(
          () -> Long.toString(Store.duration(c)).equals(persisted(c).get("duration")),
          "Extended duration is written to disk");
      Store.set(c, "keys", 0);
      check(!Store.escape(c) && Store.active(c), "Empty key balance cannot end session");
      runOnMainSync(
          () -> {
            screen.render();
            BlockService.start(c);
          });
      capture("02-session");
      Thread.sleep(2000);
      c.startActivity(
          new Intent(android.provider.Settings.ACTION_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      Thread.sleep(4000);
      check(
          !FocusAccessibilityService.getConnected().isBlockingVisible(),
          "Whitelisted Settings remains usable");
      capture("floating-timer");
      String blocked = "com.google.android.apps.photos";
      Intent launch = c.getPackageManager().getLaunchIntentForPackage(blocked);
      if (launch == null) {
        blocked = "com.android.contacts";
        launch = c.getPackageManager().getLaunchIntentForPackage(blocked);
      }
      check(launch != null, "A non-whitelisted app is installed");
      c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      until(
          () -> FocusAccessibilityService.getConnected().isBlockingVisible(),
          "Non-whitelisted app receives full-screen block");
      capture("03-blocked-app");
      Store.set(c, "keys", 2);
      boolean[] atomicExit = {false};
      android.content.SharedPreferences.OnSharedPreferenceChangeListener exitListener =
          (preferences, key) -> {
            if ("keys".equals(key) && preferences.getInt("keys", 0) == 1)
              atomicExit[0] = !Store.active(c) && Store.history(c).length() == 1;
          };
      boolean[] escaped = {false};
      prefs.registerOnSharedPreferenceChangeListener(exitListener);
      try {
        runOnMainSync(() -> escaped[0] = Store.escape(c));
      } finally {
        prefs.unregisterOnSharedPreferenceChangeListener(exitListener);
      }
      check(escaped[0], "Escape Key ends active session");
      check(atomicExit[0], "Preference observers see a complete early-exit transaction");
      check(Store.number(c, "keys", 0) == 1, "Exactly one key consumed");
      until(
          () -> {
            Map<String, String> disk = persisted(c);
            return "false".equals(disk.get("active"))
                && "1".equals(disk.get("keys"))
                && Store.history(c).toString().equals(disk.get("history"));
          },
          "Early-exit key balance, history, and inactive state reach disk together");
      Store.changed(c);
      until(
          () -> !FocusAccessibilityService.getConnected().isBlockingVisible(),
          "Block overlay removed after early exit");
      check(
          Store.history(c).length() == 1
              && !Store.history(c).optJSONObject(0).optBoolean("completed"),
          "Early exit recorded once without tower credit");
      check(Store.focusedDays(c).isEmpty(), "Early exit does not earn a floor");
      Store.finish(c, false);
      check(Store.history(c).length() == 1, "Duplicate finish is idempotent");
      Store.begin(c, 5, set(), "Completion test");
      prefs
          .edit()
          .putLong("elapsed_end", SystemClock.elapsedRealtime() - 1)
          .putLong("end", System.currentTimeMillis() - 1)
          .commit();
      runOnMainSync(() -> BlockService.start(c));
      until(() -> !Store.active(c), "Timer expiry completes session automatically");
      check(
          Store.history(c).length() == 2
              && Store.history(c).optJSONObject(1).optBoolean("completed"),
          "Completion persisted");
      check(Store.focusedDays(c).size() == 1, "Completed session earns one floor");
      Store.begin(c, 5, set(), "Same-day test");
      Store.finish(c, true);
      check(Store.focusedDays(c).size() == 1, "Multiple sessions on one day still earn one floor");
      result.putString(
          "stream", report.toString() + "\nPASS: " + checks + " device integration checks\n");
      result.putInt("checks", checks);
      status = Activity.RESULT_OK;
    } catch (Throwable e) {
      try {
        capture("ui-failure");
      } catch (Exception ignored) {
      }
      result.putString(
          "stream", report + "\nFAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      status = Activity.RESULT_CANCELED;
    } finally {
      runOnMainSync(
          () -> {
            c.stopService(new Intent(c, BlockService.class));
            FocusAccessibilityService service = FocusAccessibilityService.getConnected();
            if (service != null) service.hideShield();
          });
      android.content.SharedPreferences.Editor edit = prefs.edit().clear();
      for (Map.Entry<String, ?> entry : saved.entrySet()) {
        Object value = entry.getValue();
        String key = entry.getKey();
        if (value instanceof String) edit.putString(key, (String) value);
        else if (value instanceof Integer) edit.putInt(key, (Integer) value);
        else if (value instanceof Long) edit.putLong(key, (Long) value);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
      }
      edit.commit();
    }
    finish(status, result);
  }

  void shell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd =
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand(command);
        java.io.InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      byte[] buffer = new byte[1024];
      while (stream.read(buffer) != -1) {}
    }
  }

  Map<String, String> persisted(Context c) {
    Map<String, String> values = new HashMap<>();
    java.io.File file =
        new java.io.File(c.getApplicationInfo().dataDir, "shared_prefs/stayput.xml");
    try (java.io.InputStream stream = new java.io.FileInputStream(file)) {
      org.xmlpull.v1.XmlPullParser xml = android.util.Xml.newPullParser();
      xml.setInput(stream, "UTF-8");
      for (int event = xml.getEventType();
          event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
          event = xml.next()) {
        if (event != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
        String key = xml.getAttributeValue(null, "name");
        if (key == null) continue;
        if ("string".equals(xml.getName())) values.put(key, xml.nextText());
        else values.put(key, xml.getAttributeValue(null, "value"));
      }
    } catch (Exception ignored) {
      // The first asynchronous write may not have created the file yet.
    }
    return values;
  }

  void capture(String name) throws Exception {
    Thread.sleep(1500);
    android.graphics.Bitmap bitmap =
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
    if (bitmap == null) return;
    java.io.File dir =
        new java.io.File(getTargetContext().getExternalFilesDir(null), "screenshots");
    dir.mkdirs();
    try (java.io.FileOutputStream stream =
        new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
    }
    bitmap.recycle();
  }
}
