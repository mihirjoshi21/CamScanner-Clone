package com.mihirjoshi.ocr;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mihirj on 3/1/2016.
 */
public class ActivityOcr extends AppCompatActivity {

    TextView text_name, text_father_name, text_dob, text_pan;
    int index_of_date = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_data);

        text_name = (TextView) findViewById(R.id.text_name);
        text_father_name = (TextView) findViewById(R.id.text_father_name);
        text_dob = (TextView) findViewById(R.id.text_dob);
        text_pan = (TextView) findViewById(R.id.text_pan);
        ArrayList<String> ocrResults = getIntent().
                getStringArrayListExtra(ImageProcessingActivity.OCR_DATA);

        int i = 0;
        for (String ocrResult : ocrResults) {
            findPatternDate(ocrResult, "\\d{2}.\\d{2}.\\d{4}", i);
            if (!text_dob.getText().toString().isEmpty()) break;
            i++;
        }

        i = 0;
        for (String ocrResult : ocrResults) {
            if (i > index_of_date) break;
            findPatternPan(ocrResult, "[A-Z0-9]{10}");
            if (!text_pan.getText().toString().isEmpty()) break;
            i++;
        }

        i = 0;
        for (String ocrResult : ocrResults) {
            if (i < index_of_date) {
                i++;
                continue;
            }
            findPatternName(ocrResult, "[A-Z\\s|]{5,}");
            if (!text_name.getText().toString().isEmpty()) break;
        }
    }


    private void findPatternDate(String text, String regex, int index) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            text_dob.setText(getString(R.string.text_dob, matcher.group()));
            index_of_date = index;
        }
    }

    private void findPatternPan(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            text_pan.setText(getString(R.string.text_pan, matcher.group()));
        }
    }

    private void findPatternName(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            if (text_father_name.getText().toString().isEmpty()) {
                text_father_name.setText(getString(R.string.text_father_name, matcher.group()));
            } else {
                text_name.setText(getString(R.string.text_name, matcher.group()));
            }
        }
    }
}
