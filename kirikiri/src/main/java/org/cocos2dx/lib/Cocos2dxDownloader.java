package org.cocos2dx.lib;

import java.util.HashMap;

/**
 * Aow Monika: bản tối giản. Gốc dùng thư viện android-async-http + httpclient (~1,3 MB) cho trình tải của cocos2d-x;
 * Kirikiri không dùng. Mã native vẫn tìm lớp/hàm này theo tên → giữ đủ chữ ký, mọi yêu cầu tải báo lỗi ngay.
 */
public class Cocos2dxDownloader {
    private int _id = 0;
    private static final HashMap<String, Boolean> _resumingSupport = new HashMap<String, Boolean>();

    public static void setResumingSupport(String host, Boolean support) {
        _resumingSupport.put(host, support);
    }

    public static Cocos2dxDownloader createDownloader(int id, int timeoutInSeconds, String tempFileNameSufix, int countOfMaxProcessingTasks) {
        Cocos2dxDownloader d = new Cocos2dxDownloader();
        d._id = id;
        return d;
    }

    public static void createTask(final Cocos2dxDownloader downloader, int id, String url, String path) {
        downloader.nativeOnFinish(downloader._id, id, 0, "Tải qua mạng không được hỗ trợ trong bản nhúng", null);
    }

    public static void cancelAllRequests(final Cocos2dxDownloader downloader) {}

    native void nativeOnProgress(int id, int taskId, long dl, long dlnow, long dltotal);
    native void nativeOnFinish(int id, int taskId, int errCode, String errStr, final byte[] data);
}
