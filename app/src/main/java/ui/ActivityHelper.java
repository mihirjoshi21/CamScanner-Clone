package ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.mihirjoshi.ocr.camera.CameraActivity;

import io.card.payment.CardIOActivity;


/**
 * Activity Helper class to perform common operations
 */
public final class ActivityHelper {

    private static final CharSequence TAKE_PHOTO = "Take Photo";
    private static final CharSequence CHOOSE_PHOTO = "Choose Photo";
    private static final CharSequence CARD_READER = "Card Reader";
    private static final CharSequence CANCEL = "Cancel";
    private static final String PHOTO_MESSAGE = "Select Photo";

    private ActivityHelper() {
    }

    /**
     * Open Image Selection dialog
     *
     * @param activity          - Activity reference
     * @param activityCamera    - Request Code Camera
     * @param activitySelection - Request Code File
     */
    public static void selectImage(Activity activity, int activityCamera, int activitySelection, Uri uri) {
        final CharSequence[] items = {TAKE_PHOTO, CHOOSE_PHOTO, CARD_READER, CANCEL};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(PHOTO_MESSAGE);
        builder.setCancelable(false);
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals(TAKE_PHOTO)) {
                Intent captureIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    captureIntent = new Intent(activity, CameraActivity.class);
                } else {
                    captureIntent = new Intent(
                            MediaStore.ACTION_IMAGE_CAPTURE);
                }
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                activity.startActivityForResult(captureIntent,
                        activityCamera);
            } else if (items[item].equals(CHOOSE_PHOTO)) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, CHOOSE_PHOTO),
                        activitySelection);
            } else if (items[item].equals(CARD_READER)) {
                Intent scanIntent = new Intent(activity, CardIOActivity.class);

                scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, false); // default: false
                scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_CVV, false); // default: false
                scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_POSTAL_CODE, false); // default: false
                scanIntent.putExtra(CardIOActivity.EXTRA_RESTRICT_POSTAL_CODE_TO_NUMERIC_ONLY, false); // default: false
                scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_CARDHOLDER_NAME, false); // default: false

                // hides the manual entry button
                // if set, developers should provide their own manual entry mechanism in the app
                scanIntent.putExtra(CardIOActivity.EXTRA_SUPPRESS_MANUAL_ENTRY, false); // default: false

                // matches the theme of your application
                scanIntent.putExtra(CardIOActivity.EXTRA_KEEP_APPLICATION_THEME, false);
                activity.startActivityForResult(scanIntent, 333);
            } else if (items[item].equals(CANCEL)) {
                activity.finish();
            }
        });
        builder.show();
    }
}