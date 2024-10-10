package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private AudioRecord audioRecord;
    private LineChart audioChart;
    private LineDataSet dataSet;
    private LineData lineData;
    private SNRBar snrBar; // Custom SNR Bar
    private HandlerThread backgroundThread;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private int bufferSize;
    private long lastUpdateTime = 0;  // Used for throttling graph updates
    private ArrayList<short[]> recordedAudioData = new ArrayList<>();  // Store the recorded audio data
    private Handler handler = new Handler(); // Handler for SNR updates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Using view binding to avoid findViewById redundancy
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Initialize buttons as local variables
        Button startRecordButton = binding.buttonStartRecord;
        Button stopRecordButton = binding.buttonStopRecord;
        Button viewSavedFilesButton = binding.buttonViewSavedFiles;
        snrBar = binding.snrBar;  // Initialize SNR bar

        stopRecordButton.setVisibility(Button.GONE);  // Initially hide stop button

        // Set Start Button listener
        startRecordButton.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            } else {
                startAudioRecording();
            }
        });

        // Set Stop Button listener
        stopRecordButton.setOnClickListener(view -> {
            stopAudioRecording();
            stopRecordButton.setVisibility(Button.GONE);
            startRecordButton.setVisibility(Button.VISIBLE);
        });

        // Set View Saved Files button listener
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());

        // Initialize chart and audio monitoring
        audioChart = binding.audioChart;
        dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        lineData = new LineData(dataSet);
        audioChart.setData(lineData);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording();
            } else {
                Toast.makeText(this, "Recording permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MainActivity.this, "Recording permission is not granted", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        // Initialize the audio recording with appropriate buffer size
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "Unable to initialize AudioRecord", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            audioRecord.startRecording();
        } catch (SecurityException e) {
            Toast.makeText(this, "Recording permission is not granted", Toast.LENGTH_LONG).show();
            return;
        }

        isRecording = true;

        // Hide the start button and show the stop button
        runOnUiThread(() -> {
            findViewById(R.id.button_start_record).setVisibility(Button.GONE);
            findViewById(R.id.button_stop_record).setVisibility(Button.VISIBLE);
        });

        // Run audio capture and processing on a background thread
        backgroundThread = new HandlerThread("AudioRecordingThread");
        backgroundThread.start();
        Handler backgroundHandler = new Handler(backgroundThread.getLooper());

        backgroundHandler.post(() -> {
            short[] buffer = new short[bufferSize];

            while (isRecording) {
                int readSize = audioRecord.read(buffer, 0, buffer.length);
                if (readSize > 0) {
                    recordedAudioData.add(buffer.clone());  // Store the recorded audio data
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime > 100) {
                        final short[] bufferCopy = buffer.clone();  // Clone buffer to avoid modification
                        runOnUiThread(() -> {
                            updateGraph(bufferCopy); // Update graph
                            updateSNRBar(bufferCopy); // Update SNR bar
                        });
                        lastUpdateTime = currentTime;
                    }
                }
            }
        });
    }

    private void updateGraph(short[] buffer) {
        ArrayList<Entry> newEntries = new ArrayList<>();
        int currentX = dataSet.getEntryCount();

        for (short amplitude : buffer) {
            float normalizedAmplitude = amplitude / 32768f;
            newEntries.add(new Entry(currentX++, normalizedAmplitude));
        }

        for (Entry entry : newEntries) {
            dataSet.addEntry(entry);
        }

        // Limit the number of points to prevent performance issues
        int maxVisiblePoints = 500;
        while (dataSet.getEntryCount() > maxVisiblePoints) {
            dataSet.removeFirst();
        }

        // Notify changes
        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        audioChart.notifyDataSetChanged();
        audioChart.invalidate();
    }

    private void updateSNRBar(short[] buffer) {
        double signalPower = calculateRMS(buffer);
        double noisePower = 1; // Placeholder for noise power
        double snr = signalPower / noisePower;

        // Normalize SNR for display (0 to 1)
        float snrRatio = (float) Math.min(1, snr / 100.0);

        if (snrBar != null) {
            handler.post(() -> snrBar.updateSNR(snrRatio));
        }
    }

    private double calculateRMS(short[] buffer) {
        double sum = 0.0;
        for (short s : buffer) {
            sum += s * s;
        }
        return Math.sqrt(sum / buffer.length);
    }

    private void stopAudioRecording() {
        isRecording = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.e("AudioRecord", "Error stopping recording", e);
            }
            audioRecord.release();
            audioRecord = null;
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Log.e("AudioRecord", "Error joining background thread", e);
            }
            backgroundThread = null;
        }

        // Save the recorded audio data to file after stopping the recording
        saveAudioToFile(recordedAudioData);  // Pass the ArrayList directly

        runOnUiThread(() -> Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show());
    }

    private void viewSavedBaselineFiles() {
        File directory = getExternalFilesDir(null);

        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();

            if (files != null && files.length > 0) {
                String[] fileNames = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    fileNames[i] = files[i].getName();
                }

                new AlertDialog.Builder(this)
                        .setTitle("Saved Baseline Files")
                        .setItems(fileNames, (dialog, which) -> {
                            String selectedFile = fileNames[which];
                            Toast.makeText(this, "Selected file: " + selectedFile, Toast.LENGTH_SHORT).show();
                        })
                        .show();
            } else {
                Toast.makeText(this, "No saved baseline files found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Directory not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAudioToFile(ArrayList<short[]> recordedAudioData) {
        File directory = getExternalFilesDir(null);

        if (directory == null) {
            Toast.makeText(this, "Error: Directory not available", Toast.LENGTH_SHORT).show();
            return;
        }

        File outputFile = new File(directory, "baseline_audio.pcm");

        int totalSize = 0;
        for (short[] chunk : recordedAudioData) {
            totalSize += chunk.length;
        }

        short[] flattenedData = new short[totalSize];
        int currentIndex = 0;
        for (short[] chunk : recordedAudioData) {
            System.arraycopy(chunk, 0, flattenedData, currentIndex, chunk.length);
            currentIndex += chunk.length;
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] byteData = new byte[flattenedData.length * 2];

            for (int i = 0; i < flattenedData.length; i++) {
                byteData[i * 2] = (byte) (flattenedData[i] & 0x00FF);
                byteData[i * 2 + 1] = (byte) ((flattenedData[i] >> 8) & 0xFF);
            }

            fos.write(byteData);
            fos.flush();

            Toast.makeText(this, "Audio saved at: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e("AudioSave", "Error saving audio", e);
            Toast.makeText(this, "Error saving audio: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
