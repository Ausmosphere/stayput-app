package app.stayput;

import android.content.*;
import android.hardware.*;
import android.os.Build;
import com.nothing.ketchum.*;

/** Optional official Nothing SDK adapter. Progress is visible when the phone is face down. */
public final class GlyphBridge implements SensorEventListener {
  private final Context context;
  private GlyphManager manager;
  private SensorManager sensors;
  private boolean connected = false, faceDown = false, attempted = false;
  private int last = -1;

  GlyphBridge(Context c) {
    context = c;
  }

  public static boolean supported() {
    if (Build.VERSION.SDK_INT < 34) return false;
    try {
      return Common.is20111()
          || Common.is22111()
          || Common.is23111()
          || Common.is23113()
          || Common.is24111()
          || Common.is25111()
          || Common.is25131();
    } catch (Throwable e) {
      return false;
    }
  }

  public void update(long remaining, long duration) {
    if (!Store.bool(context, "glyph", false) || !supported()) {
      close();
      return;
    }
    if (!attempted) {
      attempted = true;
      try {
        manager = GlyphManager.getInstance(context);
        manager.init(
            new GlyphManager.Callback() {
              public void onServiceConnected(ComponentName name) {
                try {
                  String device =
                      Common.is20111()
                          ? Glyph.DEVICE_20111
                          : Common.is22111()
                              ? Glyph.DEVICE_22111
                              : Common.is23111()
                                  ? Glyph.DEVICE_23111
                                  : Common.is23113()
                                      ? Glyph.DEVICE_23113
                                      : Common.is24111()
                                          ? Glyph.DEVICE_24111
                                          : Common.is25111()
                                              ? Glyph.DEVICE_25111
                                              : Glyph.DEVICE_25131;
                  if (!manager.register(device)) {
                    Store.set(context, "glyph_status", "SDK authorization needed on this phone");
                    return;
                  }
                  manager.openSession();
                  connected = true;
                  Store.set(context, "glyph_status", "Ready · Turn your phone face down");
                } catch (Exception e) {
                  Store.set(
                      context, "glyph_status", "Glyph unavailable · Check Nothing OS settings");
                }
              }

              public void onServiceDisconnected(ComponentName name) {
                connected = false;
              }
            });
        sensors = context.getSystemService(SensorManager.class);
        Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor != null)
          sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
      } catch (Exception e) {
        Store.set(context, "glyph_status", "Glyph SDK connection unavailable");
      }
    }
    if (!connected || manager == null) return;
    int percentage = faceDown ? (int) Math.ceil(100.0 * remaining / Math.max(duration, 1)) : 0;
    if (last == percentage) return;
    last = percentage;
    try {
      if (!faceDown) manager.turnOff();
      else {
        GlyphFrame.Builder builder = manager.getGlyphFrameBuilder();
        GlyphFrame frame =
            (Common.is20111() ? builder.buildChannelD() : builder.buildChannelC()).build();
        manager.displayProgress(frame, Math.max(0, Math.min(100, percentage)));
      }
    } catch (Exception ignored) {
    }
  }

  public void close() {
    if (sensors != null) sensors.unregisterListener(this);
    sensors = null;
    if (manager != null) {
      try {
        if (connected) {
          manager.turnOff();
          manager.closeSession();
        }
        manager.unInit();
      } catch (Exception ignored) {
      }
      manager = null;
    }
    connected = false;
    attempted = false;
    last = -1;
  }

  public void onSensorChanged(SensorEvent e) {
    faceDown = e.values[2] < -6;
  }

  public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
