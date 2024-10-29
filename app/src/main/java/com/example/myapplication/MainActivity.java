package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

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
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";  // Added TAG for logging
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;

    private AudioProcessor audioProcessor;
    private LineChart audioChart;
    private LineDataSet dataSet;
    private LineData lineData;
    private SNRBar snrBar; // Custom SNR Bar

    private long lastGraphUpdateTime = 0;  // Last update time for graph
    private static final int GRAPH_UPDATE_INTERVAL = 100;  // Update interval for graph (milliseconds)

    private float[] baselineNoiseValues;  // Store baseline noise values
    private boolean isRecordingBaseline = false; // Flag to track if baseline recording is in progress

    private Button recordBaselineButton;
    private Button startRecordButton;
    private Button stopRecordButton;
    private Button viewSavedFilesButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Using view binding to avoid findViewById redundancy
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Initialize buttons
        recordBaselineButton = binding.buttonRecordBaseline;
        startRecordButton = binding.buttonStartRecord;
        stopRecordButton = binding.buttonStopRecord;
        viewSavedFilesButton = binding.buttonViewSavedFiles;
        snrBar = binding.snrBar;

        // Hide stop recording button initially
        stopRecordButton.setVisibility(Button.GONE);

        // Request RECORD_AUDIO permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission();
        } else {
            permissionToRecordAccepted = true;
            initializeAudioProcessor();
        }

        // Set Record Baseline Button listener
        recordBaselineButton.setOnClickListener(view -> recordBaseline());

        // Set Start Button listener
        startRecordButton.setOnClickListener(view -> startRecording());

        // Set Stop Button listener
        stopRecordButton.setOnClickListener(view -> stopRecording());

        // Set View Saved Files button listener
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());

        // Initialize chart and audio monitoring
        initializeChart(binding);
    }

    private void initializeAudioProcessor() {
        // Instantiate AudioProcessor after permission is granted
        audioProcessor = new AudioProcessor(this, new AudioProcessor.RecordingCallback() {
            @Override
            public void onAudioDataReceived(short[] audioBuffer) {
                updateGraph(audioBuffer);
            }

            @Override
            public void onBaselineRecorded(float[] baselineValues) {
                baselineNoiseValues = baselineValues;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
                    // Enable the start recording button after baseline is recorded
                    startRecordButton.setEnabled(true);
                });
            }

            @Override
            public void onSNRCalculated(double snrValue) {
                Log.d(TAG, "SNR Value: " + snrValue);
                updateSNRBar(snrValue);
            }
        });
    }

    private void initializeChart(ActivityMainBinding binding) {
        audioChart = binding.audioChart;
        dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        lineData = new LineData(dataSet);
        audioChart.setData(lineData);
        audioChart.getDescription().setEnabled(false);
        audioChart.getLegend().setEnabled(false);
        audioChart.setTouchEnabled(false);
        audioChart.setViewPortOffsets(0, 0, 0, 0);
    }

    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);  // Added this line

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                initializeAudioProcessor();
                Toast.makeText(this, "Permission granted, you can now record audio.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Recording permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void recordBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }

        if (audioProcessor != null) {
            isRecordingBaseline = true;
            audioProcessor.recordBaseline();
            // Update button visibility
            runOnUiThread(() -> {
                recordBaselineButton.setEnabled(false);
                startRecordButton.setEnabled(false);
                stopRecordButton.setVisibility(Button.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
        } else {
            Toast.makeText(this, "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }

        if (baselineNoiseValues == null) {
            Toast.makeText(this, "Please record baseline noise first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioProcessor != null) {
            audioProcessor.setBaseline(baselineNoiseValues);
            audioProcessor.startRecording();
            // Update button visibility
            runOnUiThread(() -> {
                startRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(false);
                stopRecordButton.setVisibility(Button.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
        } else {
            Toast.makeText(this, "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (audioProcessor != null) {
            if (isRecordingBaseline) {
                audioProcessor.stopBaselineRecording();
                isRecordingBaseline = false;
            } else {
                audioProcessor.stopRecording();
            }
            // Update button visibility
            runOnUiThread(() -> {
                stopRecordButton.setVisibility(Button.GONE);
                stopRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(true);
                startRecordButton.setEnabled(true);
            });
        }
    }

    private void updateGraph(short[] buffer) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGraphUpdateTime < GRAPH_UPDATE_INTERVAL) {
            return;
        }
        lastGraphUpdateTime = currentTime;

        runOnUiThread(() -> {
            int currentX = dataSet.getEntryCount();

            for (short amplitude : buffer) {
                float normalizedAmplitude = amplitude / 32768f;
                dataSet.addEntry(new Entry(currentX++, normalizedAmplitude));
            }

            // Limit the number of points to prevent performance issues
            int maxVisiblePoints = 500;
            while (dataSet.getEntryCount() > maxVisiblePoints) {
                dataSet.removeFirst();
            }

            dataSet.notifyDataSetChanged();
            lineData.notifyDataChanged();
            audioChart.notifyDataSetChanged();
            audioChart.invalidate();
        });
    }

    private void updateSNRBar(double snrValue) {
        snrBar.setSNRValue(snrValue);
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
}
