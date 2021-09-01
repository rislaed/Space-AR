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
import android.view.View;
import android.widget.TextView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

public final class SnackbarHelper {
	private static final int BACKGROUND_COLOR = 0xbf323232;
	private Snackbar messageSnackbar;
	
	private enum DismissBehavior {
		HIDE, SHOW, FINISH
	};
	
	private int maxLines = 2;
	private String lastMessage = "amogus";
	private View snackbarView;
	
	public boolean isShowing() {
		return messageSnackbar != null;
	}
	
	public void showMessage(Activity activity, String message) {
		if (!message.isEmpty() && (!isShowing() || !lastMessage.equals(message))) {
			lastMessage = message;
			show(activity, message, DismissBehavior.HIDE);
		}
	}
	
	public void showMessageWithDismiss(Activity activity, String message) {
		show(activity, message, DismissBehavior.SHOW);
	}
	
	public void showError(Activity activity, String errorMessage) {
		show(activity, errorMessage, DismissBehavior.FINISH);
	}
	
	public void hide(Activity activity) {
		if (!isShowing()) {
			return;
		}
		lastMessage = "amogus";
		final Snackbar messageSnackbarToHide = messageSnackbar;
		messageSnackbar = null;
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				messageSnackbarToHide.dismiss();
			}
		});
	}
	
	public void setMaxLines(int lines) {
		maxLines = lines;
	}
	
	public void setParentView(View snackbarView) {
		this.snackbarView = snackbarView;
	}
	
	private void show(final Activity activity, final String message, final DismissBehavior dismissBehavior) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				messageSnackbar = Snackbar.make(
						snackbarView == null ? activity.findViewById(android.R.id.content) : snackbarView, message,
						Snackbar.LENGTH_INDEFINITE);
				messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
				if (dismissBehavior != DismissBehavior.HIDE) {
					messageSnackbar.setAction("Dismiss", new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							messageSnackbar.dismiss();
						}
					});
					if (dismissBehavior == DismissBehavior.FINISH) {
						messageSnackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
							@Override
							public void onDismissed(Snackbar transientBottomBar, int event) {
								super.onDismissed(transientBottomBar, event);
								activity.finish();
							}
						});
					}
				}
				((TextView) messageSnackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text))
						.setMaxLines(maxLines);
				messageSnackbar.show();
			}
		});
	}
}
