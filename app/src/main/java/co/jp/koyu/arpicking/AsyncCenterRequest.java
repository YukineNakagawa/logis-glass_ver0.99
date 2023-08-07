package co.jp.koyu.arpicking;

import android.app.Activity;

import android.app.Activity;
import android.os.Looper;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import android.os.Handler;
import android.view.View;

/**
 * 非同期でセンター(サーバー)へリクエストを送ってJsonNode形式のデータを取得します。
 * <p>
 * メインフラグメントでのコーディング例)
 * String url = getString(R.string.app_url) + "/api/deliveryplan/driver?hdn_driverId=1&hdn_startDate=20230106&hdn_endDate=20230106";
 * AsyncCenterRequest centerRequest = new AsyncCenterRequest((MainActivity) getActivity(),"showDriverFragment", url);
 * centerRequest.execute();
 */
public class AsyncCenterRequest {
    private ExecutorService executorService;
    private MainActivity _activity;
    private String _postMethodName;
    private String _url;
    private JsonNode _json;
    private View _view;

    public AsyncCenterRequest(MainActivity activity, String postMethodName, String url, View view) {
        super();
        _activity = activity;
        _postMethodName = postMethodName;
        _url = url;
        _view = view;
        executorService = Executors.newSingleThreadExecutor();
    }

    private class TaskRun implements Runnable {

        // メインフラグメント(MainFragment)から呼ばれます

        @Override
        public void run() {
            String result = "";
            try {
                URL url = new URL(_url);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.connect(); // URL接続
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String tmp = "";

                while ((tmp = in.readLine()) != null) {
                    result += tmp;
                }

                in.close();
                con.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (result == null || result.isEmpty()) {
                _json = null;
            } else {
                _json = null;
                try {
                    _json = CommonUtil.getInstance().convertStringToJson(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> onPostExecute(_json));
        }
    }

    void execute() {
        onPreExecute();
        executorService.submit(new TaskRun());
    }

    void onPreExecute() {
    }

    void onPostExecute(JsonNode root) {
        _activity.postExecute(_postMethodName, root, _view);
    }
}
