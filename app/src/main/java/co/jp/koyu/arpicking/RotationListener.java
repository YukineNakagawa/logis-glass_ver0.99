/*'*****************************************************************************
Copyright (c) 2018, Vuzix Corporation
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

*  Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

*  Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

*  Neither the name of Vuzix Corporation nor the names of
   its contributors may be used to endorse or promote products derived
   from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*'*****************************************************************************/
package co.jp.koyu.arpicking;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import android.view.WindowManager;

/**
 * 回転を監視するクラス。変更されるたびにコールバックを提供します。
 */
public class RotationListener {

    /**
     * コールバックを受け取るためのインターフェース
     */
    public interface rotationCallbackFn {
        /**
         * 回転が変化したときに呼ばれるメソッド
         * @param newRotation int または Surface.ROTATION_0 or Surface.ROTATION_180
         */
        void onRotationChanged(int newRotation);
    }

    private int lastRotation;
    private WindowManager mWindowManager;
    private OrientationEventListener mOrientationEventListener;

    private rotationCallbackFn mCallback;


    /**
     * リスナーの登録
     * @param context リスナー登録するアクティビティのコンテキスト
     * @param callback 回転が変化したときに呼び出されるコールバック
     */
    public void listen(Context context, rotationCallbackFn callback) {
        // リスナー登録は一度だけ
        stop();
        mCallback = callback;
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        mOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                onOrientationChangedHandler();
            }
        };
        mOrientationEventListener.enable();
        lastRotation = mWindowManager.getDefaultDisplay().getRotation();
    }

    /**
     * 回転の変更を処理します。角度ごとに呼び出されます。変化があった場合のみコールバックを呼び出します
     */
    private void onOrientationChangedHandler() {
        if( mWindowManager != null && mCallback != null) {
            int newRotation = mWindowManager.getDefaultDisplay().getRotation();
            if (newRotation != lastRotation) {
                mCallback.onRotationChanged(newRotation);
                lastRotation = newRotation;
            }
        }
    }

    /**
     * コールバックの受信を停止します。 リスナー登録したアクティビティの onPause() から呼ばれます
     */
    public void stop() {
        if(mOrientationEventListener != null) {
            mOrientationEventListener.disable();
        }
        mOrientationEventListener = null;
        mWindowManager = null;
        mCallback = null;
    }    
}
