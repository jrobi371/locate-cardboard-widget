package site.jrobin17.cardboardwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class WidgetStore {
    private static final String PREFS = "cardboard_widgets";

    private WidgetStore() {}

    static void saveSelection(Context context, int widgetId, CardPrinting card) {
        prefs(context).edit()
                .putString(key(widgetId, "id"), card.id)
                .putString(key(widgetId, "name"), card.name)
                .putString(key(widgetId, "meta"), card.metaLine())
                .putString(key(widgetId, "price_primary"), card.primaryPrice())
                .putString(key(widgetId, "price_secondary"), card.secondaryPrice())
                .putString(key(widgetId, "scryfall_uri"), card.scryfallUri)
                .apply();
    }

    static String getCardId(Context context, int widgetId) {
        return prefs(context).getString(key(widgetId, "id"), null);
    }

    static Snapshot getSnapshot(Context context, int widgetId) {
        SharedPreferences preferences = prefs(context);
        String name = preferences.getString(key(widgetId, "name"), null);
        if (name == null) return null;
        return new Snapshot(
                name,
                preferences.getString(key(widgetId, "meta"), ""),
                preferences.getString(key(widgetId, "price_primary"), "TCGplayer · No current USD price"),
                preferences.getString(key(widgetId, "price_secondary"), "Daily market estimate via Scryfall"),
                preferences.getString(key(widgetId, "scryfall_uri"), ApiClient.SITE_ROOT)
        );
    }

    static void saveImage(Context context, int widgetId, Bitmap bitmap) throws IOException {
        File destination = imageFile(context, widgetId);
        try (FileOutputStream output = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw new IOException("The card image could not be cached.");
            }
        }
    }

    static Bitmap loadImage(Context context, int widgetId) {
        File source = imageFile(context, widgetId);
        return source.exists() ? BitmapFactory.decodeFile(source.getAbsolutePath()) : null;
    }

    static void delete(Context context, int widgetId) {
        prefs(context).edit()
                .remove(key(widgetId, "id"))
                .remove(key(widgetId, "name"))
                .remove(key(widgetId, "meta"))
                .remove(key(widgetId, "price_primary"))
                .remove(key(widgetId, "price_secondary"))
                .remove(key(widgetId, "scryfall_uri"))
                .apply();
        File image = imageFile(context, widgetId);
        if (image.exists()) image.delete();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int widgetId, String field) {
        return "widget_" + widgetId + "_" + field;
    }

    private static File imageFile(Context context, int widgetId) {
        return new File(context.getFilesDir(), "widget_" + widgetId + "_card.jpg");
    }

    static final class Snapshot {
        final String name;
        final String meta;
        final String primaryPrice;
        final String secondaryPrice;
        final String scryfallUri;

        Snapshot(String name, String meta, String primaryPrice, String secondaryPrice, String scryfallUri) {
            this.name = name;
            this.meta = meta;
            this.primaryPrice = primaryPrice;
            this.secondaryPrice = secondaryPrice;
            this.scryfallUri = scryfallUri;
        }
    }
}
