package com.iknow.android.features.trim;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.iknow.android.interfaces.VideoMergeListener;
import com.iknow.android.interfaces.VideoTrimListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import iknow.android.utils.DeviceUtil;
import iknow.android.utils.UnitConverter;
import iknow.android.utils.callback.SingleCallback;
import iknow.android.utils.thread.BackgroundExecutor;


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
    public static final int RECYCLER_VIEW_PADDING = 0;
    public static final int THUMB_WIDTH = UnitConverter.dpToPx(50);
    private static final String TAG = VideoTrimmerUtil.class.getSimpleName();
    private static final int SCREEN_WIDTH_FULL = DeviceUtil.getDeviceWidth();
    public static final int VIDEO_FRAMES_WIDTH = SCREEN_WIDTH_FULL;
    private static final int THUMB_HEIGHT = UnitConverter.dpToPx(50);

    public static void trim(String inputFile, String outputFile, long startMs, long endMs, final VideoTrimListener callback) {


        final String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        final String outputName = "trimmedVideo_" + uniqueId + ".mp4";

        String start = convertSecondsToTime(startMs / 1000);
        String duration = convertSecondsToTime((endMs - startMs) / 1000);
        String outputPath = outputFile + "/" + outputName;

        Log.d("---->FFmpegKit", "Trimming Starts At: " + start);
        Log.d("---->FFmpegKit", "Trimming Duration: " + duration);

        // Create the command as a single string
        String command = "-y " + // Overwrite output files
                "-i " + inputFile + " " + // Input file path
                "-ss " + start + " " + // Start time in seconds
                "-t " + duration + " " + // Duration of the output video
                "-c copy " + // Copy streams without re-encoding
                outputPath; // Output file path

        callback.onTrimStart();

        // Execute the command
        FFmpegKit.executeAsync(command, session -> {
            ReturnCode returnCode = session.getReturnCode();
            if (ReturnCode.isSuccess(returnCode)) {
                // Success callback
                callback.onTrimSuccess(outputPath);
                Log.d("---->FFmpegKit", "Trimming successful");
            } else {
                // Failure callback
                callback.onTrimFailure();
                Log.e("---->FFmpegKit", "Trimming failed: " + session.getAllLogsAsString());
                Log.e("---->FFmpegKit", "Path: " + outputPath);
            }
            // Command finish callback

            Log.d("---->FFmpegKit", "Trimming finished.");
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
        return (i >= 0 && i < 10) ? "0" + i : String.valueOf(i);
    }

    public static void shootVideoThumbInBackground(final Context context, final Uri videoUri, final long videoDuration, final SingleCallback<Bitmap, Integer> callback) {
        // Determine the thumbnail width (adjust this if you need a different size)
        final int thumbnailsCount = VIDEO_FRAMES_WIDTH / (THUMB_WIDTH / 2);
        final long interval = videoDuration / (thumbnailsCount * 1000L) + 1;

        BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
            @Override
            public void execute() {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                try {
                    mediaMetadataRetriever.setDataSource(context, videoUri);
                    for (int i = 0; i < thumbnailsCount - 1; ++i) {
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
                    try {
                        mediaMetadataRetriever.release();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }


    public static void mergeVideos(Context context, List<String> videoPaths, String outputUri, final VideoMergeListener callback) {
        // Create a unique temporary output file in the app's cache directory
        File tempOutputFile = createUniqueTempFile(context.getCacheDir(), "mergedVideo", ".mp4");
        if (tempOutputFile == null) {
            callback.onMergeFailure();
            return;
        }
        String tempOutputPath = tempOutputFile.getAbsolutePath();

        // Create a temporary file listing all videos to merge
        File listFile = createListFile(context, videoPaths);
        if (listFile == null) {
            callback.onMergeFailure();
            return;
        }

        // Create the FFmpeg command
        String command = "-y -f concat -safe 0 -i " + listFile.getAbsolutePath() + " -c copy " + tempOutputPath;

        // Execute the command
        FFmpegKit.executeAsync(command, session -> {
            ReturnCode returnCode = session.getReturnCode();
            if (ReturnCode.isSuccess(returnCode)) {
                // Move the merged video to MediaStore
                Uri outputVideoUri = moveVideoToMediaStore(context, tempOutputPath);
                if (outputVideoUri != null) {
                    callback.onMergeSuccess(outputVideoUri.toString());
                } else {
                    callback.onMergeFailure();
                }
                Log.e("---->FFmpegKit", "Merging successful.");
            } else {
                callback.onMergeFailure();
                Log.e("---->FFmpegKit", "Merging failed: " + session.getAllLogsAsString());
            }
            // Delete the temporary list file
            listFile.delete();
         //   tempOutputFile.delete();
            Log.d("---->FFmpegKit", "Merging finished.");
        });
    }

    private static File createUniqueTempFile(File directory, String prefix, String suffix) {
        for (int i = 0; i < 100; i++) {
            File tempFile = new File(directory, prefix + "_" + System.currentTimeMillis() + i + suffix);
            if (!tempFile.exists()) {
                return tempFile;
            }
        }
        return null; // If no unique name found after 100 attempts
    }

    private static Uri moveVideoToMediaStore(Context context, String tempFilePath) {


        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MergedVideos");
        values.put(MediaStore.Video.Media.TITLE, new File(tempFilePath).getName());
        values.put(MediaStore.Video.Media.DISPLAY_NAME, new File(tempFilePath).getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());

        Uri videoUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        if (videoUri != null) {
            try (OutputStream out = context.getContentResolver().openOutputStream(videoUri);
                 InputStream in = new FileInputStream(tempFilePath)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return videoUri;
    }


    private static File createListFile(Context context, List<String> videoPaths) {
        File listFile = null;
        try {
            // Create a temporary file
            listFile = File.createTempFile("videolist", ".txt", context.getCacheDir());
            FileWriter writer = new FileWriter(listFile);

            // Write video paths to the file
            for (String videoPath : videoPaths) {
                writer.write("file '" + videoPath + "'\n");
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return listFile;
    }
}
