package site.jrobin17.cardboardwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class CardWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "site.jrobin17.cardboardwidget.REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            WidgetUpdater.showCached(context, widgetId);
            if (WidgetStore.getCardId(context, widgetId) != null) {
                WidgetUpdater.refresh(context, widgetId, null);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                PendingResult pendingResult = goAsync();
                WidgetUpdater.refresh(context, widgetId, pendingResult::finish);
            }
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int widgetId,
            Bundle newOptions
    ) {
        WidgetUpdater.showCached(context, widgetId);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int widgetId : widgetIds) WidgetStore.delete(context, widgetId);
    }
}
