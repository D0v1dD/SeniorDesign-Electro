package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private AudioRecord audioRecord;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private LineChart audioChart;
    private ArrayList<Entry> audioData;
    private LineDataSet dataSet;
    private LineData lineData;
    private Handler handler;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private int bufferSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
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
        handler = new Handler();

        binding.fab.setOnClickListener(view -> {
            if (permissionToRecordAccepted) {
                startAudioRecording();
            } else {
                Toast.makeText(MainActivity.this, "Recording permissions are not granted", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startAudioRecording() {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioRecord.startRecording();
        isRecording = true;

        new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isRecording) {
                int readSize = audioRecord.read(buffer, 0, buffer.length);
                if (readSize > 0) {
                    updateGraph(buffer);
                }
            }
        }).start();

        handler.postDelayed(this::stopAudioRecording, 2000);
    }

    private void updateGraph(short[] buffer) {
        runOnUiThread(() -> {
            for (short amplitude : buffer) {
                float normalizedAmplitude = amplitude / 32768f; // Normalize to -1 to 1 range
                audioData.add(new Entry(audioData.size(), normalizedAmplitude));
            }
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

        Toast.makeText(this, "Recording finished", Toast.LENGTH_SHORT).show();

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
