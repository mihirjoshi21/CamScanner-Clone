//
// Created by mihirj on 10/1/2015.
//

#include "com_mihirjoshi_ocr_ImageProcessingActivity.h"
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <algorithm>
#include <string>
#include <vector>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <opencv2/highgui/highgui.hpp>
#include <android/bitmap.h>
#define APPNAME "ImageProcessing"

using namespace cv;
using namespace std;


std::vector<cv::Rect> detectLetters(cv::Mat img)
{
    std::vector<cv::Rect> boundRect;
    cv::Mat img_gray, img_sobel, img_threshold, element;
    cvtColor(img, img_gray, CV_BGR2GRAY);
    cv::Sobel(img_gray, img_sobel, CV_8U, 1, 0, 3, 1, 0, cv::BORDER_DEFAULT);
    cv::threshold(img_sobel, img_threshold, 0, 255, CV_THRESH_OTSU+CV_THRESH_BINARY);
    element = getStructuringElement(cv::MORPH_RECT, cv::Size(40, 40));
    cv::morphologyEx(img_threshold, img_threshold, CV_MOP_CLOSE, element); //Does the trick
    std::vector< std::vector< cv::Point> > contours;
    cv::findContours(img_threshold, contours, 0, 1);
    std::vector<std::vector<cv::Point> > contours_poly( contours.size() );
    for( int i = 0; i < contours.size(); i++ )
        if (contours[i].size()>50)
        {
            cv::approxPolyDP( cv::Mat(contours[i]), contours_poly[i], 3, true );
            cv::Rect appRect( boundingRect( cv::Mat(contours_poly[i]) ));
            if (appRect.width>appRect.height)
                boundRect.push_back(appRect);
        }
    return boundRect;
}

void Sharpen(const Mat& myImage, Mat& Result)
{
    CV_Assert(myImage.depth() == CV_8U);  // accept only uchar images

    Result.create(myImage.size(), myImage.type());
    const int nChannels = myImage.channels();

    for(int j = 1; j < myImage.rows - 1; ++j)
    {
        const uchar* previous = myImage.ptr<uchar>(j - 1);
        const uchar* current  = myImage.ptr<uchar>(j    );
        const uchar* next     = myImage.ptr<uchar>(j + 1);

        uchar* output = Result.ptr<uchar>(j);

        for(int i = nChannels; i < nChannels * (myImage.cols - 1); ++i)
        {
            *output++ = saturate_cast<uchar>(5 * current[i]
                                             -current[i - nChannels] - current[i + nChannels] - previous[i] - next[i]);
        }
    }

    Result.row(0).setTo(Scalar(0));
    Result.row(Result.rows - 1).setTo(Scalar(0));
    Result.col(0).setTo(Scalar(0));
    Result.col(Result.cols - 1).setTo(Scalar(0));
}

double angle( cv::Point pt1, cv::Point pt2, cv::Point pt0 ) {
    double dx1 = pt1.x - pt0.x;
    double dy1 = pt1.y - pt0.y;
    double dx2 = pt2.x - pt0.x;
    double dy2 = pt2.y - pt0.y;
    return (dx1*dx2 + dy1*dy2)/sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
}

vector<Point> getPoints(Mat image)
{
    int width = image.size().width;
    int height = image.size().height;
    Mat image_proc = image.clone();
    vector<vector<Point> > squares;
    // blur will enhance edge detection
    Mat blurred(image_proc);
    medianBlur(image_proc, blurred, 9);

    Mat gray0(blurred.size(), CV_8U), gray;
    vector<vector<Point> > contours;

    // find squares in every color plane of the image
    for (int c = 0; c < 3; c++)
    {
        int ch[] = {c, 0};
        mixChannels(&blurred, 1, &gray0, 1, ch, 1);

        // try several threshold levels
        const int threshold_level = 2;
        for (int l = 0; l < threshold_level; l++)
        {
            // Use Canny instead of zero threshold level!
            // Canny helps to catch squares with gradient shading
            if (l == 0)
            {
                Canny(gray0, gray, 10, 20, 3); //

                // Dilate helps to remove potential holes between edge segments
                dilate(gray, gray, Mat(), Point(-1,-1));
            }
            else
            {
                gray = gray0 >= (l+1) * 255 / threshold_level;
            }

            // Find contours and store them in a list
            findContours(gray, contours, CV_RETR_LIST, CV_CHAIN_APPROX_SIMPLE);

            // Test contours
            vector<Point> approx;
            for (size_t i = 0; i < contours.size(); i++)
            {
                // approximate contour with accuracy proportional
                // to the contour perimeter
                approxPolyDP(Mat(contours[i]), approx, arcLength(Mat(contours[i]), true)*0.02, true);

                // Note: absolute value of an area is used because
                // area may be positive or negative - in accordance with the
                // contour orientation
                if (approx.size() == 4 &&
                    fabs(contourArea(Mat(approx))) > 1000 &&
                    isContourConvex(Mat(approx)))
                {
                    double maxCosine = 0;

                    for (int j = 2; j < 5; j++)
                    {
                        double cosine = fabs(angle(approx[j%4], approx[j-2], approx[j-1]));
                        maxCosine = MAX(maxCosine, cosine);
                    }

                    if (maxCosine < 0.3)
                        squares.push_back(approx);
                }
            }
        }

        double largest_area = -1;
        int largest_contour_index = 0;
        for(int i=0;i<squares.size();i++)
        {
            double a =contourArea(squares[i],false);
            if(a>largest_area)
            {
                largest_area = a;
                largest_contour_index = i;
            }
        }

        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "size() %d",squares.size());
        vector<Point> points;
        if(squares.size() > 0)
        {
            points = squares[largest_contour_index];
        }
        else
        {
            points.push_back(Point(0, 0));
            points.push_back(Point(width, 0));
            points.push_back(Point(0, height));
            points.push_back(Point(width, height));
        }

        return points;
    }
}

Point2f computePoint(int p1, int p2) {
    Point2f pt;
    pt.x = p1;
    pt.y = p2;
    return pt;
}

Mat scan(Mat img, jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat x3, jfloat y3, jfloat x4, jfloat y4) {

    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "scan() %f",x1);
    // define the destination image size:

    float w1 = sqrt( pow(x4 - x3 , 2) + pow(x4 - x3, 2));
    float w2 = sqrt( pow(x2 - x1 , 2) + pow(x2-x1, 2));
    float h1 = sqrt( pow(y2 - y4 , 2) + pow(y2 - y4, 2));
    float h2 = sqrt( pow(y1 - y3 , 2) + pow(y1-y3, 2));

    float maxWidth = (w1 < w2) ? w1 : w2;
    float maxHeight = (h1 < h2) ? h1 : h2;

    Mat dst = Mat::zeros(maxHeight, maxWidth, CV_8UC3);

    // corners of destination image with the sequence [tl, tr, bl, br]
    vector<Point2f> dst_pts, img_pts;
    dst_pts.push_back(Point(0, 0));
    dst_pts.push_back(Point(maxWidth - 1, 0));
    dst_pts.push_back(Point(0, maxHeight - 1));
    dst_pts.push_back(Point(maxWidth - 1, maxHeight - 1));

    img_pts.push_back(computePoint(x1,y1));
    img_pts.push_back(computePoint(x2,y2));
    img_pts.push_back(computePoint(x3,y3));
    img_pts.push_back(computePoint(x4,y4));

    // get transformation matrix
    Mat transmtx = getPerspectiveTransform(img_pts, dst_pts);
    warpPerspective(img, dst, transmtx, dst.size());

    return dst;
}

jobject mat_to_bitmap(JNIEnv * env, Mat & src, bool needPremultiplyAlpha, jobject bitmap_config){
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetStaticMethodID(java_bitmap_class,
                                           "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(java_bitmap_class,
                                                 mid, src.size().width, src.size().height, bitmap_config);
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
        CV_Assert(src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4);
        CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
        CV_Assert(pixels);
        if(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888){
            Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(src.type() == CV_8UC1){
                cvtColor(src, tmp, CV_GRAY2RGBA);
            }else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, CV_RGB2RGBA);
            }else if(src.type() == CV_8UC4){
                if(needPremultiplyAlpha){
                    cvtColor(src, tmp, COLOR_RGBA2mRGBA);
                }else{
                    src.copyTo(tmp);
                }
            }
        }else{
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(src.type() == CV_8UC1){
                cvtColor(src, tmp, CV_GRAY2BGR565);
            }else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, CV_RGB2BGR565);
            }else if(src.type() == CV_8UC4){
                cvtColor(src, tmp, CV_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return bitmap;
    }catch(cv::Exception e){
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("org/opencv/core/CvException");
        if(!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return bitmap;
    }catch (...){
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return bitmap;
    }
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getScannedBitmap
        (JNIEnv *env, jobject thiz, jobject bitmap,jfloat x1,jfloat y1,jfloat x2, jfloat y2, jfloat x3,jfloat y3,jfloat x4,jfloat y4)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getString");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    // init our output image
    Mat mbgra(info.height, info.width, CV_8UC4, pixels);
    Mat dst = scan(mbgra,x1,y1,x2,y2,x3,y3,x4,y4);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,dst,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getMagicColorBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getMagicColorBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    Mat mbgra(info.height, info.width, CV_8UC4, pixels);
    Mat dst = mbgra.clone();
    // init our output image
    float alpha = 2.0;
    float beta = -80;
    dst.convertTo(dst, -1, alpha, beta);
    Sharpen(dst, dst);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,dst,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;

    /*vector<Mat> channels;
    Mat img_hist_equalized;
    cvtColor(dst, img_hist_equalized, CV_BGR2YCrCb);
    split(img_hist_equalized,channels);
    equalizeHist(channels[0], channels[0]);
    merge(channels,img_hist_equalized);
    cvtColor(img_hist_equalized, img_hist_equalized, CV_YCrCb2BGR);*/
    dst.convertTo(mbgra, -1, alpha, beta);
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getFlipLeftBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getFlipRightBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    // init our input image
    Mat mbgra(info.height, info.width, CV_8UC4, pixels);

    // Transpose
    transpose(mbgra, mbgra);
    flip(mbgra, mbgra,0);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,mbgra,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getFlipRightBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getFlipRightBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    // init our input image
    Mat mbgra(info.height, info.width, CV_8UC4, pixels);

    // Transpose
    transpose(mbgra, mbgra);
    flip(mbgra, mbgra,1);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,mbgra,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getMirrorBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getMirrorBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    // init our input image
    Mat mbgra(info.height, info.width, CV_8UC4, pixels);

    // Flip
    cv::Mat dst;
    cv::flip(mbgra,dst,1);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,dst,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getBAndWBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getBWBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    Mat mbgra(info.height, info.width, CV_8UC4, pixels);
    Mat dst = mbgra.clone();

    cvtColor(mbgra, dst, CV_BGR2GRAY);
    threshold(dst,dst,0,255,THRESH_BINARY | THRESH_OTSU);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,dst,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}

JNIEXPORT jobject JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getGrayBitmap
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getGrayBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    Mat mbgra(info.height, info.width, CV_8UC4, pixels);
    Mat dst = mbgra.clone();
    //cvtColor(mbgra, mbgra, CV_RGB2GRAY);
          //equalizeHist( mbgra, mbgra );
    //cv::GaussianBlur(mbgra,dst,cv::Size(7, 7),0,0, BORDER_REPLICATE);
         //cv::addWeighted(mbgra, 1.5, dst, -0.5, 0, dst);
    //cv::adaptiveThreshold(dst, dst, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY, 7, 2);
    //resize(dst, dst, dst.size(), 10, 10, INTER_AREA );

    /*std::vector<cv::Rect> letterBBoxes=detectLetters(dst);
    //Display
    for(int i=0; i< letterBBoxes.size(); i++) {
        cv::Rect bigger_rect = letterBBoxes[i];
        bigger_rect.height += 15;
        bigger_rect.width += 15;
        bigger_rect.x -=15;
        bigger_rect.y -=15;
        cv::rectangle(dst, bigger_rect, cv::Scalar(255, 255, 0), 2, 8, 0);
        //__android_log_print(ANDROID_LOG_VERBOSE, APPNAME, letterBBoxes[i]);
    }*/

    /**
     * Test code
     */

    Mat rgb = mbgra.clone();
    // downsample and use it for processing
    //pyrDown(mbgra, rgb);
    Mat small;
    cvtColor(rgb, small, CV_BGR2GRAY);
    // morphological gradient
    Mat grad;
    Mat morphKernel = getStructuringElement(MORPH_ELLIPSE, Size(3, 3));
    morphologyEx(small, grad, MORPH_GRADIENT, morphKernel);
    // binarize
    Mat bw;
    threshold(grad, bw, 0.0, 255.0, THRESH_BINARY | THRESH_OTSU);
    // connect horizontally oriented regions
    Mat connected;
    morphKernel = getStructuringElement(MORPH_RECT, Size(20, 1));
    morphologyEx(bw, connected, MORPH_CLOSE, morphKernel);
    // find contours
    Mat mask = Mat::zeros(bw.size(), CV_8UC1);
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    findContours(connected, contours, hierarchy, CV_RETR_CCOMP, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));
    // filter contours
    int counter = 0;
    for(int idx = 0; idx >= 0; idx = hierarchy[idx][0])
    {
        Rect rect = boundingRect(contours[idx]);
        Mat maskROI(mask, rect);
        maskROI = Scalar(0, 0, 0);
        // fill the contour
        drawContours(mask, contours, idx, Scalar(255, 255, 255), CV_FILLED);
        // ratio of non-zero pixels in the filled region
        double r = (double)countNonZero(maskROI)/(rect.width*rect.height);

        if (r > .45 /* assume at least 45% of the area is filled if it contains text */
            &&
            (rect.height > 8 && rect.width > 8) /* constraints on region size */
            /* these two conditions alone are not very robust. better to use something
            like the number of significant peaks in a horizontal projection as a third condition */

                )
        {
            rectangle(rgb, rect, Scalar(0, 255, 0), 2);
            if(counter==6)
            dst = mbgra(rect);
            counter++;
        }
    }
    /**
     * Test Code ends
     */

    //Mat image_roi = mbgra(letterBBoxes[0]); //mbgra.clone();
    cvtColor(dst, dst, CV_RGB2GRAY);
    cv::GaussianBlur(dst,dst,cv::Size(5, 5),0,0, BORDER_REPLICATE);
    cv::adaptiveThreshold(dst, dst, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY, 5, 2);

    //get source bitmap's config
    jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
    jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
    jobject _bitmap = mat_to_bitmap(env,rgb,false,bitmap_config);

    AndroidBitmap_unlockPixels(env, bitmap);
    return _bitmap;
}


JNIEXPORT jobjectArray JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getBitmapsForOcr
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getGrayBitmap");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return NULL;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    Mat mbgra(info.height, info.width, CV_8UC4, pixels);
    Mat dst = mbgra.clone();

    /**
     * Test code
     */
    Mat rgb = mbgra.clone();
    int rows = rgb.rows;
    int cols = rgb.cols;
    cv::Size s = rgb.size();
    rows = s.height;
    cols = s.width;
    rgb(Rect(0, 0, rgb.cols/1.6, rgb.rows)).copyTo(rgb);  // TO REMOVE BEFORE GITHUB
    // downsample and use it for processing
    //resize(rgb,rgb,Size( rgb.cols/1.7, rgb.rows/1.7 ) );
    Mat small;
    cvtColor(rgb, small, CV_BGR2GRAY);
    // morphological gradient
    Mat grad;
    Mat morphKernel = getStructuringElement(MORPH_ELLIPSE, Size(3, 3));
    morphologyEx(small, grad, MORPH_GRADIENT, morphKernel);
    // binarize
    Mat bw;
    threshold(grad, bw, 0.0, 255.0, THRESH_BINARY | THRESH_OTSU);
    // connect horizontally oriented regions
    Mat connected;
    morphKernel = getStructuringElement(MORPH_RECT, Size(20, 1));
    morphologyEx(bw, connected, MORPH_CLOSE, morphKernel);
    // find contours
    Mat mask = Mat::zeros(bw.size(), CV_8UC1);
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    findContours(connected, contours, hierarchy, CV_RETR_CCOMP, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));
    // filter contours
    std::vector<cv::Rect> boundRect;
    for(int idx = 0; idx >= 0; idx = hierarchy[idx][0])
    {
        Rect rect = boundingRect(contours[idx]);
        Mat maskROI(mask, rect);
        maskROI = Scalar(0, 0, 0);
        // fill the contour
        drawContours(mask, contours, idx, Scalar(255, 255, 255), CV_FILLED);
        // ratio of non-zero pixels in the filled region
        double r = (double)countNonZero(maskROI)/(rect.width*rect.height);

        if (r > .45 /* assume at least 45% of the area is filled if it contains text */
            &&
            (rect.height > 8 && rect.width > 8) /* constraints on region size */
            /* these two conditions alone are not very robust. better to use something
            like the number of significant peaks in a horizontal projection as a third condition */

                )
        {
            rectangle(rgb, rect, Scalar(0, 255, 0), 2);
            boundRect.push_back(rect);

        }
    }
    /**
     * Test Code ends
     */

    //Mat image_roi = mbgra(letterBBoxes[0]); //mbgra.clone();


    //get source bitmap's config
    int sizeRect = boundRect.size();
    jobjectArray strarr = env->NewObjectArray(sizeRect, (jclass)env->FindClass("android/graphics/Bitmap"), 0);
    for (int i = 0; i < sizeRect; i++)
    {
        dst = mbgra(boundRect[i]);
        cvtColor(dst, dst, CV_RGB2GRAY);
        cv::GaussianBlur(dst,dst,cv::Size(5, 5),0,0, BORDER_REPLICATE);
        cv::adaptiveThreshold(dst, dst, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY, 5, 2);
        jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
        jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig", "()Landroid/graphics/Bitmap$Config;");
        jobject bitmap_config = env->CallObjectMethod(bitmap, mid);
        jobject _bitmap = mat_to_bitmap(env,dst,false,bitmap_config);
        AndroidBitmap_unlockPixels(env, bitmap);

        env->SetObjectArrayElement(strarr, i, _bitmap);
    }

    return strarr;
}

JNIEXPORT jfloatArray JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getPoints
        (JNIEnv *env, jobject thiz,jobject bitmap)
{
    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "getPoints");
    int ret;
    AndroidBitmapInfo info;
    void* pixels = 0;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_getInfo() failed ! error=%d", ret);
        return 0;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 )
    {       __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"Bitmap format is not RGBA_8888!");
        return 0;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, APPNAME,"AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    // init our output image
    Mat mbgra(info.height, info.width, CV_8UC4, pixels);

    vector<Point> img_pts = getPoints(mbgra);

    jfloatArray jArray = env->NewFloatArray(8);

    if (jArray != NULL)
    {
        jfloat *ptr = env->GetFloatArrayElements(jArray, NULL);

        for (int i=0,j=i+4; j<8; i++,j++)
        {
            ptr[i] = img_pts[i].x;
            ptr[j] = img_pts[i].y;
        }
        env->ReleaseFloatArrayElements(jArray, ptr, NULL);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return jArray;
}

JNIEXPORT jstring JNICALL Java_com_mihirjoshi_ocr_ImageProcessingActivity_getText
        (JNIEnv *env, jobject obj, jstring s){
    return env->NewStringUTF("Test");
}
