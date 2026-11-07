package app.stayput;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Device-selected communication apps are always available, including for existing routines. */
public final class AutomaticApps {
  private AutomaticApps() {}

  public static Set<String> packages(Context c) {
    Set<String> apps = new LinkedHashSet<>();
    TelecomManager telecom = c.getSystemService(TelecomManager.class);
    if (telecom != null) add(apps, telecom.getDefaultDialerPackage());
    add(apps, Telephony.Sms.getDefaultSmsPackage(c));
    add(apps, keyboardPackage(c));
    return apps;
  }

  static String keyboardPackage(Context c) {
    String selectedId =
        Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    ComponentName selected =
        selectedId == null ? null : ComponentName.unflattenFromString(selectedId);
    return selected == null ? null : selected.getPackageName();
  }

  public static Set<String> include(Context c, Set<String> selected) {
    Set<String> apps = packages(c);
    apps.addAll(selected);
    return apps;
  }

  public static Set<String> optional(Context c, Set<String> selected) {
    Set<String> apps = new HashSet<>(selected);
    apps.removeAll(packages(c));
    return apps;
  }

  private static void add(Set<String> apps, String name) {
    if (name != null && !name.isEmpty()) apps.add(name);
  }
}
