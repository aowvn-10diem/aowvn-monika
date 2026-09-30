package vn.aow.monika.loader;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Chạy trong tiến trình game. Monika kết nối (bind) rồi ra lệnh:
 *  1 = chép dữ liệu (job): đọc danh sách + từng file từ "content://<Monika>.gamedata/<job>/..." rồi ghi vào Android/data/<gói game>/ (game luôn được ghi vào thư mục của chính nó);
 *  2 = theo dõi (giây): gửi nhịp sống về Monika.
 * Chỉ nhận lệnh từ đúng gói Monika (kiểm tra UID người gọi).
 */
public class MonikaLoaderService extends Service {
    static final int CMD_COPY = 1;
    static final int CMD_WATCH = 2;

    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != CMD_COPY && code != CMD_WATCH) return false;
            if (!callerIsHost()) {
                if (reply != null) reply.writeInt(-1);
                return true;
            }
            if (code == CMD_COPY) {
                final String job = data.readString();
                new Thread(new Runnable() { @Override public void run() { copyJob(job); } }, "monika-copy").start();
            } else {
                MonikaLoaderProvider.resetScreen();
                Watch.arm(MonikaLoaderService.this, Math.max(1, data.readInt()) * 1000L);
            }
            if (reply != null) reply.writeInt(1);
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent) { return binder; }

    private boolean callerIsHost() {
        String host = Bus.host(this);
        if (host == null) return false;
        String[] pk = getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (pk == null) return false;
        for (String p : pk) if (host.equals(p)) return true;
        return false;
    }

    private void copyJob(String job) {
        Context ctx = this;
        String host = Bus.host(ctx);
        try {
            File filesDir = getExternalFilesDir(null);
            if (filesDir == null) throw new IOException("Bộ nhớ ngoài chưa sẵn sàng");
            File base = filesDir.getParentFile(); // .../Android/data/<gói>
            String baseCanon = base.getCanonicalPath() + File.separator;
            Uri root = Uri.parse("content://" + host + ".gamedata/" + job);
            String[] lines = readAll(getContentResolver().openInputStream(Uri.withAppendedPath(root, "manifest"))).split("\n");
            long total = 0;
            int count = 0;
            for (String l : lines) {
                String[] f = l.split("\t", 3);
                if (f.length == 3) { total += Long.parseLong(f[1]); count++; }
            }
            long done = 0;
            long lastSent = 0;
            int files = 0;
            for (String l : lines) {
                String[] f = l.split("\t", 3);
                if (f.length != 3) continue;
                long size = Long.parseLong(f[1]);
                File target = new File(base, f[2]);
                if (!target.getCanonicalPath().startsWith(baseCanon)) throw new IOException("Đường dẫn không hợp lệ: " + f[2]);
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Không tạo được thư mục: " + parent);
                if (target.isFile() && target.length() == size) {
                    done += size; files++;
                    continue;
                }
                File part = new File(target.getPath() + ".part");
                InputStream in = getContentResolver().openInputStream(Uri.withAppendedPath(root, f[0]));
                if (in == null) throw new IOException("Không mở được dữ liệu " + f[2]);
                OutputStream out = new FileOutputStream(part);
                try {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastSent > 300) { lastSent = now; progress(ctx, job, done, total, files); }
                    }
                } finally {
                    try { in.close(); } catch (IOException ignored) {}
                    out.close();
                }
                if (part.length() != size) { part.delete(); throw new IOException("Chép thiếu dữ liệu: " + f[2]); }
                target.delete();
                if (!part.renameTo(target)) throw new IOException("Không đặt được tên " + f[2]);
                files++;
            }
            Bundle b = new Bundle();
            b.putLong("bytes", total);
            b.putInt("files", files);
            b.putInt("expected", count);
            Bus.send(ctx, "done", job, b);
        } catch (Throwable t) {
            Bundle b = new Bundle();
            b.putString("error", String.valueOf(t.getMessage() == null ? t.toString() : t.getMessage()));
            Bus.send(ctx, "error", job, b);
        }
    }

    private static void progress(Context ctx, String job, long done, long total, int files) {
        Bundle b = new Bundle();
        b.putLong("done", done);
        b.putLong("total", total);
        b.putInt("files", files);
        Bus.send(ctx, "progress", job, b);
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) throw new IOException("Không đọc được danh sách dữ liệu");
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } finally {
            r.close();
        }
        return sb.toString();
    }
}
