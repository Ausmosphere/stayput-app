package app.stayput;

import java.time.*;
import java.util.Set;

/** Clock and scoring rules independent of Android so they can be tested directly. */
public final class FocusMath {
  private FocusMath() {}

  public static long remaining(
      long wallEnd, long elapsedEnd, long nowWall, long nowElapsed, boolean sameBoot) {
    return Math.max(0, sameBoot ? elapsedEnd - nowElapsed : wallEnd - nowWall);
  }

  public static long nextRoutine(int hour, int minute, Set<Integer> days, ZonedDateTime now) {
    if (days.isEmpty()) return -1;
    for (int i = 0; i <= 7; i++) {
      ZonedDateTime next = now.toLocalDate().plusDays(i).atTime(hour, minute).atZone(now.getZone());
      if (days.contains(next.getDayOfWeek().getValue()) && next.isAfter(now))
        return next.toInstant().toEpochMilli();
    }
    return -1;
  }

  public static int streak(Set<LocalDate> dates, LocalDate today) {
    LocalDate day = dates.contains(today) ? today : today.minusDays(1);
    int n = 0;
    while (dates.contains(day)) {
      n++;
      day = day.minusDays(1);
    }
    return n;
  }

  public static String clock(long millis) {
    long seconds = Math.max(0, (millis + 999) / 1000);
    return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
  }
}
