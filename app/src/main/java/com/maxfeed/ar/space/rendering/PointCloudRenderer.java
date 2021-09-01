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
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.google.ar.core.PointCloud;
import java.io.IOException;

public class PointCloudRenderer {
	private static final String TAG = PointCloud.class.getSimpleName();
	
	private static final String VERTEX_SHADER_NAME = "shaders/point_cloud.vert";
	private static final String FRAGMENT_SHADER_NAME = "shaders/point_cloud.frag";
	
	private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
	private static final int FLOATS_PER_POINT = 4;
	private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
	private static final int INITIAL_BUFFER_POINTS = 1000;
	
	private int vbo;
	private int vboSize;
	
	private int programName;
	private int positionAttribute;
	private int modelViewProjectionUniform;
	private int colorUniform;
	private int pointSizeUniform;
	
	private int numPoints = 0;
	private long lastTimestamp = 0;
	
	public PointCloudRenderer() {}
	
	public void createOnGlThread(Context context) throws IOException {
		ShaderUtil.checkGLError(TAG, "before create");
		int[] buffers = new int[1];
		GLES20.glGenBuffers(1, buffers, 0);
		vbo = buffers[0];
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
		vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
		ShaderUtil.checkGLError(TAG, "buffer alloc");
		int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
		int passthroughShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);
		programName = GLES20.glCreateProgram();
		GLES20.glAttachShader(programName, vertexShader);
		GLES20.glAttachShader(programName, passthroughShader);
		GLES20.glLinkProgram(programName);
		GLES20.glUseProgram(programName);
		ShaderUtil.checkGLError(TAG, "program");
		positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position");
		colorUniform = GLES20.glGetUniformLocation(programName, "u_Color");
		modelViewProjectionUniform = GLES20.glGetUniformLocation(programName, "u_ModelViewProjection");
		pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize");
		ShaderUtil.checkGLError(TAG, "program	params");
	}
	
	public void update(PointCloud cloud) {
		if (cloud.getTimestamp() == lastTimestamp) {
			return;
		}
		ShaderUtil.checkGLError(TAG, "before update");
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
		lastTimestamp = cloud.getTimestamp();
		numPoints = cloud.getPoints().remaining() / FLOATS_PER_POINT;
		if (numPoints * BYTES_PER_POINT > vboSize) {
			while (numPoints * BYTES_PER_POINT > vboSize) {
				vboSize *= 2;
			}
			GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
		}
		GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, numPoints * BYTES_PER_POINT, cloud.getPoints());
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
		ShaderUtil.checkGLError(TAG, "after update");
	}
	
	public void draw(float[] cameraView, float[] cameraPerspective) {
		float[] modelViewProjection = new float[16];
		Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);
		ShaderUtil.checkGLError(TAG, "Before draw");
		GLES20.glUseProgram(programName);
		GLES20.glEnableVertexAttribArray(positionAttribute);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
		GLES20.glVertexAttribPointer(positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
		GLES20.glUniform4f(colorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
		GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0);
		GLES20.glUniform1f(pointSizeUniform, 5.0f);
		GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);
		GLES20.glDisableVertexAttribArray(positionAttribute);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
		ShaderUtil.checkGLError(TAG, "Draw");
	}
}
