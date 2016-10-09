package com.mihirjoshi.ocr.camera;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;

import com.mihirjoshi.ocr.R;
import com.mihirjoshi.ocr.controller.MyBus;
import com.squareup.otto.Subscribe;

import java.io.File;

public class CameraActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        Uri uri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);

        if (null == savedInstanceState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, CameraBasicFragment.newInstance(uri.getPath()))
                        .commit();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyBus.getInstance().register(this);
    }

    @Subscribe
    public void getImageFile(File file) {
        setResult(Activity.RESULT_OK, null);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyBus.getInstance().unregister(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}