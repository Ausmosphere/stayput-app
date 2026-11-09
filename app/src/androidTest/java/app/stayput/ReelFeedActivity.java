package app.stayput;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** External app fixture that reports real Android scroll events in two layout modes. */
public final class ReelFeedActivity extends Activity {
  static final String PAGED = "paged";

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(new Feed(this, getIntent().getBooleanExtra(PAGED, false)));
  }

  private static final class Feed extends ScrollView {
    private static final int ITEMS = 100;
    private final LinearLayout content;
    private final boolean paged;
    private int itemHeight;
    private float downY;

    Feed(Context context, boolean paged) {
      super(context);
      this.paged = paged;
      content = new LinearLayout(context);
      content.setOrientation(LinearLayout.VERTICAL);
      for (int i = 0; i < ITEMS; i++) {
        TextView item = new TextView(context);
        item.setGravity(Gravity.CENTER);
        item.setText((paged ? "Clip " : "Feed item ") + (i + 1));
        item.setTextSize(24);
        content.addView(item, -1, 1);
      }
      addView(content);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      int height = MeasureSpec.getSize(heightSpec);
      int measuredItemHeight = Math.max(1, paged ? height : height / 6);
      if (measuredItemHeight != itemHeight) {
        itemHeight = measuredItemHeight;
        for (int i = 0; i < ITEMS; i++) {
          content.getChildAt(i).getLayoutParams().height = itemHeight;
          content.getChildAt(i).forceLayout();
        }
        content.forceLayout();
      }
      super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (!paged) return super.onTouchEvent(event);
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downY = event.getY();
      if (event.getActionMasked() == MotionEvent.ACTION_UP) {
        float travel = downY - event.getY();
        if (Math.abs(travel) >= itemHeight / 8f) {
          scrollBy(0, travel > 0 ? itemHeight : -itemHeight);
        }
        performClick();
      }
      return true;
    }

    @Override
    public boolean performClick() {
      return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
      super.onInitializeAccessibilityEvent(event);
      if (itemHeight == 0) return;
      event.setFromIndex(getScrollY() / itemHeight);
      event.setToIndex(Math.min(ITEMS - 1, (getScrollY() + getHeight() - 1) / itemHeight));
      event.setItemCount(ITEMS);
    }
  }
}
