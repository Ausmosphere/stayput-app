package app.stayput;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.*;
import org.json.*;

public class StatsView extends View {
  final Paint p = new Paint(3);
  final int days;
  final long[] values;

  public StatsView(Context c) {
    this(c, 7);
  }

  public StatsView(Context c, int days) {
    super(c);
    this.days = days;
    values = new long[days];
    JSONArray history = Store.history(c);
    LocalDate first = LocalDate.now().minusDays(days - 1);
    for (int i = 0; i < history.length(); i++) {
      JSONObject h = history.optJSONObject(i);
      try {
        int day =
            (int)
                java.time.temporal.ChronoUnit.DAYS.between(
                    first, LocalDate.parse(h.optString("date")));
        if (day >= 0 && day < days) values[day] += h.optLong("millis");
      } catch (Exception ignored) {
      }
    }
    StringBuilder summary = new StringBuilder("Daily focus time for the last " + days + " days. ");
    boolean recorded = false;
    java.time.format.DateTimeFormatter dates =
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM);
    for (int i = 0; i < days; i++) {
      if (values[i] <= 0) continue;
      recorded = true;
      summary
          .append(first.plusDays(i).format(dates))
          .append(": ")
          .append(Ui.duration(values[i]))
          .append(". ");
    }
    if (!recorded) summary.append("No focus time recorded yet.");
    setContentDescription(summary);
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return android.widget.ImageView.class.getName();
  }

  protected void onDraw(Canvas c) {
    float d = getResources().getDisplayMetrics().density;
    float labelSize =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics());
    float width = getWidth(), height = getHeight() - labelSize - 14 * d;
    long max = 3600000;
    for (long v : values) max = Math.max(max, v);
    p.setColor(Ui.LINE);
    p.setStrokeWidth(d);
    c.drawLine(0, height, width, height, p);
    float col = width / days;
    for (int i = 0; i < days; i++) {
      float h = Math.max(3 * d, (float) values[i] / max * (height - 10 * d));
      p.setColor(values[i] > 0 ? Ui.RED : 0xFF33363F);
      c.drawRoundRect(
          i * col + col * .2f, height - h, i * col + col * .8f, height, 3 * d, 3 * d, p);
    }
    p.setColor(Ui.MUTED);
    p.setTextSize(labelSize);
    p.setTextAlign(Paint.Align.LEFT);
    c.drawText((days - 1) + " days ago", 0, getHeight() - 2 * d, p);
    p.setTextAlign(Paint.Align.RIGHT);
    c.drawText("Today", width, getHeight() - 2 * d, p);
  }
}
