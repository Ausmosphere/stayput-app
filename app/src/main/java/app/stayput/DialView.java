package app.stayput;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.IntConsumer;

public class DialView extends View {
  final Paint p = new Paint(3);
  final Shader gradient =
      new LinearGradient(
          -90, -100, 90, 100, new int[] {0xFF24272D, 0xFF191B20}, null, Shader.TileMode.CLAMP);
  int minutes = 30;
  long remaining = -1, total = 1;
  IntConsumer listener;
  boolean centerTouch;

  public DialView(Context c) {
    super(c);
    setLayerType(LAYER_TYPE_SOFTWARE, null);
    setFocusable(true);
    updateDescription();
  }

  public void setMinutes(int value) {
    minutes = value;
    remaining = -1;
    updateDescription();
    invalidate();
  }

  public void countdown(long left, long duration) {
    remaining = left;
    total = duration;
    setContentDescription("Focus session. " + FocusMath.clock(left) + " remaining");
    invalidate();
  }

  void updateDescription() {
    setContentDescription(
        minutes + " minute focus session. Drag the dial or tap to add five minutes.");
  }

  protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w);
    setMeasuredDimension(width, resolveSize(Math.min(width, Ui.dp(getContext(), 300)), h));
  }

  protected void onDraw(Canvas c) {
    float scale = Math.min(getWidth(), getHeight()) / 300f;
    c.save();
    c.translate(getWidth() / 2f, getHeight() / 2f);
    c.scale(scale, scale);
    float progress =
        remaining >= 0
            ? Math.min(1, (float) remaining / Math.max(total, 1))
            : (float) minutes / 180;
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    for (int i = 0; i < 60; i++) {
      double a = Math.toRadians(i * 6 - 90);
      p.setStrokeWidth(i % 5 == 0 ? 2 : 1);
      p.setColor(i < progress * 60 ? Ui.RED : 0xFF373A43);
      float r = i % 5 == 0 ? 134 : 137;
      c.drawLine(
          (float) Math.cos(a) * r,
          (float) Math.sin(a) * r,
          (float) Math.cos(a) * 141,
          (float) Math.sin(a) * 141,
          p);
    }
    p.setStyle(Paint.Style.FILL);
    p.setColor(Ui.CARD);
    p.setShadowLayer(15, -7, -7, 0x60353A44);
    c.drawCircle(0, 0, 119, p);
    p.setShadowLayer(15, 9, 10, 0xD0060709);
    c.drawCircle(0, 0, 119, p);
    p.clearShadowLayer();
    p.setShader(gradient);
    c.drawCircle(0, 0, 119, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1);
    p.setColor(0xFF353840);
    c.drawCircle(0, 0, 118, p);
    p.setColor(0xFF0D0F12);
    c.drawCircle(0, 0, 108, p);
    p.setStyle(Paint.Style.FILL);
    p.setTextAlign(Paint.Align.CENTER);
    p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    p.setTextSize(remaining >= 0 ? 51 : 76);
    p.setColor(Ui.WHITE);
    c.drawText(remaining >= 0 ? FocusMath.clock(remaining) : String.valueOf(minutes), 0, 15, p);
    p.setTextSize(10);
    p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    p.setColor(Ui.MUTED);
    c.drawText(remaining >= 0 ? "TIME TO YOURSELF" : "M I N U T E S", 0, 43, p);
    p.setColor(Ui.RED);
    c.drawCircle(0, -69, 3, p);
    if (remaining < 0) {
      double a = progress * Math.PI * 2 - Math.PI / 2;
      p.setShadowLayer(8, 0, 0, 0x90FF4B50);
      c.drawCircle((float) Math.cos(a) * 139, (float) Math.sin(a) * 139, 4, p);
      p.clearShadowLayer();
    }
    c.restore();
  }

  public boolean onTouchEvent(MotionEvent e) {
    if (remaining >= 0) return super.onTouchEvent(e);
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      centerTouch =
          Math.hypot(e.getX() - getWidth() / 2f, e.getY() - getHeight() / 2f) < getHeight() * .3f;
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
      if (centerTouch) return true;
      double a = Math.atan2(e.getY() - getHeight() / 2f, e.getX() - getWidth() / 2f) + Math.PI / 2;
      if (a < 0) a += 2 * Math.PI;
      int n = Math.max(5, Math.min(180, (int) Math.round(a / (2 * Math.PI) * 36) * 5));
      if (n != minutes) {
        minutes = n;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (listener != null) listener.accept(n);
        updateDescription();
        invalidate();
      }
      return true;
    }
    if (e.getAction() == MotionEvent.ACTION_UP) {
      getParent().requestDisallowInterceptTouchEvent(false);
      if (centerTouch) performClick();
      return true;
    }
    if (e.getAction() == MotionEvent.ACTION_CANCEL)
      getParent().requestDisallowInterceptTouchEvent(false);
    return true;
  }

  public boolean performClick() {
    super.performClick();
    if (remaining < 0) {
      setMinutes(minutes >= 180 ? 5 : minutes + 5);
      if (listener != null) listener.accept(minutes);
    }
    return true;
  }
}
