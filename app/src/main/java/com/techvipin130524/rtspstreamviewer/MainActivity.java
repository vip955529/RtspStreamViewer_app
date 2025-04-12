package com.techvipin130524.rtspstreamviewer;

import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private MediaPlayer recordingPlayer;
    private VLCVideoLayout videoLayout;
    private EditText etRtspUrl;
    private Button btnPlay, btnRecord, btnPip;
    private boolean isRecording = false;

    private File recordingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        videoLayout = findViewById(R.id.videoLayout);
        etRtspUrl = findViewById(R.id.etRtspUrl);
        btnPlay = findViewById(R.id.btnPlay);
        btnRecord = findViewById(R.id.btnRecord);
        btnPip = findViewById(R.id.btnPip);

        // Set default RTSP URL (can be changed)
        etRtspUrl.setText("rtsp://192.168.163.164:5540/ch0");

        // Initialize VLC
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=300");
        libVLC = new LibVLC(this, options);

        // Play button click listener
        btnPlay.setOnClickListener(v -> {
            String rtspUrl = etRtspUrl.getText().toString();
            if (rtspUrl.isEmpty()) {
                Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show();
                return;
            }
            playStream(rtspUrl);
        });

        // Record button click listener
        btnRecord.setOnClickListener(v -> {
            if (mediaPlayer == null) {
                Toast.makeText(this, "Play stream first", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleRecording();
        });

        // PIP button click listener
        btnPip.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipMode();
            } else {
                Toast.makeText(this, "PIP requires Android Oreo or higher", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void playStream(String rtspUrl) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);

            Media media = new Media(libVLC, Uri.parse(rtspUrl));
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();

            Toast.makeText(this, "Playing stream", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error playing stream: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
            btnRecord.setText("Record");
        } else {
            startRecording();
            btnRecord.setText("Stop Recording");
        }
    }

    private void startRecording() {
        try {
            File dir = new File(getExternalFilesDir(null), "Recordings");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            recordingFile = new File(dir, "recording_" + timestamp + ".mp4");

            // Create a separate MediaPlayer instance for recording
            if (recordingPlayer != null) {
                recordingPlayer.release();
            }

            recordingPlayer = new MediaPlayer(libVLC);
            Media recordingMedia = new Media(libVLC, Uri.parse(etRtspUrl.getText().toString()));

            // Set recording options with audio
            String recordOptions = ":sout=#duplicate{dst=file{dst='" + recordingFile.getAbsolutePath() +
                    "'},dst=none :no-sout-rtp-sap :no-sout-standard-sap :sout-keep";
            recordingMedia.addOption(recordOptions);

            recordingPlayer.setMedia(recordingMedia);
            recordingMedia.release();
            recordingPlayer.play();

            isRecording = true;
            Toast.makeText(this, "Recording started: " + recordingFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        try {
            if (recordingPlayer != null) {
                recordingPlayer.stop();
                recordingPlayer.release();
                recordingPlayer = null;
            }

            if (recordingFile != null && recordingFile.exists()) {
                long fileSize = recordingFile.length();
                String sizeMessage = fileSize > 0 ?
                        "Recording saved (" + (fileSize/1024) + " KB)" :
                        "Recording failed (0 B)";

                // Get the absolute path of the recording file
                String filePath = recordingFile.getAbsolutePath();

                Toast.makeText(this, sizeMessage + ": " + recordingFile.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
                // Log the file path
                Log.d("Recording", "File saved at: " + filePath);
                Log.d("Recording", "File size: " + fileSize + " bytes (" + (fileSize/1024) + " KB)");
            }

            isRecording = false;
        } catch (Exception e) {
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(videoLayout.getWidth(), videoLayout.getHeight());
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (recordingPlayer != null) {
            recordingPlayer.release();
            recordingPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            // Hide the controls in PIP mode
            btnPlay.setVisibility(View.GONE);
            btnRecord.setVisibility(View.GONE);
            btnPip.setVisibility(View.GONE);
            etRtspUrl.setVisibility(View.GONE);
        } else {
            // Show the controls when returning from PIP mode
            btnPlay.setVisibility(View.VISIBLE);
            btnRecord.setVisibility(View.VISIBLE);
            btnPip.setVisibility(View.VISIBLE);
            etRtspUrl.setVisibility(View.VISIBLE);
        }
    }
}