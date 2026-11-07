package app.stayput;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.*;
import android.widget.*;

public final class Ui {
  public static final int BG = Color.rgb(24, 26, 30),
      CARD = Color.rgb(28, 30, 35),
      WHITE = Color.rgb(240, 240, 243),
      MUTED = Color.rgb(143, 147, 158),
      RED = Color.rgb(255, 75, 80),
      LINE = Color.rgb(47, 50, 58);

  public static int dp(Context c, float x) {
    return (int) (x * c.getResources().getDisplayMetrics().density + .5f);
  }

  public static TextView text(Context c, String s, float size, int color) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setFontFeatureSettings("tnum");
    v.setIncludeFontPadding(false);
    return v;
  }

  public static TextView bold(Context c, String s, float size, int color) {
    TextView v = text(c, s, size, color);
    v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return v;
  }

  public static LinearLayout col(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  public static LinearLayout row(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  public static void pad(View v, int h, int top, int bottom) {
    v.setPadding(
        dp(v.getContext(), h),
        dp(v.getContext(), top),
        dp(v.getContext(), h),
        dp(v.getContext(), bottom));
  }

  public static void gap(LinearLayout l, int h) {
    View v = new View(l.getContext());
    l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), h)));
  }

  public static void surface(View v, boolean inset) {
    v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    v.setBackground(new NeoDrawable(v.getContext(), inset, 20, CARD));
  }

  public static void animateIn(View v) {
    v.setAlpha(0);
    v.setTranslationY(dp(v.getContext(), 8));
    v.animate()
        .alpha(1)
        .translationY(0)
        .setDuration(260)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  public static TextView button(Context c, String label, boolean primary, Runnable action) {
    TextView v = bold(c, label, 15, primary ? WHITE : MUTED);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(c, 54));
    v.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    v.setBackground(new NeoDrawable(c, false, 17, primary ? Color.rgb(216, 53, 60) : CARD));
    v.setContentDescription(label);
    v.setFocusable(true);
    v.setOnClickListener(
        x -> {
          AudioFeedback.play(c, "tap");
          v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          v.animate()
              .scaleX(.975f)
              .scaleY(.975f)
              .setDuration(70)
              .withEndAction(() -> v.animate().scaleX(1).scaleY(1).setDuration(130).start())
              .start();
          action.run();
        });
    return v;
  }

  public static Icon icon(Context c, String type, int color, int size) {
    Icon v = new Icon(c, type, color);
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    return v;
  }

  public static LinearLayout.LayoutParams width(int w, int h, Context c) {
    return new LinearLayout.LayoutParams(w < 0 ? w : dp(c, w), h < 0 ? h : dp(c, h));
  }

  public static String duration(long millis) {
    long m = millis / 60000;
    return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
  }

  public static class NeoDrawable extends Drawable {
    final Paint p = new Paint(3);
    final float density, r;
    final boolean inset;
    final int color;

    NeoDrawable(Context c, boolean inset, int radius, int color) {
      density = c.getResources().getDisplayMetrics().density;
      r = radius * density;
      this.inset = inset;
      this.color = color;
    }

    public void draw(Canvas c) {
      Rect b = getBounds();
      float g = 3 * density;
      RectF f = new RectF(b.left + g, b.top + g, b.right - g, b.bottom - g);
      p.setStyle(Paint.Style.FILL);
      p.setColor(color);
      p.setShadowLayer(7 * density, -3 * density, -3 * density, 0x502F333C);
      c.drawRoundRect(f, r, r, p);
      p.setShadowLayer(7 * density, 4 * density, 5 * density, 0xA008090B);
      c.drawRoundRect(f, r, r, p);
      p.clearShadowLayer();
      if (inset) {
        p.setShader(
            new LinearGradient(
                0,
                b.top,
                0,
                b.bottom,
                new int[] {0xFF101216, 0xFF202228},
                null,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(f, r, r, p);
        p.setShader(null);
      }
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(density);
      p.setColor(inset ? 0xFF26292F : 0xFF30333B);
      c.drawRoundRect(f, r, r, p);
      p.setStyle(Paint.Style.FILL);
    }

    public void setAlpha(int a) {
      p.setAlpha(a);
    }

    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
    }

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  public static class Icon extends View {
    String type;
    int color;
    Paint p = new Paint(3);

    public Icon(Context c, String type, int color) {
      super(c);
      this.type = type;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      float s = Math.min(getWidth(), getHeight()) / 24f;
      canvas.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
      canvas.scale(s, s);
      drawIcon(canvas, p, type, color);
      canvas.restore();
    }
  }

  static void drawIcon(Canvas c, Paint p, String t, int color) {
    p.reset();
    p.setAntiAlias(true);
    p.setColor(color);
    p.setStrokeWidth(1.7f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    switch (t) {
      case "focus":
        c.drawCircle(12, 12, 8, p);
        c.drawCircle(12, 12, 3, p);
        break;
      case "routines":
        c.drawRoundRect(4, 5, 20, 21, 3, 3, p);
        c.drawLine(4, 10, 20, 10, p);
        c.drawLine(8, 3, 8, 7, p);
        c.drawLine(16, 3, 16, 7, p);
        c.drawLine(8, 14, 11, 14, p);
        c.drawLine(14, 17, 16, 17, p);
        break;
      case "tower":
        c.drawRoundRect(6, 9, 18, 21, 1, 1, p);
        c.drawRect(8, 4, 16, 9, p);
        c.drawLine(12, 1, 12, 4, p);
        c.drawLine(10, 13, 14, 13, p);
        c.drawLine(10, 17, 14, 17, p);
        break;
      case "settings":
        c.drawCircle(12, 12, 4, p);
        c.drawCircle(12, 12, 8, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          c.drawLine(
              12 + (float) Math.cos(a) * 8,
              12 + (float) Math.sin(a) * 8,
              12 + (float) Math.cos(a) * 10,
              12 + (float) Math.sin(a) * 10,
              p);
        }
        break;
      case "lock":
        c.drawRoundRect(5, 10, 19, 21, 3, 3, p);
        c.drawArc(8, 3, 16, 16, 180, 180, false, p);
        c.drawLine(8, 7, 8, 10, p);
        c.drawLine(16, 7, 16, 10, p);
        c.drawLine(12, 14, 12, 17, p);
        break;
      case "plus":
        c.drawLine(5, 12, 19, 12, p);
        c.drawLine(12, 5, 12, 19, p);
        break;
      case "check":
        c.drawLine(5, 12, 10, 17, p);
        c.drawLine(10, 17, 20, 6, p);
        break;
      case "arrow":
        c.drawLine(9, 6, 15, 12, p);
        c.drawLine(15, 12, 9, 18, p);
        break;
      case "back":
        c.drawLine(15, 6, 9, 12, p);
        c.drawLine(9, 12, 15, 18, p);
        break;
      case "key":
        c.drawCircle(8, 8, 4, p);
        c.drawLine(11, 11, 20, 20, p);
        c.drawLine(16, 16, 18, 14, p);
        c.drawLine(19, 19, 21, 17, p);
        break;
      case "sound":
        c.drawLine(4, 10, 8, 10, p);
        c.drawLine(8, 10, 13, 5, p);
        c.drawLine(13, 5, 13, 19, p);
        c.drawLine(13, 19, 8, 14, p);
        c.drawLine(8, 14, 4, 14, p);
        c.drawLine(4, 14, 4, 10, p);
        c.drawArc(10, 5, 22, 19, -60, 120, false, p);
        break;
      case "shield":
        Path path = new Path();
        path.moveTo(12, 3);
        path.lineTo(20, 6);
        path.lineTo(19, 14);
        path.quadTo(18, 19, 12, 22);
        path.quadTo(6, 19, 5, 14);
        path.lineTo(4, 6);
        path.close();
        c.drawPath(path, p);
        break;
      case "phone":
        c.drawRoundRect(6, 2, 18, 22, 3, 3, p);
        c.drawLine(10, 18, 14, 18, p);
        break;
      case "moon":
        Path moon = new Path();
        moon.moveTo(17, 3);
        moon.cubicTo(2, 1, 0, 20, 14, 21);
        moon.cubicTo(18, 21, 21, 18, 22, 15);
        moon.cubicTo(11, 20, 7, 8, 17, 3);
        c.drawPath(moon, p);
        break;
      case "clock":
        c.drawCircle(12, 12, 9, p);
        c.drawLine(12, 6, 12, 12, p);
        c.drawLine(12, 12, 16, 14, p);
        break;
      case "bag":
        c.drawRoundRect(4, 7, 20, 21, 3, 3, p);
        c.drawArc(8, 2, 16, 12, 180, 180, false, p);
        c.drawLine(8, 6, 8, 10, p);
        c.drawLine(16, 6, 16, 10, p);
        break;
      case "rain":
        for (int i = 0; i < 3; i++) c.drawLine(7 + i * 5, 6, 4 + i * 5, 18, p);
        break;
      case "wave":
        for (int i = 0; i < 3; i++) {
          Path w = new Path();
          w.moveTo(2, 7 + i * 5);
          w.cubicTo(7, 1 + i * 5, 11, 13 + i * 5, 16, 7 + i * 5);
          w.quadTo(19, 4 + i * 5, 22, 7 + i * 5);
          c.drawPath(w, p);
        }
        break;
      default:
        c.drawCircle(12, 12, 8, p);
        c.drawLine(12, 7, 12, 12, p);
        c.drawPoint(12, 16, p);
    }
  }
}
