package com.jerryyin.jface.tools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.facepp.error.FaceppParseException;
import com.facepp.http.HttpRequests;
import com.facepp.http.PostParameters;
import com.jerryyin.jface.constant.JConstants;
import com.jerryyin.jface.interfaces.onResponseListener;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;

/**
 * Created by JerryYin on 12/16/15.
 * 调用api查询图片数据工具类
 */
public class JFaceRequest {

    private static final String TAG = "JFaceRequest";

    /**
     * 发送请求的方法，实际是调用jar包里面的方法近进行操作，内部访问网络的方法在Face＋＋的sdk当中
     * 异步方式，在线程中执行
     * @param bmp
     * @param response
     */
    public static void sendFaceRequests(final Bitmap bmp, final onResponseListener response){

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //接收bitmap
                    Bitmap bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight());
                    /**
                     * 此处的bitmap太大，不能直接使用，需要压缩,
                     * 并且转化成字节数组进行传输
                     */
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                    byte[] array = stream.toByteArray();

                    HttpRequests httpRequests = new HttpRequests(JConstants.API_KEY, JConstants.API_SECRET, true, true);
                    PostParameters parameters = new PostParameters();
                    parameters.setImg(array);
                    JSONObject jsonObject = httpRequests.detectionDetect(parameters);

                    if (response != null){
                        //请求成功
                        response.onSuccess(jsonObject);
                        Log.d(TAG, "result = " + jsonObject.toString());
                    }

                } catch (FaceppParseException e) {
                    e.printStackTrace();
                    //请求失败
                    if (response != null){
                        Log.d(TAG, "error = " + e);
                        response.onError(e);
                    }
                }
            }
        }).start();
    }

//    error = com.facepp.error.FaceppParseException: error :java.net.SocketTimeoutException

}

