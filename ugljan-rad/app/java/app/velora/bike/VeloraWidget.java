package app.velora.bike;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Widget auf dem Startbildschirm: Kilometer dieser Woche und aktuelle Serie. Die App liefert die Texte. */
public class VeloraWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        update(ctx, m, ids);
    }

    static void update(Context ctx, AppWidgetManager m, int[] ids) {
        SharedPreferences p = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE);
        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.wBig, p.getString("big", "0,0 km"));
            v.setTextViewText(R.id.wSmall, p.getString("small", "diese Woche"));
            v.setTextViewText(R.id.wLine, p.getString("line", "Öffne BikeOra und fahr los"));
            v.setOnClickPendingIntent(R.id.wRoot, pi);
            m.updateAppWidget(id, v);
        }
    }
}
