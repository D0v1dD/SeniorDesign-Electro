package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
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

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String TAG = "MainActivity";
    private boolean permissionToRecordAccepted = false;
    private AudioProcessor audioProcessor;
    private LineChart audioChart;
    private LineDataSet dataSet;
    private LineData lineData;
    private SNRBar snrBar; // Custom SNR Bar
    private long lastGraphUpdateTime = 0;  // Last update time for graph
    private long lastSNRUpdateTime = 0;  // Last update time for SNR bar
    private static final int GRAPH_UPDATE_INTERVAL = 500;  // Update interval for graph (milliseconds)
    private static final int SNR_UPDATE_INTERVAL = 500;  // Update interval for SNR bar (milliseconds)
    private float[] baselineNoiseValues;  // Store baseline noise values
    private float baselineAverage = 0; // Store average baseline noise level

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Using view binding to avoid findViewById redundancy
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Initialize buttons
        Button recordBaselineButton = binding.buttonRecordBaseline;
        Button startRecordButton = binding.buttonStartRecord;
        Button stopRecordButton = binding.buttonStopRecord;
        Button viewSavedFilesButton = binding.buttonViewSavedFiles;
        snrBar = binding.snrBar;

        // Hide stop recording button initially
        stopRecordButton.setVisibility(Button.GONE);

        // Request RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission();
        } else {
            initializeAudioProcessor();
        }

        // Set Record Baseline Button listener
        recordBaselineButton.setOnClickListener(view -> {
            if (permissionToRecordAccepted) {
                recordBaseline();
            } else {
                requestRecordAudioPermission();
            }
        });

        // Set Start Button listener
        startRecordButton.setOnClickListener(view -> {
            if (permissionToRecordAccepted) {
                startRecording();
                startRecordButton.setVisibility(Button.GONE);
                stopRecordButton.setVisibility(Button.VISIBLE);
            } else {
                requestRecordAudioPermission();
            }
        });

        // Set Stop Button listener
        stopRecordButton.setOnClickListener(view -> {
            if (audioProcessor != null) {
                audioProcessor.stopRecording();
                stopRecordButton.setVisibility(Button.GONE);
                startRecordButton.setVisibility(Button.VISIBLE);
            }
        });

        // Set View Saved Files button listener
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());

        // Initialize chart and audio monitoring
        audioChart = binding.audioChart;
        dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        lineData = new LineData(dataSet);
        audioChart.setData(lineData);
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
                baselineAverage = calculateAverage(baselineValues);
                Toast.makeText(MainActivity.this, "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recordBaseline() {
        if (audioProcessor != null) {
            audioProcessor.recordBaseline();
        } else {
            Toast.makeText(this, "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        if (baselineNoiseValues == null) {
            Toast.makeText(this, "Please record baseline noise first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioProcessor != null) {
            audioProcessor.setBaseline(baselineNoiseValues);
            audioProcessor.startRecording();
        } else {
            Toast.makeText(this, "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                Toast.makeText(this, "Permission granted, you can now record audio.", Toast.LENGTH_SHORT).show();
                initializeAudioProcessor();
            } else {
                Toast.makeText(this, "Recording permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateGraph(short[] buffer) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGraphUpdateTime < GRAPH_UPDATE_INTERVAL) {
            return;
        }
        lastGraphUpdateTime = currentTime;

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

        runOnUiThread(() -> {
            dataSet.notifyDataSetChanged();
            lineData.notifyDataChanged();
            audioChart.notifyDataSetChanged();
            audioChart.invalidate();
        });
    }

    private float calculateAverage(float[] values) {
        float sum = 0;
        for (float value : values) {
            sum += value;
        }
        return values.length > 0 ? sum / values.length : 0;
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
