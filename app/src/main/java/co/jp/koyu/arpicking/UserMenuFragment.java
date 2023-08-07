package co.jp.koyu.arpicking;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ユーザーメニュー用のフラグメント
 */
public class UserMenuFragment extends Fragment {
    private TextView mTextUserName;
    private Button[] mButtonsUserMenu = new Button[9];

    private static final String ARG_PARAM1 = "userData";
    private String mParam1;

    public UserMenuFragment() {
    }

    /**
     * フラグメントインスタンス新規生成
     *
     * @return ブランクのフラグメントインスタンス
     */
    public static UserMenuFragment newInstance(JsonNode userData) {
        UserMenuFragment fragment = new UserMenuFragment();

        // [TECH] Bundleを使用すると、Activityが勝手に破棄されても保持されている
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, userData.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
        }
    }

    private void getServerData() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // フラグメントを拡張する
        return inflater.inflate(R.layout.fragment_user_menu, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        JsonNode userData = CommonUtil.getInstance().convertStringToJson(mParam1);

        /// ユーザー名の表示
        mTextUserName = (TextView) getActivity().findViewById(R.id.textUserName);
        mTextUserName.setText(userData.get("userName").asText());

        /// ボタン設定
        int index = 0;
        for (JsonNode item : userData.get("userMenuData")) {
            setUserMenuButton(index, item);
            index++;
        }
    }

    /**
     * ボタンを設定する
     *
     * @param index
     * @param item
     */
    private void setUserMenuButton(int index, JsonNode item) {
        int userMenuId = getResources().getIdentifier("btn_user_menu_" + index,
                "id", getActivity().getPackageName());
        mButtonsUserMenu[index] = (Button) getActivity().findViewById(userMenuId);
        if (index == 0) {
            mButtonsUserMenu[index].requestFocus();
        }
        mButtonsUserMenu[index].setText(item.get("title").asText());
        mButtonsUserMenu[index].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                OnUserMenuClick(index, view);
            }
        });
    }

    /**
     * ユーザーメニューボタン押下時
     * ユーザーメニューデータに応じてクリック処理を行う
     */
    private void OnUserMenuClick(int index, View view) {
        JsonNode userData = CommonUtil.getInstance().convertStringToJson(mParam1);
        JsonNode menu = userData.get("userMenuData").get(index);

        String activityName = menu.get("activityName").asText();
        String dataName = menu.get("dataName").asText();
        MainActivity maActivity = (MainActivity) getActivity();
        switch (activityName) {
            case "ScanBarcodeActivity":
                /// スキャンバーコードアクティビティ開始
                maActivity.startScanActivity(mParam1, dataName);
                break;
            case "VideoHWEncodingAsyncActivity":
                /// ビデオ録画アクティビティ開始
                maActivity.startVideoActivity(mParam1, dataName, menu.get("title").asText());
                break;
            case "VideoUploadAsyncTask":
                /// ビデオアップロード
                // テスト用にビデオデータをDownloadsフォルダからvideo_encoderフォルダにコピーするには
                // エミュレータのFilesアプリを使用します。
                videoUpload(maActivity, view);
                break;
        }
    }

    /**
     * 録画データアップロード
     *
     * @param maActivity
     * @param view
     */
    private void videoUpload(Activity maActivity, View view) {
        String url = getString(R.string.app_url) + "/api/video/upload";

        new VideoUploadAsyncTask(maActivity, view).execute(url);
    }
}