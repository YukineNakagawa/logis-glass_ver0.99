package co.jp.koyu.arpicking;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.TextView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * 共通ユーティリティクラス　シングルトンパターン
 */
public class CommonUtil {
    private Properties _properties = new Properties();

    private CommonUtil() {
    }

    public static CommonUtil getInstance() {
        return CommonUtilInstanceHolder.INSTANCE;
    }

    public static class CommonUtilInstanceHolder {
        private static final CommonUtil INSTANCE = new CommonUtil();
    }

    /**
     * 日付の取得
     *
     * @param dt     nullの場合は現在日付
     * @param format nullの場合はyyyyMMdd
     * @return
     */
    public String getDate(Date dt, String format) {
        if (dt == null) dt = new Date();
        if (format == null) format = "yyyyMMdd";
        SimpleDateFormat dtf = new SimpleDateFormat(format);

        return dtf.format(dt);
    }

    /**
     * 短時間スナックバー表示
     * <p>
     * 参考) VuzixのサンプルはToastを使っている
     *
     * @param view
     * @param message
     * @param color
     */
    Snackbar mSnackbar;
    public void showShortSnackBar(View view, String message, int color) {
        mSnackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        mSnackbar.getView().setBackgroundResource(color);
        if (R.color.warning == color) {
            // 背景が薄いため、文字色を黒にする [TECH] setActionTextColor では変わらない
            TextView tv = (TextView) mSnackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
            tv.setTextColor(Color.BLACK);
        }
        mSnackbar.show();
    }

    public void showSnackBar(View view, String message, int color, String caption) {
        mSnackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
        mSnackbar.setAction(caption, new MySnackBarListener());
        mSnackbar.getView().setBackgroundResource(color);
        if (R.color.warning == color) {
            // 背景が薄いため、文字色を黒にする [TECH] setActionTextColor では変わらない
            TextView tv = (TextView) mSnackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
            tv.setTextColor(Color.BLACK);
        }
        mSnackbar.show();
    }

    public class MySnackBarListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSnackbar.dismiss();
        }
    }

    /**
     * ネットワークが利用可能かどうか
     *
     * @param activity
     * @return
     */
    public boolean isAvailableNetwork(Activity activity) {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        // NetworkCapabilitiesの取得
        // 引数にcm.activeNetworkを指定し、現在アクティブなデフォルトネットワークに対応するNetworkオブジェクトを渡している
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());

        if (capabilities == null) {
            return false;
        } else {
            // Wifiで接続 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            // モバイル通信で接続 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            return true;
        }
    }

    /**
     * Json形式の文字列をJacksonのJsonNode形式にコンバートします。
     *
     * @param param
     * @return
     */
    public JsonNode convertStringToJson(String param) {
        JsonNode root = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            root = mapper.readTree(param);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return root;
    }

    /**
     * ファイルの保存
     *
     * @param ctx
     * @param fileName
     * @param data
     */
    public void saveFile(Context ctx, String fileName, String data) {
        // 削除は deleteFile( "test.txt" ); でOK
        try {
            FileOutputStream out = ctx.openFileOutput(fileName, MODE_PRIVATE);
            out.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ファイルのロード
     *
     * @param ctx
     * @param fileName
     * @return
     */
    public String loadFile(Context ctx, String fileName) {
        String str = "";
        try {
            FileInputStream in = ctx.openFileInput(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String tmp;
            while ((tmp = reader.readLine()) != null) {
                str = str + tmp + "\n";
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return str;
        }
    }

    /**
     * テキストビューの設定
     * @param textView
     * @param config
     */
    public void configTextView(TextView textView, JsonNode config) {
        textView.setTextSize((float) config.get("size").asDouble());
        //[TECH] #AARRGGBB
        textView.setTextColor(Color.parseColor(config.get("color").asText()));
        textView.setShadowLayer(
                (float) config.get("shadowRadius").asDouble(),
                (float) config.get("shadowDx").asDouble(),
                (float) config.get("shadowDy").asDouble(),
                Color.parseColor(config.get("shadowColor").asText()));
        textView.setAllCaps(config.get("allCaps").asBoolean());
        // int NORMAL = 0;
        // int BOLD = 1;
        // int BOLD_ITALIC = 3;
        // int ITALIC = 2;
        textView.setTypeface(textView.getTypeface(), config.get("style").asInt());
    }
}
