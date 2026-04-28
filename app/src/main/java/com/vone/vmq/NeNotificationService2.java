package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService2  extends NotificationListenerService {
    private String TAG = "NeNotificationService2";
    private String host = "";
    private String key = "";
    private String pid = "";
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;


    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(Context context) {
        if (null == mWakeLock)
        {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE, "QrPay:WakeLock");
            if (null != mWakeLock)
            {
                mWakeLock.acquire(10*60*1000L /*10 minutes*/);
            }
        }
    }

    public void releaseWakeLock() {
        if (null != mWakeLock)
        {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            mWakeLock = null;
        }
    }

    public void initAppHeart(){
        Log.d(TAG, "开始启动心跳线程");
        if (newThread!=null){
            return;
        }
        acquireWakeLock(this);
        newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "心跳线程启动！");
                while (true){

                    SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
                    host = read.getString("host", "");
                    key = read.getString("key", "");
                    pid = read.getString("pid", "");

                    // 配置为空时跳过请求，避免发送无效URL
                    if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key) || TextUtils.isEmpty(pid)) {
                        try {
                            Thread.sleep(30*1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    String t = String.valueOf(new Date().getTime());
                    String sign = md5(pid + t + key);

                    OkHttpClient okHttpClient = new OkHttpClient();
                    Request request = new Request.Builder().url("http://"+host+"/appHeart?pid="+pid+"&t="+t+"&sign="+sign).method("GET",null).build();
                    Call call = okHttpClient.newCall(request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            final String error = e.getMessage();
                            Handler handlerThree=new Handler(Looper.getMainLooper());
                            handlerThree.post(new Runnable(){
                                public void run(){
                                    Toast.makeText(getApplicationContext() ,"心跳状态错误，请检查配置是否正确!"+error,Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Log.d(TAG, "onResponse heard: "+response.body().string());
                        }
                    });
                    try {
                        Thread.sleep(30*1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        newThread.start();
    }




    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "接受到通知消息");
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        pid = read.getString("pid", "");


        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                String title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
                String content = extras.getString(NotificationCompat.EXTRA_TEXT, "");
                Log.d(TAG, "**********************");
                Log.d(TAG, "包名:" + pkg);
                Log.d(TAG, "标题:" + title);
                Log.d(TAG, "内容:" + content);
                Log.d(TAG, "**********************");


                if (pkg.equals("com.eg.android.AlipayGphone")){
                    if (content!=null && !content.equals("")) {
                        if (content.contains("通过扫码向你付款") || content.contains("成功收款") || title.contains("成功收款")){
                            String money = getMoney(content);
                            if (money== null || "".equals(money)) {
                                money = getMoney(title);
                            }
                            if (money!=null){
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 " + money);
                                appPush(2, Double.valueOf(money));
                            }else {
                                Handler handlerThree=new Handler(Looper.getMainLooper());
                                handlerThree.post(new Runnable(){
                                    public void run(){
                                        Toast.makeText(getApplicationContext() ,"监听到支付宝消息但未匹配到金额！",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                    }

                }else if(pkg.equals("com.tencent.mm")){

                    if (content!=null && !content.equals("")){
                        if ((title.equals("微信支付") || title.equals("微信收款助手") || title.equals("微信收款商业版")) && content.contains("收款")){
                            String money = getMoney(content);
                            if (money!=null){
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信到账 "+ money);
                                appPush(1,Double.valueOf(money));
                            }else{
                                Handler handlerThree=new Handler(Looper.getMainLooper());
                                handlerThree.post(new Runnable(){
                                    public void run(){
                                        Toast.makeText(getApplicationContext() ,"监听到微信消息但未匹配到金额！",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                        }
                    }

                }else if(pkg.equals("com.vone.qrcode")){

                    if (content.equals("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")){
                        Handler handlerThree=new Handler(Looper.getMainLooper());
                        handlerThree.post(new Runnable(){
                            public void run(){
                                Toast.makeText(getApplicationContext() ,"监听正常，如无法正常回调请联系作者反馈！",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }



            }
        }

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    @Override
    public void onListenerConnected() {
        initAppHeart();

        Handler handlerThree = new Handler(Looper.getMainLooper());
        handlerThree.post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext() ,"监听服务开启成功！",Toast.LENGTH_SHORT).show();
            }
        });


    }





    public void appPush(int type,double price){
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        pid = read.getString("pid", "");

        Log.d(TAG, "onResponse  push: 开始:"+type+"  "+price);

        String t = String.valueOf(new Date().getTime());
        String sign = md5(type + "" + price + pid + t + key);
        String url = "http://"+host+"/appPush?pid="+pid+"&t="+t+"&type="+type+"&price="+price+"&sign="+sign;
        Log.d(TAG, "onResponse  push: 开始:"+url);

        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(url).method("GET",null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onResponse  push: 请求失败");

            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {

                Log.d(TAG, "onResponse  push: "+response.body().string());

            }
        });
    }

    public static String getMoney(String content){

        List<String> ss = new ArrayList<String>();
        for(String sss:content.replaceAll("[^0-9.]", ",").split(",")){
            if (sss.length()>0)
                ss.add(sss);
        }
        if (ss.size()<1){
            return null;
        }else {
            return ss.get(ss.size()-1);
        }

    }

    /**
     * MD5签名（修复前导零丢失BUG）
     * 旧版使用 BigInteger.toString(16) 会丢失前导零，
     * 新版逐字节转换并补零，确保始终返回32位十六进制字符串
     */
    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    result.append("0");
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

}
