package net.trileg.learncamera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
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

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
  private static final int REQUEST_CAMERA = 1;
  private CameraDevice cameraDevice;
  private Size previewSize;
  private TextureView cameraTextureView;
  private CaptureRequest.Builder previewBuilder;
  private CameraCaptureSession previewSession;
  private View topCoverView;
  private View bottomCoverView;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

    cameraTextureView = binding.activityMainTextureview;
    topCoverView = binding.topCoverView;
    bottomCoverView = binding.bottomCoverView;
    cameraTextureView.setSurfaceTextureListener(cameraViewStatusChanged); // Register listener

    ViewTreeObserver observer = cameraTextureView.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        int length = Math.abs(cameraTextureView.getHeight() - cameraTextureView.getWidth()) / 2;
        topCoverView.getLayoutParams().height = length;
        topCoverView.requestLayout();
        bottomCoverView.getLayoutParams().height = length;
        bottomCoverView.requestLayout();

        cameraTextureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      }
    });
  }

  private final TextureView.SurfaceTextureListener cameraViewStatusChanged = new TextureView.SurfaceTextureListener() {
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
      requestCameraPermission();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
      return false;
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
      prepareCameraView();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode != REQUEST_CAMERA) return;

    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) prepareCameraView();
  }

  private void prepareCameraView() {
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
          continue;


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          finish();
        }

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

        manager.openCamera(cameraId, new CameraDevice.StateCallback() {
          @Override
          public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
          }

          @Override
          public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
          }

          @Override
          public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
          }
        }, null);

      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  protected void createCameraPreviewSession() {
    if (cameraDevice == null || !cameraTextureView.isAvailable() || previewSize == null) return;

    SurfaceTexture texture = cameraTextureView.getSurfaceTexture();
    if (texture == null) return;

    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
    Surface surface = new Surface(texture);

    try {
      previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }

    previewBuilder.addTarget(surface);

    try {
      cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          previewSession = session;
          updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
      }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  protected void updatePreview() {
    if (cameraDevice == null) return;

    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

    HandlerThread thread = new HandlerThread("CameraPreview");
    thread.start();
    Handler backgroundHandler = new Handler(thread.getLooper());

    try {
      previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }
}
