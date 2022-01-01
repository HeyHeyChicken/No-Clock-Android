package fr.cuffel.mylametrictime;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;

import fr.cuffel.mylametrictime.databinding.ActivityFullscreenBinding;

public class FullscreenActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private ActivityFullscreenBinding binding;
    private SharedPreferences sharedPref;
    private FrameLayout SettingsLayout;
    public WebView MyWebView;
    private SensorManager mSensorManager;

    public class MyJavascriptInterface {
        private FullscreenActivity MyActivity;

        MyJavascriptInterface(FullscreenActivity _activity){
            MyActivity = _activity;
        }

        @JavascriptInterface
        public void playSound(String _url) {
            MediaPlayer mPlayer = new MediaPlayer();
            Uri myUri = Uri.parse(_url);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mPlayer.setDataSource(MyActivity, myUri);
                mPlayer.prepare();
                mPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MyJavascriptInterface myInterface = new MyJavascriptInterface(this);

        this.MyWebView = findViewById(R.id.webview);
        WebSettings webSettings = this.MyWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        MyWebView.addJavascriptInterface(myInterface, "Android");
        this.MyWebView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                MyWebView.evaluateJavascript("APP.android = true;", null);
            }
        });

        this.SettingsLayout = findViewById(R.id.settings);
        TextInputLayout input = findViewById(R.id.input);
        Button button = findViewById(R.id.button);

        mContentView = binding.fullscreenContent;

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        sharedPref = getSharedPreferences("myPref", MODE_PRIVATE);

        String url = sharedPref.getString("url", "null");
        if(url != "null"){
            LoadURL(url);
        }

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String value = input.getEditText().getText().toString();
                sharedPref.edit().putString("url", value).commit();
                LoadURL(value);
            }
        });
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if(sensor.getType() == Sensor.TYPE_LIGHT){
            Log.i("Sensor Changed", "Accuracy :" + accuracy);
        }

    }

    public void onSensorChanged(SensorEvent event) {
        if( event.sensor.getType() == Sensor.TYPE_LIGHT){
            Log.i("Sensor Changed", "onSensor Change :" + event.values[0]);
        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        delayedHide(100);
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void LoadURL(String _url){
        Log.d("-------> LOADING", _url);
        this.SettingsLayout.setVisibility(View.GONE);
        this.MyWebView.loadUrl(_url);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}