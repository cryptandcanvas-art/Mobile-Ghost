package app.sovereign.phone;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayDeque;

/** Bounded, process-only capture. No disk writes or network transmission. */
public final class ActionNotificationListener extends NotificationListenerService {
    private static final ArrayDeque<String> pending = new ArrayDeque<>();
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && body == null) return;
        String capture = sbn.getPackageName() + "\n" + (title == null ? "" : title) + "\n" + (body == null ? "" : body);
        synchronized (pending) {
            if (pending.size() >= 32) pending.removeFirst();
            pending.addLast(capture.substring(0, Math.min(capture.length(), 8192)));
        }
    }
    static String takeNext() { synchronized (pending) { return pending.pollFirst(); } }
    @Override public void onListenerDisconnected() { synchronized (pending) { pending.clear(); } }
}
