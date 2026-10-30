package hr.ugljan.rad;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Haelt die App waehrend Navigation/Aufzeichnung am Leben, damit GPS bei gesperrtem Bildschirm weiterlaeuft. */
public class NavService extends Service {
    private static final String CHANNEL = "fahrt";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Fahrt", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        String text = intent != null && intent.getStringExtra("text") != null ? intent.getStringExtra("text") : "Fahrt wird aufgezeichnet";
        Notification n = b.setContentTitle("Velora")
                .setContentText(text)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(1, n);
        } catch (Exception e) {
            // ohne Standort-Erlaubnis darf der Dienst nicht laufen: sauber beenden statt abstuerzen
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
