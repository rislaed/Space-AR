/*
 * Copyright 2020-2021 RislaeD (github.com/rislaed)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.maxfeed.ar.space.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.maxfeed.ar.space.R;
import com.maxfeed.ar.space.core.rendering.AugmentedImageRenderer;
import com.maxfeed.ar.space.rendering.BackgroundRenderer;
import com.maxfeed.ar.space.util.CameraPermissionHelper;
import com.maxfeed.ar.space.util.DisplayRotationHelper;
import com.maxfeed.ar.space.util.FullScreenHelper;
import com.maxfeed.ar.space.util.SnackbarHelper;
import com.maxfeed.ar.space.util.TrackingStateHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class AugmentedImageActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
	private static final String TAG = AugmentedImageActivity.class.getSimpleName();

	private GLSurfaceView surfaceView;
	private ImageView fitToScanView;
	private RequestManager glideRequestManager;

	private boolean installRequested;

	private Session session;
	private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
	private DisplayRotationHelper displayRotationHelper;
	private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

	private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
	private final AugmentedImageRenderer augmentedImageRenderer = new AugmentedImageRenderer();

	private boolean shouldConfigureSession = false;

	// Использует сжатый файл для отрисовки или же оригинальный, нестабильный
	private final boolean useSingleImage = false;
	// Связки плоскостей с их центральными точками, нужно для отрисовки
	private final Map<Integer, Pair<AugmentedImage, Anchor>> augmentedImageMap = new HashMap<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		surfaceView = findViewById(R.id.surfaceCanvas);
		displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

		surfaceView.setPreserveEGLContextOnPause(true);
		surfaceView.setEGLContextClientVersion(2);
		surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
		surfaceView.setRenderer(this);
		surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
		surfaceView.setWillNotDraw(false);

		fitToScanView = findViewById(R.id.fitToScanPlaceholder);
		glideRequestManager = Glide.with(this);
		glideRequestManager.load(Uri.parse("file:///android_asset/fit_to_scan.png")).into(fitToScanView);

		installRequested = false;
	}

	@Override
	protected void onDestroy() {
		if (session != null) {
			session.close();
			session = null;
		}
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (session == null) {
			Exception exception = null;
			String message = null;
			try {
				switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
				case INSTALL_REQUESTED:
					installRequested = true;
					return;
				case INSTALLED:
					break;
				}
				if (!CameraPermissionHelper.hasCameraPermission(this)) {
					CameraPermissionHelper.requestCameraPermission(this);
					return;
				}
				session = new Session(/* context = */ this);
			} catch (UnavailableArcoreNotInstalledException | UnavailableUserDeclinedInstallationException e) {
				message = "Пожалуйста, установите ARCore";
				exception = e;
			} catch (UnavailableApkTooOldException e) {
				message = "Пожалуйста, обновите ARCore";
				exception = e;
			} catch (UnavailableSdkTooOldException e) {
				message = "Пожалуйста, обновите это приложение";
				exception = e;
			} catch (Exception e) {
				message = "Это устройство не поддерживает AR";
				exception = e;
			}

			if (message != null) {
				messageSnackbarHelper.showError(this, message);
				Log.e(TAG, "Error when handling session", exception);
				return;
			}

			shouldConfigureSession = true;
		}

		if (shouldConfigureSession) {
			configureSession();
			shouldConfigureSession = false;
		}

		try {
			session.resume();
		} catch (CameraNotAvailableException e) {
			messageSnackbarHelper.showError(this, "Камера недоступна, попробуйте перезапустить приложение");
			session = null;
			return;
		}
		surfaceView.onResume();
		displayRotationHelper.onResume();

		fitToScanView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (session != null) {
			displayRotationHelper.onPause();
			surfaceView.onPause();
			session.pause();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
		super.onRequestPermissionsResult(requestCode, permissions, results);
		if (!CameraPermissionHelper.hasCameraPermission(this)) {
			Toast.makeText(this, "Для запуска приложения необходим доступ к камере", Toast.LENGTH_LONG).show();
			if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
				CameraPermissionHelper.launchPermissionSettings(this);
			}
			finish();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
		// Подготовка основной канвы для рисования
		try {
			backgroundRenderer.createOnGlThread(/*context=*/ this);
			augmentedImageRenderer.createOnGlThread(/*context=*/ this);
		} catch (IOException e) {
			Log.e(TAG, "Oh nose everything broke", e);
		}
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		displayRotationHelper.onSurfaceChanged(width, height);
		GLES20.glViewport(0, 0, width, height);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		if (session == null) {
			return;
		}
		displayRotationHelper.updateSessionIfNeeded(session);
		try {
			session.setCameraTextureName(backgroundRenderer.getTextureId());
			Frame frame = session.update();
			Camera camera = frame.getCamera();
			trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
			backgroundRenderer.draw(frame);
			// Обработка матрицы направления (4х4) для определения местоположения в пространстве
			float[] projmtx = new float[16];
			camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);
			// Обработка матрицы камеры (4х4) и последующая отрисовка
			float[] viewmtx = new float[16];
			camera.getViewMatrix(viewmtx, 0);
			// Адаптивная обработка окружающего уровня освещения
			final float[] colorCorrectionRgba = new float[4];
			frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);
			// Финальный этап отрисовки кешированных объектов
			drawAugmentedImages(frame, projmtx, viewmtx, colorCorrectionRgba);
		} catch (Throwable t) {
			Log.e(TAG, "Oh nose everything broke", t);
		}
	}

	private void configureSession() {
		Config config = new Config(session);
		config.setFocusMode(Config.FocusMode.AUTO);
		if (!setupAugmentedImageDatabase(config)) {
			messageSnackbarHelper.showError(this, "Не удалось обработать межпланетную базу данных");
		}
		session.configure(config);
	}

	private void drawAugmentedImages(Frame frame, float[] projmtx, float[] viewmtx, float[] colorCorrectionRgba) {
		Collection<AugmentedImage> updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);
		for (AugmentedImage augmentedImage : updatedAugmentedImages) {
			switch (augmentedImage.getTrackingState()) {
			case PAUSED:
				String text = String.format("Найдена планета %d", augmentedImage.getIndex());
				messageSnackbarHelper.showMessage(this, text);
				break;

			case TRACKING:
				this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						fitToScanView.setVisibility(View.GONE);
					}
				});

				// Создание новых плоскостей для новых планет
				if (!augmentedImageMap.containsKey(augmentedImage.getIndex())) {
					Anchor centerPoseAnchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
					augmentedImageMap.put(augmentedImage.getIndex(), Pair.create(augmentedImage, centerPoseAnchor));
				}
				break;

			case STOPPED:
				augmentedImageMap.remove(augmentedImage.getIndex());
				break;

			default:
				break;
			}
		}
		for (Pair<AugmentedImage, Anchor> pair : augmentedImageMap.values()) {
			AugmentedImage augmentedImage = pair.first;
			Anchor centerAnchor = augmentedImageMap.get(augmentedImage.getIndex()).second;
			switch (augmentedImage.getTrackingState()) {
			case TRACKING:
				augmentedImageRenderer.draw(viewmtx, projmtx, augmentedImage, centerAnchor, colorCorrectionRgba);
				break;
			default:
				break;
			}
		}
	}

	private boolean setupAugmentedImageDatabase(Config config) {
		AugmentedImageDatabase augmentedImageDatabase;
		if (useSingleImage) {
			Bitmap augmentedImageBitmap = loadAugmentedImageBitmap();
			if (augmentedImageBitmap == null) {
				return false;
			}
			augmentedImageDatabase = new AugmentedImageDatabase(session);
			augmentedImageDatabase.addImage("image_name", augmentedImageBitmap);
		} else {
			try {
				InputStream is = getAssets().open("sample_database.imgdb");
				augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, is);
			} catch (IOException e) {
				Log.e(TAG, "Not found AR parsed image database", e);
				return false;
			}
		}
		config.setAugmentedImageDatabase(augmentedImageDatabase);
		return true;
	}

	private Bitmap loadAugmentedImageBitmap() {
		try {
			InputStream is = getAssets().open("default.jpg");
			return BitmapFactory.decodeStream(is);
		} catch (IOException e) {
			Log.e(TAG, "Not found AR default resource", e);
		}
		return null;
	}
}
