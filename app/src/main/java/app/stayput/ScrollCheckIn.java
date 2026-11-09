package app.stayput;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Prompts during sustained vertical paging in a watched app. */
final class ScrollCheckIn {
  static final String ENABLED = "check_in_enabled";
  static final String MINUTES = "check_in_minutes";
  static final int DEFAULT_MINUTES = 10;
  private static final long OPEN_TIMEOUT_MILLIS = 5_000;
  private static final long SCROLL_SETTLE_MILLIS = 350;

  enum Intensity {
    GENTLE(R.string.check_in_gentle),
    REPEAT(R.string.check_in_repeat),
    FIRM(R.string.check_in_firm);

    private static final String KEY = "check_in_intensity";
    private final int description;

    Intensity(int description) {
      this.description = description;
    }

    static Intensity load(Context context) {
      String saved = Store.text(context, KEY, GENTLE.name());
      if ("PAUSE".equals(saved)) return REPEAT;
      for (Intensity option : values()) {
        if (option.name().equals(saved)) return option;
      }
      return GENTLE;
    }

    void save(Context context) {
      Store.set(context, KEY, name());
    }

    String label(Context context) {
      return context.getString(description);
    }
  }

  private final FocusAccessibilityService service;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ReelScrollDetector detector = new ReelScrollDetector();
  private final Runnable settleScroll = this::onScrollSettled;
  private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged =
      (preferences, key) -> {
        if ("active".equals(key) || ENABLED.equals(key)) {
          resetVisit();
          updateSubscription();
        }
      };
  private final BroadcastReceiver screenChanged =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          resetVisit();
        }
      };
  private String foreground = "";
  private int foregroundWindow = -1;
  private boolean dismissedInVisit;
  private View overlay;
  private int requestedMinutes;
  private long requestExpiresAt;

  ScrollCheckIn(FocusAccessibilityService service) {
    this.service = service;
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_USER_PRESENT);
    service.registerReceiver(screenChanged, filter);
    Store.p(service).registerOnSharedPreferenceChangeListener(settingsChanged);
    updateSubscription();
  }

  private void updateSubscription() {
    service.listenForScrolls(Store.bool(service, ENABLED, false) && !Store.active(service));
  }

  void foregroundChanged(String packageName, int windowId) {
    if (foreground.equals(packageName) && foregroundWindow == windowId) return;
    if (!foreground.equals(packageName)) dismissedInVisit = false;
    foreground = packageName;
    foregroundWindow = windowId;
    resetDetection();
  }

  void onScroll(String packageName, AccessibilityEvent event) {
    if (!foreground.equals(packageName)
        || foregroundWindow != event.getWindowId()
        || !Store.checkInApps(service).contains(packageName)) return;
    if (overlay != null && Intensity.load(service) != Intensity.FIRM) return;
    if (dismissedInVisit && Intensity.load(service) == Intensity.GENTLE) return;
    CharSequence viewClass = event.getClassName();
    if (viewClass == null) return;
    detector.observe(
        viewClass.toString(),
        event.getFromIndex(),
        event.getToIndex(),
        event.getScrollX(),
        event.getScrollY(),
        Build.VERSION.SDK_INT >= 28 ? event.getScrollDeltaX() : 0,
        Build.VERSION.SDK_INT >= 28 ? event.getScrollDeltaY() : 0);
    handler.removeCallbacks(settleScroll);
    handler.postDelayed(settleScroll, SCROLL_SETTLE_MILLIS);
  }

  private void onScrollSettled() {
    if (!eligible()) {
      resetDetection();
      return;
    }
    Intensity intensity = Intensity.load(service);
    if (dismissedInVisit && intensity == Intensity.GENTLE) return;
    long delay = Store.number(service, MINUTES, DEFAULT_MINUTES) * 60_000L;
    boolean repeatOnAdvance = dismissedInVisit && intensity == Intensity.FIRM;
    if (detector.settle(SystemClock.uptimeMillis(), delay, Ui.dp(service, 16), repeatOnAdvance)
        && overlay == null) {
      show(intensity);
    }
  }

  int onStayputOpened() {
    foregroundChanged("", -1);
    int minutes = SystemClock.uptimeMillis() < requestExpiresAt ? requestedMinutes : 0;
    requestedMinutes = 0;
    return minutes;
  }

  private void resetVisit() {
    dismissedInVisit = false;
    resetDetection();
  }

  private void resetDetection() {
    handler.removeCallbacks(settleScroll);
    detector.reset();
    hideOverlay();
  }

  private void hideOverlay() {
    service.removeOverlay(overlay);
    overlay = null;
  }

  private void dismiss(Intensity intensity) {
    dismissedInVisit = true;
    if (intensity == Intensity.FIRM) {
      // The feed may have advanced behind the alert. Use its latest page as the baseline.
      handler.removeCallbacks(settleScroll);
      detector.settle(SystemClock.uptimeMillis(), 0, Ui.dp(service, 16), true);
      hideOverlay();
    } else {
      resetDetection();
    }
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

  private void show(Intensity intensity) {
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
    TextView message =
        Ui.text(
            service,
            service.getString(R.string.check_in_message, label),
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
    Runnable dismiss = () -> dismiss(intensity);
    root.addView(
        Ui.button(service, service.getString(R.string.check_in_continue), false, dismiss),
        Ui.width(-1, -2, service));
    Ui.gap(root, 16);
    TextView hint = Ui.text(service, service.getString(R.string.check_in_swipe), 13, Ui.MUTED);
    hint.setGravity(Gravity.CENTER);
    root.addView(hint);
    overlay = service.showOverlay(root, new SwipeDismissView(service, dismiss));
  }

  @android.annotation.SuppressLint("ViewConstructor") // Built only for the check-in overlay.
  private static final class SwipeDismissView extends ScrollView {
    private final Runnable dismiss;
    private final int threshold;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean canScrollUp;
    private boolean canScrollDown;

    SwipeDismissView(Context context, Runnable dismiss) {
      super(context);
      this.dismiss = dismiss;
      threshold = Ui.dp(context, 64);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        tracking = true;
        downX = event.getX();
        downY = event.getY();
        // Preserve normal scrolling when the dialog does not fit on the screen.
        canScrollUp = canScrollVertically(-1);
        canScrollDown = canScrollVertically(1);
      } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
        tracking = false;
      } else if (action == MotionEvent.ACTION_UP && tracking) {
        tracking = false;
        float dy = event.getY() - downY;
        boolean scrollsContent = dy > 0 ? canScrollUp : canScrollDown;
        if (!scrollsContent && Math.abs(dy) >= threshold
            && Math.abs(dy) > Math.abs(event.getX() - downX)) {
          MotionEvent cancel = MotionEvent.obtain(event);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
          dismiss.run();
          return true;
        }
      }
      return super.dispatchTouchEvent(event) || action == MotionEvent.ACTION_DOWN;
    }
  }

  private void startCycle(int minutes) {
    if (overlay == null || !eligible()) return;
    resetDetection();
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
    resetVisit();
    Store.p(service).unregisterOnSharedPreferenceChangeListener(settingsChanged);
    service.unregisterReceiver(screenChanged);
  }
}
