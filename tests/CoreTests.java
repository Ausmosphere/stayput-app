import app.stayput.FocusMath;
import java.time.*;
import java.util.*;

public class CoreTests {
  static int checks;

  static void equal(Object actual, Object expected, String message) {
    checks++;
    if (!actual.equals(expected))
      throw new AssertionError(message + ": expected " + expected + ", got " + actual);
  }

  public static void main(String[] args) {
    equal(FocusMath.remaining(10000, 8000, 2000, 3000, true), 5000L, "Monotonic time counts down");
    equal(
        FocusMath.remaining(10000, 8000, 900000, 3000, true),
        5000L,
        "Wall-clock changes cannot end a running session");
    equal(
        FocusMath.remaining(10000, 8000, 2000, 3000, false),
        8000L,
        "Reboot falls back to persisted wall deadline");
    equal(FocusMath.remaining(10000, 8000, 20000, 9000, true), 0L, "No negative countdown");
    equal(FocusMath.clock(1), "00:01", "Partial seconds round up");
    equal(FocusMath.clock(1800000), "30:00", "Duration format");
    equal(FocusMath.clock(-1), "00:00", "Expired format");
    LocalDate today = LocalDate.of(2026, 11, 7);
    equal(
        FocusMath.streak(Set.of(today, today.minusDays(1), today.minusDays(2)), today),
        3,
        "Consecutive focus days");
    equal(
        FocusMath.streak(Set.of(today.minusDays(1), today.minusDays(2)), today),
        2,
        "Yesterday's streak stays alive today");
    equal(FocusMath.streak(Set.of(today.minusDays(2)), today), 0, "Missed day breaks streak");
    equal(FocusMath.streak(Set.of(), today), 0, "Empty tower");
    ZonedDateTime now = ZonedDateTime.of(2026, 11, 6, 21, 0, 0, 0, ZoneId.of("UTC"));
    equal(
        FocusMath.nextRoutine(22, 0, Set.of(5), now),
        now.withHour(22).toInstant().toEpochMilli(),
        "Same-day next routine");
    equal(
        FocusMath.nextRoutine(20, 0, Set.of(5), now),
        now.plusWeeks(1).withHour(20).toInstant().toEpochMilli(),
        "Past routine rolls one week");
    equal(
        FocusMath.nextRoutine(21, 0, Set.of(5), now),
        now.plusWeeks(1).toInstant().toEpochMilli(),
        "Exact current timestamp doesn't retrigger");
    equal(
        FocusMath.nextRoutine(9, 0, Set.of(1), now),
        now.plusDays(3).withHour(9).toInstant().toEpochMilli(),
        "Weekday selection");
    equal(FocusMath.nextRoutine(9, 0, Set.of(), now), -1L, "No days doesn't schedule");
    ZoneId ny = ZoneId.of("America/New_York");
    ZonedDateTime before = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, ny);
    long dst = FocusMath.nextRoutine(2, 30, Set.of(7), before);
    equal(Instant.ofEpochMilli(dst).atZone(ny).getHour(), 3, "Nonexistent DST time shifts forward");
    System.out.println("PASS: " + checks + " clock, streak, routine and DST checks");
  }
}
