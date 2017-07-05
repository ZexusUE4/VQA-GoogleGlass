package com.example.adminstrator.glasstest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.widget.Toast;

import com.google.android.glass.content.Intents;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.Key;
import java.util.List;

/**
 * A transparent {@link Activity} displaying a "Stop" options menu to remove the {@link LiveCard}.
 */
public class LiveCardMenuActivity extends Activity {


    private static final int TAKE_PICTURE_REQUEST = 1;
    private static final int SPEECH_REQUEST = 2;

    private static String spokenText;
    private static ByteArrayOutputStream pictureBytes;
    private static String picturePath;
    private TextToSpeechController tts;
    private boolean shouldFinishOnMenuClose;
    private Socket socket;
    private BufferedReader socketOutput;

    public static String thread_answer = null;

    private final String ip = "192.168.0.100";
    private final int port = 9999;

    private final int maxBytesToSend = 1024;

    private static boolean connected = false;


    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Open the options menu right away.
        openOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vqa, menu);
        return true;
    }

    private void connect(){

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    socket = new Socket(ip, port);
                    socketOutput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    connected = true;
                } catch (Exception e) {
                    String error = e.getMessage();
                }
            }
        });
        thread.start();

        try {
            thread.join();

            if(connected)
            {
                tts = new TextToSpeechController(getApplicationContext());
                Toast.makeText(getApplicationContext(), "Connected to server", Toast.LENGTH_LONG).show();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Unable to connect", Toast.LENGTH_LONG).show();
            }
        }catch (Exception e){}
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        shouldFinishOnMenuClose = false;

        switch (item.getItemId()) {
            case R.id.action_stop:
                // Stop the service which will unpublish the live card.
                stopService(new Intent(this, VQAService.class));
                return true;

            case R.id.action_Connect:
                    connect();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        // Nothing else to do, finish the Activity.
        if (shouldFinishOnMenuClose) {
            finish();
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        Log.d("ON KEY DOWN",String.valueOf((keyCode)));

        if (connected && keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {

            takePicture();

            return false;
        }else if(!connected){

            openOptionsMenu();

            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-acquire the camera and start the preview.
    }

    private void takePicture() {
        shouldFinishOnMenuClose = false;
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);

    }

    public void displaySpeechRecognizer() {
        shouldFinishOnMenuClose = false;
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        startActivityForResult(speechIntent, SPEECH_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
            String thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);
            picturePath = data.getStringExtra(Intents.EXTRA_PICTURE_FILE_PATH);
            displaySpeechRecognizer();
        }

        // Speech recognition
        else if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            spokenText = results.get(0);
            tts.speakTheText(spokenText);
            processPictureWhenReady(picturePath);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processPictureWhenReady(final String picturePath) {
        final File pictureFile = new File(picturePath);

        if (pictureFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
            bitmap = Bitmap.createScaledBitmap(bitmap, 448, 448, false);

            pictureBytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, pictureBytes);

            try
            {
                String answer = getAnswer();
                tts.speakTheText(answer);

                Toast.makeText(getApplicationContext(), "Answer: " + answer, Toast.LENGTH_LONG).show();
            }
            catch (Exception e)
            {
                String error = e.getMessage();
                connected = false;
            }

        } else {
            // The file does not exist yet. Before starting the file observer, you
            // can update your UI to let the user know that the application is
            // waiting for the picture (for example, by displaying the thumbnail
            // image and a progress indicator).

            final File parentDirectory = pictureFile.getParentFile();
            FileObserver observer = new FileObserver(parentDirectory.getPath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                // Protect against additional pending events after CLOSE_WRITE
                // or MOVED_TO is handled.
                private boolean isFileWritten;

                @Override
                public void onEvent(int event, String path) {
                    if (!isFileWritten) {
                        // For safety, make sure that the file that was created in
                        // the directory is actually the one that we're expecting.
                        File affectedFile = new File(parentDirectory, path);
                        isFileWritten = affectedFile.equals(pictureFile);

                        if (isFileWritten) {
                            stopWatching();

                            // Now that the file is ready, recursively call
                            // processPictureWhenReady again (on the UI thread).
                                    runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    processPictureWhenReady(picturePath);
                                }
                            });
                        }
                    }
                }
            };
            observer.startWatching();
        }
    }

    private String getAnswer() throws Exception {

        DataOutputStream DOS = new DataOutputStream(socket.getOutputStream());
        byte[] bytesToSend = pictureBytes.toByteArray();
        int offset = 0;

        DOS.writeUTF(spokenText);
        // Send Length first
        DOS.writeInt(bytesToSend.length);

        while (offset < bytesToSend.length)
        {
            int toSend = Math.min(maxBytesToSend, bytesToSend.length - offset);
            DOS.write(bytesToSend, offset, toSend);
            offset += toSend;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try  {
                    LiveCardMenuActivity.thread_answer = socketOutput.readLine();
                    Log.e("ANSWER",LiveCardMenuActivity.thread_answer);
                } catch (Exception e) {
                    String error = e.getMessage();
                    connected = false;
                }
            }
        });

        thread.start();
        thread.join();

        return thread_answer;
    }
}
