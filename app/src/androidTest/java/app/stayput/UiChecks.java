package app.stayput;

import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
import java.util.function.Predicate;

/** Exercises the rendered app and Android accessibility actions on a disposable emulator. */
final class UiChecks {
  static void main(SmokeInstrumentation t, Runnable action) {
    Throwable[] failure = {null};
    t.runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable error) {
            failure[0] = error;
          }
        });
    if (failure[0] != null) throw new AssertionError("Rendered UI check failed", failure[0]);
  }

  static View find(View root, Predicate<View> match) {
    if (match.test(root)) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), match);
        if (found != null) return found;
      }
    }
    return null;
  }

  static AccessibilityNodeInfo node(
      SmokeInstrumentation t, Predicate<AccessibilityNodeInfo> match) {
    return node(
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .getRootInActiveWindow(),
        match);
  }

  static AccessibilityNodeInfo node(
      AccessibilityNodeInfo root, Predicate<AccessibilityNodeInfo> match) {
    if (root == null) return null;
    if (match.test(root)) return root;
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo result = node(root.getChild(i), match);
      if (result != null) return result;
    }
    return null;
  }

  static AccessibilityNodeInfo text(SmokeInstrumentation t, String label) {
    return node(t, n -> label.equalsIgnoreCase(n.getText() == null ? "" : n.getText().toString()));
  }

  static void click(SmokeInstrumentation t, String label) throws Exception {
    t.until(() -> text(t, label) != null, label + " is visible");
    t.check(text(t, label).performAction(AccessibilityNodeInfo.ACTION_CLICK), label + " activates");
    t.waitForIdleSync();
  }

  static void setSearch(SmokeInstrumentation t, String value) {
    AccessibilityNodeInfo input = node(t, AccessibilityNodeInfo::isEditable);
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    t.check(
        input != null && input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args),
        "App search accepts text through accessibility");
    t.waitForIdleSync();
  }

  static void run(SmokeInstrumentation t, MainActivity screen) throws Exception {
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo automationInfo =
        automation.getServiceInfo();
    automationInfo.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    automation.setServiceInfo(automationInfo);
    t.waitForIdleSync();
    t.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
    t.waitForIdleSync();
    main(
        t,
        () -> {
          for (int i = 0; i < screen.nav.getChildCount(); i++) {
            TextView label = (TextView) ((ViewGroup) screen.nav.getChildAt(i)).getChildAt(2);
            t.check(
                label.getLayout().getLineBottom(label.getLineCount() - 1)
                    <= label.getHeight() - label.getPaddingBottom(),
                "Navigation label fits enlarged text: " + label.getText());
          }
          screen.dial.setMinutes(30);
          AccessibilityNodeInfo dial = screen.dial.createAccessibilityNodeInfo();
          t.check(
              dial.getRangeInfo() != null
                  && dial.getRangeInfo().getMin() == 5
                  && dial.getRangeInfo().getMax() == 180,
              "Duration dial exposes its adjustable range");
          screen.dial.dispatchKeyEvent(
              new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
          t.check(screen.minutes == 35, "Keyboard arrow increases duration");
          screen.dial.performAccessibilityAction(
              AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
          t.check(screen.minutes == 30, "Accessibility action decreases duration");
          Bundle progress = new Bundle();
          progress.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 70);
          screen.dial.performAccessibilityAction(
              AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), progress);
          t.check(screen.minutes == 70, "Accessibility action sets a precise duration");
          TextView preset =
              (TextView)
                  find(
                      screen.content,
                      v ->
                          v instanceof TextView
                              && "30 min".contentEquals(((TextView) v).getText()));
          t.check(!preset.isSelected(), "Dial updates the selected preset state");
          t.check(
              preset.getHeight() >= Ui.dp(screen, 48)
                  && Button.class
                      .getName()
                      .contentEquals(preset.createAccessibilityNodeInfo().getClassName()),
              "Duration presets have button semantics and 48dp touch targets");
          preset.performClick();
          t.check(screen.minutes == 30, "Tapping a duration preset updates the session");
          View settings = screen.nav.getChildAt(3);
          settings.requestFocus();
          settings.performClick();
          t.check(
              screen.tab == 3
                  && screen.nav.getChildAt(3).isSelected()
                  && screen.nav.getChildAt(3).hasFocus(),
              "Tab navigation retains focus and selected state");
          Switch floating =
              (Switch)
                  find(
                      screen.content,
                      v ->
                          v instanceof Switch
                              && v.getContentDescription().toString().startsWith("Floating timer"));
          boolean original = floating.isChecked();
          ((View) floating.getParent()).performClick();
          t.check(floating.isChecked() != original, "Tapping a settings label toggles its switch");
          ((View) floating.getParent()).performClick();
          if (!Ui.motionEnabled()) {
            Ui.animateIn(screen.content);
            t.check(
                screen.content.getAlpha() == 1 && screen.content.getTranslationY() == 0,
                "Reduced motion leaves content fully visible without a transition");
          }
          screen.tab = 0;
          screen.render();
        });
    t.capture("ui-large-text-focus");
    main(t, () -> screen.routineEditor(-1));
    t.waitForIdleSync();
    AccessibilityNodeInfo name = node(t, AccessibilityNodeInfo::isEditable);
    t.check(
        name != null && name.getLabeledBy() != null, "Routine name has a persistent field label");
    Bundle rename = new Bundle();
    rename.putCharSequence(
        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Changed draft");
    name.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, rename);
    click(t, "Cancel");
    t.until(
        () -> text(t, "Discard changes?") != null, "Unsaved routine changes require confirmation");
    click(t, "Keep editing");
    t.until(() -> text(t, "Changed draft") != null, "Keeping a routine draft preserves its edits");
    click(t, "Cancel");
    click(t, "Discard changes");
    final Set<String> saved = new HashSet<>();
    main(t, () -> screen.chooseApps(Collections.emptySet(), saved::addAll));
    t.until(
        () -> text(t, "Save apps") != null && text(t, "Save apps").isEnabled(),
        "Installed apps finish loading");
    setSearch(t, "zz_no_matching_app_9473");
    t.until(
        () -> text(t, "No apps match your search.") != null,
        "Search displays a useful empty state");
    AccessibilityNodeInfo search = node(t, AccessibilityNodeInfo::isEditable);
    t.check(
        search != null && search.getLabeledBy() != null, "App search has a persistent field label");
    Rect input = new Rect();
    search.getBoundsInScreen(input);
    t.shell("input tap " + input.centerX() + " " + input.centerY());
    t.until(
        () ->
            automation.getWindows().stream()
                .anyMatch(w -> w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD),
        "Software keyboard opens for app search");
    t.capture("ui-picker-keyboard");
    Rect saveBounds = new Rect();
    text(t, "Save apps").getBoundsInScreen(saveBounds);
    t.check(
        saveBounds.height() >= Ui.dp(screen, 48),
        "App picker save action remains a full touch target");
    for (AccessibilityWindowInfo window : automation.getWindows()) {
      if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
      Rect keyboard = new Rect();
      window.getBoundsInScreen(keyboard);
      t.check(
          !Rect.intersects(keyboard, saveBounds),
          "Keyboard does not cover the app picker save action");
    }
    setSearch(t, "Settings");
    t.until(
        () ->
            node(
                    t,
                    n ->
                        n.isCheckable()
                            && "Settings"
                                .contentEquals(
                                    n.getContentDescription() == null
                                        ? ""
                                        : n.getContentDescription()))
                != null,
        "Search finds an installed app");
    AccessibilityNodeInfo choice =
        node(
            t,
            n ->
                n.isCheckable()
                    && "Settings"
                        .contentEquals(
                            n.getContentDescription() == null ? "" : n.getContentDescription()));
    t.check(
        choice.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "App choice supports accessibility activation");
    t.waitForIdleSync();
    t.until(
        () -> {
          AccessibilityNodeInfo selected =
              node(
                  t,
                  n ->
                      n.isCheckable()
                          && "Settings"
                              .contentEquals(
                                  n.getContentDescription() == null
                                      ? ""
                                      : n.getContentDescription()));
          return selected != null && selected.refresh() && selected.isChecked();
        },
        "App choice exposes its checked state");
    t.shell("input keyevent KEYCODE_BACK");
    click(t, "Save apps");
    t.check(saved.contains("com.android.settings"), "App picker saves the selected app");
    main(t, () -> screen.chooseApps(Collections.emptySet(), ignored -> {}));
    t.until(
        () -> text(t, "Save apps") != null && text(t, "Save apps").isEnabled(),
        "Automatic app choices finish loading");
    for (String pkg : AutomaticApps.packages(screen)) {
      String label =
          screen
              .getPackageManager()
              .getApplicationLabel(screen.getPackageManager().getApplicationInfo(pkg, 0))
              .toString();
      setSearch(t, label);
      t.until(
          () ->
              node(
                      t,
                      n ->
                          (label + ". Always included.")
                              .contentEquals(
                                  n.getContentDescription() == null
                                      ? ""
                                      : n.getContentDescription()))
                  != null,
          "Automatic app is visible in picker: " + label);
      AccessibilityNodeInfo required =
          node(
              t,
              n ->
                  (label + ". Always included.")
                      .contentEquals(
                          n.getContentDescription() == null ? "" : n.getContentDescription()));
      t.check(
          required.isChecked() && !required.isEnabled(),
          "Automatic app is checked and cannot be removed: " + label);
    }
    click(t, "Cancel");
  }

  static void active(SmokeInstrumentation t, MainActivity screen) throws Exception {
    main(
        t,
        () -> {
          screen.render();
          screen.scroll.fullScroll(View.FOCUS_DOWN);
        });
    long duration = Store.duration(screen);
    main(t, () -> find(screen.content, v -> v instanceof Ui.HoldButton).performClick());
    t.waitForIdleSync();
    t.until(
        () -> text(t, "Add 15 minutes?") != null && Store.duration(screen) == duration,
        "Accessible extension asks for confirmation before changing the session");
    click(t, "Cancel");
    t.check(
        Store.duration(screen) == duration, "Cancelling an extension keeps the session duration");
  }
}
