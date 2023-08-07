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
 * リクエストは送らず、非同期でJsonNode形式のデータを取得します。
 * データはdemo_user_data_all.xmlに持っています。
 * デモ・テスト用のモックアップクラスです。
 * <p>
 *
 */
public class AsyncCenterMockup {
    private ExecutorService executorService;
    private MainActivity _activity;
    private String _postMethodName;
    private String _url;
    private JsonNode _json;
    private View _view;

    public AsyncCenterMockup(MainActivity activity, String postMethodName, String url, View view) {
        super();
        _activity = activity;
        _postMethodName = postMethodName;
        _url = url;
        _view = view;
        executorService = Executors.newSingleThreadExecutor();
    }

    private class TaskRun implements Runnable {

        @Override
        public void run() {
            String result = "";

            if (_url.contains("/api/user/data/all")) {
                // ローカルのファイルを取得
                // 大阪ではこのコードが原因で動作しなかったようだ...finallyで必ず返すように修正
                /* TODO:TESTでdemo_user_data_all.xmlを使用するためファイルからのロードはいったんコメントアウト BY FUKE
                result = CommonUtil.getInstance().loadFile(
                        _activity.getApplicationContext(),
                        _activity.getString(R.string.demo_user_data_all_file_name));
                */

                if (result == null || result.isEmpty()) {
                    // ログインユーザーのデモ用設定・トランデータの取得
                    result = _activity.getString(R.string.demo_user_data_all);
                }
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
