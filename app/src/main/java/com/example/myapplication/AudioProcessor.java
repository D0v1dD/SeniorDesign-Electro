package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    // Made SAMPLE_RATE public to allow external classes to access it (e.g., MicrophoneTestFragment)
    public static final int SAMPLE_RATE = 44100; // Hz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private AudioRecord audioRecord;
    private boolean isRecording;
    private float[] baselineNoiseValues;
    private RecordingCallback recordingCallback;
    private TestingCallback testingCallback;
    private Context context;

    // Constructor accepting a RecordingCallback as a parameter
    public AudioProcessor(Context context, RecordingCallback recordingCallback) {
        this.context = context;
        this.recordingCallback = recordingCallback;

        if (BUFFER_SIZE == AudioRecord.ERROR || BUFFER_SIZE == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: " + BUFFER_SIZE);
            return; // Handle invalid buffer size appropriately
        }

        // Check if permission is granted before initializing AudioRecord
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                );

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

    // Recording methods (existing functionality)
    public void recordBaseline() {
        if (!isAudioRecordInitialized()) {
            return;
        }

        isRecording = true;
        new Thread(this::processBaseline).start();
    }

    private void processBaseline() {
        short[] audioBuffer = new short[BUFFER_SIZE];
        baselineNoiseValues = new float[BUFFER_SIZE];

        if (audioRecord != null) {
            audioRecord.startRecording();
            while (isRecording) {
                int readBytes = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                Log.d(TAG, "Bytes read for baseline: " + readBytes);

                if (readBytes > 0) {
                    for (int i = 0; i < readBytes; i++) {
                        baselineNoiseValues[i] = audioBuffer[i] / 32768.0f; // Normalize 16-bit PCM data
                    }
                    Log.d(TAG, "Baseline data recorded successfully.");

                    // Stop after recording one buffer of baseline
                    isRecording = false;
                } else {
                    Log.e(TAG, "Failed to read audio data for baseline.");
                }
            }
            audioRecord.stop();

            // Notify MainActivity (recordingCallback) that baseline recording is complete
            if (recordingCallback != null) {
                recordingCallback.onBaselineRecorded(baselineNoiseValues);
            }
        } else {
            Log.e(TAG, "AudioRecord object is null.");
        }
    }

    public void stopBaselineRecording() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    public void setBaseline(float[] baselineValues) {
        this.baselineNoiseValues = baselineValues;
    }

    public void startRecording() {
        if (!isAudioRecordInitialized()) {
            return;
        }

        if (baselineNoiseValues == null || baselineNoiseValues.length == 0) {
            Log.e(TAG, "Baseline noise values are not set.");
            return;
        }

        isRecording = true;
        new Thread(this::processRecording).start();
    }

    private void processRecording() {
        short[] audioBuffer = new short[BUFFER_SIZE];

        if (audioRecord != null) {
            audioRecord.startRecording();

            while (isRecording) {
                int readBytes = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                if (readBytes > 0) {
                    // Trim the buffer to the number of bytes read
                    short[] trimmedBuffer = Arrays.copyOf(audioBuffer, readBytes);

                    // Callback to notify new audio data
                    if (recordingCallback != null) {
                        recordingCallback.onAudioDataReceived(trimmedBuffer);

                        // Calculate SNR
                        double snrValue = calculateSNR(trimmedBuffer, baselineNoiseValues);
                        recordingCallback.onSNRCalculated(snrValue);
                    }
                } else {
                    Log.e(TAG, "Failed to read audio data.");
                }
            }
            audioRecord.stop();
        } else {
            Log.e(TAG, "AudioRecord object is null.");
        }
    }

    public void stopRecording() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    // Microphone Testing Methods (new functionality)
    public void testMicrophone(TestingCallback testingCallback) {
        if (!isAudioRecordInitialized()) {
            return;
        }

        this.testingCallback = testingCallback;
        short[] audioBuffer = new short[BUFFER_SIZE];
        isRecording = true;

        new Thread(() -> {
            if (audioRecord == null) {
                Log.e(TAG, "AudioRecord not properly initialized for testing");
                return;
            }

            audioRecord.startRecording();
            long recordingStart = System.currentTimeMillis();
            long testDuration = 3000; // Record for 3 seconds

            while (isRecording && System.currentTimeMillis() - recordingStart < testDuration) {
                int readBytes = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                if (readBytes > 0 && testingCallback != null) {
                    short[] trimmedBuffer = Arrays.copyOf(audioBuffer, readBytes);
                    testingCallback.onTestingDataReceived(trimmedBuffer);
                }
            }

            if (isRecording) {
                stopAudioRecord();
            }

            // Analyze audio after recording
            double amplitude = calculateAmplitude(audioBuffer);
            double[] frequencySpectrum = calculateFrequencySpectrum(audioBuffer);

            if (testingCallback != null) {
                testingCallback.onTestCompleted(amplitude, frequencySpectrum);
            }

        }).start();
    }

    public void stopMicrophoneTest() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    // Utility Methods
    private void stopAudioRecord() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
        }
    }

    private boolean isAudioRecordInitialized() {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not properly initialized");
            return false;
        }
        return true;
    }

    private double calculateSNR(short[] signalBuffer, float[] noiseBuffer) {
        // Convert short[] to double[] for signal
        double[] signal = new double[signalBuffer.length];
        for (int i = 0; i < signalBuffer.length; i++) {
            signal[i] = signalBuffer[i] / 32768.0; // Normalize to [-1,1]
        }

        // Ensure noiseBuffer and signalBuffer are the same length
        double[] noise = new double[signalBuffer.length];
        if (noiseBuffer.length >= signalBuffer.length) {
            for (int i = 0; i < signalBuffer.length; i++) {
                noise[i] = noiseBuffer[i];
            }
        } else {
            // If noiseBuffer is shorter, repeat it to match the signal length
            int repeats = signalBuffer.length / noiseBuffer.length;
            for (int i = 0; i < signalBuffer.length; i++) {
                noise[i] = noiseBuffer[i % noiseBuffer.length];
            }
        }

        // Calculate power of signal and noise
        double signalPower = calculatePower(signal);
        double noisePower = calculatePower(noise);

        if (noisePower == 0) {
            return 0; // Avoid division by zero
        }

        double snr = 10 * Math.log10(signalPower / noisePower);
        return snr;
    }

    private double calculatePower(double[] buffer) {
        double sum = 0;
        for (double value : buffer) {
            sum += value * value;
        }
        return sum / buffer.length;
    }

    private double calculateAmplitude(short[] audioBuffer) {
        long sum = 0;
        for (short sample : audioBuffer) {
            sum += Math.abs(sample);
        }
        return (double) sum / audioBuffer.length;
    }

    private double[] calculateFrequencySpectrum(short[] audioBuffer) {
        // Convert short[] to double[] for FFT
        double[] audioDataDouble = new double[audioBuffer.length];
        for (int i = 0; i < audioBuffer.length; i++) {
            audioDataDouble[i] = audioBuffer[i] / 32768.0; // Normalize
        }

        // Perform FFT and return frequency spectrum
        double[] frequencySpectrum = fft(audioDataDouble);
        return frequencySpectrum;
    }

    // Placeholder FFT implementation (to be replaced with an actual FFT algorithm or library)
    private double[] fft(double[] audioData) {
        // TODO: Implement FFT calculation or use an FFT library (e.g., JTransform)
        // This placeholder simply returns an array of zeros representing a blank frequency spectrum
        return new double[audioData.length / 2];
    }

    // Define the RecordingCallback interface
    public interface RecordingCallback {
        void onAudioDataReceived(short[] audioBuffer);
        void onBaselineRecorded(float[] baselineValues);
        void onSNRCalculated(double snrValue);
    }

    // TestingCallback interface for microphone testing
    public interface TestingCallback {
        void onTestingDataReceived(short[] audioBuffer);
        void onTestCompleted(double amplitude, double[] frequencySpectrum);
    }
}
