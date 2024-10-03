package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AudioRecord audioRecord;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private LineChart audioChart;
    private LineDataSet dataSet;
    private LineData lineData;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private int bufferSize;
    private Button startRecordButton;
    private Button stopRecordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Initialize buttons
        startRecordButton = findViewById(R.id.button_start_record);
        stopRecordButton = findViewById(R.id.button_stop_record);
        stopRecordButton.setVisibility(Button.GONE);  // Initially hide stop button

        // Permission check (handle API level 33+ for READ_MEDIA_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ?
                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_AUDIO} :
                            new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            permissionToRecordAccepted = true;
        }

        // Initialize chart and audio monitoring
        audioChart = findViewById(R.id.audio_chart);
        dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        lineData = new LineData(dataSet);
        audioChart.setData(lineData);

        // Set Start Button listener
        startRecordButton.setOnClickListener(view -> {
            if (permissionToRecordAccepted) {
                startAudioRecording();
            } else {
                Toast.makeText(MainActivity.this, "Recording permissions are not granted", Toast.LENGTH_LONG).show();
            }
        });

        // Set Stop Button listener
        stopRecordButton.setOnClickListener(view -> {
            stopAudioRecording();
            stopRecordButton.setVisibility(Button.GONE);
            startRecordButton.setVisibility(Button.VISIBLE);
        });
    }

private void startAudioRecording() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(MainActivity.this, "Audio recording permission is required", Toast.LENGTH_LONG).show();
        return;
    }

    // Initialize the audio recording with appropriate buffer size
    bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize);

    // Start recording
    audioRecord.startRecording();
    isRecording = true;

    // Hide the start button and show the stop button
    runOnUiThread(() -> {
        startRecordButton.setVisibility(Button.GONE);
        stopRecordButton.setVisibility(Button.VISIBLE);
    });

    // Run audio capture and processing on a background thread
    backgroundThread = new HandlerThread("AudioRecordingThread");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());

    backgroundHandler.post(() -> {
        short[] buffer = new short[bufferSize];
        long lastUpdateTime = System.currentTimeMillis();  // Initialize the last update time

        while (isRecording) {
            int readSize = audioRecord.read(buffer, 0, buffer.length);
            if (readSize > 0) {
                long currentTime = System.currentTimeMillis();
                // Throttle updates to the UI every 50ms
                if (currentTime - lastUpdateTime > 50) {  // Adjust the interval as needed
                    runOnUiThread(() -> updateGraph(buffer));  // Update the graph on the UI thread
                    lastUpdateTime = currentTime;  // Update the last update time
                }
            }
        }
    });
}


private void updateGraph(short[] buffer) {
    ArrayList<Entry> newEntries = new ArrayList<>();
    int currentX = dataSet.getEntryCount();  // Start X from the current number of entries

    for (short amplitude : buffer) {
        float normalizedAmplitude = amplitude / 32768f;  // Normalize to -1 to 1 range
        // Add new entries incrementing the X-value
        newEntries.add(new Entry(currentX++, normalizedAmplitude));
    }

    runOnUiThread(() -> {
        // Add the new entries to the dataset without clearing the previous ones
        for (Entry entry : newEntries) {
            dataSet.addEntry(entry);
        }

        // Optionally, limit the number of points to prevent performance issues
        int maxVisiblePoints = 500;  // Adjust based on your needs
        while (dataSet.getEntryCount() > maxVisiblePoints) {
            dataSet.removeFirst();  // Remove the oldest data point
        }

        // Notify changes
        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        audioChart.notifyDataSetChanged();
        audioChart.invalidate();  // Redraw the chart
    });
}

    private void stopAudioRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (IllegalStateException e) {
                Log.e("AudioRecord", "Error stopping recording", e);
            }
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();  // Ensure the thread is fully terminated
            } catch (InterruptedException e) {
                Log.e("AudioRecord", "Error joining background thread", e);
            }
            backgroundThread = null;
        }

        // Reset the UI to show the start button and hide the stop button
        runOnUiThread(() -> {
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted) finish();
    }
}
