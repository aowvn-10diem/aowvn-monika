package com.android.vending.expansion.zipfile;

import android.content.res.AssetFileDescriptor;

/** Aow Monika: thay thế rỗng cho thư viện OBB mở rộng của Google (Kirikiri không dùng OBB) — luôn "không có file". */
public class ZipResourceFile {
    public AssetFileDescriptor getAssetFileDescriptor(String assetPath) { return null; }
}
