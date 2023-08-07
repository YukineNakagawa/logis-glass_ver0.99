package co.jp.koyu.arpicking;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.vuzix.sdk.barcode.ScanResult2;

/**
 * A fragment to show the result of the barcode scan
 * Vuzixのサンプルを元に修正 BY FUKEHARA
 */
@SuppressLint("ValidFragment")
public class ScanResultFragment extends Fragment {

    public static final String ARG_BITMAP = "bitmap";
    public static final String ARG_SCAN_RESULT = "scan_result";
    public static final String ARG_DISPLAY_AREA = "display_area";
    public static final String ARG_DISPLAY_HEADER = "display_header";
    public static final String ARG_DISPLAY_LIST = "display_list";
    public static final String ARG_DISPLAY_CONFIG = "display_config";

    private Button mButtonScanComp;
    private String mScanCompButtonText;
    private String mDataType;
    private TableLayout mTableLayoutScanDisp;

    private JsonNode mDispHeader;
    private JsonNode mDispList;
    private int mDispStartNext = 0;
    private int mDispEndNext = 0;
    private int mDispStartPrev = 0;
    private int mDispEndPrev = 0;

    @SuppressLint("ValidFragment")
    public ScanResultFragment(String buttonText, String dataType) {
        mScanCompButtonText = buttonText;
        mDataType = dataType;
    }

    /**
     * Inflate the correct layout upon creation
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment,
     * @param container          If non-null, this is the parent view that the fragment's UI should be attached to.
     *                           The fragment should not add the view itself, but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     * @return - Returns the View for the fragment's UI, or null.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    /**
     * ビューが作成されたら、スキャン結果を含む画像を表示します
     *
     * @param view               - The new view
     * @param savedInstanceState - required argument that we ignore
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ScanBarcodeActivity activity = (ScanBarcodeActivity) getActivity(); // ADD BY FUKE
        ScanResultImageView bitmap = (ScanResultImageView) view.findViewById(R.id.bitmap);
        TextView text = (TextView) view.findViewById(R.id.text);
        CommonUtil.getInstance().configTextView(text, activity.mConfigMessageText); // ADD BY FUKE

        // 引数 Bundle は、バーコードの認識時に取得されたビットマップを提供し、
        // 画像内のバーコードから抽出されたテキスト
        Bundle args = getArguments();
        if (args != null) {
            ScanResult2 scanResult = args.getParcelable(ARG_SCAN_RESULT);
            bitmap.setImageBitmap((Bitmap) args.getParcelable(ARG_BITMAP));
            bitmap.setLocation(scanResult.getResultPoints());

            // リスト表示はいったんクリア
            mTableLayoutScanDisp = (TableLayout) getActivity().findViewById(R.id.table_layout_scan_disp);
            mTableLayoutScanDisp.removeAllViews();

            boolean isDispList = false;
            if (Objects.nonNull(args.getString(ScanResultFragment.ARG_DISPLAY_HEADER))) {
                if (mDataType.equals("ボックスリスト")) {
                    if (!activity.mBoxState.equals("BoxItemScan")) {
                        // リスト表示
                        isDispList = true;
                    }
                }
            }
            if (isDispList) {
                // リスト表示
                mDispHeader = CommonUtil.getInstance().convertStringToJson(args.getString(ScanResultFragment.ARG_DISPLAY_HEADER));
                mDispList = CommonUtil.getInstance().convertStringToJson(args.getString(ScanResultFragment.ARG_DISPLAY_LIST));
                dispBoxList(0, 0);
            } else {
                // 表示データの取得 ScanBarcodeActivity.showScanResult⇒onViewCreated
                String displayData = args.getString(ScanResultFragment.ARG_DISPLAY_AREA);
                // スキャン内容を保持するため Activity 側にセットして表示する
                activity.mScanDisplayData = displayData;
                activity.dispResult(mTableLayoutScanDisp);
            }

            ///
            /// スキャン決定ボタンを表示する
            /// ※ボックスデータタイプ時は表示せず アイテム表示へ
            /// ※ボックスリストデータタイプ時は各アイテムスキャン時は表示する スキャンしてリスト表示⇒各アイテムのスキャンへ
            ///
            ScanBarcodeActivity scanActivity = (ScanBarcodeActivity) getActivity();
            mButtonScanComp = (Button) getActivity().findViewById(R.id.btn_scan_comp);
            boolean isVisibleComp = true;
            if (mDataType.equals("ボックス")) {
                isVisibleComp = false;
            } else if (mDataType.equals("ボックスリスト")) {
                if (isDispList) {
                    isVisibleComp = false;
                }
            }
            if (isVisibleComp) {
                // mButtonDemoLogin.requestFocusFromTouch(); [TECH] この命令を入れるとフォーカスのセットがうまくいかん
                mButtonScanComp.requestFocus();
                mButtonScanComp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) throws IllegalArgumentException,
                            SecurityException, IllegalStateException {
                        scanActivity.scanComp(mButtonScanComp);
                    }
                });
                mButtonScanComp.setText(mScanCompButtonText);
            } else {
                mButtonScanComp.setVisibility(View.GONE);
            }

            ///
            /// ScanBarcodeActivity の mBoxState の更新
            ///
            if (mDataType.equals("ボックス")) {
                if (!activity.mBoxState.equals("BoxScan")) {
                    activity.mBoxState = "BoxScan";
                }
            } else if (mDataType.equals("ボックスリスト")) {
                if (!activity.mBoxState.equals("BoxItemScan")) {
                    activity.mBoxState = "BoxListScanned";
                }
            }
        }
    }

    public void dispBoxListNext() {
        Log.d("■dispBoxListNext■", mDispStartNext + "～" + mDispEndNext);
        if (mDispStartNext == 0 && mDispEndNext == 0) {
            // 次ページなし そのまま表示
        } else {
            mTableLayoutScanDisp.removeAllViews();
            dispBoxList(mDispStartNext, mDispEndNext);
        }
    }

    public void dispBoxListPrev() {
        Log.d("■dispBoxListPrev■", mDispStartPrev + "～" + mDispEndPrev);
        if (mDispStartPrev == 0 && mDispEndPrev == 0) {
            // 前ページなし そのまま表示
        } else {
            mTableLayoutScanDisp.removeAllViews();
            dispBoxList(mDispStartPrev, mDispEndPrev);
        }
    }

    /**
     * リスト表示メソッド
     */
    public void dispBoxList(int dispStart, int dispEnd) {
        int cellPaddingLeft = 5, cellPaddingTop = 8, cellPaddingRight = 5, cellPaddingBottom = 8;
        int textSize = (int) getResources().getDimension(R.dimen.font_size_verysmall);
        int smallTextSize = (int) getResources().getDimension(R.dimen.font_size_small);
        int mediumTextSize = (int) getResources().getDimension(R.dimen.font_size_medium);

        Context context = mTableLayoutScanDisp.getContext();
        mTableLayoutScanDisp.setBackgroundColor(Color.argb(100, 0, 0, 255));

        // 行のレイアウト
        TableLayout.LayoutParams trParams = new
                TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT);
        trParams.setMargins(2, 0, 2, 0);

        //
        // ヘッダーのセット
        //
        final TableRow tr_h = new TableRow(context);
        tr_h.setId((int) 1);
        tr_h.setLayoutParams(trParams);
        for (JsonNode header : mDispHeader) {
            Log.d("■dispBoxList■ header", header.toString());

            final TextView tv = new TextView(context);
            tv.setLayoutParams(new
                    TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(cellPaddingLeft, cellPaddingTop, cellPaddingRight, cellPaddingBottom);
            tv.setText(header.get("caption").asText());
            tv.setTextColor(Color.WHITE);
            tv.setBackgroundColor(Color.argb(220, 50, 50, 255));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize);

            tr_h.addView(tv);
        }
        mTableLayoutScanDisp.addView(tr_h, trParams);

        //
        // データのセット
        //
        int count_data_row = 1;
        int start = dispStart;
        int end = dispEnd;
        if (end == 0) {
            if (mDispList.size() > 5) {
                end = 5;
            } else {
                end = mDispList.size();
            }
        }
        for (int i = start; i < end; i++) {
            JsonNode row = mDispList.get(i);
            Log.d("■dispBoxList■ list", row.toString());

            final TableRow tr = new TableRow(context);
            tr.setId(count_data_row);
            tr.setLayoutParams(trParams);

            JsonNode cols = CommonUtil.getInstance().convertStringToJson(row.toString());
            for (JsonNode col : cols) {
                // Log.d("■dispBoxList■ list col", col.toString());

                final TextView tv = new TextView(context);
                tv.setLayoutParams(new
                        TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                        TableRow.LayoutParams.WRAP_CONTENT));
                tv.setGravity(Gravity.LEFT);
                tv.setPadding(cellPaddingLeft, cellPaddingTop, cellPaddingRight, cellPaddingBottom);
                tv.setText(col.asText());
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(Color.argb(220, 255, 255, 255));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mediumTextSize);

                tr.addView(tv);
            }

            mTableLayoutScanDisp.addView(tr, trParams);

            count_data_row++;
        }

        //
        // フッター(ページ)の表示
        //
        final TableRow tr_f = new TableRow(context);
        tr_f.setId(count_data_row + 1);
        tr_f.setPadding(0, 2, 0, 0);
        tr_f.setLayoutParams(trParams);

        final TextView tv_f = new TextView(context);
        TableRow.LayoutParams params_f = new
                TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT);
        params_f.span = mDispHeader.size();
        tv_f.setLayoutParams(params_f);
        tv_f.setGravity(Gravity.CENTER);
        tv_f.setPadding(cellPaddingLeft, cellPaddingTop, cellPaddingRight, cellPaddingBottom);
        // [TECH] 整数同士の除算の結果は余りがあったとしても、整数になってしまう。どちらかを小数点数にすること
        int total = (int) Math.ceil(mDispList.size() / 5d);
        int page = (dispStart / 5) + 1;
        if (mDispList.size() > 0) {
            tv_f.setText("ページ " + page + "/" + total);
        } else {
            tv_f.setText("データがありません。");
        }
        tv_f.setTextColor(Color.WHITE);
        tv_f.setBackgroundColor(Color.argb(220, 100, 100, 255));
        tv_f.setTextSize(TypedValue.COMPLEX_UNIT_PX, mediumTextSize);
        tr_f.addView(tv_f);

        mTableLayoutScanDisp.addView(tr_f, trParams);

        //
        // 前後情報のセット
        //
        if (page < total) {
            // 次のページあり
            mDispStartNext = 5 * page;
            if ((page + 1) == total) {
                // 次が最終ページ
                mDispEndNext = mDispList.size();
            } else {
                // 次が途中のページ
                mDispEndNext = mDispStartNext + 5;
            }
        } else {
            // 最終ページを表示している
            mDispStartNext = 0;
            mDispEndNext = 0;
        }
        if (page > 1) {
            // 前のページあり
            mDispStartPrev = (5 * (page - 1)) - 5;
            if ((page - 1) == 1) {
                // 前が最初ページ
                mDispEndPrev = 5;
            } else {
                // 前が途中のページ
                mDispEndPrev = mDispStartPrev + 5;
            }
        } else {
            // 最初ページを表示している
            mDispStartPrev = 0;
            mDispEndPrev = 0;
        }
    }
}
