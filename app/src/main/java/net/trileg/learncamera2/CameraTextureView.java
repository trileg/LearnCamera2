package net.trileg.learncamera2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class CameraTextureView extends TextureView {
  private int ratioWidth = 0;
  private int ratioHeight = 0;

  public CameraTextureView(Context context) {
    super(context);
  }

  public CameraTextureView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setAspectRatio(int width, int height) {
    if (width < 0 || height < 0) throw new IllegalArgumentException("Size cannot be negative.");

    ratioWidth = width;
    ratioHeight = height;

    // View更新（onMeasureも呼ばれる）
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    int fixedWidth, fixedHeight;
    float ratio;

    if (ratioWidth == 0 || ratioHeight == 0) {
      fixedWidth = width;
      fixedHeight = height;
    } else {
      if (width > height) {
        // landscape
        ratio = (float) ratioHeight / ratioWidth;
        fixedWidth = width;
        fixedHeight = (int) (width * ratio);
      } else {
        // portrait
        ratio = (float) ratioWidth / ratioHeight;
        fixedWidth = (int) (height * ratio);
        fixedHeight = height;
      }
    }

    setMeasuredDimension(fixedWidth, fixedHeight);

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    widthMeasureSpec = MeasureSpec.makeMeasureSpec(fixedWidth, widthMode);
    heightMeasureSpec = MeasureSpec.makeMeasureSpec(fixedHeight, heightMode);

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
