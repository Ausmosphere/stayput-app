package app.stayput;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class TowerView extends View {
  final Paint p = new Paint(3);
  final int floors;

  public TowerView(Context c) {
    this(c, 0);
  }

  public TowerView(Context c, int floors) {
    super(c);
    this.floors = floors;
    setLayerType(LAYER_TYPE_SOFTWARE, null);
    setContentDescription(floors + " floors in your focus tower");
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return android.widget.ImageView.class.getName();
  }

  void polygon(Canvas c, int color, float... points) {
    Path path = new Path();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    path.close();
    p.setColor(color);
    p.setStyle(Paint.Style.FILL);
    c.drawPath(path, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(.7f);
    p.setColor(0xFF45404A);
    c.drawPath(path, p);
    p.setStyle(Paint.Style.FILL);
  }

  protected void onDraw(Canvas c) {
    float s = Math.min(getWidth() / 280f, getHeight() / 240f);
    c.save();
    c.translate(getWidth() / 2f, getHeight() - 26 * s);
    c.scale(s, s);
    p.setStrokeWidth(.6f);
    p.setColor(0xFF2A2C33);
    for (int i = -4; i <= 4; i++) {
      c.drawLine(-130, i * 14 - 40, 130, i * 14 + 40, p);
      c.drawLine(-130, i * 14 + 40, 130, i * 14 - 40, p);
    }
    int visible = Math.max(1, Math.min(floors, 9));
    p.setColor(0x40FF4B50);
    p.setShadowLayer(32, 0, 0, 0x60FF4B50);
    c.drawOval(-65, -20, 65, 10, p);
    p.clearShadowLayer();
    for (int i = 0; i < visible; i++) {
      float y = -i * 19;
      polygon(
          c, floors == 0 ? 0xFF25272F : 0xFF42252D, -48, y - 27, 0, y - 9, 0, y + 9, -48, y - 9);
      polygon(c, floors == 0 ? 0xFF1B1D23 : 0xFF2A1F27, 0, y - 9, 48, y - 27, 48, y - 9, 0, y + 9);
      polygon(
          c,
          i == visible - 1 && floors > 0 ? Ui.RED : 0xFF57313A,
          -48,
          y - 27,
          0,
          y - 45,
          48,
          y - 27,
          0,
          y - 9);
      if (floors > 0) {
        p.setColor(0xFFFF787B);
        for (int j = 0; j < 3; j++)
          c.drawRoundRect(-39 + j * 12, y - 20 + j * 4, -34 + j * 12, y - 15 + j * 4, 1, 1, p);
      }
    }
    c.restore();
  }
}
