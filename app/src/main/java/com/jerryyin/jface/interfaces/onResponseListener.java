package com.jerryyin.jface.interfaces;

import com.facepp.error.FaceppParseException;

import org.json.JSONObject;

/**
 * Created by JerryYin on 12/16/15.
 * 请求完毕回调接口
 */
public interface onResponseListener {

    void onSuccess(JSONObject object);

    void onError(FaceppParseException e);
}
