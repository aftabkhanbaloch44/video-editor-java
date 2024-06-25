package com.iknow.android.features.trim;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.iknow.android.interfaces.VideoTrimListener;
import com.iknow.android.utils.ToastUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import iknow.android.utils.DeviceUtil;
import iknow.android.utils.UnitConverter;
import iknow.android.utils.callback.SingleCallback;
import iknow.android.utils.thread.BackgroundExecutor;
import nl.bravobit.ffmpeg.FFcommandExecuteResponseHandler;
import nl.bravobit.ffmpeg.FFmpeg;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
public class VideoTrimmerUtil {

    public static final long MIN_SHOOT_DURATION = 3000L;
    public static final int VIDEO_MAX_TIME = 10;// 10秒
    public static final long MAX_SHOOT_DURATION = VIDEO_MAX_TIME * 1000L;
    public static final int MAX_COUNT_RANGE = 10;  //seekBar的区域内一共有多少张图片
    public static final int RECYCLER_VIEW_PADDING = UnitConverter.dpToPx(15);
    public static final int THUMB_WIDTH = UnitConverter.dpToPx(50);
    private static final String TAG = VideoTrimmerUtil.class.getSimpleName();
    private static final int SCREEN_WIDTH_FULL = DeviceUtil.getDeviceWidth();
    public static final int VIDEO_FRAMES_WIDTH = SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 4;
    private static final int THUMB_HEIGHT = UnitConverter.dpToPx(50);

    public static void trim(Context context, String inputFile, String outputFile, long startMs, long endMs, final VideoTrimListener callback) {
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String outputName = "trimmedVideo_" + timeStamp + ".mp4";

        String start = convertSecondsToTime(startMs / 1000);
        String duration = convertSecondsToTime((endMs - startMs) / 1000);
        String outputPath = outputFile + "/" + outputName;

        FFmpeg ffmpeg = FFmpeg.getInstance(context);

        if (ffmpeg.isSupported()) {
            // Create the command
            String[] command = {
                    "-y", // Overwrite output files
                    "-i", inputFile, // Input file path
                    "-ss", start, // Start time in seconds
                    "-t", duration, // Duration of the output video
                    "-c", "copy", // Copy streams without re-encoding
                    outputPath // Output file path
            };

            try {
                // Execute the command
                ffmpeg.execute(command, new FFcommandExecuteResponseHandler() {
                    @Override
                    public void onSuccess(String message) {
                        // Success callback
                        callback.onTrimSuccess();
                        Log.d("---->FFmpeg", "Trimming successful: " + message);
                    }

                    @Override
                    public void onProgress(String message) {
                        // Progress callback
                        callback.onTrimProgress();
                        Log.d("---->FFmpeg", "Progress: " + message);
                    }

                    @Override
                    public void onFailure(String message) {
                        // Failure callback
                        callback.onTrimFailure();
                        Log.e("---->FFmpeg", "Trimming failed: " + message);
                        Log.e("---->FFmpeg", "Path: " + outputPath);
                    }

                    @Override
                    public void onStart() {
                        // Command start callback
                        callback.onTrimStart();
                        Log.d("---->FFmpeg", "Trimming started.");
                    }

                    @Override
                    public void onFinish() {
                        // Command finish callback
                        callback.onTrimFinish(outputPath);
                        Log.d("---->FFmpeg", "Trimming finished.");
                    }
                });
            } catch (Exception ignored) {
            }
        } else {
            // FFmpeg is not supported
            Log.e("---->FFmpeg", "FFmpeg is not supported on this device.");
        }


    }

    public static void shootVideoThumbInBackground(final Context context, final Uri videoUri, final long videoDuration, final SingleCallback<Bitmap, Integer> callback) {
        // Determine the thumbnail width (adjust this if you need a different size)
        final int thumbnailsCount = VIDEO_FRAMES_WIDTH / (THUMB_WIDTH / 2);
        final long interval = videoDuration / (thumbnailsCount * 1000L)+1;

        BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
            @Override
            public void execute() {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                try {
                    mediaMetadataRetriever.setDataSource(context, videoUri);
                    for (int i = 0; i < thumbnailsCount-1; ++i) {
                        long frameTime = i * interval;
                        Log.d("FRAME TAKEN AT:", frameTime + "s");
                        Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(frameTime * 1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (bitmap == null) continue;
                        bitmap = Bitmap.createScaledBitmap(bitmap, THUMB_WIDTH, THUMB_HEIGHT, false);
                        callback.onSingleCallback(bitmap, (int) interval);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error retrieving video frames: ", e);
                } finally {
                    mediaMetadataRetriever.release();
                }
            }
        });
    }


    private static String convertSecondsToTime(long seconds) {
        String timeStr;
        int hour;
        int minute;
        int second;
        if (seconds <= 0) {
            return "00:00";
        } else {
            minute = (int) seconds / 60;
            if (minute < 60) {
                second = (int) seconds % 60;
                timeStr = "00:" + unitFormat(minute) + ":" + unitFormat(second);
            } else {
                hour = minute / 60;
                if (hour > 99) return "99:59:59";
                minute = minute % 60;
                second = (int) (seconds - hour * 3600 - minute * 60);
                timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
            }
        }
        return timeStr;
    }

    private static String unitFormat(int i) {
        String retStr;
        if (i >= 0 && i < 10) {
            retStr = "0" + i;
        } else {
            retStr = "" + i;
        }
        return retStr;
    }
}
