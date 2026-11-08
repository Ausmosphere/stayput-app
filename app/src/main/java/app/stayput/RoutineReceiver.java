package app.stayput;

import android.app.*;
import android.content.*;
import java.time.*;
import java.util.*;
import org.json.*;

public class RoutineReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    if ("app.stayput.ROUTINE".equals(intent.getAction())) {
      String id = intent.getStringExtra("id");
      JSONArray routines = Store.routines(c);
      for (int i = 0; i < routines.length(); i++) {
        JSONObject r = routines.optJSONObject(i);
        if (r == null || !r.optString("id").equals(id) || !r.optBoolean("enabled", true)) continue;
        if (!Store.active(c) && Permissions.ready(c)) {
          Set<String> apps = new HashSet<>();
          JSONArray a = r.optJSONArray("apps");
          if (a != null) for (int j = 0; j < a.length(); j++) apps.add(a.optString(j));
          Store.begin(c, r.optInt("duration", 30), apps, r.optString("name", "Routine"));
          try {
            BlockService.start(c);
            Store.changed(c);
          } catch (RuntimeException e) {
            Store.finish(c, false);
            remind(c, "Open Stayput to start your routine. Android restricted background startup.");
          }
        } else if (!Permissions.ready(c))
          remind(
              c,
              "Your routine needs app blocking and overlay permissions. Open Stayput to set up.");
      }
    } else if (Store.active(c)) {
      if (Store.remaining(c) <= 0) Store.finish(c, true);
      else
        try {
          BlockService.start(c);
        } catch (RuntimeException ignored) {
        }
    }
    scheduleAll(c);
  }

  static void remind(Context c, String message) {
    BlockService.channels(c);
    PendingIntent pi =
        PendingIntent.getActivity(
            c,
            3,
            new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    if (Permissions.notifications(c))
      c.getSystemService(NotificationManager.class)
          .notify(
              3,
              new Notification.Builder(c, BlockService.ROUTINES)
                  .setSmallIcon(R.drawable.ic_brand_monochrome)
                  .setContentTitle("Stayput · Routine ready")
                  .setContentText(message)
                  .setStyle(new Notification.BigTextStyle().bigText(message))
                  .setContentIntent(pi)
                  .setAutoCancel(true)
                  .build());
  }

  static PendingIntent pending(Context c, String id) {
    Intent intent =
        new Intent(c, RoutineReceiver.class)
            .setAction("app.stayput.ROUTINE")
            .setData(android.net.Uri.parse("stayput://routine/" + id))
            .putExtra("id", id);
    return PendingIntent.getBroadcast(
        c, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public static void cancel(Context c, String id) {
    c.getSystemService(AlarmManager.class).cancel(pending(c, id));
  }

  public static void scheduleAll(Context c) {
    JSONArray routines = Store.routines(c);
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    for (int i = 0; i < routines.length(); i++) {
      JSONObject r = routines.optJSONObject(i);
      if (r == null) continue;
      PendingIntent pi = pending(c, r.optString("id"));
      alarms.cancel(pi);
      if (!r.optBoolean("enabled", true)) continue;
      Set<Integer> days = new HashSet<>();
      JSONArray d = r.optJSONArray("days");
      if (d != null) for (int j = 0; j < d.length(); j++) days.add(d.optInt(j));
      long next =
          FocusMath.nextRoutine(r.optInt("hour"), r.optInt("minute"), days, ZonedDateTime.now());
      if (next < 0) continue;
      try {
        if (Permissions.exact(c))
          alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
      } catch (SecurityException e) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
      }
    }
  }
}
