package co.jp.koyu.arpicking;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.app.ProgressDialog;
import android.content.Context;
import android.view.View;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * ビデオファイルアップロード
 */
public class VideoUploadAsyncTask
        extends AsyncTask<String, Void, String> {

    ProgressDialog dialog;
    String responseBody;
    Context context;
    View view;

    public VideoUploadAsyncTask(Context context, View view) {
        this.context = context;
        this.view = view;
    }

    /**
     * video_encoderフォルダのファイルをとにかくアップロードして、元のファイルは削除します。
     *
     * @param params param[0]…url
     * @return
     */
    @Override
    protected String doInBackground(String... params) {
        // Log.d("■ExternalStorageDirectory■", Environment.getExternalStorageDirectory().getPath());
        File mediaStorageDir = new File(
                Environment.getExternalStorageDirectory(), "video_encoder");
        if (!mediaStorageDir.exists()) {
            return "{ \"isWarning\": true, \"isError\": false, \"message\": \"録画フォルダがありません。\" }";
        }

        // ファイル一覧の取得
        File[] files = new File(mediaStorageDir.getPath()).listFiles();
        if (files.length == 0) {
            return "{ \"isWarning\": true, \"isError\": false, \"message\": \"録画データがありません。\" }";
        }

        String url = params[0];
        MediaType media = MediaType.parse("multipart/form-data");
        for (int i = 0; i < files.length; i++) {
            try {
                String FileName = files[i].getName();
                Log.d("■FileName■", FileName);
                String boundary = String.valueOf(System.currentTimeMillis());

                RequestBody requestBody = new MultipartBody.Builder(boundary).setType(MultipartBody.FORM)
                        .addFormDataPart("file", FileName, RequestBody.create(media, files[i]))
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build();

                //TODO: readTimeoutを30秒にすることによって SocketTimeoutException は発生しなくなったが、
                //      今度は"413 Content Too Large"が発生する
                // php.iniの upload_max_filesize は 200M に修正したがダメ
                // php artisan serve 側の設定か？ ⇒ ビルトインhttpサーバーなので設定無理？
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
                Log.d("■connect timeout■", String.valueOf(client.connectTimeoutMillis()));
                Log.d("■soket timeout■", String.valueOf(client.readTimeoutMillis()));
                Log.d("■write timeout■", String.valueOf(client.writeTimeoutMillis()));

                Response response = client.newCall(request).execute();
                responseBody = response.body().string();

                JsonNode responseJson = CommonUtil.getInstance().convertStringToJson(responseBody);
                if (responseJson != null) {
                    if (!responseJson.get("isError").asBoolean()) {
                        // エラーでなければファイル削除
                        files[i].delete();
                    }
                }

            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                return "{ \"isWarning\": false, \"isError\": true, \"message\": \"サーバーに接続できませんでした。\" }";
            } catch (IOException e) {
                e.printStackTrace();
                return "{ \"isWarning\": false, \"isError\": true, \"message\": \"予期せぬエラー " + e.getMessage() + "\" }";
            }
        }

        return responseBody;
    }

    @Override
    protected void onPostExecute(String result) {
        if (dialog != null) {
            dialog.dismiss();
        }

        MainActivity maActivity = (MainActivity) context;
        maActivity.onVideoUploadPostExecute(result, view);
    }

    @Override
    protected void onPreExecute() {
        dialog = new ProgressDialog(context);
        dialog.setTitle("お待ちください。");
        dialog.setMessage("録画ファイルのアップロード中です...");
        dialog.show();
    }
}