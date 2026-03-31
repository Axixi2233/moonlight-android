package com.limelight.fsr;

import android.content.Context;
import android.opengl.GLES20;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlProgram {
    private final int vertexShader;
    private final int fragmentShader;
    private final int programId;

    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Map<String, Integer> attributeLocations = new HashMap<>();
    private final Map<String, UniformValue> pendingUniforms = new HashMap<>();
    private final Map<String, AttributeValue> attributes = new HashMap<>();
    private final List<Integer> enabledAttributes = new ArrayList<>();

    public GlProgram(Context context, String vertexShaderAssetPath, String fragmentShaderAssetPath)
            throws IOException {
        this(GlUtil.loadAssetText(context, vertexShaderAssetPath),
                GlUtil.loadAssetText(context, fragmentShaderAssetPath));
    }

    public GlProgram(String vertexShaderSource, String fragmentShaderSource) {
        vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
        fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);

        programId = GLES20.glCreateProgram();
        if (programId == 0) {
            throw new GlUtil.GlException("glCreateProgram() returned 0");
        }

        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String infoLog = GLES20.glGetProgramInfoLog(programId);
            delete();
            throw new GlUtil.GlException("Program link failed: " + infoLog);
        }
    }

    public int getProgramId() {
        return programId;
    }

    public void use() {
        GLES20.glUseProgram(programId);
        GlUtil.checkGlError("glUseProgram");
    }

    public void setBufferAttribute(String name, FloatBuffer buffer, int vectorSize) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        if (vectorSize < 1 || vectorSize > 4) {
            throw new IllegalArgumentException("vectorSize must be between 1 and 4");
        }
        attributes.put(name, new AttributeValue(buffer, vectorSize));
    }

    public void setSamplerTexIdUniform(String name, int textureId, int unit, int target) {
        pendingUniforms.put(name, new SamplerUniformValue(textureId, unit, target));
    }

    public void setFloatUniform(String name, float value) {
        pendingUniforms.put(name, new FloatUniformValue(value));
    }

    public void setFloatsUniform(String name, float[] values) {
        if (values == null || values.length == 0 || values.length > 4) {
            throw new IllegalArgumentException("values length must be between 1 and 4");
        }
        pendingUniforms.put(name, new FloatArrayUniformValue(values.clone()));
    }

    public void setMatrix4Uniform(String name, float[] values) {
        if (values == null || values.length != 16) {
            throw new IllegalArgumentException("matrix must contain exactly 16 floats");
        }
        pendingUniforms.put(name, new Matrix4UniformValue(values.clone()));
    }

    public void bindAttributesAndUniforms() {
        use();

        for (int location : enabledAttributes) {
            GLES20.glDisableVertexAttribArray(location);
        }
        enabledAttributes.clear();

        for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
            int location = getAttributeLocation(entry.getKey());
            if (location < 0) {
                continue;
            }

            AttributeValue value = entry.getValue();
            value.buffer.position(0);
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(location, value.vectorSize,
                    GLES20.GL_FLOAT, false, 0, value.buffer);
            enabledAttributes.add(location);
        }
        GlUtil.checkGlError("bindAttributes");

        for (Map.Entry<String, UniformValue> entry : pendingUniforms.entrySet()) {
            int location = getUniformLocation(entry.getKey());
            if (location < 0) {
                continue;
            }
            entry.getValue().apply(location);
        }
        GlUtil.checkGlError("bindUniforms");
    }

    public void delete() {
        for (int location : enabledAttributes) {
            GLES20.glDisableVertexAttribArray(location);
        }
        enabledAttributes.clear();

        if (programId != 0) {
            GLES20.glDeleteProgram(programId);
        }
        if (vertexShader != 0) {
            GLES20.glDeleteShader(vertexShader);
        }
        if (fragmentShader != 0) {
            GLES20.glDeleteShader(fragmentShader);
        }
    }

    private int getUniformLocation(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }

        int location = GLES20.glGetUniformLocation(programId, name);
        uniformLocations.put(name, location);
        return location;
    }

    private int getAttributeLocation(String name) {
        Integer cached = attributeLocations.get(name);
        if (cached != null) {
            return cached;
        }

        int location = GLES20.glGetAttribLocation(programId, name);
        attributeLocations.put(name, location);
        return location;
    }

    private static int compileShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            throw new GlUtil.GlException("glCreateShader() returned 0 for type " + shaderType);
        }

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            String infoLog = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new GlUtil.GlException("Shader compile failed: " + infoLog);
        }

        return shader;
    }

    private interface UniformValue {
        void apply(int location);
    }

    private static final class AttributeValue {
        private final FloatBuffer buffer;
        private final int vectorSize;

        private AttributeValue(FloatBuffer buffer, int vectorSize) {
            this.buffer = buffer;
            this.vectorSize = vectorSize;
        }
    }

    private static final class SamplerUniformValue implements UniformValue {
        private final int textureId;
        private final int unit;
        private final int target;

        private SamplerUniformValue(int textureId, int unit, int target) {
            this.textureId = textureId;
            this.unit = unit;
            this.target = target;
        }

        @Override
        public void apply(int location) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(target, textureId);
            GLES20.glUniform1i(location, unit);
        }
    }

    private static final class FloatUniformValue implements UniformValue {
        private final float value;

        private FloatUniformValue(float value) {
            this.value = value;
        }

        @Override
        public void apply(int location) {
            GLES20.glUniform1f(location, value);
        }
    }

    private static final class FloatArrayUniformValue implements UniformValue {
        private final float[] values;

        private FloatArrayUniformValue(float[] values) {
            this.values = values;
        }

        @Override
        public void apply(int location) {
            switch (values.length) {
                case 1:
                    GLES20.glUniform1fv(location, 1, values, 0);
                    break;
                case 2:
                    GLES20.glUniform2fv(location, 1, values, 0);
                    break;
                case 3:
                    GLES20.glUniform3fv(location, 1, values, 0);
                    break;
                case 4:
                    GLES20.glUniform4fv(location, 1, values, 0);
                    break;
                default:
                    throw new IllegalStateException("Unsupported float uniform size: " + values.length);
            }
        }
    }

    private static final class Matrix4UniformValue implements UniformValue {
        private final float[] values;

        private Matrix4UniformValue(float[] values) {
            this.values = values;
        }

        @Override
        public void apply(int location) {
            GLES20.glUniformMatrix4fv(location, 1, false, values, 0);
        }
    }
}
