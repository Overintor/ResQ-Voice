package com.example.resq_voice;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // กำหนดเวลาแสดงหน้าโหลด (2 วินาที)
    private static final int SPLASH_TIME_OUT = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // ใช้ Handler หน่วงเวลาแล้วเด้งไปหน้า HomeActivity
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {

                Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                startActivity(intent);
                finish(); // ปิดหน้า Splash ทิ้ง
            }
        }, SPLASH_TIME_OUT);
    }
}