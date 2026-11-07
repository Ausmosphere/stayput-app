package app.stayput;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.telecom.TelecomManager;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Only inspects foreground package names; never requests screen content. */
public class FocusAccessibilityService extends AccessibilityService {
  private static volatile WeakReference<FocusAccessibilityService> connectedService =
      new WeakReference<>(null);
  private WindowManager manager;
  private View shield;
  private TextView remaining;
  private final Set<String> keyboards = new HashSet<>();
  private String lastPackage = "";

  static FocusAccessibilityService getConnected() {
    return connectedService.get();
  }

  private void clearConnected() {
    WeakReference<FocusAccessibilityService> connection = connectedService;
    if (connection.get() == this) connection.clear();
  }

  @Override
  protected void onServiceConnected() {
    connectedService = new WeakReference<>(this);
    manager = getSystemService(WindowManager.class);
    InputMethodManager im = getSystemService(InputMethodManager.class);
    for (InputMethodInfo input : im.getEnabledInputMethodList())
      keyboards.add(input.getPackageName());
    Store.changed(this);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent e) {
    if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    CharSequence name = e.getPackageName();
    if (name == null) return;
    String pkg = name.toString();
    if (pkg.equals(getPackageName())
        || pkg.equals("com.android.systemui")
        || keyboards.contains(pkg)) return;
    lastPackage = pkg;
    if (!Store.active(this) || Store.remaining(this) <= 0) {
      hideShield();
      return;
    }
    if (isAllowed(pkg)) hideShield();
    else showShield();
  }

  private boolean isAllowed(String pkg) {
    if (Store.sessionAllowed(this).contains(pkg)) return true;
    TelecomManager telecom = getSystemService(TelecomManager.class);
    String dialer = telecom.getDefaultDialerPackage();
    return pkg.equals(dialer)
        || pkg.equals("com.android.server.telecom")
        || pkg.equals("com.android.emergency");
  }

  public void refresh() {
    if (!Store.active(this) || Store.remaining(this) <= 0) {
      hideShield();
      return;
    }
    if (remaining != null) remaining.setText(FocusMath.clock(Store.remaining(this)));
  }

  boolean isBlockingVisible() {
    return shield != null && shield.isAttachedToWindow();
  }

  private void showShield() {
    if (shield != null) return;
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    root.setGravity(Gravity.CENTER);
    Ui.pad(root, 28, 48, 40);
    root.addView(Ui.icon(this, "lock", Ui.RED, 42));
    Ui.gap(root, 28);
    TextView eyebrow = Ui.bold(this, "S T A Y  W I T H  I T", 11, Ui.RED);
    eyebrow.setGravity(Gravity.CENTER);
    root.addView(eyebrow);
    Ui.gap(root, 18);
    TextView title = Ui.text(this, "A moment away.\nA little more life.", 32, Ui.WHITE);
    Ui.heading(title);
    title.setGravity(Gravity.CENTER);
    root.addView(title);
    Ui.gap(root, 32);
    remaining = Ui.text(this, FocusMath.clock(Store.remaining(this)), 60, Ui.WHITE);
    remaining.setGravity(Gravity.CENTER);
    remaining.setTypeface(android.graphics.Typeface.create("sans-serif-light", Typeface.NORMAL));
    root.addView(remaining);
    TextView sub = Ui.text(this, "YOUR FOCUS TIME REMAINING", 10, Ui.MUTED);
    sub.setGravity(Gravity.CENTER);
    root.addView(sub);
    Ui.gap(root, 36);
    TextView caption =
        Ui.text(
            this, "This app is taking a break.\nYour essential apps are still here.", 14, Ui.MUTED);
    caption.setGravity(Gravity.CENTER);
    caption.setLineSpacing(Ui.dp(this, 5), 1);
    root.addView(caption);
    Ui.gap(root, 24);
    LinearLayout apps = Ui.row(this);
    apps.setGravity(Gravity.CENTER);
    for (String pkg : Store.sessionAllowed(this)) {
      Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
      if (launch == null) continue;
      try {
        android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
        LinearLayout item = Ui.col(this);
        item.setGravity(Gravity.CENTER);
        Ui.pad(item, 8, 6, 6);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(info.loadIcon(getPackageManager()));
        item.addView(icon, Ui.width(40, 40, this));
        TextView name = Ui.text(this, info.loadLabel(getPackageManager()).toString(), 10, Ui.MUTED);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(name, Ui.width(68, 22, this));
        item.setContentDescription("Open " + info.loadLabel(getPackageManager()));
        Ui.action(item, item.getContentDescription().toString());
        item.setOnClickListener(
            v -> {
              hideShield();
              launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              try {
                startActivity(launch);
              } catch (Exception ignored) {
                showShield();
              }
            });
        apps.addView(item);
      } catch (Exception ignored) {
      }
    }
    if (apps.getChildCount() > 0) {
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      scroll.addView(apps);
      root.addView(scroll);
      Ui.gap(root, 24);
    }
    root.addView(
        Ui.button(
            this,
            "Return to Stayput",
            true,
            () -> {
              hideShield();
              startActivity(
                  new Intent(this, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }),
        Ui.width(-1, -2, this));
    Ui.gap(root, 12);
    TextView emergency = Ui.text(this, "Phone & emergency calls", 13, Ui.MUTED);
    emergency.setGravity(Gravity.CENTER);
    emergency.setMinHeight(Ui.dp(this, 48));
    Ui.action(emergency, "Phone and emergency calls");
    emergency.setOnClickListener(
        v -> {
          Intent dial =
              new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:"))
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          String pkg = getSystemService(TelecomManager.class).getDefaultDialerPackage();
          if (pkg != null) dial.setPackage(pkg);
          try {
            hideShield();
            startActivity(dial);
          } catch (Exception ignored) {
            showShield();
          }
        });
    root.addView(emergency);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Ui.BG);
    scroll.addView(root);
    shield = scroll;
    WindowManager.LayoutParams params =
        new WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE);
    try {
      manager.addView(shield, params);
      Ui.animateIn(root);
    } catch (Exception e) {
      shield = null;
      remaining = null;
    }
  }

  void hideShield() {
    if (shield != null) {
      try {
        manager.removeView(shield);
      } catch (Exception ignored) {
      }
      shield = null;
      remaining = null;
    }
  }

  @Override
  public void onInterrupt() {
    hideShield();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    hideShield();
    clearConnected();
    Store.changed(this);
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    hideShield();
    clearConnected();
    super.onDestroy();
  }
}
