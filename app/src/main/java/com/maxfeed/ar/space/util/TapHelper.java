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

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class TapHelper implements OnTouchListener {
	private final GestureDetector gestureDetector;
	private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
	
	public TapHelper(Context context) {
		gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				queuedSingleTaps.offer(e);
				return true;
			}

			@Override
			public boolean onDown(MotionEvent e) {
				return true;
			}
		});
	}
	
	public MotionEvent poll() {
		return queuedSingleTaps.poll();
	}
	
	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		return gestureDetector.onTouchEvent(motionEvent);
	}
}
