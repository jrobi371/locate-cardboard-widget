package site.jrobin17.cardboardwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WidgetUpdater {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private WidgetUpdater() {}

    static void showCached(Context context, int widgetId) {
        Context appContext = context.getApplicationContext();
        render(
                appContext,
                widgetId,
                WidgetStore.getSnapshot(appContext, widgetId),
                WidgetStore.loadImage(appContext, widgetId),
                null
        );
    }

    static void refresh(Context context, int widgetId, Runnable completion) {
        Context appContext = context.getApplicationContext();
        WidgetStore.Snapshot cached = WidgetStore.getSnapshot(appContext, widgetId);
        Bitmap cachedImage = WidgetStore.loadImage(appContext, widgetId);
        if (cached == null) {
            render(appContext, widgetId, null, null, appContext.getString(R.string.widget_loading));
        } else {
            render(appContext, widgetId, cached, cachedImage, "Refreshing prices…");
        }

        EXECUTOR.execute(() -> {
            try {
                String cardId = WidgetStore.getCardId(appContext, widgetId);
                if (cardId == null) {
                    render(appContext, widgetId, null, null, null);
                    return;
                }
                CardPrinting card = ApiClient.fetchPrinting(cardId);
                Bitmap image = ApiClient.fetchCardImage(card.imageUrl);
                WidgetStore.saveSelection(appContext, widgetId, card);
                WidgetStore.saveImage(appContext, widgetId, image);
                render(appContext, widgetId, WidgetStore.getSnapshot(appContext, widgetId), image, null);
            } catch (Exception error) {
                WidgetStore.Snapshot snapshot = WidgetStore.getSnapshot(appContext, widgetId);
                Bitmap image = WidgetStore.loadImage(appContext, widgetId);
                render(
                        appContext,
                        widgetId,
                        snapshot,
                        image,
                        snapshot == null ? "Card data could not be loaded." : "Refresh failed · cached details shown"
                );
            } finally {
                if (completion != null) completion.run();
            }
        });
    }

    private static void render(
            Context context,
            int widgetId,
            WidgetStore.Snapshot snapshot,
            Bitmap image,
            String message
    ) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.card_widget);
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250);
        boolean compact = minHeight < 220;
        boolean narrow = minWidth < 165;

        bindActions(context, widgetId, snapshot, views);
        views.setViewVisibility(R.id.widget_header, compact ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.printing_meta, compact ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.price_secondary, compact ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.refresh_button, narrow ? View.GONE : View.VISIBLE);

        if (snapshot == null) {
            views.setImageViewResource(R.id.card_image, R.drawable.card_placeholder);
            views.setViewVisibility(R.id.widget_message, View.VISIBLE);
            views.setTextViewText(
                    R.id.widget_message,
                    message == null ? context.getString(R.string.widget_empty) : message
            );
            views.setViewVisibility(R.id.widget_details, View.GONE);
        } else {
            if (image == null) {
                views.setImageViewResource(R.id.card_image, R.drawable.card_placeholder);
            } else {
                views.setImageViewBitmap(R.id.card_image, image);
            }
            views.setViewVisibility(R.id.widget_details, View.VISIBLE);
            views.setTextViewText(R.id.card_name, snapshot.name);
            views.setTextViewText(R.id.printing_meta, snapshot.meta);
            views.setTextViewText(R.id.price_primary, snapshot.primaryPrice);
            views.setTextViewText(
                    R.id.price_secondary,
                    message == null ? snapshot.secondaryPrice : message
            );
            views.setViewVisibility(R.id.widget_message, View.GONE);
        }

        manager.updateAppWidget(widgetId, views);
    }

    private static void bindActions(
            Context context,
            int widgetId,
            WidgetStore.Snapshot snapshot,
            RemoteViews views
    ) {
        Intent refresh = new Intent(context, CardWidgetProvider.class)
                .setAction(CardWidgetProvider.ACTION_REFRESH)
                .setData(Uri.parse("cardboard://refresh/" + widgetId))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent refreshIntent = PendingIntent.getBroadcast(
                context,
                widgetId * 10 + 1,
                refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent configure = new Intent(context, CardSearchActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData(Uri.parse("cardboard://configure/" + widgetId))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent configureIntent = PendingIntent.getActivity(
                context,
                widgetId * 10 + 2,
                configure,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent openSite = new Intent(Intent.ACTION_VIEW, Uri.parse(ApiClient.SITE_ROOT));
        PendingIntent openSiteIntent = PendingIntent.getActivity(
                context,
                widgetId * 10 + 3,
                openSite,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.refresh_button, refreshIntent);
        views.setOnClickPendingIntent(R.id.change_button, configureIntent);
        views.setOnClickPendingIntent(R.id.card_image, openSiteIntent);
        views.setOnClickPendingIntent(R.id.card_name, openSiteIntent);
    }
}
