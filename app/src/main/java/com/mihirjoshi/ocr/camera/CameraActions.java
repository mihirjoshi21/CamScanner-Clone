package com.mihirjoshi.ocr.camera;

import android.hardware.Camera;

/**
 * Default implementation of CameraActions
 */
public class CameraActions implements EasyCamera.CameraActions {

    private EasyCamera camera;

    CameraActions(EasyCamera camera) {
        this.camera = camera;
    }

    @Override
    public void takePicture(final EasyCamera.Callbacks callbacks) {
        camera.getRawCamera().takePicture(rawCallbackOrNull(callbacks.getShutterCallback()),
                rawCallbackOrNull(callbacks.getRawCallback()),
                rawCallbackOrNull(callbacks.getPostviewCallback()), (data, camera1) -> {
                    if (callbacks.getJpegCallback() != null) {
                        callbacks.getJpegCallback().onPictureTaken(data, CameraActions.this);
                    }
                    if (callbacks.isRestartPreviewAfterCallbacks()) {
                        camera1.startPreview();
                    }
                });
    }

    @Override
    public EasyCamera getCamera() {
        return camera;
    }

    private Camera.PictureCallback rawCallbackOrNull(final EasyCamera.PictureCallback callback) {
        if (callback != null) {
            return (data, camera1) -> callback.onPictureTaken(data, CameraActions.this);
        }
        return null;
    }

    private Camera.ShutterCallback rawCallbackOrNull(final EasyCamera.ShutterCallback callback) {
        if (callback != null) {
            return callback::onShutter;
        }
        return null;
    }
}
