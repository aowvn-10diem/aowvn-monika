package vn.aow.monika.loader;

import android.content.Context;

/** Gửi "nhịp sống" mỗi giây về Monika trong lúc được theo dõi (60 giây đầu sau khi game mở, hoặc khi Monika yêu cầu). */
final class Watch {
    private static volatile long until;
    private static boolean running;

    private Watch() {}

    static synchronized void arm(final Context ctx, long millis) {
        long end = System.currentTimeMillis() + millis;
        if (end > until) until = end;
        if (running) return;
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    while (System.currentTimeMillis() < until) {
                        Bus.send(ctx, "beat", null, null);
                        try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    }
                } finally {
                    synchronized (Watch.class) { running = false; }
                }
            }
        }, "monika-beat");
        t.setDaemon(true);
        t.start();
    }
}
