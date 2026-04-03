package com.limelight.binding.audio;

import com.limelight.binding.input.ControllerHandler;

import java.io.ByteArrayOutputStream;

public final class ControllerAudioHapticsController {
    private static final int TARGET_SAMPLE_RATE = 3000;

    private static final float DS5_LOWPASS_CUTOFF_HZ = 260.0f;
    private static final float DS5_VOICE_LOWPASS_CUTOFF_HZ = 1800.0f;
    private static final float DS5_NOISE_GATE = 0.0035f;
    private static final float DS5_RELEASE_FLOOR = 0.0015f;
    private static final float DS5_DRY_BLEND = 0.45f;
    private static final float DS5_TRANSIENT_BLEND = 0.28f;
    private static final float DS5_STRENGTH_MULTIPLIER = 2.5f;
    private static final float DS5_GAIN_MULTIPLIER = 2.35f;
    private static final float DS5_SUSTAIN_FLOOR = 0.065f;

    private static final float STANDARD_LOWPASS_CUTOFF_HZ = 200.0f;
    private static final float STANDARD_VOICE_LOWPASS_CUTOFF_HZ = 1600.0f;
    private static final float STANDARD_NOISE_GATE = 0.0030f;
    private static final float STANDARD_RELEASE_FLOOR = 0.0010f;
    private static final float STANDARD_TRANSIENT_BLEND = 0.18f;
    private static final float STANDARD_SUSTAIN_FLOOR = 0.060f;
    private static final float STANDARD_LOW_MOTOR_MULTIPLIER = 2.45f;
    private static final float STANDARD_HIGH_MOTOR_MULTIPLIER = 1.35f;
    private static final short STANDARD_STOP_THRESHOLD = 1100;

    private static final float KISHI_LOWPASS_CUTOFF_HZ = 220.0f;
    private static final float KISHI_VOICE_LOWPASS_CUTOFF_HZ = 1500.0f;
    private static final float KISHI_NOISE_GATE = 0.0018f;
    private static final float KISHI_RELEASE_FLOOR = 0.0008f;
    private static final float KISHI_DRY_BLEND = 0.82f;
    private static final float KISHI_TRANSIENT_BLEND = 0.06f;
    private static final float KISHI_STRENGTH_MULTIPLIER = 3.35f;
    private static final float KISHI_GAIN_MULTIPLIER = 2.9f;
    private static final float KISHI_SUSTAIN_FLOOR = 0.15f;

    private final ControllerHandler controllerHandler;
    private boolean enabled;
    private int strengthPercent;
    private String voiceFilterMode;

    private int channelCount;
    private int sampleRate;

    private int ds5ResampleAccumulator;
    private float ds5LeftLowPassState;
    private float ds5RightLowPassState;
    private float ds5LeftVoiceLowPassState;
    private float ds5RightVoiceLowPassState;
    private float ds5SmoothedLevel;

    private float standardLeftLowPassState;
    private float standardRightLowPassState;
    private float standardLeftVoiceLowPassState;
    private float standardRightVoiceLowPassState;
    private float standardSmoothedLevel;
    private short lastStandardLowMotor;
    private short lastStandardHighMotor;

    private int kishiResampleAccumulator;
    private float kishiLeftLowPassState;
    private float kishiRightLowPassState;
    private float kishiLeftVoiceLowPassState;
    private float kishiRightVoiceLowPassState;
    private float kishiSmoothedLevel;

    public ControllerAudioHapticsController(ControllerHandler controllerHandler,
                                            boolean enabled, int strengthPercent,
                                            String voiceFilterMode) {
        this.controllerHandler = controllerHandler;
        this.enabled = enabled && controllerHandler != null;
        this.strengthPercent = Math.max(0, strengthPercent);
        this.voiceFilterMode = normalizeVoiceFilterMode(voiceFilterMode);
    }

    public synchronized void configure(int channelCount, int sampleRate) {
        this.channelCount = Math.max(1, channelCount);
        this.sampleRate = Math.max(1, sampleRate);
        resetState();
    }

    public synchronized void setSettings(boolean enabled, int strengthPercent, String voiceFilterMode) {
        this.enabled = enabled && controllerHandler != null;
        this.strengthPercent = Math.max(0, strengthPercent);
        this.voiceFilterMode = normalizeVoiceFilterMode(voiceFilterMode);
    }

    public synchronized void onAudioFrame(short[] audioData) {
        if (!enabled || controllerHandler == null || audioData == null ||
                audioData.length < channelCount || channelCount <= 0 || sampleRate <= 0) {
            return;
        }

        float ds5Alpha = getLowPassAlpha(DS5_LOWPASS_CUTOFF_HZ);
        float ds5VoiceAlpha = getLowPassAlpha(DS5_VOICE_LOWPASS_CUTOFF_HZ);
        float ds5Suppression = getDs5VoiceSuppressionStrength();

        float standardAlpha = getLowPassAlpha(STANDARD_LOWPASS_CUTOFF_HZ);
        float standardVoiceAlpha = getLowPassAlpha(STANDARD_VOICE_LOWPASS_CUTOFF_HZ);
        float standardSuppression = getStandardVoiceSuppressionStrength();

        float kishiAlpha = getLowPassAlpha(KISHI_LOWPASS_CUTOFF_HZ);
        float kishiVoiceAlpha = getLowPassAlpha(KISHI_VOICE_LOWPASS_CUTOFF_HZ);
        float kishiSuppression = getKishiVoiceSuppressionStrength();

        int frames = audioData.length / channelCount;
        if (frames <= 0) {
            return;
        }

        ByteArrayOutputStream ds5Out = new ByteArrayOutputStream(128);
        ByteArrayOutputStream kishiOut = new ByteArrayOutputStream(128);
        float standardBassPeak = 0.0f;
        float standardTransientPeak = 0.0f;

        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            int base = frameIndex * channelCount;
            float leftSample = extractChannelSample(audioData, base, 0);
            float rightSample = extractChannelSample(audioData, base, Math.min(1, channelCount - 1));

            processDs5Sample(leftSample, rightSample, ds5Alpha, ds5VoiceAlpha, ds5Suppression, ds5Out);
            float[] standardLevels = processStandardSample(leftSample, rightSample,
                    standardAlpha, standardVoiceAlpha, standardSuppression);
            standardBassPeak = Math.max(standardBassPeak, standardLevels[0]);
            standardTransientPeak = Math.max(standardTransientPeak, standardLevels[1]);
            processKishiSample(leftSample, rightSample, kishiAlpha, kishiVoiceAlpha, kishiSuppression, kishiOut);
        }

        short standardLowMotor = toMotorValue(standardBassPeak * STANDARD_LOW_MOTOR_MULTIPLIER);
        short standardHighMotor = toMotorValue(standardTransientPeak * STANDARD_HIGH_MOTOR_MULTIPLIER);
        if (standardLowMotor < STANDARD_STOP_THRESHOLD) {
            standardLowMotor = 0;
        }
        if (standardHighMotor < STANDARD_STOP_THRESHOLD) {
            standardHighMotor = 0;
        }
        if (standardLowMotor != lastStandardLowMotor || standardHighMotor != lastStandardHighMotor) {
            controllerHandler.handleStandardControllerAudioHaptics(standardLowMotor, standardHighMotor);
            lastStandardLowMotor = standardLowMotor;
            lastStandardHighMotor = standardHighMotor;
        }

        byte[] ds5Frame = ds5Out.toByteArray();
        if (ds5Frame.length > 0) {
            controllerHandler.handleControllerAdvancedAudioHapticsFrame(ds5Frame,
                    Math.min(2.75f, (strengthPercent / 100.0f) * DS5_GAIN_MULTIPLIER));
        }

        byte[] kishiFrame = kishiOut.toByteArray();
        if (kishiFrame.length > 0) {
            controllerHandler.handleRazerKishiAudioHapticsFrame(kishiFrame,
                    Math.min(3.0f, (strengthPercent / 100.0f) * KISHI_GAIN_MULTIPLIER));
        }
    }

    public synchronized void stop() {
        if (lastStandardLowMotor != 0 || lastStandardHighMotor != 0) {
            controllerHandler.handleStandardControllerAudioHaptics((short) 0, (short) 0);
        }
        resetState();
    }

    private void processDs5Sample(float leftSample, float rightSample,
                                  float alpha, float voiceAlpha, float suppression,
                                  ByteArrayOutputStream out) {
        ds5LeftLowPassState += alpha * (leftSample - ds5LeftLowPassState);
        ds5RightLowPassState += alpha * (rightSample - ds5RightLowPassState);
        ds5LeftVoiceLowPassState += voiceAlpha * (leftSample - ds5LeftVoiceLowPassState);
        ds5RightVoiceLowPassState += voiceAlpha * (rightSample - ds5RightVoiceLowPassState);

        float leftVoiceResidual = Math.abs(ds5LeftVoiceLowPassState - ds5LeftLowPassState);
        float rightVoiceResidual = Math.abs(ds5RightVoiceLowPassState - ds5RightLowPassState);
        float leftTransient = (leftSample - ds5LeftLowPassState) * DS5_TRANSIENT_BLEND;
        float rightTransient = (rightSample - ds5RightLowPassState) * DS5_TRANSIENT_BLEND;
        float leftBody = (ds5LeftLowPassState * (1.0f - DS5_DRY_BLEND)) + (leftSample * DS5_DRY_BLEND) + leftTransient;
        float rightBody = (ds5RightLowPassState * (1.0f - DS5_DRY_BLEND)) + (rightSample * DS5_DRY_BLEND) + rightTransient;
        float leftFiltered = applyVoiceAttenuation(leftBody, leftVoiceResidual, suppression);
        float rightFiltered = applyVoiceAttenuation(rightBody, rightVoiceResidual, suppression);

        float envelopeInput = Math.max(Math.abs(leftFiltered), Math.abs(rightFiltered)) +
                (0.35f * Math.max(Math.abs(leftTransient), Math.abs(rightTransient)));
        float targetLevel = Math.max(0.0f, (envelopeInput - DS5_NOISE_GATE) / (0.12f - DS5_NOISE_GATE));
        float attack = targetLevel > ds5SmoothedLevel ? 0.62f : 0.10f;
        ds5SmoothedLevel += attack * (targetLevel - ds5SmoothedLevel);
        if (envelopeInput > (DS5_NOISE_GATE * 1.4f) &&
                ds5SmoothedLevel > DS5_RELEASE_FLOOR && targetLevel < DS5_SUSTAIN_FLOOR) {
            ds5SmoothedLevel = Math.max(ds5SmoothedLevel, DS5_SUSTAIN_FLOOR);
        }

        float levelScale = ds5SmoothedLevel >= DS5_RELEASE_FLOOR ?
                (float) Math.pow(Math.min(1.0f, ds5SmoothedLevel), 0.62f) : 0.0f;
        float strengthScale = Math.min(2.75f,
                (strengthPercent / 100.0f) * DS5_STRENGTH_MULTIPLIER);

        ds5ResampleAccumulator += TARGET_SAMPLE_RATE;
        while (ds5ResampleAccumulator >= sampleRate) {
            short leftOut = floatToShort(leftFiltered * levelScale * strengthScale);
            short rightOut = floatToShort(rightFiltered * levelScale * strengthScale);
            out.write(leftOut & 0xFF);
            out.write((leftOut >> 8) & 0xFF);
            out.write(rightOut & 0xFF);
            out.write((rightOut >> 8) & 0xFF);
            ds5ResampleAccumulator -= sampleRate;
        }
    }

    private float[] processStandardSample(float leftSample, float rightSample,
                                          float alpha, float voiceAlpha, float suppression) {
        standardLeftLowPassState += alpha * (leftSample - standardLeftLowPassState);
        standardRightLowPassState += alpha * (rightSample - standardRightLowPassState);
        standardLeftVoiceLowPassState += voiceAlpha * (leftSample - standardLeftVoiceLowPassState);
        standardRightVoiceLowPassState += voiceAlpha * (rightSample - standardRightVoiceLowPassState);

        float leftVoiceResidual = Math.abs(standardLeftVoiceLowPassState - standardLeftLowPassState);
        float rightVoiceResidual = Math.abs(standardRightVoiceLowPassState - standardRightLowPassState);
        float leftTransient = Math.abs(leftSample - standardLeftLowPassState) * STANDARD_TRANSIENT_BLEND;
        float rightTransient = Math.abs(rightSample - standardRightLowPassState) * STANDARD_TRANSIENT_BLEND;
        float leftFiltered = applyVoiceFilter(standardLeftLowPassState, leftVoiceResidual, suppression);
        float rightFiltered = applyVoiceFilter(standardRightLowPassState, rightVoiceResidual, suppression);

        float bassPresence = 0.5f * (Math.abs(leftFiltered) + Math.abs(rightFiltered));
        float transientPresence = Math.max(leftTransient, rightTransient);
        float envelopeInput = bassPresence + (0.40f * transientPresence);
        float targetLevel = Math.max(0.0f, (envelopeInput - STANDARD_NOISE_GATE) / (0.10f - STANDARD_NOISE_GATE));
        float attack = targetLevel > standardSmoothedLevel ? 0.38f : 0.14f;
        standardSmoothedLevel += attack * (targetLevel - standardSmoothedLevel);
        if (bassPresence > (STANDARD_NOISE_GATE * 1.2f) &&
                standardSmoothedLevel > STANDARD_RELEASE_FLOOR &&
                targetLevel < STANDARD_SUSTAIN_FLOOR) {
            standardSmoothedLevel = Math.max(standardSmoothedLevel, STANDARD_SUSTAIN_FLOOR);
        }

        float levelScale = standardSmoothedLevel >= STANDARD_RELEASE_FLOOR ?
                (float) Math.pow(Math.min(1.0f, standardSmoothedLevel), 0.80f) : 0.0f;
        float strengthScale = Math.max(0.0f, strengthPercent / 100.0f);

        return new float[] {
                bassPresence * levelScale * strengthScale,
                transientPresence * levelScale * strengthScale
        };
    }

    private void processKishiSample(float leftSample, float rightSample,
                                    float alpha, float voiceAlpha, float suppression,
                                    ByteArrayOutputStream out) {
        kishiLeftLowPassState += alpha * (leftSample - kishiLeftLowPassState);
        kishiRightLowPassState += alpha * (rightSample - kishiRightLowPassState);
        kishiLeftVoiceLowPassState += voiceAlpha * (leftSample - kishiLeftVoiceLowPassState);
        kishiRightVoiceLowPassState += voiceAlpha * (rightSample - kishiRightVoiceLowPassState);

        float leftVoiceResidual = Math.abs(kishiLeftVoiceLowPassState - kishiLeftLowPassState);
        float rightVoiceResidual = Math.abs(kishiRightVoiceLowPassState - kishiRightLowPassState);
        float leftTransient = (leftSample - kishiLeftLowPassState) * KISHI_TRANSIENT_BLEND;
        float rightTransient = (rightSample - kishiRightLowPassState) * KISHI_TRANSIENT_BLEND;
        float leftBody = (kishiLeftLowPassState * (1.0f - KISHI_DRY_BLEND)) + (leftSample * KISHI_DRY_BLEND) + leftTransient;
        float rightBody = (kishiRightLowPassState * (1.0f - KISHI_DRY_BLEND)) + (rightSample * KISHI_DRY_BLEND) + rightTransient;
        float leftFiltered = applyVoiceFilter(leftBody, leftVoiceResidual, suppression);
        float rightFiltered = applyVoiceFilter(rightBody, rightVoiceResidual, suppression);

        float bassPresence = 0.5f * (Math.abs(kishiLeftLowPassState) + Math.abs(kishiRightLowPassState));
        float envelopeInput = (0.72f * bassPresence) +
                (0.22f * (Math.abs(leftFiltered) + Math.abs(rightFiltered))) +
                (0.12f * Math.max(Math.abs(leftTransient), Math.abs(rightTransient)));
        float targetLevel = Math.max(0.0f, (envelopeInput - KISHI_NOISE_GATE) / (0.10f - KISHI_NOISE_GATE));
        float attack = targetLevel > kishiSmoothedLevel ? 0.48f : 0.18f;
        kishiSmoothedLevel += attack * (targetLevel - kishiSmoothedLevel);
        if (bassPresence > (KISHI_NOISE_GATE * 1.2f) &&
                kishiSmoothedLevel > KISHI_RELEASE_FLOOR && targetLevel < KISHI_SUSTAIN_FLOOR) {
            kishiSmoothedLevel = Math.max(kishiSmoothedLevel, KISHI_SUSTAIN_FLOOR);
        }

        float levelScale = kishiSmoothedLevel >= KISHI_RELEASE_FLOOR ?
                (float) Math.pow(Math.min(1.0f, kishiSmoothedLevel), 0.76f) : 0.0f;
        float strengthScale = Math.min(3.5f,
                (strengthPercent / 100.0f) * KISHI_STRENGTH_MULTIPLIER);

        kishiResampleAccumulator += TARGET_SAMPLE_RATE;
        while (kishiResampleAccumulator >= sampleRate) {
            short leftOut = floatToShort(leftFiltered * levelScale * strengthScale);
            short rightOut = floatToShort(rightFiltered * levelScale * strengthScale);
            out.write(leftOut & 0xFF);
            out.write((leftOut >> 8) & 0xFF);
            out.write(rightOut & 0xFF);
            out.write((rightOut >> 8) & 0xFF);
            kishiResampleAccumulator -= sampleRate;
        }
    }

    private void resetState() {
        ds5ResampleAccumulator = 0;
        ds5LeftLowPassState = 0.0f;
        ds5RightLowPassState = 0.0f;
        ds5LeftVoiceLowPassState = 0.0f;
        ds5RightVoiceLowPassState = 0.0f;
        ds5SmoothedLevel = 0.0f;

        standardLeftLowPassState = 0.0f;
        standardRightLowPassState = 0.0f;
        standardLeftVoiceLowPassState = 0.0f;
        standardRightVoiceLowPassState = 0.0f;
        standardSmoothedLevel = 0.0f;
        lastStandardLowMotor = 0;
        lastStandardHighMotor = 0;

        kishiResampleAccumulator = 0;
        kishiLeftLowPassState = 0.0f;
        kishiRightLowPassState = 0.0f;
        kishiLeftVoiceLowPassState = 0.0f;
        kishiRightVoiceLowPassState = 0.0f;
        kishiSmoothedLevel = 0.0f;
    }

    private float extractChannelSample(short[] audioData, int base, int channelIndex) {
        if (channelCount == 1) {
            return audioData[base] / 32768.0f;
        }

        int resolvedIndex = Math.min(channelIndex, channelCount - 1);
        return audioData[base + resolvedIndex] / 32768.0f;
    }

    private float getLowPassAlpha(float cutoffHz) {
        float omega = (float) (2.0 * Math.PI * cutoffHz / sampleRate);
        return Math.min(1.0f, Math.max(0.005f, omega));
    }

    private float applyVoiceFilter(float bassSample, float voiceResidual, float suppression) {
        if (suppression <= 0.0f) {
            return bassSample;
        }

        return Math.max(0.0f, bassSample - (voiceResidual * suppression));
    }

    private float applyVoiceAttenuation(float sample, float voiceResidual, float suppression) {
        if (suppression <= 0.0f) {
            return sample;
        }

        float attenuation = Math.max(0.30f, 1.0f - (voiceResidual * suppression * 6.0f));
        return sample * attenuation;
    }

    private float getDs5VoiceSuppressionStrength() {
        switch (voiceFilterMode) {
            case AudioHapticsController.VOICE_FILTER_LOW:
                return 0.22f;
            case AudioHapticsController.VOICE_FILTER_MEDIUM:
                return 0.44f;
            case AudioHapticsController.VOICE_FILTER_HIGH:
                return 0.72f;
            case AudioHapticsController.VOICE_FILTER_OFF:
            default:
                return 0.0f;
        }
    }

    private float getStandardVoiceSuppressionStrength() {
        switch (voiceFilterMode) {
            case AudioHapticsController.VOICE_FILTER_LOW:
                return 0.20f;
            case AudioHapticsController.VOICE_FILTER_MEDIUM:
                return 0.38f;
            case AudioHapticsController.VOICE_FILTER_HIGH:
                return 0.60f;
            case AudioHapticsController.VOICE_FILTER_OFF:
            default:
                return 0.0f;
        }
    }

    private float getKishiVoiceSuppressionStrength() {
        switch (voiceFilterMode) {
            case AudioHapticsController.VOICE_FILTER_LOW:
                return 0.06f;
            case AudioHapticsController.VOICE_FILTER_MEDIUM:
                return 0.12f;
            case AudioHapticsController.VOICE_FILTER_HIGH:
                return 0.20f;
            case AudioHapticsController.VOICE_FILTER_OFF:
            default:
                return 0.0f;
        }
    }

    private String normalizeVoiceFilterMode(String mode) {
        if (AudioHapticsController.VOICE_FILTER_LOW.equals(mode) ||
                AudioHapticsController.VOICE_FILTER_MEDIUM.equals(mode) ||
                AudioHapticsController.VOICE_FILTER_HIGH.equals(mode)) {
            return mode;
        }

        return AudioHapticsController.VOICE_FILTER_OFF;
    }

    private short floatToShort(float sample) {
        int value = Math.round(Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
        return (short) value;
    }

    private short toMotorValue(float normalizedLevel) {
        float clamped = Math.max(0.0f, Math.min(1.0f, normalizedLevel));
        int value = Math.round(clamped * 65535.0f);
        return (short) Math.min(65535, value);
    }
}
