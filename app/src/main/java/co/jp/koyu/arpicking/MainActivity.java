package co.jp.koyu.arpicking;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.vuzix.sdk.barcode.ScanResult2;
import com.vuzix.sdk.barcode.ScannerIntent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * メインアクティビティ
 * <p>
 * [TECH] BY FUKEHARA
 * Illegal char <*> at index 0: *.lock のエラーが発生して起動できない場合 ⇒ 停止できていない為
 * Waiting for target device to come online と出たままアプリが起動しない場合 ⇒ Wipe Data (データを消去)
 */
public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SCAN = 90001;  // このアクティビティ内で一意である必要があります
    private Button mButtonScan;
    private EditText mTextEntryField;

    public boolean mIsUseMockup = false;
    private String mUserData;

    /**
     * UIのセットアップ
     *
     * @param savedInstanceState - 未使用で、変更されずにスーパークラスに渡されます
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // メインアクティビティに戻る際にユーザーメニューを表示する場合がある対応
        // VideoHWEncodingAsyncActivityのonBackPressed時
        Intent intent = getIntent();
        try {
            mUserData = intent.getExtras().getString("userData");
        } catch (Exception e) {
        }
        if (mUserData == null || mUserData.isEmpty()) {
            showMainFragment();
        } else {
            JsonNode userData = CommonUtil.getInstance().convertStringToJson(mUserData);
            showUserMenuFragment(userData, null);
        }
    }

    /**
     * ボタン押下時 スキャナアプリを呼び出す
     * ※この方法だとQRコードも読み、バーコードの読み込みスピード・精度も高い
     */
    public void scanQRLogin() {
        Intent scannerIntent = new Intent(ScannerIntent.ACTION);
        try {
            // Vuzixに登録されているスキャナアプリを呼び出す
            startActivityForResult(scannerIntent, REQUEST_CODE_SCAN);
        } catch (ActivityNotFoundException activityNotFound) {
            Toast.makeText(this, R.string.only_on_mseries, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * スキャナアプリはバーコードをスキャンして返す
     *
     * @param requestCode int startActivityForResultで指定した識別子
     * @param resultCode  int スキャン操作の結果
     * @param data        Intent containing a ScanResult whenver the resultCode indicates RESULT_OK
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_SCAN:
                if (resultCode == Activity.RESULT_OK) {
                    ScanResult2 scanResult = data.getParcelableExtra(ScannerIntent.RESULT_EXTRA_SCAN_RESULT2);
                    ///
                    /// TODO:メニュー画面へ 未実装
                    /// TODO:QRコードログイン用の処理 未実装
                    ///
                    String scanText = scanResult.getText();
                    //TODO:スキャンしたテキストから復号化処理未
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 結果フラグメントからスキャナフラグメントに戻るか、スキャナからアプリを終了するか制御
     */
    @Override
    public void onBackPressed() {
        if (isFragmentShowing("MainFragment")) {
            finish();
            return;
        }
        if (isFragmentShowing("UserMenuFragment")) {
            showMainFragment();
            return;
        }
        super.onBackPressed();
    }

    /**
     * フラグメントが表示されているかどうかを判断する
     *
     * @return 表示されている場合はtrue
     */
    private boolean isFragmentShowing(String fragmentName) {
        switch (fragmentName) {
            case "MainFragment":
                return getFragmentManager().findFragmentById(R.id.container) instanceof MainFragment;
            case "UserMenuFragment":
                return getFragmentManager().findFragmentById(R.id.container) instanceof UserMenuFragment;
        }

        return false;
    }

    /**
     * メインフラグメントの表示
     */
    private void showMainFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.container, MainFragment.newInstance());
        fragmentTransaction.commit();
    }

    /**
     * AsyncCenterRequestのpostExecuteから呼ばれるメソッド
     * <p>
     * [TECH] BY FUKEHARA
     * method渡しのinvoke実行や、function渡しのapply実行がうまくいかなかったので、
     * mainActivityのインスタンスを渡して、postExecute用の共通メソッドをコールバックする形に
     */
    public void postExecute(String methodName, JsonNode root, View view) {
        if (root == null) {
            CommonUtil.getInstance().showShortSnackBar(view, "サーバーからのデータ取得に失敗しました。", R.color.error);
            return;
        }

        if (!mIsUseMockup) {
            // ファイルに保存
            Context ctx = getApplicationContext();
            CommonUtil.getInstance().saveFile(ctx, getString(R.string.demo_user_data_all_file_name), root.toString());
        }

        switch (methodName) {
            case "showUserMenuFragment":
                this.showUserMenuFragment(root, view);
                break;
            default:
                Log.e("MainActivity.postExecute", "メソッド名間違い");
        }
    }

    /**
     * ユーザーメニューフラグメントの表示
     */
    public void showUserMenuFragment(JsonNode root, View view) {
        if (root.get("userMenuData").size() == 0) {
            CommonUtil.getInstance().showShortSnackBar(view, "ユーザーメニューの設定がありません。", R.color.warning);
        } else {
            //TODO:正常時のスナックバー表示をどうする？
            /*
            CommonUtil.getInstance().showShortSnackBar(view,
                    "配送計画データは" + root.get("deliveryPlanData").size() + "件、" +
                            "棚データは" + root.get("rackData").size() + "件" +
                            "です。", R.color.success);
             */
        }

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // UserMenuFragmentのインスタンス化 パラメータ⇒ユーザーデータ(設定、トランデータ)
        Log.d("Data", root.toString());
        fragmentTransaction.replace(R.id.container, UserMenuFragment.newInstance(root));
        fragmentTransaction.commit();
    }

    /**
     * スキャンバーコードアクティビティ開始
     * そのユーザーに関する全データを渡している
     */
    public void startScanActivity(String userData, String dataName) {
        Intent intent = new Intent();
        JsonNode json = CommonUtil.getInstance().convertStringToJson(userData);
        intent.putExtra("userData", userData);
        intent.putExtra("userDataName", dataName);
        intent.putExtra("targetData", json.get(dataName).toString());
        intent.setClassName("co.jp.koyu.arpicking.prototype",
                "co.jp.koyu.arpicking.ScanBarcodeActivity");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Log.d("StartScanActitity", "Error", ex);
        }
    }

    /**
     * ビデオ録画アクティビティ開始
     */
    public void startVideoActivity(String userData, String dataName, String title) {
        Intent intent = new Intent();
        JsonNode json = CommonUtil.getInstance().convertStringToJson(userData);
        intent.putExtra("userData", userData);
        intent.putExtra("userDataName", "");
        intent.putExtra("targetData", json.get(dataName).toString());
        intent.putExtra("customerId", json.get("hdn_customerId").asText());
        intent.putExtra("userId", json.get("hdn_userId").asText());
        intent.putExtra("dataName", dataName);
        intent.putExtra("config", json.get(dataName).get("config").toString());
        intent.putExtra("mode", "");
        intent.setClassName("co.jp.koyu.arpicking.prototype",
                "co.jp.koyu.arpicking.VideoHWEncodingAsyncActivity");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Log.d("StartVideoActitity", "Error", ex);
        }
    }

    /**
     * 録画アップロード後の処理
     */
    public void onVideoUploadPostExecute(String result, View view) {
        if (result != null) {
            Log.d("■onPostExecute■", result);

            JsonNode json = CommonUtil.getInstance().convertStringToJson(result);
            if (json == null) {
                CommonUtil.getInstance().showSnackBar(view, "予期せぬエラーが発生しました。", R.color.error, "OK");
                return;
            } else {
                if (json.get("isWarning").asBoolean()) {
                    CommonUtil.getInstance().showSnackBar(view, json.get("message").asText(), R.color.warning, "OK");
                    return;
                }
                if (json.get("isError").asBoolean()) {
                    CommonUtil.getInstance().showSnackBar(view, json.get("message").asText(), R.color.error, "OK");
                    return;
                }
                CommonUtil.getInstance().showShortSnackBar(view, "録画アップロードに成功しました。", R.color.success);
            }
        }
    }
}
