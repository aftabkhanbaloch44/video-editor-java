package com.iknow.android.widget;

import static com.iknow.android.features.trim.VideoTrimmerUtil.MAX_COUNT_RANGE;
import static com.iknow.android.features.trim.VideoTrimmerUtil.MAX_SHOOT_DURATION;
import static com.iknow.android.features.trim.VideoTrimmerUtil.RECYCLER_VIEW_PADDING;
import static com.iknow.android.features.trim.VideoTrimmerUtil.THUMB_WIDTH;
import static com.iknow.android.features.trim.VideoTrimmerUtil.VIDEO_FRAMES_WIDTH;

import android.animation.ValueAnimator;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.iknow.android.R;
import com.iknow.android.features.trim.VideoThumbsAdapter;
import com.iknow.android.features.trim.VideoTrimmerUtil;
import com.iknow.android.features.trim.callback.Trigger;
import com.iknow.android.interfaces.IVideoTrimmerView;
import com.iknow.android.interfaces.VideoTrimListener;
import com.iknow.android.utils.StorageUtil;

import iknow.android.utils.thread.BackgroundExecutor;
import iknow.android.utils.thread.UiThreadExecutor;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
public class VideoTrimmerView extends FrameLayout implements IVideoTrimmerView, Trigger {

    private static final String TAG = VideoTrimmerView.class.getSimpleName();
    public VideoThumbsAdapter mVideoThumbAdapter;
    private int mMaxWidth = VIDEO_FRAMES_WIDTH;
    private Context mContext;
    private ZVideoView mVideoView;
    private RecyclerView mVideoThumbRecyclerView;
    private RangeSeekBarView mRangeSeekBarView;
    private LinearLayout mSeekBarLayout;
    private ImageView mRedProgressIcon;
    private float mAverageMsPx;//每毫秒所占的px
    private float averagePxMs;//每px所占用的ms毫秒
    private Uri mSourceUri;
    private VideoTrimListener mOnTrimVideoListener;
    private int mDuration = 0;
    private boolean isFromRestore = false;
    //new
    private long mLeftProgressPos, mRightProgressPos;
    private long mRedProgressBarPos = 0;
    private long scrollPos = 0;
    private int mScaledTouchSlop;
    private int lastScrollX;
    private boolean isSeeking;
    private final RangeSeekBarView.OnRangeSeekBarChangeListener mOnRangeSeekBarChangeListener = new RangeSeekBarView.OnRangeSeekBarChangeListener() {
        @Override
        public void onRangeSeekBarValuesChanged(RangeSeekBarView bar, long minValue, long maxValue, int action, boolean isMin,
                                                RangeSeekBarView.Thumb pressedThumb) {
            Log.d(TAG, "-----minValue----->>>>>>" + minValue);
            Log.d(TAG, "-----maxValue----->>>>>>" + maxValue);
            mLeftProgressPos = minValue + scrollPos;
            mRedProgressBarPos = mLeftProgressPos;
            mRightProgressPos = maxValue + scrollPos;
            Log.d(TAG, "-----mLeftProgressPos----->>>>>>" + mLeftProgressPos);
            Log.d(TAG, "-----mRightProgressPos----->>>>>>" + mRightProgressPos);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    isSeeking = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    isSeeking = true;
                    seekTo((int) (pressedThumb == RangeSeekBarView.Thumb.MIN ? mLeftProgressPos : mRightProgressPos));
                    break;
                case MotionEvent.ACTION_UP:
                    isSeeking = false;
                    seekTo((int) mLeftProgressPos);
                    break;
                default:
                    break;
            }

            mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
        }
    };
    private boolean isOverScaledTouchSlop;
    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            Log.d(TAG, "newState = " + newState);
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            isSeeking = false;
            int scrollX = calcScrollXDistance();
            //达不到滑动的距离
            if (Math.abs(lastScrollX - scrollX) < mScaledTouchSlop) {
                isOverScaledTouchSlop = false;
                return;
            }
            isOverScaledTouchSlop = true;
            //初始状态,why ? 因为默认的时候有35dp的空白！
            if (scrollX == -RECYCLER_VIEW_PADDING) {
                scrollPos = 0;
                mLeftProgressPos = mRangeSeekBarView.getSelectedMinValue() + scrollPos;
                mRightProgressPos = mRangeSeekBarView.getSelectedMaxValue() + scrollPos;
                Log.d(TAG, "onScrolled >>>> mLeftProgressPos = " + mLeftProgressPos);
                mRedProgressBarPos = mLeftProgressPos;
            } else {
                isSeeking = true;
                scrollPos = (long) (mAverageMsPx * (RECYCLER_VIEW_PADDING + scrollX) / THUMB_WIDTH);
                mLeftProgressPos = mRangeSeekBarView.getSelectedMinValue() + scrollPos;
                mRightProgressPos = mRangeSeekBarView.getSelectedMaxValue() + scrollPos;
                Log.d(TAG, "onScrolled >>>> mLeftProgressPos = " + mLeftProgressPos);
                mRedProgressBarPos = mLeftProgressPos;
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                    setPlayPauseViewIcon(false);
                }
                mRedProgressIcon.setVisibility(GONE);
                seekTo(mLeftProgressPos);
                mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
                mRangeSeekBarView.invalidate();
            }
            lastScrollX = scrollX;
        }
    };
    private int mThumbsTotalCount;
    private ValueAnimator mRedProgressAnimator;
    private Handler mAnimationHandler = new Handler();

    private ImageView btnCancel, btnDone, btnPlayVideo;

    public VideoTrimmerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public VideoTrimmerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        LayoutInflater.from(context).inflate(R.layout.video_trimmer_view, this, true);

        mSeekBarLayout = findViewById(R.id.seekBarLayout);
        mRedProgressIcon = findViewById(R.id.positionIcon);
        mVideoThumbRecyclerView = findViewById(R.id.video_frames_recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        mVideoThumbRecyclerView.setLayoutManager(layoutManager);

        mVideoThumbRecyclerView.addOnScrollListener(mOnScrollListener);


    }

    private void initRangeSeekBarView() {
        if (mRangeSeekBarView != null) return;
        mLeftProgressPos = 0;
        if (mDuration <= MAX_SHOOT_DURATION) {
            mThumbsTotalCount = MAX_COUNT_RANGE;
        } else {
            mThumbsTotalCount = (int) (mDuration * 1.0f / (MAX_SHOOT_DURATION * 1.0f) * MAX_COUNT_RANGE);
        }

        mRightProgressPos = mDuration;

        mVideoThumbRecyclerView.addItemDecoration(new SpacesItemDecoration2(RECYCLER_VIEW_PADDING, mThumbsTotalCount));
        mRangeSeekBarView = new RangeSeekBarView(mContext, mLeftProgressPos, mRightProgressPos);
        mRangeSeekBarView.setSelectedMinValue(mLeftProgressPos);
        mRangeSeekBarView.setSelectedMaxValue(mRightProgressPos);
        mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos);
        mRangeSeekBarView.setMinShootTime(VideoTrimmerUtil.MIN_SHOOT_DURATION);
        mRangeSeekBarView.setNotifyWhileDragging(true);
        mRangeSeekBarView.setOnRangeSeekBarChangeListener(mOnRangeSeekBarChangeListener);
        mSeekBarLayout.addView(mRangeSeekBarView);
        if (mThumbsTotalCount - MAX_COUNT_RANGE > 0) {
            mAverageMsPx = (mDuration - MAX_SHOOT_DURATION) / (float) (mThumbsTotalCount - MAX_COUNT_RANGE);
        } else {
            mAverageMsPx = 0f;
        }
        averagePxMs = (mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos));
    }

    public void initVideoByURI(final Uri videoURI) {
        mSourceUri = videoURI;
        mVideoView.setVideoURI(videoURI);
        mVideoView.requestFocus();
    }

    private void startShootVideoThumbs(final Context context, final Uri videoUri, int videoDuration) {
        if (mVideoThumbAdapter == null) {
            mVideoThumbAdapter = new VideoThumbsAdapter(mContext);
            mVideoThumbRecyclerView.setAdapter(mVideoThumbAdapter);
        } else {
            return;
        }
        VideoTrimmerUtil.shootVideoThumbInBackground(context, videoUri, videoDuration,
                (bitmap, interval) -> {
                    if (bitmap != null) {
                        UiThreadExecutor.runTask("", () -> mVideoThumbAdapter.addBitmaps(bitmap), 0L);
                    }
                });
    }

    private void onCancelClicked() {
        mOnTrimVideoListener.onTrimCancel();
    }

    private void videoPrepared(MediaPlayer mp) {
        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();

        float videoProportion = (float) videoWidth / (float) videoHeight;
        int screenWidth = mVideoView.getWidth();
        int screenHeight = mVideoView.getHeight();

        if (videoHeight > videoWidth) {
            lp.width = screenWidth;
            lp.height = screenHeight;
        } else {
            lp.width = screenWidth;
            float r = videoHeight / (float) videoWidth;
            lp.height = (int) (lp.width * r);
        }
        mVideoView.setLayoutParams(lp);
        mDuration = mVideoView.getDuration();
        if (!getRestoreState()) {
            seekTo((int) mRedProgressBarPos);
        } else {
            setRestoreState(false);
            seekTo((int) mRedProgressBarPos);
        }
        initRangeSeekBarView();
        startShootVideoThumbs(mContext, mSourceUri, mDuration);
    }

    private void videoCompleted() {
        seekTo(mLeftProgressPos);
        setPlayPauseViewIcon(false);
    }

    private void onVideoReset() {
        mVideoView.stopPlayback();
        mRedProgressBarPos = mLeftProgressPos;
        pauseRedProgressAnimation();
        setPlayPauseViewIcon(false);
    }

    private void playVideoOrPause() {
        mRedProgressBarPos = mVideoView.getCurrentPosition();
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            pauseRedProgressAnimation();
        } else {
            mVideoView.start();
            playingRedProgressAnimation();
        }
        setPlayPauseViewIcon(mVideoView.isPlaying());
    }

    public void onVideoPause() {
        if (mVideoView.isPlaying()) {
            seekTo(mLeftProgressPos);//复位
            mVideoView.pause();
            setPlayPauseViewIcon(false);
            mRedProgressIcon.setVisibility(GONE);
        }
    }

    public void setOnTrimVideoListener(VideoTrimListener onTrimVideoListener) {
        mOnTrimVideoListener = onTrimVideoListener;
    }

    private void setUpListeners() {
        btnCancel.setOnClickListener(view -> onCancelClicked());

        if (mVideoView != null) {
            mVideoView.setOnPreparedListener(mp -> {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                videoPrepared(mp);
            });

            mVideoView.setOnCompletionListener(mp -> videoCompleted());
        }
        btnPlayVideo.setOnClickListener(v -> playVideoOrPause());
    }

    private void onSaveClicked() {
        if (mRightProgressPos - mLeftProgressPos < VideoTrimmerUtil.MIN_SHOOT_DURATION) {
            Toast.makeText(mContext, "视频长不足3秒,无法上传", Toast.LENGTH_SHORT).show();
        } else {
            mVideoView.pause();

            VideoTrimmerUtil.trim(
                    mSourceUri.getPath(),
                    StorageUtil.getCacheDir(),
                    mLeftProgressPos,
                    mRightProgressPos,
                    mOnTrimVideoListener);
        }
    }

    private void seekTo(long mSec) {
        mVideoView.seekTo((int) mSec);
        Log.d(TAG, "seekTo = " + mSec);
    }

    private boolean getRestoreState() {
        return isFromRestore;
    }

    public void setRestoreState(boolean fromRestore) {
        isFromRestore = fromRestore;
    }

    private void setPlayPauseViewIcon(boolean isPlaying) {
        btnPlayVideo.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /**
     * 水平滑动了多少px
     */
    private int calcScrollXDistance() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mVideoThumbRecyclerView.getLayoutManager();
        int position = 0;
        if (layoutManager != null) {
            position = layoutManager.findFirstVisibleItemPosition();
        }
        View firstVisibleChildView = null;
        if (layoutManager != null) {
            firstVisibleChildView = layoutManager.findViewByPosition(position);
        }
        int itemWidth = 0;
        if (firstVisibleChildView != null) {
            itemWidth = firstVisibleChildView.getWidth();
        }
        if (firstVisibleChildView != null) {
            return (position) * itemWidth - firstVisibleChildView.getLeft();
        }
        return 0;
    }

    private void playingRedProgressAnimation() {
        pauseRedProgressAnimation();
        playingAnimation();
        mAnimationHandler.post(mAnimationRunnable);
    }

    private void playingAnimation() {
        if (mRedProgressIcon.getVisibility() == View.GONE) {
            mRedProgressIcon.setVisibility(View.VISIBLE);
        }
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mRedProgressIcon.getLayoutParams();
        int start = (int) (RECYCLER_VIEW_PADDING + (mRedProgressBarPos - scrollPos) * averagePxMs);
        int end = (int) (RECYCLER_VIEW_PADDING + (mRightProgressPos - scrollPos) * averagePxMs);
        mRedProgressAnimator = ValueAnimator.ofInt(start, end).setDuration((mRightProgressPos - scrollPos) - (mRedProgressBarPos - scrollPos));
        mRedProgressAnimator.setInterpolator(new LinearInterpolator());
        mRedProgressAnimator.addUpdateListener(animation -> {
            params.leftMargin = (int) animation.getAnimatedValue();
            mRedProgressIcon.setLayoutParams(params);
            Log.d(TAG, "----onAnimationUpdate--->>>>>>>" + mRedProgressBarPos);
        });
        mRedProgressAnimator.start();
    }

    private void pauseRedProgressAnimation() {
        mRedProgressIcon.clearAnimation();
        if (mRedProgressAnimator != null && mRedProgressAnimator.isRunning()) {
            mAnimationHandler.removeCallbacks(mAnimationRunnable);
            mRedProgressAnimator.cancel();
        }
    }

    private void updateVideoProgress() {
        long currentPosition = mVideoView.getCurrentPosition();
        if (currentPosition >= (mRightProgressPos)) {
            mRedProgressBarPos = mLeftProgressPos;
            pauseRedProgressAnimation();
            onVideoPause();
        } else {
            mAnimationHandler.post(mAnimationRunnable);
        }
    }

    /**
     * Cancel trim thread execut action when finish
     */
    @Override
    public void onDestroy() {
        BackgroundExecutor.cancelAll("", true);
        UiThreadExecutor.cancelAll("");
    }

    public void setButtons(ImageView btnCancel, ImageView btnDone, ImageView btnPlayVideo) {
        this.btnCancel = btnCancel;
        this.btnDone = btnDone;
        this.btnPlayVideo = btnPlayVideo;
        setUpListeners();
    }

    public void setVideoView(ZVideoView videoView) {
        this.mVideoView = videoView;
    }

    public void replaceVideoView(ZVideoView videoView, String path) {
        onVideoReset();
        this.mVideoView = videoView;
        onVideoReset();
        initVideoByURI(Uri.parse(path));
    }    private Runnable mAnimationRunnable = this::updateVideoProgress;


    @Override
    public void onTriggered() {
        onSaveClicked();
    }
}
