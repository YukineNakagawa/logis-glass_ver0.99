package co.jp.koyu.arpicking;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;

import com.fasterxml.jackson.databind.JsonNode;
import com.vuzix.sdk.barcode.ScanResult2;
import com.vuzix.sdk.barcode.ScannerFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Vuzixのライブラリを使用したバーコード(QRコード)のスキャン
 * Vuzixのサンプル BarcodeEmbeddedScanner を元に作成
 * <p>
 * TODO:命名規約の整理 publicな変数は、頭に"m"を付ける
 * https://qiita.com/Reyurnible/items/2de397b80391189af8e4
 * <p>
 * バーコードを読み込んだ際に利用されます
 * <p>
 * TODO:ボックスタイプで存在しない場合に落ちてる...
 */
public class ScanBarcodeActivity extends Activity implements PermissionsFragment.Listener {

    // スキャンで表示するデータ
    public String mScanDisplayData;
    public JsonNode mConfigMessageText;
    public String mBoxState;    // ScanResultFragment で更新する

    private static final String TAG_PERMISSIONS_FRAGMENT = "permissions";

    private TextView scanInstructionsView;
    private TextView scanPreResultView;
    private TableLayout mTableLayoutPreResult;
    private ScannerFragment.Listener2 mScannerListener;

    private Handler scanLoopHandler;
    private Runnable scanLoopRunnable;

    private String mUserData;
    private String mUserDataName;
    private JsonNode mTargetData;
    private JsonNode mBoxItem;
    private String mDataType;
    private int mScanDelayMillis = 3000;
    private Button mButtonScanComp;

    private ScanResultFragment mScanResultFragment;

    /**
     * アクティビティの開始。ビューと権限を設定します
     *
     * @param savedInstanceState - スーパークラスにパススルーするだけ
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_barcode);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        // Vuzix M400 API はレベル 23 であるため、常にランタイム権限を使用してください
        PermissionsFragment permissionsFragment = (PermissionsFragment) getFragmentManager().findFragmentByTag(TAG_PERMISSIONS_FRAGMENT);
        if (permissionsFragment == null) {
            permissionsFragment = new PermissionsFragment();
            getFragmentManager().beginTransaction().add(permissionsFragment, TAG_PERMISSIONS_FRAGMENT).commit();
        }
        // PermissionsFragment.Listener として登録して、 permissionsGranted() が呼び出されるようにします
        permissionsFragment.setListener(this);

        // 許可が得られるまで指示を非表示にします
        scanInstructionsView = findViewById(R.id.scan_instructions);
        scanInstructionsView.setVisibility(View.GONE);
        scanPreResultView = findViewById(R.id.scan_pre_result);
        scanPreResultView.setVisibility(View.GONE);
        mTableLayoutPreResult = findViewById(R.id.table_layout_scan_pre_result);
        mTableLayoutPreResult.setVisibility(View.GONE);

        creeateScannerListener();

        // 〇秒後処理用のハンドラー
        scanLoopHandler = new Handler(getMainLooper());

        // ユーザーメニュー⇒メインアクティビティからターゲットのデータを渡される
        Intent intent = getIntent();
        mUserData = intent.getExtras().getString("userData");
        Log.d("■ onCreate mUserData ■", mUserData);
        mUserDataName = intent.getExtras().getString("userDataName");
        String targetData = intent.getExtras().getString("targetData");
        mTargetData = CommonUtil.getInstance().convertStringToJson(targetData);
        scanInstructionsView.setText(mTargetData.get("config").get("scanInstructionsMessage").asText());
        mDataType = mTargetData.get("dataType").asText();
        mBoxState = "";
        mScanDelayMillis = mTargetData.get("config").get("scanDelayMillis").asInt();
        // スキャン結果テキストの設定
        mConfigMessageText = mTargetData.get("config").get("messageText");
        CommonUtil.getInstance().configTextView(scanPreResultView, mConfigMessageText);
    }

    /**
     * アクセス許可が付与されたときに呼び出されます。これは、API 23 でスキャナーを表示する唯一の方法です。
     */
    @Override
    public void permissionsGranted() {
        showScanner();
    }

    /**
     * アクティビティのスキャナー フラグメントを表示します
     */
    private void showScanner() {
        if (mBoxState.equals("BoxScan") && Objects.nonNull(mBoxItem)) {
            /// バーコードアクティビティは終了して、ビデオ録画アクティビティを起動する
            finish();
            Intent intent = getIntent();
            //[TECH] intentは別のアクティビティでセットしたデータも残っているわけでない(残っているように見えたが)
            //[TECH] asText()とtoString()は動作が違う Stringを渡したい場合はtoString()(""付きになる)
            intent.putExtra("userData", mUserData);
            Log.d("■ showScanner mUserData ■", mUserData);
            intent.putExtra("userDataName", mUserDataName);
            intent.putExtra("mode", "boxItemList");
            intent.putExtra("boxItem", mBoxItem.toString());
            intent.putExtra("targetData", mTargetData.toString());
            intent.setClassName("co.jp.koyu.arpicking.prototype",
                    "co.jp.koyu.arpicking.VideoHWEncodingAsyncActivity");
            try {
                startActivity(intent);
            } catch (Exception ex) {
                Log.d("StartVideoActitity", "Error", ex);
            }
        } else {
            ///
            /// バーコードアクティビティで引き続きスキャン
            ///

            // 案内メッセージの変更
            if (mDataType.equals("ボックスリスト")) {
                if (mBoxState.equals("BoxItemScan")) {
                    scanInstructionsView.setText(mTargetData.get("config").get("scanInstructionsMessageItem").asText());
                } else {
                    scanInstructionsView.setText(mTargetData.get("config").get("scanInstructionsMessage").asText());
                }
            }

            ScannerFragment scannerFragment = new ScannerFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, scannerFragment).commit();
            scannerFragment.setListener2(mScannerListener);    // スキャン結果を取得するためのリスナー
            scanInstructionsView.setVisibility(View.VISIBLE);  // 指示を画面に戻す

            // 次のスキャンがされるまで、前回の表示結果を表示する
            dispResult(mTableLayoutPreResult);
        }
    }

    private void creeateScannerListener() {
        try {
            /**
             * これは単純なラッパー クラスです。
             *
             * MainActivity を直接実装するのではなく、これを行います。
             * ScannerFragment.Listener により、NoClassDefFoundError を適切にキャッチできます。
             * (M シリーズで実行していない場合)
             */
            class OurScannerListener implements ScannerFragment.Listener2 {
                @Override
                public void onScan2Result(Bitmap bitmap, ScanResult2[] results) {
                    onScanFragmentScanResult(bitmap, results);
                }

                @Override
                public void onError() {
                    onScanFragmentError();
                }
            }

            mScannerListener = new OurScannerListener();

        } catch (NoClassDefFoundError e) {
            // この例外は、コンパイルした SDK スタブを解決できない場合に発生します。
            // 実行時に、音声をサポートする M400 でコードが実行されていない場合に発生します。
            Toast.makeText(this, R.string.only_on_mseries, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * このコールバックにより、スキャン結果が得られます。これは mScannerListener.onScanResult を通じて中継されます。
     * <p>
     * このサンプルは、結果を画面に表示するヘルパークラスを呼び出します
     *
     * @param bitmap  -  バーコードが見つかったビットマップ
     * @param results -  スキャン結果の配列
     */
    private void onScanFragmentScanResult(Bitmap bitmap, ScanResult2[] results) {
        ScannerFragment scannerFragment = (ScannerFragment) getFragmentManager().findFragmentById(R.id.fragment_container);
        scannerFragment.setListener2(null);
        showScanResult(bitmap, results[0]);
    }

    /**
     * このコールバックにより、スキャン エラー発生時に呼び出されます。これは mScannerListener.onError を通じて中継されます
     * <p>
     * 少なくとも、スキャナー フラグメントをアクティビティから削除する必要があります。
     * 他に機能がないため、アクティビティ全体を閉じます。
     */
    private void onScanFragmentError() {
        finish();
        Toast.makeText(this, R.string.scanner_error_message, Toast.LENGTH_LONG).show();
    }

    /**
     * スキャン結果を表示するヘルパー メソッド
     *
     * @param bitmap -  バーコードが見つかったビットマップ
     * @param result -  スキャン結果の配列
     */
    private void showScanResult(Bitmap bitmap, ScanResult2 result) {
        scanPreResultView.setVisibility(View.GONE);
        scanInstructionsView.setVisibility(View.GONE);
        mTableLayoutPreResult.setVisibility(View.GONE);
        mScanResultFragment = new ScanResultFragment
                (mTargetData.get("config").get("scanCompButtonText").asText(), mDataType);
        Bundle args = new Bundle();
        args.putParcelable(ScanResultFragment.ARG_BITMAP, bitmap);
        args.putParcelable(ScanResultFragment.ARG_SCAN_RESULT, result);
        String code = result.getText();
        Log.d("スキャン結果", code);

        ///
        /// ARピッキング独自処理
        ///
        JsonNode data = getDisplayData(code);
        if (data != null) {
            if (mDataType.equals("ボックスリスト")) {
                if (mBoxState.equals("BoxItemScan")) {
                    // 個々のアイテムをスキャン⇒テキスト表示
                    Log.d("showScanResult", data.get("scanDispData").asText());
                    args.putString(ScanResultFragment.ARG_DISPLAY_AREA, data.get("scanDispData").asText());
                } else {
                    // リスト表示のヘッダーを渡す 文字列で渡して ScanResultFragment で JsonNode にデコード
                    args.putString(ScanResultFragment.ARG_DISPLAY_HEADER, mTargetData.get("config").get("listHeader").toString());
                    // リスト表示のデータを渡す 文字列で渡して ScanResultFragment で JsonNode にデコード
                    args.putString(ScanResultFragment.ARG_DISPLAY_LIST, data.get("scanDispList").toString());
                }
            } else {
                // テキスト表示
                Log.d("showScanResult", data.get("scanDispData").asText());
                args.putString(ScanResultFragment.ARG_DISPLAY_AREA, data.get("scanDispData").asText());
            }

            // 結果のフラグメント表示
            beep();
            mScanResultFragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mScanResultFragment).commit();
        } else {
            //TODO:データがなかったらスキャン継続のパターンを追加中

            // データなしを表示のパターン
            args.putString(ScanResultFragment.ARG_DISPLAY_AREA, "[データなし]\n" + code);
            // 結果のフラグメント表示
            beep();
            mScanResultFragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mScanResultFragment).commit();

            // データなしの場合、そのままスキャン状態にする
            //TODO:直接関係ないが、視ている大きさとVuzixでの大きさの違いが気になる⇒CameraのZoomで合わせられるように出来る？
            //TODO:いったん暗くなるのが目立つようになった...
            // showScanner();
        }

        // x秒毎に再スキャン⇒ボックスタイプの場合は自動的に次のアイテム一覧表示へ シングルの場合は決定ボタンで静止画
        if (mDataType.equals("ボックス")) {
            scanLoopHandler.postDelayed(scanLoopRunnable = new Runnable() {
                // Runnable型のインスタンス化と定義
                @Override
                public void run() {
                    showScanner();
                }
            }, mScanDelayMillis);
        }
    }

    /**
     * スキャン完了 決定ボタンクリック時の処理
     */
    public void scanComp(View view) {
        view.setVisibility(View.GONE);
        showScanner();
    }

    /**
     * 該当コードの表示内容の取得
     *
     * @return
     */
    private JsonNode getDisplayData(String code) {
        if (mTargetData != null) {
            if (mDataType.equals("シングル")) {
                // スキャンされたバーコードからそのまま表示
                JsonNode itemList = mTargetData.get("itemList");
                if (Objects.nonNull(itemList)) {
                    for (JsonNode item : itemList) {
                        if (item.get("scanCode").asText().equals(code)) {
                            Log.d("一致！", code);
                            return item;
                        }
                    }
                }
            }

            if (mDataType.equals("ボックス")) {
                // ボックスタイプ スキャンされたバーコードから表示⇒以降 アイテムリストを表示
                JsonNode boxList = mTargetData.get("boxList");
                if (Objects.nonNull(boxList)) {
                    for (JsonNode box : boxList) {
                        JsonNode boxItem = box.get("boxItem");
                        if (boxItem.get("scanCode").asText().equals(code)) {
                            Log.d("一致！", code);

                            // ボックス用のアイテムリストにセットして、スキャンループを終了する
                            mBoxItem = box;
                            scanLoopHandler.removeCallbacksAndMessages(null);
                            return boxItem;
                        }
                    }
                }
            }

            if (mDataType.equals("ボックスリスト")) {
                if (mBoxState.equals("BoxItemScan")) {
                    // boxItem の　itemList から取得
                    if (Objects.nonNull(mBoxItem)) {
                        JsonNode itemList = mBoxItem.get("itemList");
                        if (itemList != null) {
                            for (JsonNode item : itemList) {
                                if (item.get("scanCode").asText().equals(code)) {
                                    Log.d("一致！", code);
                                    return item;
                                }
                            }
                        }
                    }
                } else {
                    // ボックスリストの場合も、リスト表示の場合はボックスと同じく boxItem を返す
                    JsonNode boxList = mTargetData.get("boxList");
                    if (Objects.nonNull(boxList)) {
                        for (JsonNode box : boxList) {
                            JsonNode boxItem = box.get("boxItem");
                            if (boxItem.get("scanCode").asText().equals(code)) {
                                Log.d("一致！", code);

                                // ボックス用のアイテムリストにセットして、スキャンループを終了する
                                mBoxItem = box;
                                scanLoopHandler.removeCallbacksAndMessages(null);
                                return boxItem;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * スキャン操作中の音声フィードバック。ピピッと鳴ります。
     */
    private void beep() {
        MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }
        });
        try {
            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            file.close();
            player.setVolume(.1f, .1f);
            player.prepare();
            player.start();
        } catch (IOException e) {
            player.release();
        }
    }

    /**
     * 結果フラグメントからスキャナフラグメントに戻るか、スキャナからアプリを終了するか制御
     */
    @Override
    public void onBackPressed() {
        // Log.d("scanLoopRunnable", scanLoopRunnable.toString());

        // スキャンループを終了させる
        // [TECH] RunnableのscanLoopRunnableを指定してもうまく終了しない。ハンドラーはスキャンループ専用なので、全て削除
        scanLoopHandler.removeCallbacksAndMessages(null);
        super.onBackPressed();
    }

    /**
     * スキャナー結果のフラグメントが表示されているかどうかを判断する
     *
     * @return 表示されている場合はtrue
     */
    private boolean isScanResultShowing() {
        return getFragmentManager().findFragmentById(R.id.fragment_container) instanceof ScanResultFragment;
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d("onKeyUp", Integer.toString(keyCode) + " " + mBoxState);
        // ボックスリスト表示中はボタンで制御
        // TODO:全般的にボタンでも制御できるように対応が必要か、現状BoxListの場合のみ対応
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // モニター側ボタン 「進む」
                if (Objects.nonNull(mScanResultFragment)) {
                    if (mScanResultFragment.isVisible()) {
                        if (mDataType.equals("ボックスリスト")) {
                            if (mBoxState.equals("BoxListScanned")) {
                                mScanResultFragment.dispBoxListNext();
                            }
                        }
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 真ん中のボタン 「戻る」
                if (Objects.nonNull(mScanResultFragment)) {
                    if (mScanResultFragment.isVisible()) {
                        if (mDataType.equals("ボックスリスト")) {
                            if (mBoxState.equals("BoxListScanned")) {
                                mScanResultFragment.dispBoxListPrev();
                            } else if (mBoxState.equals("BoxItemScan")) {
                                // 初期状態(リストスキャン)に戻る
                                mBoxState = "";
                                showScanner();
                            }
                        }
                    } else {
                        // スキャン画面表示中
                        // 初期状態(リストスキャン)に戻る
                        mBoxState = "";
                        showScanner();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // 手前のボタン 「決定」
                if (Objects.nonNull(mScanResultFragment)) {
                    if (mScanResultFragment.isVisible()) {
                        if (mDataType.equals("ボックスリスト")) {
                            if (mBoxState.equals("BoxListScanned")) {
                                mBoxState = "BoxItemScan";
                                showScanner();
                            } else if (mBoxState.equals("BoxItemScan")) {
                                showScanner();
                            }
                        }
                    }
                }
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    /**
     * 結果表示メソッド
     * <p>
     * スキャン中にも表示するため、Atvitity側に作成
     */
    public void dispResult(TableLayout tableLayout) {
        JsonNode configMessageList = mTargetData.get("config").get("messageList");
        if (Objects.nonNull(mScanDisplayData)) {
            if (Objects.isNull(configMessageList)) {
                // そのまま表示
                scanPreResultView.setText(mScanDisplayData);
                scanPreResultView.setVisibility(View.VISIBLE);
                return;
            }
        } else {
            // 表示するデータがない
            return;
        }

        //
        // テーブルレイアウトで表示
        //

        tableLayout.setVisibility(View.VISIBLE);
        Context context = tableLayout.getContext();
        tableLayout.removeAllViews();
        tableLayout.setBackgroundColor(Color.parseColor(configMessageList.get("backgroundColor").asText()));

        // 行のレイアウト
        TableLayout.LayoutParams trParams = new
                TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT);

        // "`br`"は改行とみなす
        String[] array = mScanDisplayData.split("`br`");
        for (int i = 0; i < array.length; i++) {
            JsonNode config = configMessageList.get("tr").get(i);
            final TableRow tr = new TableRow(context);
            tr.setId(i);
            tr.setLayoutParams(trParams);
            JsonNode margin = config.get("margin");
            tr.setPadding(
                    margin.get("left").asInt(),
                    margin.get("top").asInt(),
                    margin.get("right").asInt(),
                    margin.get("bottom").asInt());

            TableRow.LayoutParams cellParams = new
                    TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT);
            JsonNode cellPading = config.get("cellPadding");

            final TextView tv = new TextView(context);
            tv.setLayoutParams(cellParams);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(
                    cellPading.get("left").asInt(),
                    cellPading.get("top").asInt(),
                    cellPading.get("right").asInt(),
                    cellPading.get("bottom").asInt());
            tv.setText(array[i]);
            tv.setTextColor(Color.parseColor(config.get("textColor").asText()));
            tv.setBackgroundColor(Color.parseColor(config.get("backgroundColor").asText()));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, config.get("size").asInt());

            tr.addView(tv);

            tableLayout.addView(tr, trParams);
        }
    }
}
