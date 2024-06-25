package com.iknow.android.features.trim;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import com.iknow.android.R;
import com.iknow.android.databinding.ActivityVideoTrimBinding;
import com.iknow.android.features.common.ui.BaseActivity;
import com.iknow.android.interfaces.VideoTrimListener;
import com.iknow.android.utils.ToastUtil;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
public class VideoTrimmerActivity extends BaseActivity implements VideoTrimListener {

  private static final String TAG = "jason";
  private static final String VIDEO_PATH_KEY = "video-file-path";
  private static final String COMPRESSED_VIDEO_FILE_NAME = "compress.mp4";
  public static final int VIDEO_TRIM_REQUEST_CODE = 0x001;
  private ActivityVideoTrimBinding mBinding;
  private ProgressDialog mProgressDialog;

  public static void call(FragmentActivity from, String videoPath) {
    if (!TextUtils.isEmpty(videoPath)) {
      Bundle bundle = new Bundle();
      bundle.putString(VIDEO_PATH_KEY, videoPath);
      Intent intent = new Intent(from, VideoTrimmerActivity.class);
      intent.putExtras(bundle);
      from.startActivityForResult(intent, VIDEO_TRIM_REQUEST_CODE);
    }
  }

  @Override public void initUI() {
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_video_trim);
    Bundle bd = getIntent().getExtras();
    String path = "";
    if (bd != null) path = bd.getString(VIDEO_PATH_KEY);
    if (mBinding.trimmerView != null) {
      mBinding.trimmerView.setOnTrimVideoListener(this);
      mBinding.trimmerView.initVideoByURI(Uri.parse(path));
    }
  }

  @Override public void onResume() {
    super.onResume();
  }

  @Override public void onPause() {
    super.onPause();
    mBinding.trimmerView.onVideoPause();
    mBinding.trimmerView.setRestoreState(true);
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    mBinding.trimmerView.onDestroy();
  }

  private ProgressDialog buildDialog(String msg) {
    if (mProgressDialog == null) {
      mProgressDialog = ProgressDialog.show(this, "", msg);
    }
    mProgressDialog.setMessage(msg);
    return mProgressDialog;
  }

  @Override
  public void onTrimStart() {
    buildDialog(getResources().getString(R.string.trimming)).show();
  }

  @Override
  public void onTrimProgress() {

  }

  @Override
  public void onTrimSuccess() {

  }

  @Override
  public void onTrimFailure() {

  }

  @Override
  public void onTrimFinish(String url) {
    if (mProgressDialog.isShowing()) mProgressDialog.dismiss();
    finish();
    //TODO: please handle your trimmed video url here!!!
    //String out = StorageUtil.getCacheDir() + File.separator + COMPRESSED_VIDEO_FILE_NAME;
    //buildDialog(getResources().getString(R.string.compressing)).show();
    //VideoCompressor.compress(this, in, out, new VideoCompressListener() {
    //  @Override public void onSuccess(String message) {
    //  }
    //
    //  @Override public void onFailure(String message) {
    //  }
    //
    //  @Override public void onFinish() {
    //    if (mProgressDialog.isShowing()) mProgressDialog.dismiss();
    //    finish();
    //  }
    //});
  }

  @Override
  public void onTrimCancel() {
    mBinding.trimmerView.onDestroy();
    finish();
  }
}
