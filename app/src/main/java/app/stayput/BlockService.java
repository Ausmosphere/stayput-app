package app.stayput;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Owns the persistent countdown, foreground notification, ambient audio and overlay. */
public class BlockService extends Service {
  public static final String CHANNEL = "focus", ROUTINES = "routines";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private WindowManager wm;
  private LinearLayout pill;
  private TextView clock;
  private WindowManager.LayoutParams params;
  private MediaPlayer player;
  private String playing = "";
  private long lastNotification = -1;
  private boolean cleaned = false;
  private GlyphBridge glyph;

  public static void start(Context c) {
    c.startForegroundService(new Intent(c, BlockService.class));
  }

  public static void channels(Context c) {
    NotificationManager n = c.getSystemService(NotificationManager.class);
    n.createNotificationChannel(
        new NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW));
    n.createNotificationChannel(
        new NotificationChannel(
            ROUTINES, "Routine reminders", NotificationManager.IMPORTANCE_DEFAULT));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    channels(this);
    AudioFeedback.init(this);
    wm = getSystemService(WindowManager.class);
    glyph = new GlyphBridge(this);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    cleaned = false;
    startForeground(1, notification());
    if (!Store.active(this)) {
      cleanup();
      return START_NOT_STICKY;
    }
    applyDnd();
    handler.removeCallbacks(tick);
    handler.post(tick);
    return START_STICKY;
  }

  private Notification notification() {
    Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pi =
        PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    String title =
        Permissions.ready(this)
            ? "Stayput · " + FocusMath.clock(Store.remaining(this))
            : "Stayput · Check blocking permissions";
    return new Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(title)
        .setContentText("Focus session in progress · Tap to return")
        .setContentIntent(pi)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .build();
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!Store.active(BlockService.this)) {
            cleanup();
            return;
          }
          long remaining = Store.remaining(BlockService.this);
          if (remaining <= 0) {
            Store.finish(BlockService.this, true);
            AudioFeedback.play(BlockService.this, "complete");
            Store.changed(BlockService.this);
            cleanup();
            return;
          }
          if (!MainActivity.isVisible
              && Permissions.overlay(BlockService.this)
              && Store.bool(BlockService.this, "floating", true)) {
            if (pill == null) showPill();
            if (clock != null) clock.setText(FocusMath.clock(remaining));
          } else removePill();
          long second = remaining / 1000;
          if (second != lastNotification) {
            getSystemService(NotificationManager.class).notify(1, notification());
            lastNotification = second;
          }
          if (Store.bool(BlockService.this, "dnd", false)) applyDnd();
          else restoreDnd();
          ambient();
          glyph.update(remaining, Store.duration(BlockService.this));
          FocusAccessibilityService service = FocusAccessibilityService.instance;
          if (service != null) service.refresh();
          handler.postDelayed(this, 1000);
        }
      };

  private void showPill() {
    pill = Ui.row(this);
    Ui.pad(pill, 14, 8, 8);
    Ui.surface(pill, false);
    pill.addView(Ui.icon(this, "focus", Ui.RED, 19));
    clock = Ui.bold(this, FocusMath.clock(Store.remaining(this)), 15, Ui.WHITE);
    LinearLayout.LayoutParams time = new LinearLayout.LayoutParams(-2, -2);
    time.leftMargin = Ui.dp(this, 8);
    pill.addView(clock, time);
    pill.setContentDescription("Stayput floating timer. Drag to move, tap to open.");
    params =
        new WindowManager.LayoutParams(
            Ui.dp(this, 126),
            Ui.dp(this, 48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.LEFT;
    params.x = Store.number(this, "pill_x", 0);
    params.y = Store.number(this, "pill_y", Ui.dp(this, 160));
    clamp();
    pill.setOnTouchListener(
        new View.OnTouchListener() {
          float x, y;
          int ox, oy;
          boolean moved;

          public boolean onTouch(View v, android.view.MotionEvent e) {
            switch (e.getAction()) {
              case MotionEvent.ACTION_DOWN:
                x = e.getRawX();
                y = e.getRawY();
                ox = params.x;
                oy = params.y;
                moved = false;
                return true;
              case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - x, dy = e.getRawY() - y;
                if (Math.abs(dx) + Math.abs(dy) > Ui.dp(BlockService.this, 6)) moved = true;
                params.x = ox + (int) dx;
                params.y = oy + (int) dy;
                clamp();
                try {
                  wm.updateViewLayout(pill, params);
                } catch (Exception ignored) {
                }
                return true;
              case MotionEvent.ACTION_UP:
                if (!moved) {
                  v.performClick();
                } else {
                  int width = getResources().getDisplayMetrics().widthPixels;
                  params.x = params.x + params.width / 2 < width / 2 ? 0 : width - params.width;
                  clamp();
                  try {
                    wm.updateViewLayout(pill, params);
                  } catch (Exception ignored) {
                  }
                  Store.set(BlockService.this, "pill_x", params.x);
                  Store.set(BlockService.this, "pill_y", params.y);
                }
                return true;
            }
            return false;
          }
        });
    pill.setOnClickListener(v -> openApp());
    try {
      wm.addView(pill, params);
    } catch (Exception e) {
      pill = null;
      clock = null;
    }
  }

  private void clamp() {
    android.util.DisplayMetrics m = getResources().getDisplayMetrics();
    params.x = Math.max(0, Math.min(params.x, m.widthPixels - params.width));
    params.y =
        Math.max(
            Ui.dp(this, 24), Math.min(params.y, m.heightPixels - params.height - Ui.dp(this, 48)));
  }

  private void openApp() {
    startActivity(
        new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
  }

  private void removePill() {
    if (pill != null) {
      try {
        wm.removeView(pill);
      } catch (Exception ignored) {
      }
      pill = null;
      clock = null;
    }
  }

  private void ambient() {
    String wanted = Store.text(this, "ambient", "none");
    if (wanted.equals(playing)) return;
    releaseAudio();
    playing = wanted;
    if (wanted.equals("none")) return;
    if (!java.util.Arrays.asList("rain", "waves", "noise", "city").contains(wanted)) return;
    try {
      android.content.res.AssetFileDescriptor fd = getAssets().openFd(wanted + ".wav");
      player = new MediaPlayer();
      player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build());
      player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
      fd.close();
      player.setLooping(true);
      player.setVolume(.28f, .28f);
      player.prepare();
      player.start();
    } catch (Exception e) {
      releaseAudio();
    }
  }

  private void releaseAudio() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

  private void applyDnd() {
    if (Store.bool(this, "dnd", false)
        && Permissions.dnd(this)
        && !Store.bool(this, "dnd_applied", false)) {
      NotificationManager n = getSystemService(NotificationManager.class);
      Store.set(this, "dnd_previous", n.getCurrentInterruptionFilter());
      try {
        n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        Store.set(this, "dnd_applied", true);
      } catch (SecurityException ignored) {
      }
    }
  }

  private void restoreDnd() {
    if (Store.bool(this, "dnd_applied", false)) {
      try {
        if (Permissions.dnd(this))
          getSystemService(NotificationManager.class)
              .setInterruptionFilter(
                  Store.number(this, "dnd_previous", NotificationManager.INTERRUPTION_FILTER_ALL));
      } catch (SecurityException ignored) {
      }
      Store.set(this, "dnd_applied", false);
    }
  }

  private void cleanup() {
    if (cleaned) return;
    cleaned = true;
    handler.removeCallbacks(tick);
    removePill();
    releaseAudio();
    restoreDnd();
    glyph.close();
    FocusAccessibilityService s = FocusAccessibilityService.instance;
    if (s != null) s.refresh();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(tick);
    removePill();
    releaseAudio();
    restoreDnd();
    if (glyph != null) glyph.close();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }
}
