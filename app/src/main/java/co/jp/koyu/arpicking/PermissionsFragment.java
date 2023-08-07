package co.jp.koyu.arpicking;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * ランタイム権限をカプセル化するフラグメント
 */
@TargetApi(23)
public class PermissionsFragment extends Fragment {

    private static final int REQUEST_CODE_PERMISSIONS = 0;

    protected Listener listener;

    /**
     * 初期化。ビューを設定する。
     * @param savedInstanceState - スーパークラスにパススルーするだけ
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        requestPermissions();
    }

    /**
     * システムにアクセス許可を要求する
     */
    private void requestPermissions() {
        if (getContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionsGranted();
        } else {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSIONS);
        }
    }

    /**
     * アクセス許可が付与されたときに呼び出されます。パーミッション リスナーに通知します。
     */
    private synchronized void permissionsGranted() {
        if (listener != null) {
            listener.permissionsGranted();
        }
    }


    /**
     * permissionsGranted() を呼び出すリスナーを設定します
     * @param listener リスナーへのポインタ
     */
    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * 実行時のアクセス許可を要求するアクティビティに必要なインターフェイス
     *
     * @see <a href="https://developer.android.com/training/permissions/requesting.html">https://developer.android.com/training/permissions/requesting.html</a>
     * @param requestCode int: requestPermissions(android.app.Activity, String[], int)に渡されるリクエストコード
     * @param permissions String: 要求する権限。NULL NG
     * @param grantResults int: PERMISSION_GRANTED または PERMISSION_DENIED アクセス許可の付与結果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_PERMISSIONS:
                if (permissions.length == 1) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        permissionsGranted();  // 権限がユーザーによって付与された
                    } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        requestPermissions();  // もう一度許可を求める
                    } else {
                        // 許可されなかった。ユーザーにヒントを与えて終了する。
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        getActivity().finish();
                        Toast.makeText(getContext(), R.string.grant_camera_permission, Toast.LENGTH_LONG).show();
                    }
                }
                return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * リスナーのインターフェース
     */
    interface Listener {
        void permissionsGranted();
    }
}
