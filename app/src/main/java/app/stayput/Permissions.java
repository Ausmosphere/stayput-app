package app.stayput;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

public final class Permissions {
  public static boolean accessibility(Context c) {
    AccessibilityManager manager =
        (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
    for (AccessibilityServiceInfo info :
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
      if (info.getResolveInfo().serviceInfo.packageName.equals(c.getPackageName())
          && info.getResolveInfo()
              .serviceInfo
              .name
              .equals(FocusAccessibilityService.class.getName())) return true;
    return false;
  }

  public static boolean overlay(Context c) {
    return Settings.canDrawOverlays(c);
  }

  public static boolean ready(Context c) {
    return accessibility(c) && overlay(c) && FocusAccessibilityService.instance != null;
  }

  public static boolean notifications(Context c) {
    return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
        .areNotificationsEnabled();
  }

  public static boolean exact(Context c) {
    return Build.VERSION.SDK_INT < 31
        || ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
  }

  public static boolean dnd(Context c) {
    return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
        .isNotificationPolicyAccessGranted();
  }

  public static void open(Activity a, String action) {
    Intent i = new Intent(action);
    if (action.equals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        || action.equals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
      i.setData(android.net.Uri.parse("package:" + a.getPackageName()));
    try {
      a.startActivity(i);
    } catch (ActivityNotFoundException e) {
      a.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }
  }

  public static void requestNotifications(Activity a) {
    if (Build.VERSION.SDK_INT >= 33
        && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      a.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 12);
    else
      a.startActivity(
          new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
  }
}
