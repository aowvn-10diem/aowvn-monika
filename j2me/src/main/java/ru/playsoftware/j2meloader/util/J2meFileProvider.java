package ru.playsoftware.j2meloader.util;

import androidx.core.content.FileProvider;

/**
 * Aow Monika: lớp con riêng để không trùng khai báo FileProvider với app chính khi gộp Manifest.
 * Authority vẫn là ${applicationId}.provider như bản gốc.
 */
public class J2meFileProvider extends FileProvider {
}
