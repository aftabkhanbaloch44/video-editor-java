package com.iknow.android.features.trim;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.iknow.android.R;
import com.iknow.android.databinding.ItemVideoTrimBinding;
import com.iknow.android.interfaces.VideoMergeListener;
import com.iknow.android.interfaces.VideoTrimListener;
import com.iknow.android.utils.ToastUtil;
import com.iknow.android.widget.VideoTrimmerView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrimmerAdapter extends RecyclerView.Adapter<TrimmerAdapter.ViewHolder> {

    List<String> videoPaths;
    VideoTrimmerActivity activity;
    int currentSelected = 0, successResult = 0, failureResult = 0;

    ArrayList<VideoTrimmerView> trimmerViews = new ArrayList<>();

    VideoTrimmerView trimmerViewAt0;

    public TrimmerAdapter(List<String> videoPaths, VideoTrimmerActivity activity) {
        this.videoPaths = videoPaths;
        this.activity = activity;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new ViewHolder(ItemVideoTrimBinding.inflate(LayoutInflater.from(activity), viewGroup, false));
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, @SuppressLint("RecyclerView") int position) {

        ViewGroup.LayoutParams params = viewHolder.binding.getRoot().getLayoutParams();
        viewHolder.binding.getRoot().setTag(R.id.original_width, params.width);

        ItemVideoTrimBinding mBinding = viewHolder.binding;
        VideoTrimmerView trimmerView = mBinding.videoTrimmerView;

        if (position == 0) trimmerViewAt0 = trimmerView;

        VideoThumbsAdapter mVideoThumbAdapter = trimmerView.mVideoThumbAdapter;

        if (mVideoThumbAdapter == null || mVideoThumbAdapter.mBitmaps == null || mVideoThumbAdapter.mBitmaps.isEmpty()) {
            trimmerView.setVideoView(position == 0 ? activity.mBinding.videoView : activity.mBinding.videoViewDummy);

            if (position == 0)
                trimmerView.setButtons(activity.mBinding.imgViewCancel, activity.mBinding.imgViewDoneDummy, activity.mBinding.imgViewPlay);
            else
                trimmerView.setButtons(activity.mBinding.imgViewCancel, activity.mBinding.imgViewDoneDummy, activity.mBinding.imgViewPlay);

            trimmerView.setOnTrimVideoListener(new VideoTrimListener() {
                @Override
                public void onTrimStart() {
                    if (activity.mBinding.loadingWithMask.getRoot().getVisibility() == View.GONE)
                        activity.mBinding.loadingWithMask.getRoot().setVisibility(View.VISIBLE);
                }

                @Override
                public void onTrimSuccess(String output) {

                    if (!TextUtils.isEmpty(output)) {
                        videoPaths.set(position, output);
                    }

                    successResult++;

                    ToastUtil.longShow(activity, "Success: " + successResult);

                    Log.e("---->FFmpegKit", "Path: " + position + "=>" + output);


                    if (successResult == videoPaths.size()) {

                        successResult = 0;


                        String outputPath = saveMergedVideoPublicly(); // Save the merged video to a public directory
                        ToastUtil.longShow(activity, "Merging at: " + outputPath);

                        VideoTrimmerUtil.mergeVideos(activity, videoPaths, outputPath, new VideoMergeListener() {
                            @Override
                            public void onMergeSuccess(String outputPath) {
                                activity.runOnUiThread(() -> {
                                    activity.mBinding.loadingWithMask.getRoot().setVisibility(View.GONE);
                                    ToastUtil.longShow(activity, "Success: " + outputPath);
                                });
                            }

                            @Override
                            public void onMergeFinish(String outputPath) {
                                activity.runOnUiThread(() -> {
                                    activity.mBinding.loadingWithMask.getRoot().setVisibility(View.GONE);
                                    ToastUtil.longShow(activity, "Finished: " + outputPath);
                                });
                            }

                            @Override
                            public void onMergeFailure() {
                                activity.runOnUiThread(() -> {
                                    activity.mBinding.loadingWithMask.getRoot().setVisibility(View.GONE);
                                    ToastUtil.longShow(activity, "Failed to merge videos.");
                                });
                            }
                        });

                    } else if (failureResult + successResult == videoPaths.size()) {
                        activity.runOnUiThread(() -> {
                            activity.mBinding.loadingWithMask.getRoot().setVisibility(View.GONE);
                            ToastUtil.longShow(activity, "Failed to trim video " + position);
                        });
                        failureResult = 0;
                    }
                }

                @Override
                public void onTrimFailure() {
                    failureResult++;
                    if (failureResult + successResult == videoPaths.size()) {
                        activity.runOnUiThread(() -> {
                            activity.mBinding.loadingWithMask.getRoot().setVisibility(View.GONE);
                            ToastUtil.longShow(activity, "Failed to trim video " + position);
                        });
                        failureResult = 0;
                    }
                }

                @Override
                public void onTrimCancel() {
                    trimmerView.onDestroy();
                }
            });

            if (!trimmerViews.contains(trimmerView))
                trimmerViews.add(trimmerView);

            activity.mBinding.imgViewDone.setOnClickListener(view -> {
                for (VideoTrimmerView trimmer : trimmerViews)
                    trimmer.onTriggered();
            });

            trimmerView.initVideoByURI(Uri.parse(videoPaths.get(position)));
            trimmerView.setRestoreState(true);

            mBinding.btn.setOnClickListener(v -> {
                currentSelected = position;
                notifyDataSetChanged();
            });
        }

        if (position != currentSelected) {
            mBinding.btn.setVisibility(View.VISIBLE);
            LinearLayout seekBarLayout = trimmerView.getRootView().findViewById(R.id.seekBarLayout);
            ImageView pointer = trimmerView.getRootView().findViewById(R.id.positionIcon);
            seekBarLayout.setVisibility(View.GONE);
            pointer.setVisibility(View.GONE);
        } else {
            mBinding.videoTrimmerView.replaceVideoView(activity.mBinding.videoView, videoPaths.get(position));
            trimmerView.setButtons(activity.mBinding.imgViewCancel, activity.mBinding.imgViewDoneDummy, activity.mBinding.imgViewPlay);
            mBinding.btn.setVisibility(View.GONE);
            LinearLayout seekBarLayout = trimmerView.getRootView().findViewById(R.id.seekBarLayout);
            ImageView pointer = trimmerView.getRootView().findViewById(R.id.positionIcon);
            seekBarLayout.setVisibility(View.VISIBLE);
            pointer.setVisibility(View.VISIBLE);
        }
    }


    public void logVideoDuration(Context context, String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                // Handle URIs
                Uri uri = Uri.parse(videoPath);
                retriever.setDataSource(context, uri);
            } else {
                // Handle file paths
                retriever.setDataSource(videoPath);
            }

            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long timeInMillis = Long.parseLong(time);

            // Convert duration to minutes and seconds
            long minutes = (timeInMillis / 1000) / 60;
            long seconds = (timeInMillis / 1000) % 60;

            String duration = String.format("%02d:%02d", minutes, seconds);
            Log.d("FFmpegKit", "Video Duration: " + duration);

        } catch (Exception e) {
            Log.e("FFmpegKit", "Error retrieving video duration: ", e);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String saveMergedVideoPublicly() {
        String videoFileName = "MergedVideo_" + System.currentTimeMillis() + ".mp4";
        Uri videoUri;
        Context context = activity.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MergedVideos");
            values.put(MediaStore.Video.Media.TITLE, videoFileName);
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());

            videoUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            try (OutputStream out = context.getContentResolver().openOutputStream(videoUri)) {
                // Create the file in this output stream.
                // Since this is a placeholder, assume the video is created and stored here.
            } catch (IOException e) {
                e.printStackTrace();
                ToastUtil.longShow(activity, "Failed to create video file.");
                return null;
            }
        } else {
            File movieDir = new File(activity.getExternalFilesDir(null), "Movies/MergedVideos");
            if (!movieDir.exists()) {
                movieDir.mkdirs();
            }
            File videoFile = new File(movieDir, videoFileName);
            videoUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", videoFile);
        }

        return videoUri != null ? videoUri.toString() : null;
    }

    @Override
    public int getItemCount() {
        return videoPaths.size();
    }

    public void animateItemWidth(ViewHolder holder, int startWidth, int endWidth, int duration) {
        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, endWidth);
        widthAnimator.setDuration(duration);
        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
            params.width = (int) animation.getAnimatedValue();
            holder.binding.getRoot().setLayoutParams(params);
        });
        widthAnimator.start();
    }

    public void animateItemAlpha(ViewHolder holder, float endAlpha, int duration) {
        holder.binding.getRoot().animate().alpha(endAlpha).setDuration(duration).start();
    }

    public void resetAllItemsWidth(int originalWidth, int duration) {
        for (int i = 0; i < getItemCount(); i++) {
            ViewHolder holder = (ViewHolder) ((RecyclerView) activity.mBinding.recViewVideos).findViewHolderForAdapterPosition(i);
            if (holder != null) {
                animateItemWidth(holder, holder.binding.getRoot().getLayoutParams().width, originalWidth, duration);
            }
        }
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(videoPaths, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(videoPaths, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemVideoTrimBinding binding;

        public ViewHolder(@NonNull ItemVideoTrimBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
