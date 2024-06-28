package com.iknow.android.interfaces;

public interface VideoMergeListener {

    void onMergeSuccess(String outputPath);

    void onMergeFinish(String outputPath);

    void onMergeFailure();

}
