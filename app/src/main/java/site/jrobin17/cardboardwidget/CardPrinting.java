package site.jrobin17.cardboardwidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CardPrinting {
    final String id;
    final String name;
    final String setName;
    final String setCode;
    final String collectorNumber;
    final String releasedAt;
    final String scryfallUri;
    final String imageUrl;
    final String oracleText;
    final String rulingsUri;
    final String usd;
    final String usdFoil;
    final String usdEtched;

    private CardPrinting(
            String id,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String releasedAt,
            String scryfallUri,
            String imageUrl,
            String oracleText,
            String rulingsUri,
            String usd,
            String usdFoil,
            String usdEtched
    ) {
        this.id = id;
        this.name = name;
        this.setName = setName;
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.releasedAt = releasedAt;
        this.scryfallUri = scryfallUri;
        this.imageUrl = imageUrl;
        this.oracleText = oracleText;
        this.rulingsUri = rulingsUri;
        this.usd = usd;
        this.usdFoil = usdFoil;
        this.usdEtched = usdEtched;
    }

    static CardPrinting fromJson(JSONObject card) throws JSONException {
        JSONObject prices = card.optJSONObject("prices");
        return new CardPrinting(
                card.getString("id"),
                card.optString("name", "Unknown card"),
                card.optString("set_name", "Unknown set"),
                card.optString("set", "???").toUpperCase(Locale.US),
                card.optString("collector_number", "?"),
                card.optString("released_at", ""),
                card.optString("scryfall_uri", ApiClient.SITE_ROOT),
                findImageUrl(card),
                findOracleText(card),
                card.optString("rulings_uri", ""),
                nullablePrice(prices, "usd"),
                nullablePrice(prices, "usd_foil"),
                nullablePrice(prices, "usd_etched")
        );
    }

    private static String findOracleText(JSONObject card) {
        String oracleText = card.optString("oracle_text", "");
        if (!oracleText.isEmpty()) return oracleText;

        JSONArray faces = card.optJSONArray("card_faces");
        if (faces == null) return "";

        StringBuilder combined = new StringBuilder();
        for (int index = 0; index < faces.length(); index++) {
            JSONObject face = faces.optJSONObject(index);
            if (face == null) continue;
            String faceText = face.optString("oracle_text", "");
            if (faceText.isEmpty()) continue;
            if (combined.length() > 0) combined.append("\n\n");
            String faceName = face.optString("name", "");
            if (!faceName.isEmpty()) combined.append(faceName).append("\n");
            combined.append(faceText);
        }
        return combined.toString();
    }

    private static String nullablePrice(JSONObject prices, String key) {
        if (prices == null || prices.isNull(key)) return null;
        String value = prices.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static String findImageUrl(JSONObject card) {
        JSONObject imageUris = card.optJSONObject("image_uris");
        if (imageUris == null) {
            JSONArray faces = card.optJSONArray("card_faces");
            if (faces != null && faces.length() > 0) {
                imageUris = faces.optJSONObject(0) == null
                        ? null
                        : faces.optJSONObject(0).optJSONObject("image_uris");
            }
        }
        if (imageUris == null) return null;
        String normal = imageUris.optString("normal", "");
        if (!normal.isEmpty()) return normal;
        String large = imageUris.optString("large", "");
        if (!large.isEmpty()) return large;
        String png = imageUris.optString("png", "");
        return png.isEmpty() ? null : png;
    }

    String metaLine() {
        String year = releasedAt.length() >= 4 ? releasedAt.substring(0, 4) : "Unknown date";
        return setName + " · " + setCode + " #" + collectorNumber + " · " + year;
    }

    String printingLabel() {
        return setName + " (" + setCode + ") #" + collectorNumber + " · "
                + (releasedAt.length() >= 4 ? releasedAt.substring(0, 4) : "Unknown date");
    }

    String primaryPrice() {
        List<String> prices = priceParts();
        return prices.isEmpty()
                ? "TCGplayer · No current USD price"
                : "TCGplayer · " + prices.get(0);
    }

    String secondaryPrice() {
        List<String> prices = priceParts();
        if (prices.size() < 2) return "Daily market estimate via Scryfall";
        return String.join(" · ", prices.subList(1, prices.size()));
    }

    String fullPriceLine() {
        List<String> prices = priceParts();
        return prices.isEmpty()
                ? "No current USD price for this printing"
                : "TCGplayer market · " + String.join(" · ", prices);
    }

    private List<String> priceParts() {
        List<String> prices = new ArrayList<>();
        if (usd != null) prices.add("Normal $" + usd);
        if (usdFoil != null) prices.add("Foil $" + usdFoil);
        if (usdEtched != null) prices.add("Etched $" + usdEtched);
        return prices;
    }

    @Override
    public String toString() {
        return printingLabel();
    }
}
