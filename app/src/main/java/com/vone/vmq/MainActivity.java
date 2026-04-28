package com.vone.vmq;

import android.Manifest;
import android.app.*;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.vone.qrcode.R;
import com.vone.vmq.util.Constant;
import com.google.zxing.activity.CaptureActivity;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity{

    private static final int REQ_PERM_NOTIFICATION = 11005;

    private TextView txthost;
    private TextView txtkey;
    private TextView txtpid;

    private boolean isOk = false;
    private static String TAG = "MainActivity";

    private static String host;
    private static String key;
    private static String pid;

    private long startDate;

    private boolean showDateFlag = true;

    int id = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txthost = (TextView) findViewById(R.id.txt_host);
        txtkey = (TextView) findViewById(R.id.txt_key);
        txtpid = (TextView) findViewById(R.id.txt_pid);

        //检测通知使用权是否启用
        if (!isNotificationListenersEnabled()) {
            //跳转到通知使用权页面
            gotoNotificationAccessSetting();
        }
        //重启监听服务
        toggleNotificationListenerService(this);

        // Android 13+ 请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM_NOTIFICATION);
            }
        }

        //读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        pid = read.getString("pid", "");

        if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(key) && !TextUtils.isEmpty(pid)){
            txthost.setText(" 通知地址："+host);
            txtpid.setText(" 商户PID："+pid);
            txtkey.setText(" 通讯密钥："+maskKey(key));
            isOk = true;
        }


        Toast.makeText(MainActivity.this, "网程码支付 v2.0.0", Toast.LENGTH_SHORT).show();

        notifyEvent(null,"收款监听中...");
        startDate = new Date().getTime();
    }

    private void showDateInfo() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!showDateFlag) {
                    long now = new Date().getTime() - startDate;
                    long s = now / 1000L % 60L;
                    long m = now / 1000L / 60L % 60L;
                    long h = now / 1000L / 60L / 60L % 24L;
                    long d = now / 1000L / 60L / 60L / 24L;
                    notifyEvent(0, "收款监听中，已监听"
                            + d + "天"
                            + h + "时"
                            + m + "分"
                            + s + "秒");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    private void notifyEvent(Integer tempId,String contentText) {
        // 软件运行标识
        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("1",
                    "Channel1", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);

            Notification.Builder builder = new Notification.Builder(this,"1");

            mNotification = builder
                    .setSmallIcon(R.mipmap.gugu)
                    .setTicker("收款监听中...")
                    .setContentTitle("收款监听已开启")
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .build();
        }else{
            mNotification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.gugu)
                    .setTicker("收款监听中...")
                    .setContentTitle("收款监听已开启")
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .build();
        }
        if (tempId != null) {
            mNotificationManager.notify(tempId,mNotification);
        } else {
            mNotificationManager.notify(id++, mNotification);
        }
    }



    //扫码配置
    public void startQrCode(View v) {
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, Constant.REQ_PERM_CAMERA);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, Constant.REQ_QR_CODE);
    }
    //手动配置
    public void doInput(View v){
        final EditText inputServer = new EditText(this);
        // 如果已配置，回显当前参数
        if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(pid) && !TextUtils.isEmpty(key)) {
            inputServer.setText(host + "/" + pid + "/" + key);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入配置数据").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();

                String[] tmp = scanResult.split("/");
                if (tmp.length!=3){
                    Toast.makeText(MainActivity.this, "数据错误，请您输入网站上显示的配置数据!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String t = String.valueOf(new Date().getTime());
                String sign = md5(tmp[1] + t + tmp[2]);

                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url("http://"+tmp[0]+"/appHeart?pid="+tmp[1]+"&t="+t+"&sign="+sign).method("GET",null).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Log.d(TAG, "onResponse: "+response.body().string());
                        isOk = true;

                    }
                });
                if (tmp[0].indexOf("localhost")>=0){
                    Toast.makeText(MainActivity.this, "配置信息错误，本机调试请访问 本机局域网IP:8080(如192.168.1.101:8080) 获取配置信息进行配置!", Toast.LENGTH_LONG).show();

                    return;
                }
                //将扫描出的信息显示出来
                txthost.setText(" 通知地址："+tmp[0]);
                txtpid.setText(" 商户PID："+tmp[1]);
                txtkey.setText(" 通讯密钥："+maskKey(tmp[2]));
                host = tmp[0];
                pid = tmp[1];
                key = tmp[2];

                SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
                editor.putString("host", host);
                editor.putString("pid", pid);
                editor.putString("key", key);
                editor.apply();

            }
        });
        builder.show();

    }
    //检测心跳
    public void doStart(View view) {
        if (!isOk){
            Toast.makeText(MainActivity.this, "请您先配置!", Toast.LENGTH_SHORT).show();
            return;
        }


        String t = String.valueOf(new Date().getTime());
        String sign = md5(pid + t + key);

        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url("http://"+host+"/appHeart?pid="+pid+"&t="+t+"&sign="+sign).method("GET",null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "心跳返回："+response.body().string(), Toast.LENGTH_LONG).show();
                Looper.loop();
            }
        });
    }
    //检测监听
    public void checkPush(View v){

        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("1",
                    "Channel1", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);

            Notification.Builder builder = new Notification.Builder(this,"1");

            mNotification = builder
                    .setSmallIcon(R.mipmap.gugu)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("网程码支付")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }else{
            mNotification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.gugu)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("网程码支付")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }

        mNotificationManager.notify(id++, mNotification);
    }

    public void showDateBtn(View v) {
        TextView tv_hello = findViewById(R.id.btn_showDate);
        if (showDateFlag) {
            tv_hello.setText("展示时间:已展示");
            Toast.makeText(MainActivity.this, "开始展示时间...", Toast.LENGTH_SHORT).show();
            showDateInfo();
        } else {
            tv_hello.setText("展示时间:已关闭");
            Toast.makeText(MainActivity.this, "关闭展示时间...", Toast.LENGTH_SHORT).show();
            notifyEvent(0,"收款监听中...");
        }
        showDateFlag = !showDateFlag;
    }



    //各种权限的判断
    private void toggleNotificationListenerService(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Toast.makeText(MainActivity.this, "监听服务启动中...", Toast.LENGTH_SHORT).show();
    }
    public boolean isNotificationListenersEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    protected boolean gotoNotificationAccessSetting() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;

        } catch (ActivityNotFoundException e) {
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                startActivity(intent);
                return true;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            Toast.makeText(this, "对不起，您的手机暂不支持", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
    }



    private String maskKey(String key) {
        if (key == null || key.length() <= 6) {
            return key;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(key.substring(0, 3));
        for (int i = 0; i < key.length() - 6; i++) {
            sb.append("*");
        }
        sb.append(key.substring(key.length() - 3));
        return sb.toString();
    }

    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
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
        }
        return "";
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //扫描结果回调
        if (requestCode == Constant.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(Constant.INTENT_EXTRA_KEY_QR_SCAN);

            String[] tmp = scanResult.split("/");
            if (tmp.length!=3){
                Toast.makeText(MainActivity.this, "二维码错误，请您扫描网站上显示的二维码!", Toast.LENGTH_SHORT).show();
                return;
            }

            String t = String.valueOf(new Date().getTime());
            String sign = md5(tmp[1] + t + tmp[2]);

            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder().url("http://"+tmp[0]+"/appHeart?pid="+tmp[1]+"&t="+t+"&sign="+sign).method("GET",null).build();
            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "onResponse: "+response.body().string());
                    isOk = true;

                }
            });

            //将扫描出的信息显示出来
            txthost.setText(" 通知地址："+tmp[0]);
            txtpid.setText(" 商户PID："+tmp[1]);
            txtkey.setText(" 通讯密钥："+maskKey(tmp[2]));
            host = tmp[0];
            pid = tmp[1];
            key = tmp[2];

            SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
            editor.putString("host", host);
            editor.putString("pid", pid);
            editor.putString("key", key);
            editor.apply();

        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startQrCode(null);
                } else {
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case REQ_PERM_NOTIFICATION:
                // Android 13+ 通知权限回调
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "通知权限已授予", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }



}
