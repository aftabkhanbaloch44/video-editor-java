package com.iknow.android.interfaces;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
public interface VideoTrimListener {
    void onTrimStart();
    void onTrimSuccess(String path);
    void onTrimFailure();
    void onTrimCancel();
}
