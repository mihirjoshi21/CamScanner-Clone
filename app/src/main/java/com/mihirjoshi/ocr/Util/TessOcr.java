package com.mihirjoshi.ocr.Util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.mihirjoshi.ocr.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by mihirj on 2/17/2016.
 */
public class TessOcr {
    private TessBaseAPI mTess;

    Activity mActivity;

    public TessOcr(Activity activity) {
        mActivity = activity;
        String path = Environment.getExternalStorageDirectory() + "/tesseract/tessdata";
        File dir = new File(path);
        if (dir.mkdirs() || dir.isDirectory()) {
            String str_song_name = "eng.traineddata";
            try {
                CopyRAWtoSDCard(path + File.separator + str_song_name);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mTess = new TessBaseAPI();
        String datapath = Environment.getExternalStorageDirectory() + "/tesseract/";
        String language = "eng";
        dir = new File(datapath + "tessdata/");
        if (!dir.exists())
            dir.mkdirs();
        mTess.init(datapath, language);
    }

    private void CopyRAWtoSDCard(String path) throws IOException {
        InputStream in = mActivity.getResources().openRawResource(R.raw.eng);
        FileOutputStream out = new FileOutputStream(path);
        byte[] buff = new byte[1024];
        int read;
        try {
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    public String getOCRResult(Bitmap bitmap) {

        mTess.setImage(bitmap);
        String result = mTess.getUTF8Text();

        return result;
    }

    public void onDestroy() {
        if (mTess != null)
            mTess.end();
    }

}
