package fr.cuffel.mylametrictime;

import android.annotation.SuppressLint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.content.SharedPreferences;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import com.google.android.material.textfield.TextInputLayout;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.Executors;
import fr.cuffel.mylametrictime.databinding.ActivityFullscreenBinding;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FullscreenActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private ActivityFullscreenBinding binding;
    private FrameLayout SettingsLayout;
    public WebView MyWebView;
    private HttpServer mHttpServer;
    private int WebServerPort = 5000;
    private int Green = Color.parseColor("#FF4CAF50");
    private boolean IsSettingsPannelOpen = false;
    private SharedPreferences MySharedPreferences;

    private HttpHandler rootHandler = new HttpHandler() {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            if(httpExchange.getRequestMethod().equals("GET")){
                File f = new File(getFilesDir().getAbsolutePath() + "/My-LaMetric-Time-main/public" + httpExchange.getRequestURI().toString());
                FileInputStream fl = new FileInputStream(f);
                byte[] arr = new byte[(int)f.length()];
                fl.read(arr);
                fl.close();

                httpExchange.sendResponseHeaders(200, arr.length);//response code and length
                OutputStream os = httpExchange.getResponseBody();
                os.write(arr);
                os.close();
            }
            else if(httpExchange.getRequestMethod().equals("POST")){
                if(httpExchange.getRequestURI().toString().equals(("/notification"))){
                    String response = "Notification sended !";

                    Headers headers = httpExchange.getRequestHeaders();
                    if(headers.containsKey("api-key")){
                        File file = new File(getFilesDir().getAbsolutePath() + "/My-LaMetric-Time-main/settings.json");
                        String content = MySharedPreferences.getString("settings", ReadTextFile(file));
                        try {
                            JSONObject json = new JSONObject(content);
                            if(json.getString("APIKey").equals(headers.getFirst("api-key"))){
                                // GET REQ BODY
                                InputStreamReader isr =  new InputStreamReader(httpExchange.getRequestBody() ,"utf-8");
                                BufferedReader br = new BufferedReader(isr);
                                int b;
                                StringBuilder buf = new StringBuilder();
                                while ((b = br.read()) != -1) {
                                    buf.append((char) b);
                                }
                                br.close();
                                isr.close();

                                try {
                                    JSONObject notification_json = new JSONObject(buf.toString());

                                    MyWebView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            MyWebView.evaluateJavascript("APP.showNotification(JSON.parse(\"" + notification_json.toString().replace("\"", "\\\"") + "\"));", null);
                                        }
                                    });
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            else{
                                response = "Wrong API key.";
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    else{
                        response = "An API key is required. You will find it in the \"settings.json\" file.";
                    }

                    OutputStream os = httpExchange.getResponseBody();
                    httpExchange.sendResponseHeaders(200, response.length());//response code and length
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    };

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

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;

        public DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(context.getFilesDir().getAbsolutePath() + "/main.zip");

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        protected void onPostExecute(String result) {
            if (result != null){
                Toast.makeText(context,"Download error: " + result, Toast.LENGTH_SHORT).show();
            }
            else{
                TextView step1 = findViewById(R.id.step1);
                step1.setTextColor(Green);
                unpackZip(context.getFilesDir().getAbsolutePath() + "/", "main.zip");

                try {
                    File file = new File(getFilesDir().getAbsolutePath() + "/My-LaMetric-Time-main/settings.json");
                    String settings = ReadTextFile(file);
                    JSONObject json = new JSONObject(settings);

                    // We generate the API key.
                    json.put("APIKey",GeneratePassword(40));

                    // We adjust the screen size in the settings
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                    wm.getDefaultDisplay().getMetrics(displayMetrics);
                    json.getJSONObject("client").put("DesiredScreenWidth", displayMetrics.widthPixels).put("DesiredScreenHeight", displayMetrics.heightPixels);

                    SaveSettings(json.toString());
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
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

        MySharedPreferences = getSharedPreferences("myPrefs", MODE_PRIVATE);

        MyJavascriptInterface myInterface = new MyJavascriptInterface(this);

        this.MyWebView = findViewById(R.id.webview);
        WebSettings webSettings = this.MyWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        MyWebView.addJavascriptInterface(myInterface, "Android");
        this.MyWebView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                MyWebView.evaluateJavascript("APP.android = true;", null);

                String settings = MySharedPreferences.getString("settings", "null");
                if(!settings.equals("null")){
                    try {
                        JSONObject json = new JSONObject(settings);
                        MyWebView.evaluateJavascript("APP.setSettings(JSON.parse(\"" + json.getJSONObject("client").toString().replace("\"", "\\\"") + "\"));", null);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.SettingsLayout = findViewById(R.id.settings);

        mContentView = binding.fullscreenContent;

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //sharedPref = getSharedPreferences("myPref", MODE_PRIVATE);

        File file = new File(getFilesDir().getAbsolutePath() + "/main.zip");
        if(!file.exists()){
            final DownloadTask downloadTask = new DownloadTask(this);
            downloadTask.execute("https://github.com/HeyHeyChicken/My-LaMetric-Time/archive/refs/heads/main.zip");
        }
        else{
            TextView step1 = findViewById(R.id.step1);
            step1.setTextColor(Green);
            unpackZip(getFilesDir().getAbsolutePath() + "/", "main.zip");
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
        this.MyWebView.loadUrl(_url);
        this.SettingsLayout.setVisibility(View.GONE);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * This function starts the web server.
     * @param port
     */
    private void startServer(int port) {
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
            mHttpServer = HttpServer.create(inetSocketAddress, 0);
            mHttpServer.setExecutor(Executors.newCachedThreadPool());
            mHttpServer.createContext("/", rootHandler);
            mHttpServer.start();//start server;
            // Log.d("--------> ", "Server is running on " + mHttpServer.getAddress() + ":" + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function unzip an archive.
     * @param path
     * @param zipname
     * @return
     */
    private boolean unpackZip(String path, String zipname) {
        InputStream is;
        ZipInputStream zis;
        try
        {
            String filename;
            is = new FileInputStream(path + zipname);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null)
            {
                filename = ze.getName();

                // Need to create directories if not exists, or
                // it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(path + filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(path + filename);

                while ((count = zis.read(buffer)) != -1)
                {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();

            startServer(WebServerPort);

            LoadURL("http://localhost:" + WebServerPort + "/index.html");
        }
        catch(IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static String GeneratePassword(int length) {
        final Random RANDOM = new Random();
        final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
        final String NUMERIC = "0123456789";
        final String DICTIONNARY = ALPHABET + ALPHABET.toUpperCase() + NUMERIC;
        String result = "";
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(DICTIONNARY.length());
            result += DICTIONNARY.charAt(index);
        }
        return result;
    }

    /**
     * This function is executed when the user presses the "back" button on their device.
     */
    @Override
    public void onBackPressed() {
        String path = getFilesDir().getAbsolutePath() + "/My-LaMetric-Time-main/settings.json";
        File file = new File(path);
        if(file.exists()){
            try {
                TextInputLayout t = findViewById(R.id.jsonsettings);
                IsSettingsPannelOpen = !IsSettingsPannelOpen;
                if (IsSettingsPannelOpen) {
                    String content = MySharedPreferences.getString("settings", ReadTextFile(file));

                    JSONObject json = new JSONObject(content);
                    json.put("serverPort",WebServerPort);

                    t.getEditText().setText(json.toString(4));
                    t.setVisibility(View.VISIBLE);
                    MyWebView.setVisibility(View.GONE);
                }
                else{
                    if(t.getEditText().getText().toString().length() > 0){
                        String content = t.getEditText().getText().toString();

                        try {
                            JSONObject json = new JSONObject(content);

                            MyWebView.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        MyWebView.evaluateJavascript("APP.setSettings(JSON.parse(\"" + json.getJSONObject("client").toString().replace("\"", "\\\"") + "\"));", null);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        SaveSettings(content);
                        t.getEditText().setText("");
                    }
                    MyWebView.setVisibility(View.VISIBLE);
                    t.setVisibility(View.GONE);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This function allows you to save your settings.
     * @param data
     * @throws IOException
     */
    public void SaveSettings(String data) throws IOException {
        SharedPreferences.Editor editor = MySharedPreferences.edit();
        editor.putString("settings", data); // add or overwrite someValue
        editor.commit();
    }

    /**
     * This function allows you to retrieve the contents of a file.
     * @param file
     * @return
     * @throws IOException
     */
    public String ReadTextFile(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) { }
        String result = text.toString();
        return result;
    }
}