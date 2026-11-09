package app.stayput;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.*;
import java.util.*;
import org.json.*;

public final class Store {
  static SharedPreferences p(Context c) {
    return c.getSharedPreferences("stayput", Context.MODE_PRIVATE);
  }

  public static boolean bool(Context c, String key, boolean def) {
    return p(c).getBoolean(key, def);
  }

  public static void set(Context c, String key, boolean value) {
    p(c).edit().putBoolean(key, value).apply();
  }

  public static int number(Context c, String key, int def) {
    return p(c).getInt(key, def);
  }

  public static void set(Context c, String key, int value) {
    p(c).edit().putInt(key, value).apply();
  }

  public static String text(Context c, String key, String def) {
    return p(c).getString(key, def);
  }

  public static void set(Context c, String key, String value) {
    p(c).edit().putString(key, value).apply();
  }

  public static Set<String> allowed(Context c) {
    return AutomaticApps.include(c, p(c).getStringSet("allowed", Collections.emptySet()));
  }

  public static Set<String> sessionAllowed(Context c) {
    return AutomaticApps.include(c, p(c).getStringSet("session_allowed", Collections.emptySet()));
  }

  public static void allowed(Context c, Set<String> apps) {
    p(c).edit().putStringSet("allowed", AutomaticApps.optional(c, apps)).apply();
  }

  static Set<String> checkInApps(Context c) {
    return p(c).getStringSet("check_in_apps", Collections.emptySet());
  }

  static void checkInApps(Context c, Set<String> apps) {
    p(c).edit().putStringSet("check_in_apps", new HashSet<>(apps)).apply();
  }

  public static boolean active(Context c) {
    return p(c).getBoolean("active", false);
  }

  public static long remaining(Context c) {
    return FocusMath.remaining(
        p(c).getLong("end", 0),
        p(c).getLong("elapsed_end", 0),
        System.currentTimeMillis(),
        SystemClock.elapsedRealtime(),
        p(c).getInt("boot", -1) == boot(c));
  }

  static int boot(Context c) {
    return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
  }

  public static synchronized boolean begin(Context c, int minutes, Set<String> apps, String label) {
    if (active(c)) return false;
    long duration = minutes * 60000L;
    p(c).edit()
        .putBoolean("active", true)
        .putLong("start", System.currentTimeMillis())
        .putLong("duration", duration)
        .putLong("end", System.currentTimeMillis() + duration)
        .putLong("elapsed_end", SystemClock.elapsedRealtime() + duration)
        .putInt("boot", boot(c))
        .putStringSet("session_allowed", AutomaticApps.optional(c, apps))
        .putString("session_label", label)
        .apply();
    return true;
  }

  public static synchronized void extend(Context c) {
    if (!active(c)) return;
    long left = remaining(c);
    p(c).edit()
        .putLong("end", System.currentTimeMillis() + left + 900000)
        .putLong("elapsed_end", SystemClock.elapsedRealtime() + left + 900000)
        .putInt("boot", boot(c))
        .putLong("duration", duration(c) + 900000)
        .apply();
  }

  public static long duration(Context c) {
    return p(c).getLong("duration", 1800000);
  }

  public static synchronized void finish(Context c, boolean completed) {
    finish(c, completed, p(c).edit());
  }

  private static void finish(Context c, boolean completed, SharedPreferences.Editor edit) {
    if (!active(c)) return;
    long focused = completed ? duration(c) : Math.max(0, duration(c) - remaining(c));
    JSONArray h = history(c);
    JSONObject item = new JSONObject();
    try {
      item.put("date", LocalDate.now().toString())
          .put("end", System.currentTimeMillis())
          .put("millis", focused)
          .put("completed", completed)
          .put("label", text(c, "session_label", "Focus session"));
      h.put(item);
    } catch (JSONException ignored) {
    }
    edit.putBoolean("active", false).putString("history", h.toString()).apply();
  }

  public static synchronized boolean escape(Context c) {
    int keys = number(c, "keys", 3);
    if (keys <= 0 || !active(c)) return false;
    finish(c, false, p(c).edit().putInt("keys", keys - 1));
    return true;
  }

  public static JSONArray history(Context c) {
    return array(text(c, "history", "[]"));
  }

  public static JSONArray routines(Context c) {
    return array(text(c, "routines", "[]"));
  }

  public static JSONArray array(String s) {
    try {
      return new JSONArray(s);
    } catch (JSONException e) {
      return new JSONArray();
    }
  }

  public static Set<LocalDate> focusedDays(Context c) {
    Set<LocalDate> dates = new HashSet<>();
    JSONArray h = history(c);
    for (int i = 0; i < h.length(); i++) {
      JSONObject o = h.optJSONObject(i);
      if (o != null && o.optBoolean("completed"))
        try {
          dates.add(LocalDate.parse(o.optString("date")));
        } catch (Exception ignored) {
        }
    }
    return dates;
  }

  public static long total(Context c) {
    long total = 0;
    JSONArray h = history(c);
    for (int i = 0; i < h.length(); i++) total += h.optJSONObject(i).optLong("millis");
    return total;
  }

  public static void changed(Context c) {
    c.sendBroadcast(new Intent("app.stayput.CHANGED").setPackage(c.getPackageName()));
  }
}
