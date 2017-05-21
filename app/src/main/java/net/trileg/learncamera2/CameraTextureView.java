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
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);

    if (ratioWidth == 0 || ratioHeight == 0) {
      setMeasuredDimension(width, height);
    } else {
      if (width > height) {
        setMeasuredDimension(width, width * ratioHeight / ratioWidth);
      } else {
        setMeasuredDimension(height * ratioWidth / ratioHeight, height);
      }
    }
  }
}
