package com.mihirjoshi.ocr;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by mihirj on 5/10/2016.
 */
public class CardActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_data);
        TextView name = (TextView) findViewById(R.id.text_name);
        ImageView card = (ImageView) findViewById(R.id.image_card);

        byte[] image = getIntent().getByteArrayExtra("Image");
        card.setImageBitmap(BitmapFactory.decodeByteArray(image, 0, image.length));
        name.setText(getIntent().getStringExtra("Result"));


    }
}
