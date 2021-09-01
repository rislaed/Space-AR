/*
 * Copyright 2020-2021 RislaeD (github.com/rislaed)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.maxfeed.ar.space.util;

import android.app.Activity;
import android.os.Build;
import android.view.WindowManager;
import com.google.ar.core.Camera;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

public final class TrackingStateHelper {
	private static final String INSUFFICIENT_FEATURES_MESSAGE = "Не удается ничего найти. Попробуйте направить камеру на более яркое место.";
	private static final String EXCESSIVE_MOTION_MESSAGE = "Слишком быстрые движения. Не торопитесь.";
	private static final String INSUFFICIENT_LIGHT_MESSAGE = "Слишком темно. Попробуйте найти свет.";
	private static final String INSUFFICIENT_LIGHT_ANDROID_S_MESSAGE = "Слишком темно. Попробуйте найти свет."
			+ " Также, убедитесь, что блокировка камеры не включена в настройках устройства.";
	private static final String BAD_STATE_MESSAGE = "Произошла внутренняя ошибка, попробуйте перезапустить приложение.";
	private static final String CAMERA_UNAVAILABLE_MESSAGE = "Другое приложение уже использует камеру, для начала попробуйте обнаружить его.";
	private static final int ANDROID_S_SDK_VERSION = 31;
	
	private final Activity activity;
	private TrackingState previousTrackingState;
	
	public TrackingStateHelper(Activity activity) {
		this.activity = activity;
	}
	
	public void updateKeepScreenOnFlag(TrackingState trackingState) {
		if (trackingState == previousTrackingState) {
			return;
		}
		previousTrackingState = trackingState;
		switch (trackingState) {
		case PAUSED:
		case STOPPED:
			activity.runOnUiThread(new Runnable() {
				public void run() {
					activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				}
			});
			break;
		case TRACKING:
			activity.runOnUiThread(new Runnable() {
				public void run() {
					activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				}
			});
			break;
		}
	}
	
	public static String getTrackingFailureReasonString(Camera camera) {
		TrackingFailureReason reason = camera.getTrackingFailureReason();
		switch (reason) {
		case NONE:
			return "?";
		case BAD_STATE:
			return BAD_STATE_MESSAGE;
		case INSUFFICIENT_LIGHT:
			if (Build.VERSION.SDK_INT < ANDROID_S_SDK_VERSION) {
				return INSUFFICIENT_LIGHT_MESSAGE;
			} else {
				return INSUFFICIENT_LIGHT_ANDROID_S_MESSAGE;
			}
		case EXCESSIVE_MOTION:
			return EXCESSIVE_MOTION_MESSAGE;
		case INSUFFICIENT_FEATURES:
			return INSUFFICIENT_FEATURES_MESSAGE;
		case CAMERA_UNAVAILABLE:
			return CAMERA_UNAVAILABLE_MESSAGE;
		}
		return "Необычная проблема: " + reason;
	}
}
