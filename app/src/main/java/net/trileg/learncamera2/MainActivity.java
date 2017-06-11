package net.trileg.learncamera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import net.trileg.learncamera2.databinding.ActivityMainBinding;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
  private static final int REQUEST_CAMERA = 1;
  private static final int TEXTURE_VIEW_MAX_WIDTH = 1920;
  private static final int TEXTURE_VIEW_MAX_HEIGHT = 1080;
  private static final int MAX_IMAGES = 2;

  private ActivityMainBinding binding;

  private HandlerThread backgroundHandlerThread;
  private Handler backgroundHandler;

  private Size previewSize;
  private CameraDevice cameraDevice;
  private CameraCharacteristics cameraCharacteristics;
  private CaptureRequest.Builder previewBuilder;
  private CameraCaptureSession previewSession;
  private ImageReader previewImageReader;
  private DisplayManager displayManager;
  private DisplayManager.DisplayListener displayListener;

  private Image capturedImage;
  private int lastOrientationNum = -1;
  private int savedOrientationNum = -1;
  private int numberOfCameras = 0;
  private String currentCameraId = "0";
  private boolean isCameraFacing = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

    binding.cameraTextureView.setSurfaceTextureListener(cameraViewStatusChanged);

    ViewTreeObserver observer = binding.cameraTextureView.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        int length = Math.abs(binding.cameraTextureView.getHeight() - binding.cameraTextureView.getWidth()) / 2;
        binding.topCoverView.getLayoutParams().height = length;
        binding.topCoverView.requestLayout();
        binding.bottomCoverView.getLayoutParams().height = length;
        binding.bottomCoverView.requestLayout();

        binding.cameraTextureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      }
    });

    binding.setCloseBtnListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    binding.setSwitchBtnListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        closeCamera();
        currentCameraId = String.valueOf(Integer.valueOf(currentCameraId) + 1);
        if (Integer.valueOf(currentCameraId) >= numberOfCameras) currentCameraId = "0";

        lastOrientationNum = getWindowManager().getDefaultDisplay().getRotation();

        prepareCameraView(binding.cameraTextureView.getWidth(), binding.cameraTextureView.getHeight());
      }
    });

    binding.setTakeBtnListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        takePhoto();
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    startBackgroundThread();

    lastOrientationNum = getWindowManager().getDefaultDisplay().getRotation();

    if (savedOrientationNum == lastOrientationNum && previewSize != null) {
      prepareCameraView(previewSize.getWidth(), previewSize.getHeight());
      savedOrientationNum = -1;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (previewSession != null) {
      previewSession.close();
      previewSession = null;
    }
    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }
    if (displayManager != null && displayListener != null) {
      displayManager.unregisterDisplayListener(displayListener);
    }
    displayManager = null;
    displayListener = null;

    stopBackgroundThread();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (previewImageReader != null) {
      previewImageReader.close();
      previewImageReader = null;
    }

    savedOrientationNum = -1;
  }

  private final TextureView.SurfaceTextureListener cameraViewStatusChanged = new TextureView.SurfaceTextureListener() {
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
      requestCameraPermission();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
      configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
  };

  private void requestCameraPermission() {
    if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      // 二度目の許可ダイアログにて，Never ask againにチェックが入れられていないと表示されるもの
      if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
        Toast.makeText(this, "For using camera, you need to allow permission.", Toast.LENGTH_LONG).show();

      // 権限許可を促すシステムダイアログ表示
      requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    } else {
      prepareCameraView(binding.cameraTextureView.getWidth(), binding.cameraTextureView.getHeight());
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode != REQUEST_CAMERA) return;

    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      prepareCameraView(binding.cameraTextureView.getWidth(), binding.cameraTextureView.getHeight());
    }
  }

  private void prepareCameraView(int width, int height) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      finish();
    }
    if (width <= 0 || height <= 0) return;

    displayListener = new DisplayManager.DisplayListener() {
      @Override
      public void onDisplayAdded(int displayId) {}

      @Override
      public void onDisplayRemoved(int displayId) {}

      @Override
      public void onDisplayChanged(int displayId) {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        configureTransform(displaySize.x, displaySize.y);

        int newOrientationNum = getWindowManager().getDefaultDisplay().getRotation();
        // 端末を180度回転させると二回目のconfigureTransformが呼ばれないのでここで実行
        if (Math.abs(newOrientationNum - lastOrientationNum) == 2) {
          configureTransform(binding.cameraTextureView.getWidth(), binding.cameraTextureView.getHeight());

          // 180度回転の場合はonResumeが呼ばれないのでここで保持
          lastOrientationNum = newOrientationNum;
        }
      }
    };
    displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
    displayManager.registerDisplayListener(displayListener, backgroundHandler);

    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

    try {
      numberOfCameras = manager.getCameraIdList().length;

      cameraCharacteristics = manager.getCameraCharacteristics(currentCameraId);
      isCameraFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
      configureTransform(binding.cameraTextureView.getWidth(), binding.cameraTextureView.getHeight());

      // ストリームの設定を取得（出力サイズを取得する）
      StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);

      // sizes配列から最大の組み合わせを取得する
      Size maxSize = new Size(0, 0);
      for (Size size : sizes) {
        if (maxSize.getWidth() <= size.getWidth()) maxSize = size;
      }
      Size maxImageSize = maxSize;

      previewImageReader = ImageReader.newInstance(maxImageSize.getWidth(), maxImageSize.getHeight(), ImageFormat.JPEG, MAX_IMAGES);

      previewImageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

      int displayRotationNum = getWindowManager().getDefaultDisplay().getRotation();

      if (displayRotationNum == Surface.ROTATION_90 || displayRotationNum == Surface.ROTATION_270) {
        binding.cameraTextureView.setAspectRatio(maxImageSize.getWidth(), maxImageSize.getHeight());
      } else {
        binding.cameraTextureView.setAspectRatio(maxImageSize.getHeight(), maxImageSize.getWidth());
      }

      // 取得したSizeのうち，画面のアスペクト比に合致かつTEXTURE_VIEW_MAX_WIDTH・TEXTURE_VIEW_MAX_HEIGHT以下の最大値をセット
      final float aspectRatio = ((float) maxImageSize.getHeight() / (float) maxImageSize.getWidth());

      int maxWidth = TEXTURE_VIEW_MAX_WIDTH, maxHeight = TEXTURE_VIEW_MAX_HEIGHT;
      Size setSize = new Size(maxWidth, maxHeight);
      for (Size size : sizes) {
        if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight && size.getHeight() == (size.getWidth() * aspectRatio)) {
          if (setSize.getWidth() <= size.getWidth()) setSize = size;
        }
      }
      previewSize = setSize;

      manager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
          if (cameraDevice == null) cameraDevice = camera;
          createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
          cameraDevice.close();
          cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
          cameraDevice.close();
          cameraDevice = null;
        }
      }, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  protected void createCameraPreviewSession() {
    if (cameraDevice == null || !binding.cameraTextureView.isAvailable() || previewSize == null)
      return;

    SurfaceTexture texture = binding.cameraTextureView.getSurfaceTexture();
    if (texture == null) return;

    previewImageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
    Surface surface = new Surface(texture);

    try {
      previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }

    previewBuilder.addTarget(surface);

    try {
      cameraDevice.createCaptureSession(
          Arrays.asList(surface, previewImageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
              previewSession = session;
              if (cameraDevice == null) return;
              setCameraMode(previewBuilder);
              try {
                previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
              } catch (CameraAccessException e) {
                e.printStackTrace();
              }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            }
          }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void takePhoto() {
    if (cameraDevice == null || previewSession == null) return;

    try {
      final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(previewImageReader.getSurface());
      setCameraMode(captureBuilder);

      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                         getJpegOrientation());

      previewSession.stopRepeating();

      previewSession.capture(captureBuilder.build(), null, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {

    @Override
    public void onImageAvailable(ImageReader reader) {
      capturedImage = reader.acquireLatestImage();

      backgroundHandler.post(new Runnable() {
        @Override
        public void run() {
          ByteBuffer byteBuffer = capturedImage.getPlanes()[0].getBuffer();
          byte[] bytes = new byte[byteBuffer.capacity()];
          byteBuffer.get(bytes);

          Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

          Matrix matrix = new Matrix();

          if (isCameraFacing) {
            matrix.preScale(-1.0f, 1.0f);
            matrix.postRotate(270.f);
          } else {
            matrix.postRotate(90.f);
          }

          bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

          float cropWidthRatio = (float) binding.activityMainRalative.getMeasuredWidth() / (float) binding.cameraTextureView.getMeasuredWidth();
          int cropWidth = (int) (bitmap.getWidth() - (bitmap.getWidth() * cropWidthRatio));

          float cropHeightRatio = (float) binding.activityMainRalative.getMeasuredWidth() / (float) binding.cameraTextureView.getMeasuredHeight();
          int cropHeight = (int) (bitmap.getHeight() - (bitmap.getHeight() * cropHeightRatio));

          bitmap = Bitmap.createBitmap(bitmap, 0, cropHeight / 2, bitmap.getWidth() - cropWidth, bitmap.getHeight() - cropHeight);

          if (cameraDevice == null) prepareCameraView(binding.cameraTextureView.getWidth(),
                                                      binding.cameraTextureView.getHeight());
          else createCameraPreviewSession();

          capturedImage.close();
        }
      });
    }
  };

  private void configureTransform(final int viewWidth, final int viewHeight) {
    if (binding.cameraTextureView == null || previewSize == null) return;

    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        RectF rectView = new RectF(0, 0, viewWidth, viewHeight);
        RectF rectPreview = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());

        float centerX = rectView.centerX();
        float centerY = rectView.centerY();

        Matrix matrix = new Matrix();

        int displayRotationNum = getWindowManager().getDefaultDisplay().getRotation();
        if (displayRotationNum == Surface.ROTATION_90 || displayRotationNum == Surface.ROTATION_270) {
          rectPreview.offset(centerX - rectPreview.centerX(), centerY - rectPreview.centerY());
          matrix.setRectToRect(rectView, rectPreview, Matrix.ScaleToFit.FILL);

          // 縦または横の画面一杯に表示するためのScale値を取得
          float scale = Math.max((float) viewHeight / previewSize.getHeight(),
                                 (float) viewWidth / previewSize.getWidth());

          matrix.postScale(scale, scale, centerX, centerY);

          // Nexus 5Xの場合は，以下を使う
          if (Build.MODEL.contains("Nexus 5X")) {
            // ROTATION_90: 270度回転，ROTATION_270: 90度回転
            matrix.postRotate((90 * (displayRotationNum + 2)) % 360, centerX, centerY);
          } else {
            if (isCameraFacing) matrix.postRotate(90 * displayRotationNum, centerX, centerY);
            else matrix.postRotate((90 * (displayRotationNum + 2)) % 360, centerX, centerY);
          }
        } else {
          // Nexus 5Xの場合は，以下を使う
          if (Build.MODEL.contains("Nexus 5X")) {
            // ROTATION_0: 0度回転，ROTATION_180: 180度回転
            matrix.postRotate(90 * displayRotationNum, centerX, centerY);
          } else {
            if (isCameraFacing)
              matrix.postRotate((90 * (displayRotationNum + 2)) % 360, centerX, centerY);
            else matrix.postRotate(90 * displayRotationNum, centerX, centerY);
          }
        }
        binding.cameraTextureView.setTransform(matrix);
      }
    });
  }

  private void setCameraMode(CaptureRequest.Builder requestBuilder) {
    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
  }

  private int getJpegOrientation() {
    if (lastOrientationNum == -1) return 0;

    int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

    int deviceOrientation = (lastOrientationNum + 45) / 90 * 90;

    if (isCameraFacing) deviceOrientation = -deviceOrientation;

    return (sensorOrientation + deviceOrientation + 360) % 360;
  }

  private void closeCamera() {
    previewSession.close();
    previewSession = null;
    cameraDevice.close();
    cameraDevice = null;
  }

  private void startBackgroundThread() {
    backgroundHandlerThread = new HandlerThread("CameraPreview");
    backgroundHandlerThread.start();
    backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
  }

  private void stopBackgroundThread() {
    backgroundHandlerThread.quitSafely();
    try {
      backgroundHandlerThread.join();
      backgroundHandlerThread = null;
      backgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
