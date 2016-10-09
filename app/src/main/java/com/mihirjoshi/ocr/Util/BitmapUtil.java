package com.mihirjoshi.ocr.Util;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to do bitmap related operations
 */
public class BitmapUtil {

    private static final int KILOBYTES = 16 * 1024;
    private static final int ORIENTATION_90 = 90;
    private static final int ORIENTATION_180 = 180;
    private static final int ORIENTATION_270 = 270;
    private static final int SAMPLE_SIZE = 2;

    private BitmapUtil() {
    }

    /**
     * Get a mirror Bitmap
     *
     * @param sourceBitmap Bitmap to Change
     * @return Mirror bitmap
     */
    public static Bitmap getMirrorBitmap(Bitmap sourceBitmap) {
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        Bitmap dst = Bitmap.createBitmap(sourceBitmap, 0, 0,
                sourceBitmap.getWidth(), sourceBitmap.getHeight(), m, false);
        dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        return dst;
    }

    /**
     * Scale a bitmap and correct the dimensions
     *
     * @param bitmap      Bitmap to scale
     * @param width       width for scaling
     * @param height      height for scaling
     * @param orientation Current orientation of the Image
     * @return Scaled bitmap
     */
    public static Bitmap getScaledBitmap(Bitmap bitmap, int width, int height, int orientation) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            m.postRotate(ORIENTATION_90);
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            m.postRotate(ORIENTATION_180);
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            m.postRotate(ORIENTATION_270);
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }


    /**
     * Get Image Orientation from file
     *
     * @param imageFile File for which orientation is changed
     * @return Current Orientation
     */
    public static int getBitmapOrientation(File imageFile) {
        try {
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (IOException e) {
        }
        return 0;
    }

    /**
     * Get Image orientation from uri
     *
     * @param context  Context of image
     * @param photoUri Uri of image
     * @return
     */
    public static int getOrientation(Context context, Uri photoUri) {
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION},
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                if (cursor.getInt(0) == ORIENTATION_270)
                    return ExifInterface.ORIENTATION_ROTATE_270;
                else if (cursor.getInt(0) == ORIENTATION_180)
                    return ExifInterface.ORIENTATION_ROTATE_180;
                else if (cursor.getInt(0) == ORIENTATION_90)
                    return ExifInterface.ORIENTATION_ROTATE_90;
            }
        } finally {
            cursor.close();
        }
        return -1;
    }


    /**
     * Get Bitmap from file
     *
     * @param imageFile - Image file
     * @return Generated Bitmap
     */
    public static Bitmap getBitmapFromFile(File imageFile) {
        BitmapFactory.Options bitmapDecodeOptions = new BitmapFactory.Options();
        bitmapDecodeOptions.inTempStorage = new byte[KILOBYTES];
        bitmapDecodeOptions.inSampleSize = SAMPLE_SIZE;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bitmapDecodeOptions);
    }


    /**
     * Get Bitmap from Uri
     *
     * @param uri Uri to get Bitmap
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static Bitmap getImageFromUri(Activity activity, Uri uri, File file) throws IOException {

        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither = true;//optional
        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional
        if (file != null) {
            BitmapFactory.decodeFile(file.getAbsolutePath(), onlyBoundsOptions);
        } else {
            InputStream input = activity.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
            input.close();
        }

        if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1))
            return null;

        float scale = activity.getResources().getDisplayMetrics().density;
        int pHeight = (int) (activity.getResources().getConfiguration().screenHeightDp * scale + 0.5f);
        int originalSize = (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) ? onlyBoundsOptions.outHeight : onlyBoundsOptions.outWidth;

        double ratio = (originalSize > pHeight) ? (originalSize / pHeight) : 1.0;

        int REQUIRED_SIZE = activity.getResources().getDisplayMetrics().heightPixels / 2;

        /**/
        int Scale = 1;
        while (onlyBoundsOptions.outWidth / Scale / 2 >= REQUIRED_SIZE &&
                onlyBoundsOptions.outHeight / Scale / 2 >= REQUIRED_SIZE) {
            Scale *= 2;
        }
        /**/

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = Scale;//getPowerOfTwoForSampleRatio(ratio);
        bitmapOptions.inDither = true;//optional
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional

        Bitmap bitmap;
        if (file != null) {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), bitmapOptions);
        } else {
            InputStream input = activity.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
            input.close();
        }

        return bitmap;
    }

    /**
     * Bitmap ration in power of two
     *
     * @param ratio present ratio
     * @return insample ratio
     */
    private static int getPowerOfTwoForSampleRatio(double ratio) {
        int k = Integer.highestOneBit((int) Math.floor(ratio));
        if (k == 0)
            return 1;
        else
            return k;
    }


    /**
     * Get Image Uri when clicked from Camera
     *
     * @return Uri of clicked Image
     */
    public static Uri getBitmapUri() {
        File imageStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "CRMnextImages");
        if (!imageStorageDir.exists()) {
            imageStorageDir.mkdirs();
        }
        File file = null;
        try {
            file = File.createTempFile(
                    "IMG_" + String.valueOf(System.currentTimeMillis()),
                    ".jpg",
                    imageStorageDir
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Uri.fromFile(file);
    }
}