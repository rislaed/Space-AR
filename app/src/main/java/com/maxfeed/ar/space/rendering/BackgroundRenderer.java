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
package com.maxfeed.ar.space.rendering;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import androidx.annotation.NonNull;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class BackgroundRenderer {
	private static final String TAG = BackgroundRenderer.class.getSimpleName();

	private static final String CAMERA_VERTEX_SHADER_NAME = "shaders/screenquad.vert";
	private static final String CAMERA_FRAGMENT_SHADER_NAME = "shaders/screenquad.frag";
	private static final String DEPTH_VISUALIZER_VERTEX_SHADER_NAME = "shaders/background_show_depth_color_visualization.vert";
	private static final String DEPTH_VISUALIZER_FRAGMENT_SHADER_NAME = "shaders/background_show_depth_color_visualization.frag";

	private static final int COORDS_PER_VERTEX = 2;
	private static final int TEXCOORDS_PER_VERTEX = 2;
	private static final int FLOAT_SIZE = 4;

	private FloatBuffer quadCoords;
	private FloatBuffer quadTexCoords;

	private int cameraProgram;
	private int depthProgram;
	private int cameraPositionAttrib;
	private int cameraTexCoordAttrib;
	private int cameraTextureUniform;
	private int cameraTextureId = -1;
	private boolean suppressTimestampZeroRendering = true;
	private int depthPositionAttrib;
	private int depthTexCoordAttrib;
	private int depthTextureUniform;
	private int depthTextureId = -1;

	public int getTextureId() {
		return cameraTextureId;
	}

	public void createOnGlThread(Context context, int depthTextureId) throws IOException {
		// Generate the background texture.
		int[] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		cameraTextureId = textures[0];
		int textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
		GLES20.glBindTexture(textureTarget, cameraTextureId);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		int numVertices = 4;
		if (numVertices != QUAD_COORDS.length / COORDS_PER_VERTEX) {
			throw new RuntimeException("BackgroundRenderer must get on enter quad'atic vertex count");
		}
		ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
		bbCoords.order(ByteOrder.nativeOrder());
		quadCoords = bbCoords.asFloatBuffer();
		quadCoords.put(QUAD_COORDS);
		quadCoords.position(0);
		ByteBuffer bbTexCoordsTransformed = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
		bbTexCoordsTransformed.order(ByteOrder.nativeOrder());
		quadTexCoords = bbTexCoordsTransformed.asFloatBuffer();
		{
			int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER,
					CAMERA_VERTEX_SHADER_NAME);
			int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER,
					CAMERA_FRAGMENT_SHADER_NAME);

			cameraProgram = GLES20.glCreateProgram();
			GLES20.glAttachShader(cameraProgram, vertexShader);
			GLES20.glAttachShader(cameraProgram, fragmentShader);
			GLES20.glLinkProgram(cameraProgram);
			GLES20.glUseProgram(cameraProgram);
			cameraPositionAttrib = GLES20.glGetAttribLocation(cameraProgram, "a_Position");
			cameraTexCoordAttrib = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord");
			ShaderUtil.checkGLError(TAG, "Program creation");

			cameraTextureUniform = GLES20.glGetUniformLocation(cameraProgram, "sTexture");
			ShaderUtil.checkGLError(TAG, "Program parameters");
		}
		{
			int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER,
					DEPTH_VISUALIZER_VERTEX_SHADER_NAME);
			int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER,
					DEPTH_VISUALIZER_FRAGMENT_SHADER_NAME);
			depthProgram = GLES20.glCreateProgram();
			GLES20.glAttachShader(depthProgram, vertexShader);
			GLES20.glAttachShader(depthProgram, fragmentShader);
			GLES20.glLinkProgram(depthProgram);
			GLES20.glUseProgram(depthProgram);
			depthPositionAttrib = GLES20.glGetAttribLocation(depthProgram, "a_Position");
			depthTexCoordAttrib = GLES20.glGetAttribLocation(depthProgram, "a_TexCoord");
			ShaderUtil.checkGLError(TAG, "Program creation");
			depthTextureUniform = GLES20.glGetUniformLocation(depthProgram, "u_DepthTexture");
			ShaderUtil.checkGLError(TAG, "Program parameters");
		}
		this.depthTextureId = depthTextureId;
	}

	public void createOnGlThread(Context context) throws IOException {
		createOnGlThread(context, /*depthTextureId=*/ -1);
	}

	public void suppressTimestampZeroRendering(boolean suppressTimestampZeroRendering) {
		this.suppressTimestampZeroRendering = suppressTimestampZeroRendering;
	}

	public void draw(@NonNull Frame frame, boolean debugShowDepthMap) {
		if (frame.hasDisplayGeometryChanged()) {
			frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
					Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords);
		}
		if (frame.getTimestamp() == 0 && suppressTimestampZeroRendering) {
			return;
		}
		draw(debugShowDepthMap);
	}

	public void draw(@NonNull Frame frame) {
		draw(frame, /*debugShowDepthMap=*/ false);
	}

	public void draw(int imageWidth, int imageHeight, float screenAspectRatio, int cameraToDisplayRotation) {
		float imageAspectRatio = (float) imageWidth / imageHeight;
		float croppedWidth;
		float croppedHeight;
		if (screenAspectRatio < imageAspectRatio) {
			croppedWidth = imageHeight * screenAspectRatio;
			croppedHeight = imageHeight;
		} else {
			croppedWidth = imageWidth;
			croppedHeight = imageWidth / screenAspectRatio;
		}
		float u = (imageWidth - croppedWidth) / imageWidth * 0.5f;
		float v = (imageHeight - croppedHeight) / imageHeight * 0.5f;
		float[] texCoordTransformed;
		switch (cameraToDisplayRotation) {
		case 90:
			texCoordTransformed = new float[] { 1 - u, 1 - v, 1 - u, v, u, 1 - v, u, v };
			break;
		case 180:
			texCoordTransformed = new float[] { 1 - u, v, u, v, 1 - u, 1 - v, u, 1 - v };
			break;
		case 270:
			texCoordTransformed = new float[] { u, v, u, 1 - v, 1 - u, v, 1 - u, 1 - v };
			break;
		case 0:
			texCoordTransformed = new float[] { u, 1 - v, 1 - u, 1 - v, u, v, 1 - u, v };
			break;
		default:
			throw new IllegalArgumentException("Illegal degrees: " + cameraToDisplayRotation);
		}
		quadTexCoords.position(0);
		quadTexCoords.put(texCoordTransformed);
		draw(/*debugShowDepthMap=*/ false);
	}

	private void draw(boolean debugShowDepthMap) {
		quadTexCoords.position(0);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		GLES20.glDepthMask(false);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		if (debugShowDepthMap) {
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTextureId);
			GLES20.glUseProgram(depthProgram);
			GLES20.glUniform1i(depthTextureUniform, 0);
			GLES20.glVertexAttribPointer(depthPositionAttrib, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadCoords);
			GLES20.glVertexAttribPointer(depthTexCoordAttrib, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0,
					quadTexCoords);
			GLES20.glEnableVertexAttribArray(depthPositionAttrib);
			GLES20.glEnableVertexAttribArray(depthTexCoordAttrib);
		} else {
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
			GLES20.glUseProgram(cameraProgram);
			GLES20.glUniform1i(cameraTextureUniform, 0);
			GLES20.glVertexAttribPointer(cameraPositionAttrib, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0,
					quadCoords);
			GLES20.glVertexAttribPointer(cameraTexCoordAttrib, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0,
					quadTexCoords);
			GLES20.glEnableVertexAttribArray(cameraPositionAttrib);
			GLES20.glEnableVertexAttribArray(cameraTexCoordAttrib);
		}
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		if (debugShowDepthMap) {
			GLES20.glDisableVertexAttribArray(depthPositionAttrib);
			GLES20.glDisableVertexAttribArray(depthTexCoordAttrib);
		} else {
			GLES20.glDisableVertexAttribArray(cameraPositionAttrib);
			GLES20.glDisableVertexAttribArray(cameraTexCoordAttrib);
		}
		GLES20.glDepthMask(true);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
	}

	private static final float[] QUAD_COORDS = new float[] { -1.0f, -1.0f, +1.0f, -1.0f, -1.0f, +1.0f, +1.0f, +1.0f, };
}
