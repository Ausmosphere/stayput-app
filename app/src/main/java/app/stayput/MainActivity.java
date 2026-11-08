package app.stayput;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

public class MainActivity extends Activity {
  static volatile boolean isVisible = false;
  LinearLayout root, content, nav;
  ScrollView scroll;
  int tab = 0, minutes = 30, chartDays = 7;
  DialView dial;
  TextView liveLabel;
  boolean showingActive, blockingReady;
  final Handler handler = new Handler(Looper.getMainLooper());
  MediaPlayer preview;
  private static final int DIAL_ID = View.generateViewId();
  private static final int[] NAV_IDS = {
    View.generateViewId(), View.generateViewId(), View.generateViewId(), View.generateViewId()
  };
  private static final int[] PRESET_IDS = {
    View.generateViewId(), View.generateViewId(), View.generateViewId()
  };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Ui.BG);
    getWindow().setNavigationBarColor(Ui.BG);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    minutes = Store.number(this, "minutes", 30);
    if (state != null) tab = state.getInt("tab", 0);
    AudioFeedback.init(this);
    RoutineReceiver.scheduleAll(this);
    render();
  }

  @Override
  public void onResume() {
    super.onResume();
    isVisible = true;
    FocusAccessibilityService blockingService = FocusAccessibilityService.getConnected();
    if (blockingService != null) blockingService.hideShield();
    render();
    if (Store.active(this))
      try {
        BlockService.start(this);
      } catch (RuntimeException ignored) {
      }
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  public void onPause() {
    isVisible = false;
    handler.removeCallbacks(tick);
    stopPreview();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    stopPreview();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    state.putInt("tab", tab);
    super.onSaveInstanceState(state);
  }

  @Override
  public void onBackPressed() {
    if (Store.active(this)) {
      tab = 0;
      render();
      moveTaskToBack(true);
    } else if (tab != 0) {
      tab = 0;
      render();
    } else super.onBackPressed();
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          boolean active = Store.active(MainActivity.this);
          if (active != showingActive || Permissions.ready(MainActivity.this) != blockingReady) {
            boolean finished = showingActive && !active;
            render();
            if (finished && tab == 0) toast("Session finished. Your time, well spent.");
          }
          if (active && dial != null)
            dial.countdown(Store.remaining(MainActivity.this), Store.duration(MainActivity.this));
          handler.postDelayed(this, 1000);
        }
      };

  void render() {
    int focusedId = getCurrentFocus() != null ? getCurrentFocus().getId() : View.NO_ID;
    showingActive = Store.active(this);
    blockingReady = Permissions.ready(this);
    dial = null;
    root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.insets(root, 0, 0, 0);
    LinearLayout header = Ui.row(this);
    Ui.pad(header, 24, 14, 10);
    ImageView mark = Ui.brand(this, 42);
    header.addView(mark, Ui.width(42, 42, this));
    TextView name = Ui.bold(this, "stayput", 23, Ui.WHITE);
    LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(0, -2, 1);
    nameP.leftMargin = Ui.dp(this, 12);
    header.addView(name, nameP);
    LinearLayout keys = Ui.row(this);
    Ui.pad(keys, 12, 9, 9);
    Ui.surface(keys, false);
    keys.addView(Ui.icon(this, "key", Ui.RED, 17));
    TextView keyCount = Ui.bold(this, "  " + Store.number(this, "keys", 3), 13, Ui.WHITE);
    keys.addView(keyCount);
    keys.setContentDescription("Escape Keys: " + Store.number(this, "keys", 3));
    Ui.action(keys, keys.getContentDescription().toString());
    keys.setOnClickListener(v -> keysDialog());
    keys.setMinimumWidth(Ui.dp(this, 72));
    header.addView(keys, Ui.width(-2, -2, this));
    root.addView(header);
    scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    content = Ui.col(this);
    Ui.pad(content, 24, 16, 28);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    switch (tab) {
      case 1:
        routinesPage();
        break;
      case 2:
        towerPage();
        break;
      case 3:
        settingsPage();
        break;
      case 4:
        permissionPage();
        break;
      default:
        focusPage();
    }
    if (tab == 0 && !showingActive) {
      LinearLayout dock = Ui.col(this);
      Ui.pad(dock, 24, 7, 7);
      dock.addView(
          Ui.button(this, "Start focus session   →", true, () -> prepareSession()),
          Ui.width(-1, -2, this));
      root.addView(dock);
    }
    nav = Ui.row(this);
    Ui.pad(nav, 16, 8, 10);
    String[] labels = {"Focus", "Routines", "Tower", "Settings"};
    String[] icons = {"focus", "routines", "tower", "settings"};
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout item = Ui.col(this);
      item.setId(NAV_IDS[i]);
      item.setMinimumHeight(Ui.dp(this, 65));
      item.setGravity(Gravity.CENTER);
      Ui.pad(item, 0, 8, 8);
      boolean selected = tab == i || (tab == 4 && i == 3);
      if (selected) Ui.surface(item, true);
      item.addView(Ui.icon(this, icons[i], selected ? Ui.RED : Ui.MUTED, 22));
      Ui.gap(item, 7);
      TextView label = Ui.bold(this, labels[i], 10, selected ? Ui.WHITE : Ui.MUTED);
      label.setGravity(Gravity.CENTER);
      label.setMaxLines(2);
      label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      item.addView(label);
      Ui.action(item, labels[i]);
      item.setSelected(selected);
      item.setOnClickListener(
          v -> {
            stopPreview();
            AudioFeedback.play(this, "tap");
            tab = index;
            render();
          });
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(nav);
    setContentView(root);
    root.requestApplyInsets();
    if (focusedId != View.NO_ID) {
      View restore = root.findViewById(focusedId);
      if (restore != null) restore.requestFocus();
    }
    Ui.animateIn(content);
  }

  TextView title(String eyebrow, String heading, String subtitle) {
    TextView e = Ui.bold(this, eyebrow, 10, Ui.RED);
    e.setLetterSpacing(.14f);
    content.addView(e);
    Ui.gap(content, 12);
    TextView h = Ui.text(this, heading, 33, Ui.WHITE);
    Ui.heading(h);
    h.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    h.setLineSpacing(Ui.dp(this, 1), 1);
    content.addView(h);
    if (subtitle != null) {
      Ui.gap(content, 10);
      TextView s = Ui.text(this, subtitle, 13, Ui.MUTED);
      s.setLineSpacing(Ui.dp(this, 4), 1);
      content.addView(s);
    }
    Ui.gap(content, 24);
    return h;
  }

  LinearLayout card() {
    LinearLayout c = Ui.col(this);
    Ui.surface(c, false);
    Ui.pad(c, 18, 18, 18);
    content.addView(c, new LinearLayout.LayoutParams(-1, -2));
    return c;
  }

  void caption(String s) {
    TextView t = Ui.bold(this, s, 10, Ui.MUTED);
    t.setLetterSpacing(.12f);
    content.addView(t);
    Ui.gap(content, 12);
  }

  void focusPage() {
    if (showingActive) {
      activePage();
      return;
    }
    title(getString(R.string.brand_eyebrow), getString(R.string.brand_home_heading), null)
        .setTextSize(30);
    if (!Permissions.ready(this)) {
      LinearLayout banner = Ui.row(this);
      Ui.surface(banner, true);
      Ui.pad(banner, 14, 13, 13);
      banner.addView(Ui.icon(this, "shield", Ui.RED, 22));
      LinearLayout txt = Ui.col(this);
      TextView a = Ui.bold(this, "One small setup", 13, Ui.WHITE);
      txt.addView(a);
      Ui.gap(txt, 4);
      txt.addView(Ui.text(this, "Enable blocking & floating timer", 11, Ui.MUTED));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.leftMargin = Ui.dp(this, 12);
      banner.addView(txt, p);
      banner.addView(Ui.icon(this, "arrow", Ui.MUTED, 18));
      banner.setOnClickListener(
          v -> {
            tab = 4;
            render();
          });
      Ui.action(banner, "One small setup. Enable blocking and the floating timer");
      content.addView(banner);
      Ui.gap(content, 16);
    }
    dial = new DialView(this);
    dial.setId(DIAL_ID);
    dial.setMinutes(minutes);
    dial.listener =
        n -> {
          minutes = n;
          Store.set(this, "minutes", n);
          int[] values = {15, 30, 60};
          for (int i = 0; i < values.length; i++) {
            TextView preset = root.findViewById(PRESET_IDS[i]);
            if (preset == null) continue;
            preset.setSelected(n == values[i]);
            preset.setTextColor(n == values[i] ? Ui.RED : Ui.MUTED);
            Ui.surface(preset, n == values[i]);
          }
        };
    content.addView(dial, Ui.width(-1, 240, this));
    TextView hint = Ui.text(this, "TURN THE DIAL. TAKE YOUR TIME.", 9, Ui.MUTED);
    hint.setGravity(Gravity.CENTER);
    hint.setLetterSpacing(.1f);
    content.addView(hint);
    Ui.gap(content, 18);
    LinearLayout presets = Ui.row(this);
    presets.setGravity(Gravity.CENTER);
    int presetIndex = 0;
    for (int n : new int[] {15, 30, 60}) {
      TextView p = Ui.bold(this, n + " min", 12, n == minutes ? Ui.RED : Ui.MUTED);
      p.setId(PRESET_IDS[presetIndex++]);
      p.setGravity(Gravity.CENTER);
      p.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
      Ui.action(p, n + " minute preset");
      p.setSelected(n == minutes);
      Ui.surface(p, n == minutes);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
      presets.addView(p, lp);
      p.setOnClickListener(
          v -> {
            minutes = n;
            Store.set(this, "minutes", n);
            AudioFeedback.play(this, "tap");
            render();
          });
    }
    content.addView(presets);
    Ui.gap(content, 24);
    essentialsCard(Store.allowed(this), true);
    Ui.gap(content, 12);
    LinearLayout sound = Ui.row(this);
    Ui.surface(sound, false);
    Ui.pad(sound, 17, 13, 13);
    sound.addView(Ui.icon(this, "sound", Ui.MUTED, 21));
    TextView label = Ui.bold(this, "Soundscape", 13, Ui.WHITE);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = Ui.dp(this, 12);
    LinearLayout soundWords = Ui.col(this);
    soundWords.addView(label);
    Ui.gap(soundWords, 5);
    String currentSound = soundName(Store.text(this, "ambient", "none"));
    soundWords.addView(Ui.text(this, currentSound, 12, Ui.MUTED));
    sound.addView(soundWords, lp);
    sound.addView(Ui.icon(this, "arrow", Ui.MUTED, 19));
    sound.setOnClickListener(v -> soundDialog());
    Ui.action(sound, "Soundscape. " + currentSound);
    content.addView(sound);
    Ui.gap(content, 24);
    TextView footer = Ui.text(this, "Your phone can wait. This moment can't.", 11, Ui.MUTED);
    footer.setGravity(Gravity.CENTER);
    content.addView(footer);
  }

  void essentialsCard(Set<String> apps, boolean editable) {
    LinearLayout c = card();
    LinearLayout row = Ui.row(this);
    row.addView(Ui.icon(this, "bag", Ui.RED, 20));
    TextView t = Ui.bold(this, "Essential apps", 13, Ui.WHITE);
    LinearLayout.LayoutParams textP = new LinearLayout.LayoutParams(0, -2, 1);
    textP.leftMargin = Ui.dp(this, 10);
    row.addView(t, textP);
    row.addView(Ui.icon(this, editable ? "plus" : "check", Ui.MUTED, 20));
    c.addView(row);
    Ui.gap(c, 12);
    if (apps.isEmpty())
      c.addView(Ui.text(this, "Add any other apps you need during focus.", 11, Ui.MUTED));
    else {
      LinearLayout icons = Ui.row(this);
      for (String pkg : apps) {
        try {
          ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
          ImageView v = new ImageView(this);
          v.setImageDrawable(info.loadIcon(getPackageManager()));
          v.setContentDescription(info.loadLabel(getPackageManager()));
          LinearLayout.LayoutParams p = Ui.width(32, 32, this);
          p.rightMargin = Ui.dp(this, 10);
          icons.addView(v, p);
        } catch (Exception ignored) {
        }
      }
      HorizontalScrollView s = new HorizontalScrollView(this);
      s.setHorizontalScrollBarEnabled(false);
      s.addView(icons);
      c.addView(s);
    }
    Ui.gap(c, 10);
    c.addView(Ui.text(this, getString(R.string.automatic_apps_description), 11, Ui.MUTED));
    if (editable) {
      Ui.action(
          c,
          "Edit essential apps. "
              + apps.size()
              + " included. "
              + getString(R.string.automatic_apps_description));
      c.setOnClickListener(
          v ->
              chooseApps(
                  apps,
                  result -> {
                    Store.allowed(this, result);
                    render();
                  }));
    }
  }

  void prepareSession() {
    if (!Permissions.ready(this)) {
      tab = 4;
      render();
      toast("Enable app blocking and the floating timer first.");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.brand_session_confirm)
        .setMessage(
            "Start a "
                + minutes
                + " minute session?\n\n"
                + "Only your essential apps and phone calls will be available. Ending early uses"
                + " one Escape Key.")
        .setNegativeButton("Not yet", null)
        .setPositiveButton(
            "Let's focus",
            (d, w) -> {
              if (!Permissions.ready(this)) {
                tab = 4;
                render();
                return;
              }
              if (Store.begin(this, minutes, Store.allowed(this), "Focus session")) {
                try {
                  BlockService.start(this);
                  AudioFeedback.play(this, "start");
                  render();
                } catch (RuntimeException e) {
                  Store.finish(this, false);
                  toast("Android couldn't start the session. Please try again.");
                  render();
                }
              }
            })
        .show();
  }

  void activePage() {
    title(
        "SESSION IN PROGRESS",
        getString(R.string.brand_session_heading),
        Store.text(this, "session_label", "Focus session"));
    if (!Permissions.ready(this)) {
      TextView warning =
          Ui.button(
              this,
              "Blocking interrupted · Check permissions",
              false,
              () -> {
                tab = 4;
                render();
              });
      content.addView(warning);
      Ui.gap(content, 16);
    }
    dial = new DialView(this);
    dial.countdown(Store.remaining(this), Store.duration(this));
    content.addView(dial, Ui.width(-1, 300, this));
    Ui.gap(content, 12);
    TextView breathe = Ui.text(this, getString(R.string.brand_session_reminder), 13, Ui.MUTED);
    breathe.setGravity(Gravity.CENTER);
    content.addView(breathe);
    if (Ui.motionEnabled())
      breathe
          .animate()
          .alpha(.45f)
          .setDuration(2000)
          .withEndAction(() -> breathe.animate().alpha(1).setDuration(2000).start())
          .start();
    Ui.gap(content, 28);
    essentialsCard(Store.sessionAllowed(this), false);
    Ui.gap(content, 12);
    LinearLayout launch = Ui.row(this);
    for (String pkg : Store.sessionAllowed(this)) {
      try {
        String name =
            getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                .toString();
        TextView button =
            Ui.button(
                this,
                name,
                false,
                () -> {
                  Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                  if (i != null)
                    try {
                      startActivity(i);
                    } catch (Exception e) {
                      toast("This app is unavailable.");
                    }
                });
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams appP = Ui.width(160, -2, this);
        appP.rightMargin = Ui.dp(this, 8);
        launch.addView(button, appP);
      } catch (Exception ignored) {
      }
    }
    if (launch.getChildCount() > 0) {
      HorizontalScrollView appScroll = new HorizontalScrollView(this);
      appScroll.setHorizontalScrollBarEnabled(false);
      appScroll.addView(launch);
      content.addView(appScroll);
      Ui.gap(content, 14);
    }
    TextView extend =
        new Ui.HoldButton(
            this,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Add 15 minutes?")
                    .setMessage("Extend this focus session by 15 minutes.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                        "Add 15 minutes",
                        (d, w) -> {
                          if (Store.active(this)) {
                            Store.extend(this);
                            dial.countdown(Store.remaining(this), Store.duration(this));
                            toast("15 minutes added.");
                          }
                        })
                    .show(),
            () -> {
              if (Store.active(this)) {
                Store.extend(this);
                AudioFeedback.play(this, "tap");
                dial.countdown(Store.remaining(this), Store.duration(this));
              }
            });
    content.addView(extend, Ui.width(-1, -2, this));
    Ui.gap(content, 16);
    content.addView(
        Ui.button(this, "Use an Escape Key", false, () -> keysDialog()), Ui.width(-1, -2, this));
    Ui.gap(content, 8);
    TextView note = Ui.text(this, "Need a different sound? Change it in Settings.", 11, Ui.MUTED);
    note.setGravity(Gravity.CENTER);
    content.addView(note);
  }

  void keysDialog() {
    int keys = Store.number(this, "keys", 3);
    AlertDialog.Builder b =
        new AlertDialog.Builder(this)
            .setTitle(keys + " Escape Keys")
            .setMessage(
                "An Escape Key ends a session early. Keep one for when life happens.\n\n"
                    + "MVP demo: keys are stored on this device. No purchases or payments.");
    b.setNegativeButton("Keep focusing", null);
    b.setNeutralButton(
        "Add 3 demo keys",
        (d, w) -> {
          Store.set(this, "keys", keys + 3);
          render();
          toast("3 demo keys added.");
        });
    if (Store.active(this) && keys > 0)
      b.setPositiveButton(
          "Use one key",
          (d, w) -> {
            if (Store.escape(this)) {
              Store.changed(this);
              stopService(new Intent(this, BlockService.class));
              render();
              toast("Session ended. Take care of what matters.");
            }
          });
    b.show();
  }

  void routinesPage() {
    title(
        "A RHYTHM THAT STICKS",
        "Good habits.\nOn repeat.",
        "Make focus a regular part of your day.");
    JSONArray routines = Store.routines(this);
    if (!Permissions.exact(this)) {
      TextView timing =
          Ui.button(
              this,
              "Enable precise routine timing   →",
              false,
              () -> Permissions.open(this, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
      content.addView(timing);
      Ui.gap(content, 18);
      TextView hint =
          Ui.text(this, "Without precise timing, Android may delay routines.", 11, Ui.MUTED);
      content.addView(hint);
      Ui.gap(content, 20);
    }
    if (routines.length() == 0) {
      LinearLayout empty = card();
      empty.setGravity(Gravity.CENTER);
      Ui.gap(empty, 18);
      empty.addView(Ui.icon(this, "routines", Ui.RED, 40));
      Ui.gap(empty, 18);
      TextView emptyTitle = Ui.bold(this, "Your day, with a little space.", 17, Ui.WHITE);
      emptyTitle.setGravity(Gravity.CENTER);
      empty.addView(emptyTitle);
      Ui.gap(empty, 10);
      TextView sub =
          Ui.text(
              this,
              "A quiet morning. A focused workday.\nAn earlier night. Start with one routine.",
              13,
              Ui.MUTED);
      sub.setGravity(Gravity.CENTER);
      sub.setLineSpacing(Ui.dp(this, 5), 1);
      empty.addView(sub);
      Ui.gap(empty, 18);
      Ui.gap(content, 24);
    }
    for (int i = 0; i < routines.length(); i++) {
      final int index = i;
      JSONObject r = routines.optJSONObject(i);
      if (r == null) continue;
      LinearLayout c = card();
      LinearLayout row = Ui.row(this);
      LinearLayout desc = Ui.col(this);
      desc.addView(Ui.bold(this, r.optString("name"), 16, Ui.WHITE));
      Ui.gap(desc, 8);
      desc.addView(
          Ui.text(this, routineTime(r) + "   ·   " + r.optInt("duration") + " min", 13, Ui.MUTED));
      Ui.gap(desc, 8);
      desc.addView(Ui.text(this, daysLabel(r.optJSONArray("days")), 11, Ui.MUTED));
      row.addView(desc, new LinearLayout.LayoutParams(0, -2, 1));
      Switch sw = new Switch(this);
      sw.setMinimumHeight(Ui.dp(this, 48));
      sw.setMinimumWidth(Ui.dp(this, 48));
      sw.setChecked(r.optBoolean("enabled", true));
      sw.setContentDescription("Enable " + r.optString("name"));
      sw.setOnCheckedChangeListener(
          (v, on) -> {
            try {
              r.put("enabled", on);
              Store.set(this, "routines", routines.toString());
              RoutineReceiver.scheduleAll(this);
            } catch (JSONException ignored) {
            }
          });
      row.addView(sw);
      c.addView(row);
      Ui.gap(c, 14);
      LinearLayout actions = Ui.row(this);
      TextView edit = Ui.text(this, "Edit routine", 12, Ui.RED);
      Ui.action(edit, "Edit " + r.optString("name"));
      edit.setGravity(Gravity.CENTER_VERTICAL);
      edit.setOnClickListener(v -> routineEditor(index));
      actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
      TextView delete = Ui.text(this, "Delete", 12, Ui.MUTED);
      Ui.action(delete, "Delete " + r.optString("name"));
      delete.setGravity(Gravity.CENTER_VERTICAL);
      delete.setOnClickListener(
          v ->
              new AlertDialog.Builder(this)
                  .setTitle("Delete this routine?")
                  .setNegativeButton("Keep", null)
                  .setPositiveButton(
                      "Delete",
                      (d, w) -> {
                        RoutineReceiver.cancel(this, r.optString("id"));
                        routines.remove(index);
                        Store.set(this, "routines", routines.toString());
                        render();
                      })
                  .show());
      actions.addView(delete);
      c.addView(actions);
      Ui.gap(content, 16);
    }
    content.addView(
        Ui.button(this, "+   Create a routine", true, () -> routineEditor(-1)),
        Ui.width(-1, -2, this));
  }

  String daysLabel(JSONArray days) {
    if (days == null || days.length() == 0) return "No days selected";
    if (days.length() == 7) return "Every day";
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < days.length(); i++) {
      if (i > 0) s.append(" · ");
      s.append(
          DayOfWeek.of(days.optInt(i, 1)).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
    }
    return s.toString();
  }

  String routineTime(JSONObject routine) {
    Calendar time = Calendar.getInstance();
    time.set(Calendar.HOUR_OF_DAY, routine.optInt("hour"));
    time.set(Calendar.MINUTE, routine.optInt("minute"));
    return android.text.format.DateFormat.getTimeFormat(this).format(time.getTime());
  }

  void routineEditor(int index) {
    JSONArray routines = Store.routines(this);
    JSONObject existing = index >= 0 ? routines.optJSONObject(index) : null;
    LinearLayout form = Ui.col(this);
    Ui.pad(form, 24, 12, 16);
    EditText name = new EditText(this);
    name.setId(View.generateViewId());
    TextView nameLabel = Ui.bold(this, "Routine name", 13, Ui.WHITE);
    nameLabel.setLabelFor(name.getId());
    form.addView(nameLabel);
    name.setSingleLine(true);
    name.setHint("Routine name");
    name.setText(existing != null ? existing.optString("name") : "Evening offline");
    form.addView(name);
    TimePicker time = new TimePicker(this);
    time.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this));
    time.setHour(existing != null ? existing.optInt("hour") : 21);
    time.setMinute(existing != null ? existing.optInt("minute") : 0);
    form.addView(time);
    TextView duration = Ui.text(this, "Duration", 13, Ui.MUTED);
    form.addView(duration);
    NumberPicker picker = new NumberPicker(this);
    picker.setId(View.generateViewId());
    duration.setLabelFor(picker.getId());
    picker.setMinValue(1);
    picker.setMaxValue(36);
    String[] durations = new String[36];
    for (int i = 0; i < 36; i++) durations[i] = ((i + 1) * 5) + " minutes";
    picker.setDisplayedValues(durations);
    picker.setValue(
        existing != null ? Math.max(1, Math.min(36, existing.optInt("duration", 30) / 5)) : 6);
    form.addView(picker, Ui.width(-1, -2, this));
    form.addView(Ui.bold(this, "Repeat days", 13, Ui.WHITE));
    GridLayout dayRow = new GridLayout(this);
    dayRow.setColumnCount(4);
    Set<Integer> selectedDays = new HashSet<>();
    JSONArray old = existing != null ? existing.optJSONArray("days") : null;
    if (old != null) for (int j = 0; j < old.length(); j++) selectedDays.add(old.optInt(j));
    else selectedDays.addAll(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
    for (int i = 1; i <= 7; i++) {
      final int day = i;
      TextView d =
          Ui.bold(
              this,
              DayOfWeek.of(i).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
              12,
              selectedDays.contains(i) ? Ui.RED : Ui.MUTED);
      d.setGravity(Gravity.CENTER);
      Ui.surface(d, selectedDays.contains(i));
      String dayName = DayOfWeek.of(i).getDisplayName(TextStyle.FULL, Locale.getDefault());
      Ui.action(d, dayName);
      d.setSelected(selectedDays.contains(i));
      d.setOnClickListener(
          v -> {
            if (selectedDays.contains(day)) selectedDays.remove(day);
            else selectedDays.add(day);
            d.setTextColor(selectedDays.contains(day) ? Ui.RED : Ui.MUTED);
            d.setSelected(selectedDays.contains(day));
            Ui.surface(d, selectedDays.contains(day));
          });
      GridLayout.LayoutParams dayParams =
          new GridLayout.LayoutParams(
              GridLayout.spec((i - 1) / 4), GridLayout.spec((i - 1) % 4, 1f));
      dayParams.width = 0;
      dayParams.height = -2;
      dayRow.addView(d, dayParams);
    }
    form.addView(dayRow);
    TextView dayError = Ui.text(this, "Choose at least one day.", 12, Ui.RED);
    dayError.setVisibility(View.GONE);
    dayError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    form.addView(dayError);
    Ui.gap(form, 16);
    Set<String> apps = new HashSet<>();
    JSONArray a = existing != null ? existing.optJSONArray("apps") : null;
    if (a != null) for (int j = 0; j < a.length(); j++) apps.add(a.optString(j));
    else apps.addAll(Store.allowed(this));
    apps.addAll(AutomaticApps.packages(this));
    TextView appsButton =
        Ui.button(
            this,
            getResources()
                .getQuantityString(R.plurals.essential_apps_edit, apps.size(), apps.size()),
            false,
            () ->
                chooseApps(
                    apps,
                    result -> {
                      apps.clear();
                      apps.addAll(result);
                    }));
    appsButton.setOnClickListener(
        v ->
            chooseApps(
                apps,
                result -> {
                  apps.clear();
                  apps.addAll(result);
                  appsButton.setText(
                      getResources()
                          .getQuantityString(
                              R.plurals.essential_apps_edit, apps.size(), apps.size()));
                  appsButton.setContentDescription(appsButton.getText());
                }));
    java.util.function.Supplier<List<Object>> draft =
        () ->
            Arrays.asList(
                name.getText().toString(),
                time.getHour(),
                time.getMinute(),
                picker.getValue(),
                new TreeSet<>(selectedDays),
                new TreeSet<>(apps));
    List<Object> originalDraft = draft.get();
    form.addView(appsButton);
    ScrollView sc = new ScrollView(this);
    sc.addView(form);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Create a routine" : "Edit routine")
            .setView(sc)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save routine", null)
            .create();
    dialog.setOnShowListener(
        x ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      if (name.getText().toString().trim().isEmpty()) {
                        name.setError("Give your routine a name");
                        name.requestFocus();
                        return;
                      }
                      if (selectedDays.isEmpty()) {
                        dayError.setVisibility(View.VISIBLE);
                        dayRow.getChildAt(0).requestFocus();
                        return;
                      }
                      try {
                        JSONObject r = new JSONObject();
                        r.put(
                                "id",
                                existing != null
                                    ? existing.optString("id")
                                    : UUID.randomUUID().toString())
                            .put("name", name.getText().toString().trim())
                            .put("hour", time.getHour())
                            .put("minute", time.getMinute())
                            .put("duration", picker.getValue() * 5)
                            .put("days", new JSONArray(new TreeSet<>(selectedDays)))
                            .put("apps", new JSONArray(AutomaticApps.optional(this, apps)))
                            .put(
                                "enabled",
                                existing == null || existing.optBoolean("enabled", true));
                        if (index < 0) routines.put(r);
                        else routines.put(index, r);
                        Store.set(this, "routines", routines.toString());
                        RoutineReceiver.scheduleAll(this);
                        dialog.dismiss();
                        render();
                        if (!Permissions.ready(this))
                          toast("Set up blocking permissions before this routine runs.");
                      } catch (JSONException e) {
                        toast("Couldn't save. Please try again.");
                      }
                    }));
    dialog.show();
    Runnable cancel = () -> dismissDraft(dialog, () -> !draft.get().equals(originalDraft));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> cancel.run());
    guardDialogBack(dialog, cancel);
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }

  void towerPage() {
    Set<LocalDate> days = Store.focusedDays(this);
    int streak = FocusMath.streak(days, LocalDate.now());
    title(
        "SMALL MOMENTS. SOMETHING BIG.",
        "Build a life\nbeyond your screen.",
        "Every day you finish a session adds a floor.");
    LinearLayout stats = Ui.row(this);
    stat(stats, "CURRENT STREAK", streak + " days");
    stat(stats, "FOCUS TIME", Ui.duration(Store.total(this)));
    content.addView(stats);
    Ui.gap(content, 24);
    LinearLayout tower = card();
    TowerView tv = new TowerView(this, days.size());
    tower.addView(tv, Ui.width(-1, 240, this));
    TextView level =
        Ui.bold(
            this,
            days.isEmpty()
                ? "Your first floor is waiting."
                : days.size() + " floors. Built by you.",
            17,
            Ui.WHITE);
    level.setGravity(Gravity.CENTER);
    tower.addView(level);
    Ui.gap(tower, 9);
    TextView line =
        Ui.text(
            this,
            days.isEmpty()
                ? "Finish a focus session to lay the foundation."
                : "Keep showing up. Your tower will follow.",
            12,
            Ui.MUTED);
    line.setGravity(Gravity.CENTER);
    tower.addView(line);
    Ui.gap(content, 26);
    LinearLayout heading = Ui.row(this);
    heading.addView(
        Ui.bold(this, "Your focus, over time", 16, Ui.WHITE),
        new LinearLayout.LayoutParams(0, -2, 1));
    for (int n : new int[] {7, 30}) {
      TextView choice = Ui.bold(this, n + "D", 11, chartDays == n ? Ui.RED : Ui.MUTED);
      choice.setGravity(Gravity.CENTER);
      Ui.action(choice, "Last " + n + " days");
      choice.setSelected(chartDays == n);
      choice.setOnClickListener(
          v -> {
            chartDays = n;
            render();
          });
      heading.addView(choice, Ui.width(48, -2, this));
    }
    content.addView(heading);
    Ui.gap(content, 12);
    LinearLayout graph = card();
    graph.addView(new StatsView(this, chartDays), Ui.width(-1, 130, this));
    Ui.gap(content, 24);
    caption("RECENT SESSIONS");
    JSONArray history = Store.history(this);
    if (history.length() == 0)
      content.addView(Ui.text(this, "Your story starts with your first session.", 13, Ui.MUTED));
    for (int i = history.length() - 1; i >= Math.max(0, history.length() - 10); i--) {
      JSONObject s = history.optJSONObject(i);
      LinearLayout row = Ui.row(this);
      row.addView(
          Ui.icon(
              this,
              s.optBoolean("completed") ? "check" : "key",
              s.optBoolean("completed") ? Ui.RED : Ui.MUTED,
              20));
      LinearLayout desc = Ui.col(this);
      desc.addView(Ui.bold(this, s.optString("label", "Focus session"), 13, Ui.WHITE));
      Ui.gap(desc, 5);
      desc.addView(
          Ui.text(
              this,
              historyDate(s.optString("date"))
                  + (s.optBoolean("completed") ? " · Completed" : " · Ended early"),
              11,
              Ui.MUTED));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.leftMargin = Ui.dp(this, 12);
      row.addView(desc, p);
      row.addView(Ui.text(this, Ui.duration(s.optLong("millis")), 13, Ui.WHITE));
      content.addView(row);
      Ui.gap(content, 20);
    }
  }

  String historyDate(String date) {
    try {
      return LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
    } catch (java.time.format.DateTimeParseException ignored) {
      return date;
    }
  }

  void stat(LinearLayout parent, String label, String value) {
    LinearLayout c = Ui.col(this);
    Ui.surface(c, false);
    Ui.pad(c, 16, 18, 18);
    TextView caption = Ui.text(this, label, 9, Ui.MUTED);
    caption.setMinLines(2);
    c.addView(caption);
    Ui.gap(c, 10);
    c.addView(Ui.bold(this, value, 23, Ui.WHITE));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
    p.setMarginStart(parent.getChildCount() == 0 ? 0 : Ui.dp(this, 4));
    p.setMarginEnd(parent.getChildCount() == 0 ? Ui.dp(this, 4) : 0);
    parent.addView(c, p);
  }

  void settingsPage() {
    title("MAKE IT YOURS", "A quieter phone.\nYour way.", null);
    LinearLayout permissions = card();
    settingRow(
        permissions,
        "shield",
        "Permissions",
        Permissions.ready(this) ? "Blocking is ready" : "Finish setting up",
        () -> {
          tab = 4;
          render();
        });
    Ui.gap(content, 18);
    caption("DURING YOUR SESSION");
    LinearLayout session = card();
    toggle(
        session,
        "phone",
        "Floating timer",
        "A draggable countdown over your apps",
        "floating",
        true,
        () -> true);
    divider(session);
    toggle(
        session,
        "moon",
        "Silence distractions",
        "Turn Do Not Disturb on during focus",
        "dnd",
        false,
        () -> {
          if (!Permissions.dnd(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Allow Do Not Disturb access")
                .setMessage(
                    "Stayput can enable priority-only interruptions during a session and restore"
                        + " your previous mode afterward. Choose Stayput in the next screen.")
                .setNegativeButton("Later", null)
                .setPositiveButton(
                    "Open settings",
                    (d, w) ->
                        Permissions.open(this, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                .show();
            return false;
          }
          return true;
        });
    divider(session);
    settingRow(
        session,
        "sound",
        "Soundscape",
        soundName(Store.text(this, "ambient", "none")),
        () -> soundDialog());
    Ui.gap(content, 20);
    caption("THE LITTLE THINGS");
    LinearLayout experience = card();
    toggle(
        experience,
        "sound",
        "Interface sounds",
        "Soft tones for taps, start & finish",
        "feedback",
        true,
        () -> true);
    divider(experience);
    toggle(
        experience,
        "focus",
        "Glyph progress",
        "Nothing phones with compatible Glyph lights",
        "glyph",
        false,
        () -> {
          if (!GlyphBridge.supported()) {
            toast("Glyph lights need a supported Nothing phone.");
            return false;
          }
          return true;
        });
    Ui.gap(content, 20);
    LinearLayout about = card();
    settingRow(
        about,
        "key",
        "Escape Keys",
        Store.number(this, "keys", 3) + " keys · MVP demo balance",
        () -> keysDialog());
    divider(about);
    settingRow(
        about,
        "lock",
        "Your data stays here",
        "No account. No analytics. No internet permission.",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Local by design")
                .setMessage(
                    "Session history, routines, allowed apps and demo keys are stored only on this"
                        + " device. Stayput never reads screen content. Android backup is"
                        + " disabled.\n\n"
                        + "Removing the app or clearing its storage deletes this data.")
                .setPositiveButton("Got it", null)
                .show());
    Ui.gap(content, 28);
    TextView version =
        Ui.text(this, "STAYPUT  /  0.1.0\n" + getString(R.string.brand_tagline), 11, Ui.MUTED);
    version.setGravity(Gravity.CENTER);
    version.setLineSpacing(Ui.dp(this, 8), 1);
    content.addView(version);
  }

  void settingRow(
      LinearLayout parent, String icon, String title, String description, Runnable action) {
    LinearLayout row = Ui.row(this);
    row.addView(Ui.icon(this, icon, Ui.RED, 23));
    LinearLayout words = Ui.col(this);
    words.addView(Ui.bold(this, title, 14, Ui.WHITE));
    Ui.gap(words, 6);
    TextView sub = Ui.text(this, description, 11, Ui.MUTED);
    sub.setLineSpacing(Ui.dp(this, 3), 1);
    words.addView(sub);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = Ui.dp(this, 13);
    p.rightMargin = Ui.dp(this, 8);
    row.addView(words, p);
    row.addView(Ui.icon(this, "arrow", Ui.MUTED, 20));
    row.setMinimumHeight(Ui.dp(this, 58));
    row.setOnClickListener(v -> action.run());
    Ui.action(row, title + ". " + description);
    parent.addView(row);
  }

  void toggle(
      LinearLayout parent,
      String icon,
      String title,
      String sub,
      String key,
      boolean def,
      java.util.function.BooleanSupplier allowed) {
    LinearLayout row = Ui.row(this);
    row.addView(Ui.icon(this, icon, Ui.MUTED, 22));
    LinearLayout words = Ui.col(this);
    words.addView(Ui.bold(this, title, 13, Ui.WHITE));
    Ui.gap(words, 6);
    words.addView(Ui.text(this, sub, 11, Ui.MUTED));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = Ui.dp(this, 12);
    p.rightMargin = Ui.dp(this, 8);
    row.addView(words, p);
    Switch sw = new Switch(this);
    sw.setMinimumHeight(Ui.dp(this, 48));
    sw.setMinimumWidth(Ui.dp(this, 48));
    sw.setContentDescription(title + ". " + sub);
    sw.setChecked(Store.bool(this, key, def));
    sw.setOnCheckedChangeListener(
        (v, on) -> {
          if (on && !allowed.getAsBoolean()) {
            sw.setChecked(false);
            return;
          }
          Store.set(this, key, on);
          AudioFeedback.play(this, "tap");
        });
    row.addView(sw);
    row.setMinimumHeight(Ui.dp(this, 48));
    row.setOnClickListener(v -> sw.toggle());
    row.setFocusable(false);
    row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    parent.addView(row);
  }

  void divider(LinearLayout parent) {
    Ui.gap(parent, 14);
    View line = new View(this);
    line.setBackgroundColor(Ui.LINE);
    parent.addView(line, Ui.width(-1, 1, this));
    Ui.gap(parent, 14);
  }

  void permissionPage() {
    boolean connected = FocusAccessibilityService.getConnected() != null;
    title(
        "A GOOD START",
        "Set up your\nspace to focus.",
        "A few Android permissions make the magic work.\n"
            + "Stayput checks these each time you return.");
    permissionCard(
        "shield",
        "App blocking",
        Permissions.accessibility(this) && connected,
        "Required · Full-screen block",
        Permissions.accessibility(this) && !connected
            ? "Android enabled the service, but it is disconnected. Turn Stayput off and on in"
                + " Accessibility settings to reconnect."
            : "Detects only which app is open and covers apps outside your allowed list. Never"
                + " reads screen content.",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Enable Stayput app blocking?")
                .setMessage(
                    getString(R.string.accessibility_description)
                        + "\n\n"
                        + "On the next screen, choose Installed apps / Downloaded apps → Stayput ·"
                        + " App blocking → Use service.\n\n"
                        + "If Android shows Restricted setting: open Stayput App info → menu →"
                        + " Allow restricted settings, then return here.")
                .setNegativeButton("Later", null)
                .setPositiveButton(
                    "I agree · Open settings",
                    (d, w) -> Permissions.open(this, Settings.ACTION_ACCESSIBILITY_SETTINGS))
                .show());
    permissionCard(
        "phone",
        "Display over other apps",
        Permissions.overlay(this),
        "Required · Floating timer",
        "Lets the small countdown sit above your allowed apps. You can move it or turn it off in"
            + " Settings.",
        () -> Permissions.open(this, Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
    permissionCard(
        "clock",
        "Notifications",
        Permissions.notifications(this),
        "Recommended · Session status",
        "Keeps the current session visible in your notification shade. Focus still works if you"
            + " decline.",
        () -> Permissions.requestNotifications(this));
    permissionCard(
        "moon",
        "Battery restrictions",
        getSystemService(android.os.PowerManager.class)
            .isIgnoringBatteryOptimizations(getPackageName()),
        "Recommended · Session reliability",
        "Some phones stop background timers. Choose Stayput in Android's battery settings and allow"
            + " unrestricted use.",
        () -> Permissions.open(this, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    permissionCard(
        "routines",
        "Alarms & reminders",
        Permissions.exact(this),
        "For precise routines",
        "Starts scheduled sessions at the time you choose. Otherwise Android may delay the start.",
        () -> {
          if (Build.VERSION.SDK_INT >= 31)
            Permissions.open(this, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        });
    Ui.gap(content, 10);
    TextView info =
        Ui.text(
            this,
            "Full-screen blocking is provided by the accessibility service. Stayput doesn't need"
                + " the full-screen call/alarm permission or permission to read your app usage"
                + " history.",
            11,
            Ui.MUTED);
    info.setLineSpacing(Ui.dp(this, 5), 1);
    content.addView(info);
    Ui.gap(content, 24);
    content.addView(
        Ui.button(
            this,
            Permissions.ready(this) ? "All set. Let's focus   →" : "Back to focus",
            true,
            () -> {
              tab = 0;
              render();
            }),
        Ui.width(-1, -2, this));
  }

  void permissionCard(
      String icon,
      String title,
      boolean enabled,
      String type,
      String description,
      Runnable action) {
    LinearLayout c = card();
    LinearLayout h = Ui.row(this);
    h.addView(Ui.icon(this, icon, enabled ? Ui.RED : Ui.MUTED, 24));
    TextView t = Ui.bold(this, title, 15, Ui.WHITE);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = Ui.dp(this, 12);
    h.addView(t, p);
    h.addView(Ui.icon(this, enabled ? "check" : "arrow", enabled ? Ui.RED : Ui.MUTED, 22));
    c.addView(h);
    Ui.gap(c, 12);
    c.addView(Ui.bold(this, enabled ? "Enabled" : type, 11, enabled ? Ui.RED : Ui.MUTED));
    Ui.gap(c, 8);
    TextView d = Ui.text(this, description, 12, Ui.MUTED);
    d.setLineSpacing(Ui.dp(this, 4), 1);
    c.addView(d);
    Ui.gap(c, 10);
    TextView open =
        Ui.text(
            this,
            enabled ? "Review in Android settings" : "Enable in Android settings   →",
            12,
            enabled ? Ui.MUTED : Ui.RED);
    Ui.action(open, (enabled ? "Review " : "Enable ") + title + " in Android settings");
    open.setGravity(Gravity.CENTER_VERTICAL);
    open.setOnClickListener(v -> action.run());
    c.addView(open);
    Ui.gap(content, 18);
  }

  static String soundName(String key) {
    switch (key) {
      case "rain":
        return "Soft rain";
      case "waves":
        return "Ocean waves";
      case "noise":
        return "White noise";
      case "city":
        return "Distant city";
      default:
        return "Quiet";
    }
  }

  void soundDialog() {
    String[] values = {"none", "rain", "waves", "noise", "city"};
    String[] labels = {"Quiet", "Soft rain", "Ocean waves", "White noise", "Distant city"};
    int selected = Arrays.asList(values).indexOf(Store.text(this, "ambient", "none"));
    new AlertDialog.Builder(this)
        .setTitle("Set the atmosphere")
        .setSingleChoiceItems(
            labels,
            Math.max(0, selected),
            (dialog, which) -> {
              Store.set(this, "ambient", values[which]);
              if (!Store.active(this)) preview(values[which]);
            })
        .setPositiveButton(
            "Done",
            (d, w) -> {
              stopPreview();
              render();
            })
        .setOnCancelListener(d -> stopPreview())
        .show();
  }

  void preview(String sound) {
    stopPreview();
    if (sound.equals("none")) return;
    try {
      android.content.res.AssetFileDescriptor fd = getAssets().openFd(sound + ".wav");
      preview = new MediaPlayer();
      preview.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
      fd.close();
      preview.setVolume(.25f, .25f);
      preview.prepare();
      preview.start();
    } catch (Exception ignored) {
      stopPreview();
    }
  }

  void stopPreview() {
    if (preview != null) {
      preview.release();
      preview = null;
    }
  }

  static final class AppChoice {
    final String label, packageName;
    final android.graphics.drawable.Drawable icon;

    AppChoice(ResolveInfo info, PackageManager packages) {
      label = info.loadLabel(packages).toString();
      packageName = info.activityInfo.packageName;
      icon = info.loadIcon(packages);
    }

    AppChoice(ApplicationInfo info, PackageManager packages) {
      label = info.loadLabel(packages).toString();
      packageName = info.packageName;
      icon = info.loadIcon(packages);
    }
  }

  void chooseApps(Set<String> initial, Consumer<Set<String>> save) {
    Set<String> automatic = AutomaticApps.packages(this);
    Set<String> original = new HashSet<>(initial);
    original.addAll(automatic);
    Set<String> picked = new HashSet<>(original);
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout form = Ui.col(this);
    form.setBackgroundColor(Ui.BG);
    LinearLayout introduction = Ui.col(this);
    Ui.insets(
        form,
        20,
        16,
        12,
        keyboard -> introduction.setVisibility(keyboard ? View.GONE : View.VISIBLE));
    TextView title = Ui.bold(this, "Pack your essentials", 23, Ui.WHITE);
    Ui.heading(title);
    introduction.addView(title);
    Ui.gap(introduction, 10);
    introduction.addView(
        Ui.text(
            this,
            getString(R.string.automatic_apps_description)
                + " Add any other apps you need during focus.",
            12,
            Ui.MUTED));
    Ui.gap(introduction, 16);
    form.addView(introduction);
    EditText search = new EditText(this);
    search.setId(View.generateViewId());
    search.setSingleLine(true);
    search.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_FILTER);
    search.setImeOptions(
        android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    TextView searchLabel = Ui.bold(this, "Search apps", 13, Ui.WHITE);
    searchLabel.setLabelFor(search.getId());
    form.addView(searchLabel);
    search.setHint("Search apps…");
    form.addView(search);
    TextView status = Ui.text(this, "Loading installed apps…", 12, Ui.MUTED);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    Ui.pad(status, 0, 8, 8);
    form.addView(status);
    ListView list = new ListView(this);
    form.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = Ui.row(this);
    Runnable cancel = () -> dismissDraft(dialog, () -> !picked.equals(original));
    actions.addView(
        Ui.button(this, "Cancel", false, cancel), new LinearLayout.LayoutParams(0, -2, 1));
    TextView saveButton =
        Ui.button(
            this,
            "Save apps",
            true,
            () -> {
              save.accept(new HashSet<>(picked));
              dialog.dismiss();
            });
    saveButton.setEnabled(false);
    saveButton.setAlpha(.5f);
    actions.addView(saveButton, new LinearLayout.LayoutParams(0, -2, 1));
    form.addView(actions);
    dialog.setContentView(form);
    Window window = dialog.getWindow();
    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
    if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    else
      window
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    window.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    dialog.show();
    window.setLayout(-1, -1);
    form.requestApplyInsets();
    guardDialogBack(dialog, cancel);
    new Thread(
            () -> {
              PackageManager packages = getPackageManager();
              List<ResolveInfo> available =
                  packages.queryIntentActivities(
                      new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
              LinkedHashMap<String, AppChoice> unique = new LinkedHashMap<>();
              for (ResolveInfo info : available) {
                if (info.activityInfo.packageName.equals(getPackageName())) continue;
                if (!unique.containsKey(info.activityInfo.packageName)) {
                  try {
                    AppChoice choice = new AppChoice(info, packages);
                    unique.put(choice.packageName, choice);
                  } catch (RuntimeException ignored) {
                    // An app can disappear while Android is enumerating installed apps.
                  }
                }
              }
              for (String pkg : automatic) {
                if (unique.containsKey(pkg)) continue;
                try {
                  unique.put(pkg, new AppChoice(packages.getApplicationInfo(pkg, 0), packages));
                } catch (PackageManager.NameNotFoundException ignored) {
                  // A selected keyboard can disappear while the app list loads.
                }
              }
              ArrayList<AppChoice> all = new ArrayList<>(unique.values());
              java.text.Collator collator = java.text.Collator.getInstance();
              all.sort((a, b) -> collator.compare(a.label, b.label));
              runOnUiThread(
                  () -> {
                    if (!dialog.isShowing()) return;
                    ArrayList<AppChoice> filtered = new ArrayList<>(all);
                    BaseAdapter adapter =
                        new BaseAdapter() {
                          public int getCount() {
                            return filtered.size();
                          }

                          public Object getItem(int i) {
                            return filtered.get(i);
                          }

                          public long getItemId(int i) {
                            return i;
                          }

                          @Override
                          public boolean areAllItemsEnabled() {
                            return false;
                          }

                          @Override
                          public boolean isEnabled(int i) {
                            return !automatic.contains(filtered.get(i).packageName);
                          }

                          public View getView(int i, View recycled, ViewGroup parent) {
                            CheckedTextView row;
                            if (recycled instanceof CheckedTextView)
                              row = (CheckedTextView) recycled;
                            else {
                              row = new CheckedTextView(MainActivity.this);
                              row.setTextSize(14);
                              row.setTextColor(Ui.WHITE);
                              row.setIncludeFontPadding(false);
                              Ui.pad(row, 4, 10, 10);
                              row.setCompoundDrawablePadding(Ui.dp(MainActivity.this, 12));
                              android.content.res.TypedArray style =
                                  obtainStyledAttributes(
                                      new int[] {android.R.attr.listChoiceIndicatorMultiple});
                              row.setCheckMarkDrawable(style.getDrawable(0));
                              style.recycle();
                              row.setCheckMarkTintList(
                                  new android.content.res.ColorStateList(
                                      new int[][] {
                                        new int[] {android.R.attr.state_checked}, new int[] {}
                                      },
                                      new int[] {Ui.RED, Ui.MUTED}));
                              Ui.action(row, null);
                              // CheckedTextView supplies its own native checkable semantics.
                              row.setAccessibilityDelegate(null);
                            }
                            AppChoice choice = filtered.get(i);
                            choice.icon.setBounds(
                                0, 0, Ui.dp(MainActivity.this, 36), Ui.dp(MainActivity.this, 36));
                            row.setCompoundDrawablesRelative(choice.icon, null, null, null);
                            boolean required = automatic.contains(choice.packageName);
                            row.setText(
                                required
                                    ? getString(R.string.automatic_app_label, choice.label)
                                    : choice.label);
                            row.setContentDescription(
                                required ? choice.label + ". Always included." : choice.label);
                            row.setEnabled(!required);
                            row.setChecked(picked.contains(choice.packageName));
                            row.setOnClickListener(
                                v -> {
                                  if (required) return;
                                  if (!picked.remove(choice.packageName))
                                    picked.add(choice.packageName);
                                  row.setChecked(picked.contains(choice.packageName));
                                });
                            return row;
                          }
                        };
                    list.setAdapter(adapter);
                    Runnable updateStatus =
                        () ->
                            status.setText(
                                filtered.isEmpty()
                                    ? (all.isEmpty()
                                        ? "No installed apps are available."
                                        : "No apps match your search.")
                                    : getResources()
                                        .getQuantityString(
                                            R.plurals.apps_found,
                                            filtered.size(),
                                            filtered.size()));
                    Runnable filter =
                        () -> {
                          String query =
                              search.getText().toString().trim().toLowerCase(Locale.getDefault());
                          filtered.clear();
                          for (AppChoice choice : all)
                            if (choice.label.toLowerCase(Locale.getDefault()).contains(query))
                              filtered.add(choice);
                          adapter.notifyDataSetChanged();
                          updateStatus.run();
                        };
                    search.addTextChangedListener(
                        new TextWatcher() {
                          public void beforeTextChanged(
                              CharSequence s, int st, int count, int after) {}

                          public void onTextChanged(
                              CharSequence s, int start, int before, int count) {
                            filter.run();
                          }

                          public void afterTextChanged(Editable e) {}
                        });
                    filter.run();
                    saveButton.setEnabled(true);
                    saveButton.setAlpha(1);
                  });
            },
            "installed-apps")
        .start();
  }

  void dismissDraft(Dialog dialog, java.util.function.BooleanSupplier changed) {
    if (!changed.getAsBoolean()) {
      dialog.dismiss();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Discard changes?")
        .setMessage("Your changes have not been saved.")
        .setNegativeButton("Keep editing", null)
        .setPositiveButton("Discard changes", (d, w) -> dialog.dismiss())
        .show();
  }

  void guardDialogBack(Dialog dialog, Runnable back) {
    dialog.setCanceledOnTouchOutside(false);
    if (Build.VERSION.SDK_INT >= 33)
      dialog
          .getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, back::run);
    else
      dialog.setOnKeyListener(
          (d, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) back.run();
            return true;
          });
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }
}
