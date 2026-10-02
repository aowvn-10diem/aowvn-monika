package org.cocos2dx.lib;

/**
 * Aow Monika: bản rỗng. Gốc gọi SDK tối ưu của OPPO (com.oppo.oiface) — không cần và không nhúng; mã native vẫn tìm lớp này theo tên nên giữ đủ hàm.
 */
public class Cocos2dxDataManager {
    public static void setOptimise(String thing, float value) {}
    public static void setProcessID(int pid) {}
    public static void setFrameSize(int width, int height) {}
    public static void onSceneLoaderBegin() {}
    public static void onSceneLoaderEnd() {}
    public static void onShaderLoaderBegin() {}
    public static void onShaderLoaderEnd() {}
}
