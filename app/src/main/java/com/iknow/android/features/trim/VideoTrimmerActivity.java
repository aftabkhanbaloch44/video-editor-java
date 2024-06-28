package com.iknow.android.features.trim;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.iknow.android.R;
import com.iknow.android.databinding.ActivityVideoTrimBinding;
import com.iknow.android.features.common.ui.BaseActivity;
import com.iknow.android.interfaces.VideoTrimListener;

import java.util.ArrayList;
import java.util.List;

public class VideoTrimmerActivity extends BaseActivity {

    public static final int VIDEO_TRIM_REQUEST_CODE = 0x001;
    private static final String TAG = "VideoTrimmerActivity";
    private static final String VIDEO_PATHS_KEY = "video-file-paths";
    public ActivityVideoTrimBinding mBinding;
    public ProgressDialog mProgressDialog;

    public static void call(FragmentActivity from, List<String> videoPaths) {
        if (!videoPaths.isEmpty()) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(VIDEO_PATHS_KEY, new ArrayList<>(videoPaths));
            Intent intent = new Intent(from, VideoTrimmerActivity.class);
            intent.putExtras(bundle);
            from.startActivityForResult(intent, VIDEO_TRIM_REQUEST_CODE);
        }
    }

    @Override
    public void initUI() {
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_video_trim);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            List<String> videoPaths = extras.getStringArrayList(VIDEO_PATHS_KEY);
            if (videoPaths != null && !videoPaths.isEmpty()) {
                Log.d(TAG, "Selected video paths: " + videoPaths.toString());
                mBinding.recViewVideos.setLayoutManager(new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.HORIZONTAL, false));
                TrimmerAdapter adapter = new TrimmerAdapter(videoPaths, this);
                mBinding.recViewVideos.setAdapter(adapter);
            } else {
                Log.e(TAG, "No video paths found in intent extras");
            }
        } else {
            Log.e(TAG, "Intent extras are null");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public ProgressDialog buildDialog(String msg) {
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog.show(this, "", msg);
        }
        mProgressDialog.setMessage(msg);
        return mProgressDialog;
    }
}
