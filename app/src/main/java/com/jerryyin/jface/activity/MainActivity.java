package com.jerryyin.jface.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facepp.error.FaceppParseException;
import com.jerryyin.jface.R;
import com.jerryyin.jface.interfaces.onResponseListener;
import com.jerryyin.jface.tools.JFaceRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REFRESH_UI = 0x001;
    private static final int IMAGE_OPEN = 1;
    private static final int TAKE_PHOTO = 2;


    private Button mBtnSelectImg, mBtnDetect;
    private ImageView mImage;
    private ProgressBar mLoadingBar;
    private TextView mTxtAgeSex;

    private Bitmap mCurrentBmp;
    private Paint mPaint;
    private String mCurBmpPath;
    private boolean isClicked = false;  //默认打开界面时的图片是否已经被识别过？

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });


    }

    private void initView() {
        mBtnSelectImg = (Button) findViewById(R.id.btn_select_pic);
        mBtnDetect = (Button) findViewById(R.id.btn_detect);
        mImage = (ImageView) findViewById(R.id.image);
        mLoadingBar = (ProgressBar) findViewById(R.id.loading_bar);
        mTxtAgeSex = (TextView) findViewById(R.id.text_age_sex);

        mBtnSelectImg.setOnClickListener(this);
        mBtnDetect.setOnClickListener(this);
    }

    /**
     * 接受一步操作的返回结果，刷新UI线程
     */
    private Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_UI:
                    JSONObject result = (JSONObject) msg.obj;
                    parseJsonData(result);

//                    drawCanvas();
                    mImage.setImageBitmap(mCurrentBmp);
                    mLoadingBar.setVisibility(View.INVISIBLE);
                    break;
            }
            return false;
        }
    });


    /**
     * 解析json数据, 并且绘制识别框，图片
     *
     * @param jsonObject
     */
    private void parseJsonData(JSONObject jsonObject) {

        //拿到原图, 在器基础上绘制
        Bitmap bitmap = Bitmap.createBitmap(mCurrentBmp.getWidth(), mCurrentBmp.getHeight(), mCurrentBmp.getConfig());
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(mCurrentBmp, 0, 0, null); //覆盖

        try {
            JSONArray faces = jsonObject.getJSONArray("face");

            for (int i = 0; i < faces.length(); i++) {
                JSONObject face = faces.getJSONObject(i);
                //位置参数
                JSONObject position = face.getJSONObject("position");

                JSONObject center = position.getJSONObject("center");
                float cen_x = (float) center.getDouble("x");      //中心点坐标 百分比（相对于整张图片）
                float cen_y = (float) center.getDouble("y");
                float width = (float) position.getDouble("width");//框框高宽 百分比
                float height = (float) position.getDouble("height");

                //获取人脸识别框数据， 转化成 实际坐标 和 实际宽高
                cen_x = cen_x / 100 * bitmap.getWidth();
                cen_y = cen_y / 100 * bitmap.getHeight();
                width = width / 100 * bitmap.getWidth();
                height = height / 100 * bitmap.getHeight();

                //获取 性别，年龄 数据
                String gender = face.getJSONObject("attribute").getJSONObject("gender").getString("value"); //性别
                String age = String.valueOf((int) (face.getJSONObject("attribute").getJSONObject("age").getDouble("value")));  //年龄
                Bitmap ageSexBitmap = buildAgeSexBitmap(age, gender.equals("Male"));

                //绘制
                drawFaceRect(cen_x, cen_y, width, height, canvas, bitmap, ageSexBitmap);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 绘制人脸识别框 + 识别结果框（年龄，性别）
     *
     * @param x            中心点坐标
     * @param y
     * @param width        框框高宽
     * @param height
     * @param canvas
     * @param bitmap       原图
     * @param ageSexBitmap 识别结果图
     */
    private void drawFaceRect(float x, float y, float width, float height, Canvas canvas, Bitmap bitmap, Bitmap ageSexBitmap) {
        mPaint = new Paint();
        mPaint.setColor(getResources().getColor(R.color.faceRect));
        mPaint.setStrokeWidth(6);
        canvas.drawLine(x - width / 2, y - height / 2, x + width / 2, y - height / 2, mPaint);  //上
        canvas.drawLine(x - width / 2, y + height / 2, x + width / 2, y + height / 2, mPaint);  //下
        canvas.drawLine(x - width / 2, y - height / 2, x - width / 2, y + height / 2, mPaint);  //左
        canvas.drawLine(x + width / 2, y - height / 2, x + width / 2, y + height / 2, mPaint);  //右

        //绘制识别结果图片时，需要考虑比例缩放, 此处采用的是 原图 和 imageview 的比例
        int ageWidth = ageSexBitmap.getWidth();
        int ageHeight = ageSexBitmap.getHeight();

        if (bitmap.getWidth() < mImage.getWidth() && bitmap.getHeight() < mImage.getHeight()) {
            //比例
            float ratio = Math.max(bitmap.getWidth() * 1.0f / mImage.getWidth(),
                    bitmap.getHeight() * 1.0f / mImage.getHeight());
            //缩放图片
            ageSexBitmap = Bitmap.createScaledBitmap(ageSexBitmap, (int) (ageWidth * ratio), (int) (ageHeight * ratio), false);
//            ageSexBitmap = Bitmap.createScaledBitmap(ageSexBitmap, (int) (width), (int) (height), false);
        } else {
//            ageSexBitmap = Bitmap.createScaledBitmap(ageSexBitmap, (int) (ageWidth * 4), (int) (ageHeight * 4), false);
        }
        canvas.drawBitmap(ageSexBitmap, x - ageWidth / 2, y - ageHeight - height / 2, null);

        mCurrentBmp = bitmap;
    }


    /**
     * 将TextView转化成bitmap
     *
     * @param age
     * @param isMale
     */
    private Bitmap buildAgeSexBitmap(String age, boolean isMale) {
        mTxtAgeSex.setText(age + "");
        if (isMale) {
            mTxtAgeSex.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.male), null, null, null);
        } else {
            mTxtAgeSex.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.female), null, null, null);
        }
        mTxtAgeSex.setDrawingCacheEnabled(true);//cache开启, 可通过 getDrawingCache()拿到view里面的catch图片bitmap
        Bitmap bitmap = Bitmap.createBitmap(mTxtAgeSex.getDrawingCache());
        mTxtAgeSex.destroyDrawingCache();   //若果要更新cache, 必须要调用destoryDrawingCache方法把旧的cache销毁，才能建立新的。

        return bitmap;
    }

    private void drawCanvas() {
//        canvas = new Canvas();

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_select_pic:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("choose picture");
                builder.setItems(new CharSequence[]{"local picture", "take a photo"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                //本地照片
                                Intent intent = new Intent(Intent.ACTION_PICK);
                                intent.setType("image/*");
                                startActivityForResult(intent, IMAGE_OPEN);
                                break;

                            case 1:
                                //系统相机拍照
                                Intent intent1 = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//                                    mImgFileUri = HiCameraUtil.getOutputMediaFileUri(HiCameraUtil.MEDIA_TYPE_IMAGE);  //create a file to save the image
//                                    intent1.putExtra(MediaStore.EXTRA_OUTPUT, mImgFileUri);     //set the image file name
                                startActivityForResult(intent1, TAKE_PHOTO);

                                break;
                        }
                    }
                });
                builder.show();
                break;

            case R.id.btn_detect:
                mLoadingBar.setVisibility(View.VISIBLE);
                if (mCurBmpPath == null && ! isClicked) {
                    BitmapDrawable bd = (BitmapDrawable) mImage.getDrawable();
                    mCurrentBmp = bd.getBitmap();

                } else if (mCurBmpPath == null && isClicked) {
                    mImage.setImageBitmap(mCurrentBmp);
                    mLoadingBar.setVisibility(View.INVISIBLE);
                } else {
                    mCurrentBmp = BitmapFactory.decodeFile(mCurBmpPath);
                }
                isClicked = true;
                JFaceRequest.sendFaceRequests(mCurrentBmp, new onResponseListener() {
                    @Override
                    public void onSuccess(JSONObject object) {
                        Message message = new Message();
                        message.what = REFRESH_UI;
                        message.obj = object;
                        mHandler.sendMessage(message);
                        Looper.prepare();
                    }

                    @Override
                    public void onError(FaceppParseException e) {
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "请求失败：" + e.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case IMAGE_OPEN:
                    Uri uri = data.getData();
                    if (!TextUtils.isEmpty(uri.getAuthority())) {
                        //查询选择图片
                        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                        //返回 没找到选择图片
                        if (null == cursor) {
                            return;
                        }
                        //光标移动至开头 获取图片路径
                        cursor.moveToFirst();
                        int index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                        mCurBmpPath = cursor.getString(index);  //选中图片的路劲
                        cursor.close();

                        //face++ sdk 中需要对照片进行转换成2进制数据传输， 要求图片大小必须 <3M ,所以需要压缩导合适大小
                        resizePhoto();
                        mImage.setImageBitmap(mCurrentBmp);
                    }
                    break;

                case TAKE_PHOTO:
                    mCurrentBmp = (Bitmap) data.getExtras().get("data");
                    mImage.setImageBitmap(mCurrentBmp);
                    mCurBmpPath = null;
                    break;
                default:
                    break;
            }
        }
    }

    private void resizePhoto() {
        BitmapFactory.Options options = new BitmapFactory.Options();    //option 里面含有宽，高信息
        options.inJustDecodeBounds = true;  //只是获取尺寸 而不 加载图片
        BitmapFactory.decodeFile(mCurBmpPath, options); //获取到option
        //缩放
        double radio = Math.max(options.outWidth * 1.0d / 1024f, options.outHeight * 1.0f / 1024);
        options.inSampleSize = (int) Math.ceil(radio);  //取最小整数

        options.inJustDecodeBounds = false; //加载图片
        mCurrentBmp = BitmapFactory.decodeFile(mCurBmpPath, options);
    }

}
