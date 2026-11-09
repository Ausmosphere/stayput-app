package app.stayput;

/** Recognizes consecutive forward steps in a vertical, single-item pager. */
final class ReelScrollDetector {
  private static final int MIN_ADVANCES = 5;
  private static final long MAX_PAUSE_MILLIS = 60_000;

  private String source = "";
  private int settledPage = -1;
  private int visiblePage = -1;
  private int lastX = -1;
  private int lastY = -1;
  private long horizontalTravel;
  private long verticalTravel;
  private int advances;
  private long firstAdvance;
  private long lastAdvance;

  void observe(String viewClass, int from, int to, int x, int y, int dx, int dy) {
    if (!source.equals(viewClass)) {
      reset();
      source = viewClass;
    }
    visiblePage = from >= 0 && from == to ? from : -1;
    horizontalTravel += Math.abs(movement(dx, x, lastX));
    verticalTravel += movement(dy, y, lastY);
    lastX = x;
    lastY = y;
  }

  private static long movement(int delta, int position, int previousPosition) {
    if (delta != 0 && delta != -1) return delta;
    return position >= 0 && previousPosition >= 0 ? (long) position - previousPosition : 0;
  }

  boolean settle(long now, long delay, int minimumVerticalTravel, boolean repeatOnAdvance) {
    long horizontal = horizontalTravel;
    long vertical = verticalTravel;
    horizontalTravel = 0;
    verticalTravel = 0;
    int previousPage = settledPage;
    settledPage = visiblePage;

    if (settledPage >= 0 && settledPage == previousPage
        && horizontal < minimumVerticalTravel && Math.abs(vertical) < minimumVerticalTravel) {
      return false;
    }
    // A normal feed usually settles with several items visible. A pager lands on one next item.
    if (previousPage < 0 || settledPage - previousPage != 1
        || vertical < minimumVerticalTravel || vertical <= horizontal) {
      advances = 0;
      return false;
    }

    if (advances == 0 || now - lastAdvance > MAX_PAUSE_MILLIS) {
      advances = 0;
      firstAdvance = now;
    }
    lastAdvance = now;
    advances++;
    return repeatOnAdvance || (advances >= MIN_ADVANCES && now - firstAdvance >= delay);
  }

  void reset() {
    source = "";
    settledPage = -1;
    visiblePage = -1;
    lastX = -1;
    lastY = -1;
    horizontalTravel = 0;
    verticalTravel = 0;
    advances = 0;
  }
}
