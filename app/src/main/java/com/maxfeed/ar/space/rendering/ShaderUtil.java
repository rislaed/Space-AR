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
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

public final class ShaderUtil {
	public static int loadGLShader(String tag, Context context, int type, String filename,
			Map<String, Integer> defineValuesMap) throws IOException {
		String code = readShaderFileFromAssets(context, filename);
		String defines = "";
		for (Map.Entry<String, Integer> entry : defineValuesMap.entrySet()) {
			defines += "#define " + entry.getKey() + " " + entry.getValue() + "\n";
		}
		code = defines + code;
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, code);
		GLES20.glCompileShader(shader);
		final int[] compileStatus = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
		if (compileStatus[0] == 0) {
			Log.e(tag, "Oh nose shader broke: " + GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}
		if (shader == 0) {
			throw new RuntimeException("Oh nose everything broke");
		}
		return shader;
	}
	
	public static int loadGLShader(String tag, Context context, int type, String filename) throws IOException {
		Map<String, Integer> emptyDefineValuesMap = new TreeMap<>();
		return loadGLShader(tag, context, type, filename, emptyDefineValuesMap);
	}
	
	public static void checkGLError(String tag, String label) {
		int lastError = GLES20.GL_NO_ERROR;
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(tag, label + ": glError " + error);
			lastError = error;
		}
		if (lastError != GLES20.GL_NO_ERROR) {
			throw new RuntimeException(label + ": glError " + lastError);
		}
	}
	
	private static String readShaderFileFromAssets(Context context, String filename) throws IOException {
		try (InputStream inputStream = context.getAssets().open(filename);
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(" ", -1);
				if (tokens[0].equals("#include")) {
					String includeFilename = tokens[1];
					includeFilename = includeFilename.replace("\"", "");
					if (includeFilename.equals(filename)) {
						throw new IOException(filename + " must include itself");
					}
					sb.append(readShaderFileFromAssets(context, includeFilename));
				} else {
					sb.append(line).append("\n");
				}
			}
			return sb.toString();
		}
	}
	
	private ShaderUtil() {}
}
