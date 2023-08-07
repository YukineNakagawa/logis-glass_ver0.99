package co.jp.koyu.arpicking;

import android.annotation.SuppressLint;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import co.jp.koyu.arpicking.databinding.ActivityVideoHwencodingAsyncBinding;

// public class VideoHWEncodingAsyncActivity extends AppCompatActivity {
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * このサンプルでは、H.264 ビデオ エンコードにハードウェア エンコーダーを使用する方法を示します。
 * ハードウェアエンコーダーの使用により、キャプチャーパフォーマンスが向上し、
 * CPU 負荷が低くなり、エンコーダーからの発熱が少なくなります。
 * <p>
 * ※このサンプルは非同期API(コールバックメソッド)を使用しています
 */
public class VideoHWEncodingAsyncActivity extends Activity implements RotationListener.rotationCallbackFn {

    private static final String TAG = "MediaCodec_App";
    private Button mRecordButton;
    private TextureView mTextureView;
    private String mCameraId;
    protected CameraDevice mCameraDevice;
    protected CameraCaptureSession mCameraCaptureSessions;
    protected CaptureRequest.Builder mCaptureRequestBuilder;
    private ImageReader mImageReader;
    private RotationListener mRotationListener;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundPreviewHandler;
    private HandlerThread mBackgroundPreviewThread;
    private Handler mBackgroundCodecHandler;
    private HandlerThread mBackgroundCodecThread;

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    // parameters for the encoder
    private static final int FRAME_RATE = 24;               // 24fps
    private static final int IFRAME_INTERVAL = 5;           // Iフレーム(キーフレーム)の間隔 5秒
    private static final int MAX_QUEUE_ELEMENTS = 8;

    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final String ENCODER = "OMX.qcom.video.encoder.avc"; // ハードウェアエンコーダー
    //private static final String ENCODER = "OMX.google.h264.encoder";  // ソフトウェアエンコーダー

    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private MediaFormat mEncoderFormat = null;

    private LinkedList<byte[]> mBytesQueue;
    private Object mBytesQueueLock = new Object();
    private int mQueueElementCount;

    private boolean mVideoRecording = false;
    private boolean mMuxerStarted;
    private boolean mCaptureSessionStopped;

    private int mTrackIndex;
    private long mFramesIndex;

    // 録画用 ADD BY FUKEHARA
    private String mCustomerId;
    private String mUserId;
    private String mDataName;
    private TextView mMessageText;
    private String mTitle;
    // 共通 モード・設定 ADD BY FUKEHARA
    private String mUserData;
    private String mUserDataName;
    private String mMode;
    private JsonNode mConfig;
    private JsonNode mTargetData;
    // ボックスアイテム表示用
    private JsonNode mBoxItemList;
    private int mBoxItemIndex;
    private String mCompMessage;
    private Button mNextButton;
    private Button mPrevButton;

    /**
     * アクティビティの開始。ビューを設定します
     *
     * @param savedInstanceState - スーパークラスにパススルーするだけ
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_hwencoding_async);

        mRecordButton = (Button) findViewById(R.id.btn_record);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecordOrStopClick();
            }
        });
        mRotationListener = new RotationListener();

        //
        // インテントから値取得 ADD BY FUKEHARA
        // 録画モードとボックスアイテム表示モードと分かれる
        //
        Intent intent = getIntent();
        mUserData = intent.getExtras().getString("userData");
        Log.d("■ VideoHWEncodingAsyncActivity mUserData ■", mUserData);
        mUserDataName = intent.getExtras().getString("userDataName");
        String targetData = intent.getExtras().getString("targetData");
        mTargetData = CommonUtil.getInstance().convertStringToJson(targetData);
        mMode = intent.getExtras().getString("mode");
        mConfig = mTargetData.get("config");
        mNextButton = (Button) findViewById(R.id.btn_next);
        mPrevButton = (Button) findViewById(R.id.btn_prev);
        mMessageText = (TextView) findViewById(R.id.text_message);
        CommonUtil.getInstance().configTextView(mMessageText, mConfig.get("messageText"));
        if (mMode.equals("boxItemList")) {
            // 録画ボタン非表示
            mRecordButton.setVisibility(View.GONE);

            // 設定
            mPrevButton.setText(mConfig.get("prevButtonText").asText());
            mNextButton.setText(mConfig.get("nextButtonText").asText());

            // アイテムリストセット
            String boxItem = intent.getExtras().getString("boxItem");
            JsonNode json = CommonUtil.getInstance().convertStringToJson(boxItem);
            mBoxItemList = json.get("itemList");
            mBoxItemIndex = 0;
            if (mBoxItemList == null) {
                mMessageText.setText(mConfig.get("compMessage").asText());
            } else {
                if (mBoxItemList.size() <= 1) {
                    // 1件以下の場合
                    mNextButton.setEnabled(false);
                    mPrevButton.setEnabled(false);
                } else {
                    mPrevButton.setEnabled(false);
                    mNextButton.requestFocus();
                    JsonNode item = mBoxItemList.get(mBoxItemIndex);
                    mMessageText.setText(item.get("itemDispData").asText());
                }
            }
            mNextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBoxNextClick();
                }
            });
            mPrevButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBoxPrevClick();
                }
            });
        } else {
            mCustomerId = intent.getExtras().getString("customerId");
            mUserId = intent.getExtras().getString("userId");
            mDataName = intent.getExtras().getString("dataName");
            mRecordButton.setText(mConfig.get("recButtonText").asText());

            // ボックスリスト用を非表示に
            mNextButton.setVisibility(View.GONE);
            mPrevButton.setVisibility(View.GONE);
        }
    }

    /**
     * ボックスタイプの場合、前のアイテムリストに戻ってしまうので、
     * デフォルトのonBackPressedではなく、ユーザーメニューに戻るように修正
     */
    @Override
    public void onBackPressed() {
        // super.onBackPressed();
        finish();

        Intent intent = new Intent();
        intent.putExtra("userData", mUserData);
        intent.setClassName("co.jp.koyu.arpicking.prototype",
                "co.jp.koyu.arpicking.MainActivity");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Log.d("MainActivity", "Error", ex);
        }

        // TODO:たまにスキャンできなくなる (こことは関係なし)
    }

    ///
    /// ボックスアイテム表示モード用 Start
    ///
    private void onBoxNextClick() {
        if (mNextButton.getText().equals(mConfig.get("scanCompButtonText").asText())) {
            // 決定のボタン時はスキャンアクティビティを起動 ターゲットデータを渡す
            Intent intent = new Intent();
            intent.putExtra("userData", mUserData);
            Log.d("■ onBoxNextClick mUserData ■", mUserData);
            intent.putExtra("userDataName", mUserDataName);
            intent.putExtra("targetData", mTargetData.toString());
            intent.setClassName("co.jp.koyu.arpicking.prototype",
                    "co.jp.koyu.arpicking.ScanBarcodeActivity");
            try {
                startActivity(intent);
            } catch (Exception ex) {
                Log.d("StartScanActitity", "Error", ex);
            }
        } else {
            mBoxItemIndex++;
            JsonNode item = mBoxItemList.get(mBoxItemIndex);
            mMessageText.setText(item.get("itemDispData").asText());
            mPrevButton.setEnabled(true);
            if ((mBoxItemIndex + 1) >= mBoxItemList.size()) {
                // 最後のアイテム時は決定のボタンに
                mNextButton.setText(mConfig.get("scanCompButtonText").asText());
            }
        }
    }

    private void onBoxPrevClick() {
        mBoxItemIndex--;
        JsonNode item = mBoxItemList.get(mBoxItemIndex);
        mMessageText.setText(item.get("itemDispData").asText());
        mNextButton.setEnabled(true);
        if ((mBoxItemIndex - 1) < 0) {
            mPrevButton.setEnabled(false);
        }
    }
    ///
    /// ボックスアイテム表示モード End
    ///

    ///
    /// スキャン完了モード用 Start
    /// ※現バージョンでは静止した画像のまま
    ///

    ///
    /// スキャン完了モード End
    ///

    private void onRecordOrStopClick() {
        if (!mVideoRecording) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            recordVideo();
            //[TECH] ProcessDialogを表示すると録画データが正しく作成されない
            mRecordButton.setText(mConfig.get("stopButtonText").asText());
            mMessageText.setText(mConfig.get("recText").asText());
            mVideoRecording = true;
        } else {
            try {
                mCameraCaptureSessions.abortCaptures();
                mCameraCaptureSessions.close();
                createCameraPreview();
                mRecordButton.setText(mConfig.get("recButtonText").asText());
                mMessageText.setText("");
                mVideoRecording = false;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 必要なバックグラウンドスレッドを生成します。再開時に呼び出されます。
     */
    protected void startBackgroundThread() {
        mBackgroundPreviewThread = new HandlerThread("Camera Preview");
        mBackgroundPreviewThread.start();
        mBackgroundPreviewHandler = new Handler(mBackgroundPreviewThread.getLooper());

        //the thread to handle the captured images
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        //The thread to handle the images encoding
        mBackgroundCodecThread = new HandlerThread("Codec Thread");
        mBackgroundCodecThread.start();
        mBackgroundCodecHandler = new Handler(mBackgroundCodecThread.getLooper());
    }

    /**
     * バックグラウンドスレッドを破棄します。一時停止時に呼び出されます。
     */
    protected void stopBackgroundThread() {
        mBackgroundPreviewThread.quitSafely();
        mBackgroundThread.quitSafely();
        mBackgroundCodecThread.quitSafely();

        try {
            mBackgroundPreviewThread.join();
            mBackgroundPreviewThread = null;
            mBackgroundPreviewHandler = null;

            // キャプチャした画像を処理するスレッド
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;

            // 画像のエンコードを処理するスレッド
            mBackgroundCodecThread.join();
            mBackgroundCodecThread = null;
            mBackgroundCodecHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * ビデオの録画を開始し、カメラのプレビューとビデオストリームをセットアップします
     * キャプチャされた画像を受信するようにイメージリーダーをセットアップします
     */
    protected void recordVideo() {
        if (null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null");
            return;
        }

        try {
            final int encodeWidth = mConfig.get("encodeWidth").asInt(); // 1280;       // record video size 設定から取得 BY FUKE
            final int encodeHeight = mConfig.get("encodeHeight").asInt(); // 720;       // record video size 設定から取得 BY FUKE
            // 映像ビットレート 目安 HD（1280×720px）動きの少ない画像 2.4Mbps～4.5Mbps 動きの多い画像 4.5Mbps～9Mbps
            final int encodeBitRate = mConfig.get("encodeBitRate").asInt(); // 6164000; // 6.164Mbps  設定から取得 BY FUKE

            List<Surface> outputSurfaces = new ArrayList<Surface>();
            mImageReader = ImageReader.newInstance(encodeWidth, encodeHeight, ImageFormat.YUV_420_888, 2);
            outputSurfaces.add(mImageReader.getSurface());

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 360); // preview size
            Surface previewSurface = new Surface(texture);
            outputSurfaces.add(previewSurface);

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.addTarget(previewSurface);

            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {

                boolean first = true;

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
                        ByteBuffer bufferU = image.getPlanes()[1].getBuffer();

                        byte[] bytes = new byte[bufferY.capacity() + bufferU.capacity()];
                        bufferY.get(bytes, 0, bufferY.capacity());
                        bufferU.get(bytes, bufferY.capacity(), bufferU.capacity());

                        synchronized (mBytesQueueLock) {
                            mBytesQueue.add(bytes);
                            mFramesIndex++;
                            mQueueElementCount++;

                            if (mQueueElementCount >= MAX_QUEUE_ELEMENTS) {
                                try {
                                    mBytesQueueLock.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                mBytesQueueLock.notifyAll();
                            }
                        }

                        // 最初のキャプチャされた画像を受信したら、エンコーディング処理を開始します
                        if (first) {
                            first = false;
                            mBackgroundHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    startEncoder();
                                }
                            });
                        }
                        image.close();
                    }
                }
            }, mBackgroundHandler);

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSessions = session;
                    mCaptureSessionStopped = false;
                    // カメラキャプチャセッションが作成されたら、ハードウェアエンコーダーの準備を呼び出します
                    prepareEncoder(encodeWidth, encodeHeight, encodeBitRate);
                    updatePreview();

                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                    synchronized (mBytesQueueLock) {
                        mCaptureSessionStopped = true;
                        mBytesQueueLock.notifyAll();
                    }
                }
            }, mBackgroundPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * エンコーダーパラメータを準備および設定するためのユーティリティ
     * 画像リーダーで最初にキャプチャされた画像を受信したら、startEncoder() を呼び出す必要があります
     *
     * @param width   int ビデオ画像のピクセル単位の幅
     * @param height  int ビデオ画像のピクセル単位の高さ
     * @param bitRate int エンコーダのレート (ビット/秒)
     */
    private void prepareEncoder(int width, int height, int bitRate) {

        mMuxerStarted = false;
        mTrackIndex = -1;
        mFramesIndex = 0;

        try {
            // (済) 権限ないためここでエラーとなっていた
            mMuxer = new MediaMuxer(getOutputMediaPath(width, height), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMuxer.setOrientationHint(getImageRotationDegrees(false));

            mEncoderFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            int colorFormat = selectColorFormat(selectCodec(MIME_TYPE), MIME_TYPE);

            mEncoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    colorFormat);
            mEncoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            mEncoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mEncoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            mBytesQueue = new LinkedList<byte[]>();
            mQueueElementCount = 0;
            mEncoder = MediaCodec.createByCodecName(selectCodec(MIME_TYPE).getName());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ハードウェアエンコーダーを開始するユーティリティ、MediaCodec の入出力を処理するように Callback を設定します。
     * このメソッドは、イメージ リーダーで最初にキャプチャされた画像を受け取ったら呼び出す必要があります。
     */
    void startEncoder() {

        mEncoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }

                MediaFormat newFormat = codec.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            }

            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {

                long ptsUsec = computePresentationTime(mFramesIndex);
                byte[] inputBytes = null;

                synchronized (mBytesQueueLock) {
                    inputBytes = mBytesQueue.poll();
                    mQueueElementCount--;

                    if (mQueueElementCount == 0) {
                        try {
                            mBytesQueueLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (mQueueElementCount < 0)
                            mQueueElementCount = 0;
                        mBytesQueueLock.notifyAll();
                    }
                }

                if (inputBytes != null && inputBytes.length > 0 && index >= 0) {
                    ByteBuffer input = codec.getInputBuffer(index);
                    input.clear();
                    input.put(inputBytes);
                    if (mCaptureSessionStopped) {

                        codec.queueInputBuffer(index, 0, 0, ptsUsec, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else
                        codec.queueInputBuffer(index, 0, inputBytes.length, ptsUsec, 0);
                }
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {

                if (!mMuxerStarted) {
                    throw new RuntimeException("muxer hasn't started");
                }

                ByteBuffer outputBuffer = codec.getOutputBuffer(index);

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // INFO OUTPUT_FORMAT CHANGED ステータスを取得したときに、
                    // コーデック構成データが引き出されてマルチプレクサに供給されました。
                    // それを無視します。
                    // ※マルチプレクサ…「映像」と「音声」を2つに合わせて、1つのファイル（コンテナ）にするためのソフト
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    codec.releaseOutputBuffer(index, false);
                    return;
                }
                if (info.size != 0) {
                    if (mMuxer != null) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);

                        mMuxer.writeSampleData(mTrackIndex, outputBuffer, info);
                        Log.d(TAG, "sent " + info.size + " bytes to muxer");
                    }
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "end of stream reached");
                    mMuxerStarted = false;
                    mCaptureSessionStopped = false;
                    releaseEncoder();
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }
        }, mBackgroundCodecHandler);

        mEncoder.configure(mEncoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
    }

    /**
     * フレームインデックスをミリ秒のタイムスタンプに変換するユーティリティ
     *
     * @param frameIndex long フレームインデックス
     * @return long 対応するミリ秒
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    /**
     * キャプチャしたビデオの一意のファイル名を作成するユーティリティ
     * (VIDEO_タイムスタンプ_ビデオの幅xビデオの高さ.mp4)
     *
     * @param width  - int ビデオの幅
     * @param height - int ビデオの高さ
     * @return String filename
     */
    private String getOutputMediaPath(int width, int height) {
        File mediaStorageDir = new File(
                Environment.getExternalStorageDirectory(), "video_encoder");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "can not create the directory");
            }
        }

        // カスタマーidとユーザーid、データ名を付加する 例) 1675996648
        // ファイル名からクラウド側でどのカスタマー、ユーザー、データ名か取得する
        // "-"で分割して、"_"で分割 [1][0]番目=カスタマーid、[1][1]番目=ユーザーid、[1][2]番目=データ名
        String timeStamp = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        String outputPath = (mediaStorageDir.getPath() + File.separator + "V_" + timeStamp + "_" + width + "_" + height +
                "-" + mCustomerId + "_" + mUserId + "_" + mDataName + "_.mp4").toString();

        return outputPath;
    }

    /**
     * MIMEタイプ文字列をコーデック情報クラスに変換するユーティリティ
     *
     * @param mimeType String コーデック識別子
     * @return MediaCodecInfo 選択したmimeTypeに一致するか、nullを返します
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            String codecName = codecInfo.getName();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType) && codecName.equalsIgnoreCase(ENCODER)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * コーデック情報とMIMEタイプの文字列をカラー形式に変換するユーティリティ
     *
     * @param codecInfo MediaCodecInfo コーデックの説明
     * @param mimeType  String MIMEタイプの識別
     * @return int カラー形式を表します。失敗した場合は 0
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    /**
     * 提案されたカラー形式が有効かどうかを判断するユーティリティ
     *
     * @param colorFormat int カラー形式識別子の候補
     * @return true 有効なフォーマットとして認識された場合
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // これらは、このテストで処理する方法がわかっている形式です
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * エンコーダリソースを解放します
     */
    private void releaseEncoder() {
        Log.d(TAG, "releasing encoder objects");

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

        mQueueElementCount = 0;
        mBytesQueue.clear();
    }

    /**
     * カメラのライブビューを開始します。
     */
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(640, 360);// preview size
            Surface surface = new Surface(texture);
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(VideoHWEncodingAsyncActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * すぐにプレビューできるようにカメラをオープンします。
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "is camera open");
        try {
            mCameraId = manager.getCameraIdList()[0];
            // カメラの権限を追加し、ユーザーに権限を付与させます。
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            // ADD BY FUKEHARA 事前にカメラの権限を取得しているため、権限の判断と付与を追加
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    Log.d(TAG, "onOpened");
                    mCameraDevice = camera;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    mCameraDevice.close();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向きとプレビューハンドラを提供してプレビューを更新します。
     */
    protected void updatePreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        try {
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getImageRotationDegrees(false));
            mTextureView.setRotation(getImageRotationDegrees(true));
            mCameraCaptureSessions.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRotationChanged(int newRotation) {
        Log.i(TAG, "New device orientation " + Integer.toString(newRotation));
        updatePreview();
    }

    private int getImageRotationDegrees(boolean invert) {
        // The encoder operates upside-down by default.  So invert this.  Our display is only ROTATION_0 or ROTATION_180
        int rotation = 180;
        if (((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation() == Surface.ROTATION_180) {
            rotation = 0;
        }
        if (invert) {
            if (0 == rotation) {
                rotation = 180;
            } else {
                rotation = 0;
            }
        }
        //Log.i(TAG, "Rotation " + Integer.toString(rotation) );
        return rotation;
    }

    private void closeCamera() {
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();

        mTextureView = (TextureView) findViewById(R.id.texture);

        if (mTextureView.isAvailable()) {
            openCamera();
        }

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        mRotationListener.listen(this, this);
    }

    @Override
    protected void onPause() {
        mRotationListener.stop();
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(VideoHWEncodingAsyncActivity.this, "Sorry!, you don't have permission to run this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
