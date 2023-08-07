package co.jp.koyu.arpicking;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.vuzix.sdk.barcode.ScannerIntent;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainFragment#newInstance} factory method to
 * フラグメントインスタンス生成
 */
public class MainFragment extends Fragment {
    private static final int REQUEST_CODE_SCAN = 90002;

    private Button mButtonDemoLogin;
    private Button mButtonQRLogin;

    public MainFragment() {
    }

    /**
     * フラグメントインスタンス新規生成
     *
     * @return ブランクのフラグメントインスタンス
     */
    public static MainFragment newInstance() {
        MainFragment fragment = new MainFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        /// イベント設定
        mButtonDemoLogin = (Button) getActivity().findViewById(R.id.btn_demo_const_login);
        // mButtonDemoLogin.requestFocusFromTouch(); [TECH] この命令を入れるとフォーカスのセットがうまくいかん
        mButtonDemoLogin.requestFocus();
        mButtonDemoLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                OnDemoLoginClick(view, false);
            }
        });
        mButtonQRLogin = (Button) getActivity().findViewById(R.id.btn_demo_cloud_login);
        mButtonQRLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                OnDemoLoginClick(view, true);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // フラグメントを拡張する
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    /**
     * デモログインボタン押下時
     * @param view
     * @param isCloudDemo デモログイン(クラウド利用)の場合 true
     */
    public void OnDemoLoginClick(View view, boolean isCloudDemo) {
        ///
        /// 設定データ・トランデータ取得
        ///
        MainActivity maActivity = (MainActivity) getActivity();

        if (isCloudDemo) {
            if (CommonUtil.getInstance().isAvailableNetwork(getActivity())) {
                // ネットワーク利用可　envs.xmlに設定あり
            } else {
                // ネットワーク利用不可
                CommonUtil.getInstance().showShortSnackBar(view, "ネットワークが利用できません。デモ用の固定データをセットします。", R.color.warning);
                maActivity.mIsUseMockup = true;
            }
        } else {
            maActivity.mIsUseMockup = true;
        }

        /// パラメータ カスタマーID、ユーザーIDは1固定、日付は現在日付
        //TODO:パラメータはデモ用で固定になっている ログイン実装時に修正する必要あり
        String now = CommonUtil.getInstance().getDate(null, null);
        String url = getString(R.string.app_url) +
                "/api/user/data/all" +
                "?hdn_customerId=1" +
                "&hdn_userId=1" +
                "&hdn_startDate=20230101" + // + now +
                "&hdn_endDate=20231231"; // + now;

        // ユーザーメニューの表示
        if (maActivity.mIsUseMockup) {
            AsyncCenterMockup centerMockup = new AsyncCenterMockup(
                    (MainActivity) getActivity(),"showUserMenuFragment", url, view);
            centerMockup.execute();
        } else {
            AsyncCenterRequest centerRequest = new AsyncCenterRequest(
                    (MainActivity) getActivity(),"showUserMenuFragment", url, view);
            centerRequest.execute();
        }
    }

    /**
     * QRログインボタン押下時
     */
    private void OnQRLoginClick() {
        // メインアクティビティのメソッドを呼び出す
        MainActivity maActivity = (MainActivity) getActivity();
        maActivity.scanQRLogin();

        Intent scannerIntent = new Intent(ScannerIntent.ACTION);
        try {
            // Vuzixに登録されているスキャナアプリを呼び出す
            startActivityForResult(scannerIntent, REQUEST_CODE_SCAN);
        } catch (ActivityNotFoundException activityNotFound) {
            Toast.makeText(getActivity(), R.string.only_on_mseries, Toast.LENGTH_LONG).show();
        }
    }

}