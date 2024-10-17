package com.example.myapplication;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import org.jtransforms.fft.DoubleFFT_1D;

public class AudioProcessor {
    private static final int SAMPLE_RATE = 44100; // Hz
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    private AudioRecord audioRecord;
    private boolean isRecording;
    private static final String TAG = "AudioProcessor";
    private float[] baselineNoiseValues;

    public AudioProcessor(Context context) {
        if (BUFFER_SIZE == AudioRecord.ERROR || BUFFER_SIZE == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: " + BUFFER_SIZE);
            return; // Handle invalid buffer size appropriately
        }

        // Check if permission is granted before initializing AudioRecord
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed. Check the parameters.");
                    audioRecord = null; // Set to null to prevent issues later
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid AudioRecord parameters", e);
                audioRecord = null; // Set to null to handle errors gracefully
            }
        } else {
            Log.e(TAG, "RECORD_AUDIO permission not granted.");
        }
    }

    public void recordBaseline() {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not properly initialized");
            return;
        }

        isRecording = true;
        new Thread(this::processBaseline).start();
    }

    private void processBaseline() {
        short[] audioBuffer = new short[BUFFER_SIZE];
        baselineNoiseValues = new float[BUFFER_SIZE];

        int readBytes = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
        if (readBytes > 0) {
            for (int i = 0; i < readBytes; i++) {
                baselineNoiseValues[i] = audioBuffer[i] / 32768.0f; // Normalize 16-bit PCM data
            }
        }

        isRecording = false;
        Log.d(TAG, "Baseline recorded successfully");
    }

    public void setBaseline(float[] baselineValues) {
        this.baselineNoiseValues = baselineValues;
    }

    // ... continue with startRecording() and other methods
}
