package com.CadeMixedUpGame.phoneapp;

import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import com.CadeMixedUpGame.api.AppLog;

public class SoundHelper {
    private static final int SAMPLE_RATE = 44100;
    private static final double[] TURN_ALERT_NOTES = {523.25, 659.25, 783.99};
    private static final double[] TURN_ALERT_DURATIONS = {0.16, 0.16, 0.22};
    private static final double NOTE_GAP_SECONDS = 0.025;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioTrack activeTrack;

    public void playTurnAlert() {
        try {
            releaseActiveTrack();
            short[] chime = buildChime(TURN_ALERT_NOTES, TURN_ALERT_DURATIONS);
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
            activeTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(chime.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            activeTrack.write(chime, 0, chime.length);
            activeTrack.play();
            handler.postDelayed(this::releaseActiveTrack, 900);
        }
        catch (RuntimeException ex) {
            AppLog.e(AppLog.TTS, "Could not play turn alert", ex);
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        releaseActiveTrack();
    }

    private short[] buildChime(double[] frequencies, double[] durations) {
        int totalSamples = 0;
        for (double duration : durations) {
            totalSamples += secondsToSamples(duration + NOTE_GAP_SECONDS);
        }

        short[] samples = new short[totalSamples];
        int offset = 0;
        for (int index = 0; index < frequencies.length; index++) {
            offset = writeNote(samples, offset, frequencies[index], durations[index]);
            offset += secondsToSamples(NOTE_GAP_SECONDS);
        }
        return samples;
    }

    private int writeNote(short[] samples, int offset, double frequency, double durationSeconds) {
        int sampleCount = secondsToSamples(durationSeconds);
        for (int i = 0; i < sampleCount && offset + i < samples.length; i++) {
            double progress = i / (double) sampleCount;
            double envelope = noteEnvelope(progress);
            double time = i / (double) SAMPLE_RATE;
            double tone = Math.sin(2.0 * Math.PI * frequency * time);
            double shimmer = 0.18 * Math.sin(2.0 * Math.PI * frequency * 2.0 * time);
            samples[offset + i] = (short) (Short.MAX_VALUE * 0.48 * envelope * (tone + shimmer));
        }
        return offset + sampleCount;
    }

    private double noteEnvelope(double progress) {
        double attack = Math.min(progress / 0.12, 1.0);
        double release = Math.min((1.0 - progress) / 0.28, 1.0);
        return Math.max(0.0, Math.min(attack, release));
    }

    private int secondsToSamples(double seconds) {
        return (int) Math.ceil(seconds * SAMPLE_RATE);
    }

    private void releaseActiveTrack() {
        if (activeTrack != null) {
            activeTrack.release();
            activeTrack = null;
        }
    }
}
