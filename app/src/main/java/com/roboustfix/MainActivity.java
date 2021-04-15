package com.roboustfix;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addMethod();
    }


    public void choiceButton() {
        if (PatchProxy.isSupport()) {
            return;
        }
        System.out.println("测试方法添加内容");
    }

    public void addMethod() {

    }
}