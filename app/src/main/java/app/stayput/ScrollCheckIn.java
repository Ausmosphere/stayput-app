package app.stayput;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Prompts after uninterrupted time in a selected app, using package names only. */
final class ScrollCheckIn {
  static final String ENABLED = "check_in_enabled";
  static final String MINUTES = "check_in_minutes";
  static final int DEFAULT_MINUTES = 10;
  private static final long OPEN_TIMEOUT_MILLIS = 5_000;

  private final FocusAccessibilityService service;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable prompt = this::show;
  private final SharedPreferences.OnSharedPreferenceChangeListener sessionChanged =
      (preferences, key) -> {
        if ("active".equals(key)) restart();
      };
  private final BroadcastReceiver screenChanged =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) reset();
          else restart();
        }
      };
  private String foreground = "";
  private View overlay;
  private int requestedMinutes;
  private long requestExpiresAt;

  ScrollCheckIn(FocusAccessibilityService service) {
    this.service = service;
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_USER_PRESENT);
    service.registerReceiver(screenChanged, filter);
    Store.p(service).registerOnSharedPreferenceChangeListener(sessionChanged);
  }

  void foregroundChanged(String packageName) {
    if (foreground.equals(packageName)) return;
    foreground = packageName;
    restart();
  }

  int onStayputOpened() {
    foregroundChanged("");
    int minutes = SystemClock.uptimeMillis() < requestExpiresAt ? requestedMinutes : 0;
    requestedMinutes = 0;
    return minutes;
  }

  private void reset() {
    handler.removeCallbacks(prompt);
    service.removeOverlay(overlay);
    overlay = null;
  }

  private void restart() {
    reset();
    if (eligible()) handler.postDelayed(prompt, threshold() * 60_000L);
  }

  private int threshold() {
    return Store.number(service, MINUTES, DEFAULT_MINUTES);
  }

  private boolean eligible() {
    return !MainActivity.isVisible
        && !Store.active(service)
        && Store.bool(service, ENABLED, false)
        && Store.checkInApps(service).contains(foreground)
        && !AutomaticApps.packages(service).contains(foreground)
        && service.getSystemService(PowerManager.class).isInteractive()
        && !service.getSystemService(KeyguardManager.class).isKeyguardLocked();
  }

  private void show() {
    if (!eligible()) return;
    PackageManager packages = service.getPackageManager();
    CharSequence label;
    try {
      label = packages.getApplicationLabel(packages.getApplicationInfo(foreground, 0));
    } catch (PackageManager.NameNotFoundException e) {
      return;
    }

    LinearLayout root = Ui.col(service);
    root.setGravity(Gravity.CENTER);
    Ui.insets(root, 28, 28, 28);
    root.addView(Ui.brand(service, 42));
    Ui.gap(root, 24);
    TextView title = Ui.bold(service, service.getString(R.string.check_in_title), 28, Ui.WHITE);
    title.setGravity(Gravity.CENTER);
    Ui.heading(title);
    root.addView(title);
    Ui.gap(root, 12);
    int elapsed = threshold();
    TextView message =
        Ui.text(
            service,
            service
                .getResources()
                .getQuantityString(R.plurals.check_in_message, elapsed, label, elapsed),
            16,
            Ui.MUTED);
    message.setGravity(Gravity.CENTER);
    root.addView(message);
    Ui.gap(root, 28);
    int duration = Store.number(service, "minutes", 30);
    root.addView(
        Ui.button(
            service,
            service.getString(R.string.check_in_start, duration),
            true,
            () -> startCycle(duration)),
        Ui.width(-1, -2, service));
    Ui.gap(root, 10);
    root.addView(
        Ui.button(service, service.getString(R.string.check_in_continue), false, this::restart),
        Ui.width(-1, -2, service));
    overlay = service.showOverlay(root);
  }

  private void startCycle(int minutes) {
    if (overlay == null || !eligible()) return;
    restart();
    requestedMinutes = minutes;
    requestExpiresAt = SystemClock.uptimeMillis() + OPEN_TIMEOUT_MILLIS;
    try {
      service.startActivity(
          new Intent(service, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (RuntimeException e) {
      requestedMinutes = 0;
      Toast.makeText(service, R.string.check_in_open_failed, Toast.LENGTH_SHORT).show();
    }
  }

  void close() {
    reset();
    Store.p(service).unregisterOnSharedPreferenceChangeListener(sessionChanged);
    service.unregisterReceiver(screenChanged);
  }
}
