package site.jrobin17.cardboardwidget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ApiClient {
    static final String SITE_ROOT = "https://cardboardfetch.jrobin17.chatgpt.site";
    private static final String USER_AGENT = "LocateCardboardWidget/1.0";

    private ApiClient() {}

    static List<String> autocomplete(String query) throws IOException, JSONException {
        JSONObject payload = requestJson(
                SITE_ROOT + "/scryfall-api/cards/autocomplete?q=" + encode(query)
        );
        JSONArray data = payload.optJSONArray("data");
        List<String> names = new ArrayList<>();
        if (data == null) return names;
        for (int index = 0; index < data.length(); index++) {
            String name = data.optString(index, "");
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    static List<CardPrinting> searchPrintings(String query) throws IOException, JSONException {
        JSONObject named = requestJson(
                SITE_ROOT + "/scryfall-api/cards/named?fuzzy=" + encode(query)
        );
        String nextPage = proxyApiUrl(named.getString("prints_search_uri"));
        Map<String, CardPrinting> english = new LinkedHashMap<>();
        Map<String, CardPrinting> everyLanguage = new LinkedHashMap<>();

        while (nextPage != null && !nextPage.isEmpty()) {
            JSONObject page = requestJson(nextPage);
            JSONArray data = page.optJSONArray("data");
            if (data != null) {
                for (int index = 0; index < data.length(); index++) {
                    JSONObject item = data.getJSONObject(index);
                    CardPrinting printing = CardPrinting.fromJson(item);
                    everyLanguage.put(printing.id, printing);
                    if ("en".equals(item.optString("lang"))) english.put(printing.id, printing);
                }
            }
            nextPage = page.optBoolean("has_more")
                    ? proxyApiUrl(page.optString("next_page", ""))
                    : null;
            if (nextPage != null) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Card search was interrupted.", interrupted);
                }
            }
        }

        List<CardPrinting> printings = new ArrayList<>(
                english.isEmpty() ? everyLanguage.values() : english.values()
        );
        printings.sort(
                Comparator.comparing((CardPrinting card) -> card.releasedAt).reversed()
                        .thenComparing(card -> card.setName)
                        .thenComparing(card -> card.collectorNumber)
        );
        return printings;
    }

    static CardPrinting fetchPrinting(String cardId) throws IOException, JSONException {
        return CardPrinting.fromJson(
                requestJson(SITE_ROOT + "/scryfall-api/cards/" + encode(cardId))
        );
    }

    static List<String> fetchRulings(String rulingsUrl) throws IOException, JSONException {
        List<String> rulings = new ArrayList<>();
        if (rulingsUrl == null || rulingsUrl.isEmpty()) return rulings;

        JSONObject payload = requestJson(proxyApiUrl(rulingsUrl));
        JSONArray data = payload.optJSONArray("data");
        if (data == null) return rulings;
        for (int index = 0; index < data.length(); index++) {
            JSONObject ruling = data.optJSONObject(index);
            if (ruling == null) continue;
            String comment = ruling.optString("comment", "");
            if (!comment.isEmpty()) rulings.add(comment);
        }
        return rulings;
    }

    static Bitmap fetchCardImage(String sourceUrl) throws IOException {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            throw new IOException("No card image is available for this printing.");
        }
        URL url = new URL(SITE_ROOT + "/card-image?url=" + encode(sourceUrl));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(18_000);
        connection.setRequestProperty("Accept", "image/*");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("The card image returned HTTP " + status + ".");
            }
            try (InputStream input = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) throw new IOException("The card image could not be decoded.");
                return scaleForWidget(bitmap, 360, 520);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap scaleForWidget(Bitmap original, int maxWidth, int maxHeight) {
        double ratio = Math.min(
                1.0,
                Math.min((double) maxWidth / original.getWidth(), (double) maxHeight / original.getHeight())
        );
        if (ratio >= 1.0) return original;
        int width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(original.getHeight() * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(original, width, height, true);
        if (scaled != original) original.recycle();
        return scaled;
    }

    private static JSONObject requestJson(String address) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(18_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readText(stream);
            JSONObject payload = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            if (status < 200 || status >= 300) {
                throw new IOException(payload.optString("details", "Request failed with HTTP " + status + "."));
            }
            return payload;
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private static String proxyApiUrl(String scryfallUrl) throws IOException {
        if (scryfallUrl == null || scryfallUrl.isEmpty()) return null;
        try {
            URI uri = URI.create(scryfallUrl);
            if ("api.scryfall.com".equalsIgnoreCase(uri.getHost())) {
                String query = uri.getRawQuery();
                return SITE_ROOT + "/scryfall-api" + uri.getRawPath()
                        + (query == null ? "" : "?" + query);
            }
            if (scryfallUrl.startsWith(SITE_ROOT)) return scryfallUrl;
            throw new IOException("Unexpected card-data address.");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid card-data address.", error);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is not available.", impossible);
        }
    }
}
