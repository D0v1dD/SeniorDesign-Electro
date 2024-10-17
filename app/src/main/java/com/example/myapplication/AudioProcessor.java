package com.example.myapplication;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

public class AudioProcessor {
    private static final int SAMPLE_RATE = 44100; // Hz
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    private AudioRecord audioRecord;
    private boolean isRecording;
    private static final String TAG = "AudioProcessor";

    public AudioProcessor() {
        // Adjust the buffer size to the nearest power of 2 for FFT purposes
        BUFFER_SIZE = getNearestPowerOf2(BUFFER_SIZE);

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed. Check the parameters.");
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid AudioRecord parameters", e);
        }
    }

    public void startRecording() {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not properly initialized");
            return;
        }

        audioRecord.startRecording();
        isRecording = true;
        new Thread(this::processAudioData).start();
    }

    public void stopRecording() {
        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord is null");
            return;
        }

        isRecording = false;
        audioRecord.stop();
        audioRecord.release();
    }

    private void processAudioData() {
        short[] audioBuffer = new short[BUFFER_SIZE];
        double[] fftBuffer = new double[BUFFER_SIZE];
        DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);

        while (isRecording) {
            int readBytes = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);

            if (readBytes > 0) {
                // Convert short audio data to double for FFT
                for (int i = 0; i < readBytes; i++) {
                    fftBuffer[i] = audioBuffer[i] / 32768.0; // Normalize 16-bit PCM data
                }

                // Perform STFT using FFT
                fft.realForward(fftBuffer);

                // Compute magnitude
                double[] magnitude = new double[fftBuffer.length / 2];
                for (int i = 0; i < magnitude.length; i++) {
                    double real = fftBuffer[2 * i];
                    double imag = fftBuffer[2 * i + 1];
                    magnitude[i] = Math.sqrt(real * real + imag * imag);
                }

                // Log the FFT magnitude for testing purposes
                Log.d(TAG, "FFT Magnitude: " + magnitude[0]);
                // TODO: Update SNR calculation or UI based on magnitude
            }
        }
    }

    // Method to find the nearest power of 2 greater than or equal to the given number
    private int getNearestPowerOf2(int n) {
        int power = 1;
        while (power < n) {
            power <<= 1; // Multiply by 2
        }
        return power;
    }
}
