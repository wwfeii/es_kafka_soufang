package com.wf.web.dto;

/**
 * Created by wangfei.
 */
public final class QiNiuPutRet {
    public String key;
    public String hash;
    public String bucket;
    public int width;
    public int height;

    public QiNiuPutRet(String key, int width, int height) {
        this.key = key;
        this.hash = hash;
        this.bucket = bucket;
        this.width = width;
        this.height = height;
    }

    public QiNiuPutRet() {
    }

    @Override
    public String toString() {
        return "QiNiuPutRet{" +
                "key='" + key + '\'' +
                ", hash='" + hash + '\'' +
                ", bucket='" + bucket + '\'' +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
