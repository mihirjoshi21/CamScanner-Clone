package com.mihirjoshi.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import com.mihirjoshi.ocr.Util.BitmapUtil;
import com.mihirjoshi.ocr.Util.TessOcr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.card.payment.CardIOActivity;
import io.card.payment.CreditCard;
import ui.ActivityHelper;
import widget.PolygonView;

/**
 * Image Processing Activity
 */
public class ImageProcessingActivity extends AppCompatActivity {

    private static final int STEPS = 5;
    private static final int GET_IMAGE = 100;
    private static final int EFFECT_MAGIC_COLOR = 101;
    private static final int EFFECT_GRAY_MODE = 102;
    private static final int EFFECT_BW = 103;
    private static final int EFFECT_MIRROR = 104;
    private static final int EFFECT_ANTICLOCKWISE = 105;
    private static final int EFFECT_CLOCKWISE = 106;
    private static final int EFFECT_ORIGINAL = 107;
    private static final int EFFECT_OCR = 108;

    private static final int ACTIVITY_CAMERA = 1001;
    private static final int ACTIVITY_FILE = 1002;
    public static final String OCR_DATA = "ocr_data";

    private boolean mIsCropped;
    private Bitmap mBitmap, mOriginalBitmap, mUnProcessedBitmap;
    private float mContrast = 1, mBrightness = 0;

    @Bind(R.id.horizontal_scroll)
    HorizontalScrollView mHorizontalTop;

    @Bind(R.id.image_edit)
    ImageView mImageEdit;

    @Bind(R.id.image_save)
    ImageView mImageSave;

    @Bind(R.id.image_back)
    ImageView mImageBack;

    @Bind(R.id.frame_source)
    FrameLayout mFrameSource;

    @Bind(R.id.polygon_outline)
    PolygonView mPolygonOutline;

    @Bind(R.id.image_mirror)
    ImageView mImageMirror;

    @Bind(R.id.image_rotate_left)
    ImageView mImageRotateLeft;

    @Bind(R.id.image_rotate_right)
    ImageView mImageRotateRight;

    @Bind(R.id.image_ocr)
    ImageView mImageOcr;

    @Bind(R.id.text_magic_color)
    TextView mTextMagicColor;

    @Bind(R.id.text_black_white)
    TextView mTextBlackWhite;

    @Bind(R.id.text_gray_mode)
    TextView mTextGrayMode;

    @Bind(R.id.text_original)
    TextView mTextOriginal;

    // Image Orientation
    private int mOrientation;
    private Uri mUri;
    private TessOcr mTessOCR;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTessOCR.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_processing);
        ButterKnife.bind(this);

        mUri = BitmapUtil.getBitmapUri();
        mTessOCR = new TessOcr(ImageProcessingActivity.this);

        ActivityHelper.selectImage(ImageProcessingActivity.this, ACTIVITY_CAMERA, ACTIVITY_FILE, mUri);

        /**  Set on click listeners **/

        mImageRotateLeft.setOnClickListener(v -> new FilterTask(EFFECT_ANTICLOCKWISE, mTextOriginal).execute());

        mImageRotateRight.setOnClickListener(v -> new FilterTask(EFFECT_CLOCKWISE, mTextOriginal).execute());

        mImageMirror.setOnClickListener(v -> new FilterTask(EFFECT_MIRROR, mTextOriginal).execute());

        mImageOcr.setOnClickListener(v1 -> new FilterTask(EFFECT_OCR, mTextOriginal).execute());

        mTextGrayMode.setOnClickListener(v -> new FilterTask(EFFECT_GRAY_MODE, mTextGrayMode).execute());

        mTextBlackWhite.setOnClickListener(v -> new FilterTask(EFFECT_BW, mTextBlackWhite).execute());

        mTextMagicColor.setOnClickListener(v -> new FilterTask(EFFECT_MAGIC_COLOR, mTextMagicColor).execute());

        mTextOriginal.setOnClickListener(v -> new FilterTask(EFFECT_ORIGINAL, mTextOriginal).execute());

        mImageBack.setOnClickListener(v -> {
            if (!mIsCropped) finish();
            else reset();
        });

        mImageSave.setOnClickListener(v -> {
            if (!mIsCropped) {
                Map<Integer, PointF> points = mPolygonOutline.getPoints();
                if (isScanPointsValid(points)) {
                    new ScanAsyncTask(points).execute();
                }
            } else {
                //TODO Save Image to Disk
                try {
                    File imageStorageDir = new File(
                            Environment
                                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "CRMnextImages");
                    if (!imageStorageDir.exists()) {
                        imageStorageDir.mkdirs();
                    }
                    File file = new File(imageStorageDir + File.separator + "IMG_"
                            + String.valueOf(System.currentTimeMillis()) + ".jpg");
                    FileOutputStream fOut = new FileOutputStream(file);
                    ((BitmapDrawable) mImageEdit.getDrawable()).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, fOut);
                    fOut.flush();
                    fOut.close();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("file_name", Uri.parse(file.toString()).toString());
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mIsCropped) reset();
        else super.onBackPressed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        /**
         * Card Specific
         */
        if (requestCode == 333) {
            String resultDisplayStr;
            if (data != null && data.hasExtra(CardIOActivity.EXTRA_SCAN_RESULT)) {
                CreditCard scanResult = data.getParcelableExtra(CardIOActivity.EXTRA_SCAN_RESULT);

                // Never log a raw card number. Avoid displaying it, but if necessary use getFormattedCardNumber()
                resultDisplayStr = "Card Number: " + scanResult.getRedactedCardNumber() + "\n";
                if (scanResult.cardholderName != null) {
                    resultDisplayStr += "Name: " + scanResult.cardholderName + "\n";
                }
                if (scanResult.isExpiryValid()) {
                    resultDisplayStr += "Expiration Date: " + scanResult.expiryMonth + "/" + scanResult.expiryYear + "\n";
                }

                if (scanResult.cvv != null) {
                    // Never log or display a CVV
                    resultDisplayStr += "CVV has " + scanResult.cvv.length() + " digits.\n";
                }

                if (scanResult.postalCode != null) {
                    resultDisplayStr += "Postal Code: " + scanResult.postalCode + "\n";
                }
                Intent intent = new Intent(this, ImageProcessingActivity.class);
                intent.putExtra("Result", resultDisplayStr);
                intent.putExtra("Image", data.getByteArrayExtra(CardIOActivity.EXTRA_CAPTURED_CARD_IMAGE));
                startActivity(intent);
            } else {
                resultDisplayStr = "Scan was canceled.";
            }
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case ACTIVITY_CAMERA:
                    new FilterTask(GET_IMAGE, mTextOriginal).execute();
                    break;

                case ACTIVITY_FILE:
                    mUri = data.getData();
                    new FilterTask(GET_IMAGE, mTextOriginal).execute();
                    break;
            }
        } else {
            ActivityHelper.selectImage(ImageProcessingActivity.this, ACTIVITY_CAMERA, ACTIVITY_FILE, mUri);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null) {
            outState.putString("cameraImageUri", mUri.toString());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey("cameraImageUri")) {
            mUri = Uri.parse(savedInstanceState.getString("cameraImageUri"));
        }
    }

    private void initScan() {
        mFrameSource.post(() -> setBitmap(mOriginalBitmap));
    }

    private void reset() {
        mImageEdit.setImageBitmap(mOriginalBitmap);
        initScan();
        mHorizontalTop.setVisibility(View.GONE);
        mImageMirror.setVisibility(View.GONE);
        mImageRotateLeft.setVisibility(View.GONE);
        mImageRotateRight.setVisibility(View.GONE);
        mImageOcr.setVisibility(View.GONE);
        mIsCropped = false;
    }

    private void setBitmap(Bitmap original) {
        mBitmap = BitmapUtil.getScaledBitmap(original, mFrameSource.getWidth(), mFrameSource.getHeight(), mOrientation);
        mImageEdit.setImageBitmap(mBitmap);
        Bitmap tempBitmap = ((BitmapDrawable) mImageEdit.getDrawable()).getBitmap();
        Map<Integer, PointF> pointFs = getEdgePoints(tempBitmap);
        mPolygonOutline.setPoints(pointFs);
        mPolygonOutline.setVisibility(View.VISIBLE);
        int padding = (int) getResources().getDimension(R.dimen.scanPadding);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
        layoutParams.gravity = Gravity.CENTER;
        mPolygonOutline.setLayoutParams(layoutParams);
    }

    private boolean isScanPointsValid(Map<Integer, PointF> points) {
        return points.size() == 4;
    }

    private Map<Integer, PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);
        return orderedValidEdgePoints(tempBitmap, pointFs);
    }

    private List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
        float[] points = getPoints(tempBitmap);
        float x1 = points[0];
        float x2 = points[1];
        float x3 = points[2];
        float x4 = points[3];

        float y1 = points[4];
        float y2 = points[5];
        float y3 = points[6];
        float y4 = points[7];

        List<PointF> pointFs = new ArrayList<>();
        pointFs.add(new PointF(x1, y1));
        pointFs.add(new PointF(x2, y2));
        pointFs.add(new PointF(x3, y3));
        pointFs.add(new PointF(x4, y4));
        return pointFs;
    }

    private Map<Integer, PointF> getOutlinePoints(Bitmap tempBitmap) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(tempBitmap.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, tempBitmap.getHeight()));
        outlinePoints.put(3, new PointF(tempBitmap.getWidth(), tempBitmap.getHeight()));
        return outlinePoints;
    }

    private Map<Integer, PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        Map<Integer, PointF> orderedPoints = mPolygonOutline.getOrderedPoints(pointFs);
        if (!mPolygonOutline.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }
        return orderedPoints;
    }

    /**
     * Apply filters on image
     */
    private class FilterTask extends AsyncTask<Void, Bitmap, Bitmap> {

        int taskType;
        View view;
        ProgressDialog progressDialog;

        public FilterTask(int taskType, View view) {
            this.taskType = taskType;
            this.view = view;
            progressDialog = new ProgressDialog(ImageProcessingActivity.this);
            progressDialog.setIndeterminate(true);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (taskType == GET_IMAGE) {
                try {
                    File file = new File(mUri.getPath());
                    if (file.exists()) {
                        mOrientation = BitmapUtil.getBitmapOrientation(file);
                        mBitmap = BitmapUtil.getImageFromUri(ImageProcessingActivity.this, mUri, file);
                    } else {
                        mOrientation = BitmapUtil.getOrientation(ImageProcessingActivity.this, mUri);
                        mBitmap = BitmapUtil.getImageFromUri(ImageProcessingActivity.this, mUri, null);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mOriginalBitmap = mBitmap;
                return mBitmap;
            } else if (taskType == EFFECT_ANTICLOCKWISE) {
                mBitmap = getFlipLeftBitmap(mBitmap);
                return mBitmap;
            } else if (taskType == EFFECT_CLOCKWISE) {
                mBitmap = getFlipRightBitmap(mBitmap);
                return mBitmap;
            } else if (taskType == EFFECT_MIRROR) {
                mBitmap = getMirrorBitmap(mBitmap);
                return mBitmap;
            } else if (taskType == EFFECT_MAGIC_COLOR) {
                return getMagicColorBitmap(mBitmap);
            } else if (taskType == EFFECT_GRAY_MODE) {
                return getGrayBitmap(mBitmap);
            } else if (taskType == EFFECT_BW) {
                return getBAndWBitmap(mBitmap);
            } else if (taskType == EFFECT_OCR) {
                return getGrayBitmap(mBitmap);
            } else return mBitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            try {
                progressDialog.dismiss();
            } catch (IllegalArgumentException e) {
            }
            if (taskType == EFFECT_OCR) doOCR(bitmap);
            else {
                if (taskType == GET_IMAGE) initScan();
                else selectViews(view);
                mImageEdit.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * Scan Image and do 3d cropping
     */
    private class ScanAsyncTask extends AsyncTask<Void, Bitmap, Bitmap> {

        private Map<Integer, PointF> points;
        int sWidth, sHeight;

        public ScanAsyncTask(Map<Integer, PointF> points) {
            this.points = points;
            mPolygonOutline.setVisibility(View.GONE);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            sWidth = mImageEdit.getWidth();
            sHeight = mImageEdit.getHeight();
        }

        @Override
        protected void onProgressUpdate(Bitmap... values) {
            super.onProgressUpdate(values);
            mImageEdit.setImageBitmap(values[0]);
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Map<Integer, PointF> aPoints = new HashMap<>();
            PointF pointF;
            for (float i = 0, j = sWidth, k = 0, l = sWidth,
                 a = 0, b = 0, c = sHeight, d = sHeight;
                 i < points.get(0).x || j > points.get(1).x || k < points.get(2).x || l > points.get(3).x ||
                         a < points.get(0).y || b < points.get(1).y || c > points.get(2).y || d > points.get(3).y;
                 i += points.get(0).x / STEPS, j -= (sWidth - points.get(1).x) / STEPS, k += points.get(2).x / STEPS, l -= (sWidth - points.get(3).x) / STEPS,
                         a += points.get(0).y / STEPS, b += points.get(1).y / STEPS, c -= (sHeight - points.get(2).y) / STEPS, d -= (sHeight - points.get(3).y) / STEPS) {

                pointF = new PointF();
                pointF.set(i, a);
                aPoints.put(0, pointF);

                pointF = new PointF();
                pointF.set(j, b);
                aPoints.put(1, pointF);

                pointF = new PointF();
                pointF.set(k, c);
                aPoints.put(2, pointF);

                pointF = new PointF();
                pointF.set(l, d);
                aPoints.put(3, pointF);
                publishProgress(getScannedBitmap(mBitmap, aPoints, sWidth, sHeight));
            }
            mBitmap = getScannedBitmap(mBitmap, points, sWidth, sHeight);
            // Matrix m = new Matrix();
            //m.setRectToRect(new RectF(0, 0, mBitmap.getWidth(), mBitmap.getHeight()),
            //       new RectF(0, 0, mBitmap.getWidth() * 1.5f, mBitmap.getHeight() * 1.5f), Matrix.ScaleToFit.FILL);
            // mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, true);
            //mUnProcessedBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, true);

            mBitmap = getScaledBitmap(sWidth, sHeight, mBitmap);
            return mBitmap;
        }


        private Bitmap getScaledBitmap(int maxWidth, int maxHeight, Bitmap image) {
            if (maxHeight > 0 && maxWidth > 0) {
                int width = image.getWidth();
                int height = image.getHeight();
                float ratioBitmap = (float) width / (float) height;
                float ratioMax = (float) maxWidth / (float) maxHeight;

                int finalWidth = maxWidth;
                int finalHeight = maxHeight;
                if (ratioMax > 1) {
                    finalWidth = (int) ((float) maxHeight * ratioBitmap);
                } else {
                    finalHeight = (int) ((float) maxWidth / ratioBitmap);
                }
                image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
                return image;
            } else {
                return image;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mImageEdit.setImageBitmap(bitmap);
            mHorizontalTop.setVisibility(View.VISIBLE);
            mPolygonOutline.setVisibility(View.GONE);
            mImageMirror.setVisibility(View.VISIBLE);
            mImageRotateLeft.setVisibility(View.VISIBLE);
            mImageRotateRight.setVisibility(View.VISIBLE);
            mImageOcr.setVisibility(View.VISIBLE);
            selectViews(mTextOriginal);
            mIsCropped = true;
        }
    }

    private Bitmap getScannedBitmap(Bitmap original, Map<Integer, PointF> points, int sWidth, int sHeight) {

        float xRatio = (float) original.getWidth() / sWidth;
        float yRatio = (float) original.getHeight() / sHeight;

        float x1 = (points.get(0).x) * xRatio;
        float x2 = (points.get(1).x) * xRatio;
        float x3 = (points.get(2).x) * xRatio;
        float x4 = (points.get(3).x) * xRatio;
        float y1 = (points.get(0).y) * yRatio;
        float y2 = (points.get(1).y) * yRatio;
        float y3 = (points.get(2).y) * yRatio;
        float y4 = (points.get(3).y) * yRatio;
        return getScannedBitmap(original, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    /**
     * Set Selected views for top icons and deselect all other views
     *
     * @param view Views to select
     */
    private void selectViews(View view) {
        mTextOriginal.setSelected(false);
        mTextGrayMode.setSelected(false);
        mTextMagicColor.setSelected(false);
        mTextBlackWhite.setSelected(false);
        view.setSelected(true);
    }


    private void doOCR(final Bitmap bitmap) {
        ArrayList<String> ocrResults = new ArrayList<>();
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog.show(this, "Processing",
                    "Doing OCR...", true);
        } else {
            mProgressDialog.show();
        }

        new Thread(() -> {
            String result = null;

            Bitmap[] bitmapsForOcr = getBitmapsForOcr(mBitmap);
            for (Bitmap myBitmap : bitmapsForOcr) {
                result = mTessOCR.getOCRResult(myBitmap);
                ocrResults.add(result);
            }

            runOnUiThread(() -> {
                mProgressDialog.dismiss();
                new AlertDialog.Builder(ImageProcessingActivity.this)
                        .setTitle("OCR Text")
                        .setMessage(TextUtils.join("\n", ocrResults))
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
                /*startActivity(new Intent(ImageProcessingActivity.this, ActivityOcr.class)
                        .putStringArrayListExtra(OCR_DATA, ocrResults));*/
            });

        }).start();
    }

    static {
        System.loadLibrary("opencv_java");
        System.loadLibrary("ImageProcessing");
    }

    public native String getText(String text);

    public native Bitmap getScannedBitmap(Bitmap bitmap, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4);

    public native Bitmap getGrayBitmap(Bitmap bitmap);

    public native Bitmap getMagicColorBitmap(Bitmap bitmap);

    public native Bitmap getBAndWBitmap(Bitmap bitmap);

    public native Bitmap getMirrorBitmap(Bitmap bitmap);

    public native Bitmap getFlipRightBitmap(Bitmap bitmap);

    public native Bitmap getFlipLeftBitmap(Bitmap bitmap);

    public native Bitmap[] getBitmapsForOcr(Bitmap bitmap);

    public native float[] getPoints(Bitmap bitmap);

}