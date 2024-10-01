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
    private ArrayList<Entry> audioData;
    private LineDataSet dataSet;
    private LineData lineData;
    private Handler handler;
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

        // Permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_AUDIO},
        REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
        permissionToRecordAccepted = true;
        }



        // Initialize chart and audio monitoring
        audioChart = findViewById(R.id.audio_chart);
        audioData = new ArrayList<>();
        dataSet = new LineDataSet(audioData, "Audio Levels");
        lineData = new LineData(dataSet);
        audioChart.setData(lineData);

        // Set Start Button listener
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
    // Check if permission is granted before starting the recording
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(MainActivity.this, "Audio recording permission is required", Toast.LENGTH_LONG).show();
        return;
    }

    bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    audioRecord.startRecording();
    isRecording = true;

    startRecordButton.setVisibility(Button.GONE);  // Hide start button when recording
    stopRecordButton.setVisibility(Button.VISIBLE);  // Show stop button during recording

    // Start a background thread for recording and graph updates
    backgroundThread = new HandlerThread("AudioRecordingThread");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());

    // Start recording in the background
    backgroundHandler.post(() -> {
        short[] buffer = new short[bufferSize];
        while (isRecording) {
            int readSize = audioRecord.read(buffer, 0, buffer.length);
            if (readSize > 0) {
                updateGraph(buffer);
            }
        }
    });
}


    private void updateGraph(short[] buffer) {
        // Update the graph in the background thread and UI updates on the main thread
        ArrayList<Entry> newEntries = new ArrayList<>();
        for (short amplitude : buffer) {
            float normalizedAmplitude = amplitude / 32768f; // Normalize to -1 to 1 range
            newEntries.add(new Entry(audioData.size(), normalizedAmplitude));
        }

        // Post the graph update to the UI thread to avoid main thread blockage
        runOnUiThread(() -> {
            audioData.addAll(newEntries);
            dataSet.notifyDataSetChanged();
            audioChart.notifyDataSetChanged();
            audioChart.invalidate(); // Refresh the chart
        });
    }

    private void stopAudioRecording() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }

        Toast.makeText(this, "Recording finished", Toast.LENGTH_SHORT).show();

        // Clear the graph data
        audioData.clear();
        dataSet.notifyDataSetChanged();
        audioChart.notifyDataSetChanged();
        audioChart.invalidate();
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
