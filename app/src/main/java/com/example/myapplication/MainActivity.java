package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.databinding.ActivityMainBinding;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private MediaRecorder mediaRecorder;
    private String outputFilePath;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private ProgressBar audioLevelIndicator;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Request permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_RECORD_AUDIO_PERMISSION);

        // Initialize MediaRecorder and output file path
        mediaRecorder = new MediaRecorder();
        outputFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/baseline_audio.3gp";
        audioLevelIndicator = findViewById(R.id.audio_level_indicator);
        handler = new Handler();

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (permissionToRecordAccepted) {
                    recordAudioForTwoSeconds();
                } else {
                    Toast.makeText(MainActivity.this, "Recording permissions are not granted", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void recordAudioForTwoSeconds() {
        try {
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "Recording started");

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(outputFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            startAudioLevelMonitoring(); // Start monitoring the audio level

            binding.getRoot().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mediaRecorder.stop();
                    mediaRecorder.reset();

                    Toast.makeText(MainActivity.this, "Recording finished", Toast.LENGTH_SHORT).show();
                    Log.d("MainActivity", "Recording stopped");

                    handler.removeCallbacksAndMessages(null); // Stop audio level monitoring
                    audioLevelIndicator.setProgress(0); // Reset progress
                }
            }, 2000); // 2000 milliseconds = 2 seconds

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAudioLevelMonitoring() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null) {
                    int maxAmplitude = mediaRecorder.getMaxAmplitude();
                    int audioLevel = maxAmplitude / 327; // Scale it to 0-100 range
                    audioLevelIndicator.setProgress(audioLevel);

                    // Repeat the update every 100ms
                    handler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
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
