package site.jrobin17.cardboardwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class CardSearchActivity extends Activity {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger searchRequestNumber = new AtomicInteger();
    private final AtomicInteger autocompleteRequestNumber = new AtomicInteger();
    private final AtomicInteger previewRequestNumber = new AtomicInteger();

    private AutoCompleteTextView cardSearch;
    private Button searchButton;
    private Button saveButton;
    private ProgressBar progress;
    private TextView status;
    private TextView printingLabel;
    private Spinner printingSpinner;
    private ImageView cardPreview;
    private TextView pricePreview;
    private ArrayAdapter<String> suggestionAdapter;
    private ArrayAdapter<CardPrinting> printingAdapter;
    private List<CardPrinting> printings = new ArrayList<>();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean configuringWidget;
    private Runnable autocompleteRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_search);

        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        configuringWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
        setResult(
                RESULT_CANCELED,
                new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        );

        bindViews();
        configureSearch();
        configureActions();

        if (!configuringWidget) {
            saveButton.setText("Add the widget to pin this card");
        } else {
            String currentCardId = WidgetStore.getCardId(this, widgetId);
            if (currentCardId != null) loadExistingSelection(currentCardId);
        }
    }

    private void bindViews() {
        cardSearch = findViewById(R.id.card_search);
        searchButton = findViewById(R.id.search_button);
        saveButton = findViewById(R.id.save_button);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        printingLabel = findViewById(R.id.printing_label);
        printingSpinner = findViewById(R.id.printing_spinner);
        cardPreview = findViewById(R.id.card_preview);
        pricePreview = findViewById(R.id.price_preview);

        suggestionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );
        cardSearch.setAdapter(suggestionAdapter);
    }

    private void configureSearch() {
        cardSearch.setOnItemClickListener((parent, view, position, id) -> {
            String name = suggestionAdapter.getItem(position);
            if (name != null) loadPrintings(name);
        });
        cardSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchFromField();
                return true;
            }
            return false;
        });
        cardSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                if (autocompleteRunnable != null) mainHandler.removeCallbacks(autocompleteRunnable);
                String query = value.toString().trim();
                if (query.length() < 2) {
                    suggestionAdapter.clear();
                    return;
                }
                autocompleteRunnable = () -> loadAutocomplete(query);
                mainHandler.postDelayed(autocompleteRunnable, 220L);
            }
        });
    }

    private void configureActions() {
        searchButton.setOnClickListener(view -> searchFromField());
        printingSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < printings.size()) previewPrinting(printings.get(position));
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        saveButton.setOnClickListener(view -> saveSelection());
        findViewById(R.id.open_site_button).setOnClickListener(view ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ApiClient.SITE_ROOT)))
        );
    }

    private void searchFromField() {
        String query = cardSearch.getText().toString().trim();
        if (query.isEmpty()) {
            status.setText("Enter a card name first.");
            return;
        }
        loadPrintings(query);
    }

    private void loadAutocomplete(String query) {
        int request = autocompleteRequestNumber.incrementAndGet();
        executor.execute(() -> {
            try {
                List<String> names = ApiClient.autocomplete(query);
                mainHandler.post(() -> {
                    if (request != autocompleteRequestNumber.get()
                            || !query.equals(cardSearch.getText().toString().trim())) return;
                    suggestionAdapter.clear();
                    suggestionAdapter.addAll(names);
                    suggestionAdapter.notifyDataSetChanged();
                    if (!names.isEmpty()) cardSearch.showDropDown();
                });
            } catch (Exception ignored) {
                // Autocomplete failure should not prevent a direct search.
            }
        });
    }

    private void loadPrintings(String query) {
        int request = searchRequestNumber.incrementAndGet();
        previewRequestNumber.incrementAndGet();
        setBusy(true, "Checking the cardboard archive…");
        executor.execute(() -> {
            try {
                List<CardPrinting> results = ApiClient.searchPrintings(query);
                mainHandler.post(() -> {
                    if (request != searchRequestNumber.get()) return;
                    if (results.isEmpty()) {
                        setBusy(false, "No viewable printings were returned.");
                        return;
                    }
                    showPrintings(results);
                    setBusy(false, results.size() == 1
                            ? "1 printing found. A rare outbreak of restraint."
                            : results.size() + " printings found. One was evidently not enough.");
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (request == searchRequestNumber.get()) {
                        setBusy(false, friendlyError(error));
                    }
                });
            }
        });
    }

    private void loadExistingSelection(String cardId) {
        int request = searchRequestNumber.incrementAndGet();
        previewRequestNumber.incrementAndGet();
        setBusy(true, "Loading the currently assigned cardboard…");
        executor.execute(() -> {
            try {
                CardPrinting card = ApiClient.fetchPrinting(cardId);
                mainHandler.post(() -> {
                    if (request != searchRequestNumber.get()) return;
                    cardSearch.setText(card.name, false);
                    showPrintings(Collections.singletonList(card));
                    setBusy(false, "Current widget selection loaded.");
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (request == searchRequestNumber.get()) setBusy(false, friendlyError(error));
                });
            }
        });
    }

    private void showPrintings(List<CardPrinting> results) {
        printings = new ArrayList<>(results);
        printingAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, printings);
        printingAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        printingSpinner.setAdapter(printingAdapter);
        printingLabel.setVisibility(View.VISIBLE);
        printingSpinner.setVisibility(View.VISIBLE);
        saveButton.setEnabled(configuringWidget);
    }

    private void previewPrinting(CardPrinting card) {
        int request = previewRequestNumber.incrementAndGet();
        pricePreview.setText(card.fullPriceLine());
        pricePreview.setVisibility(View.VISIBLE);
        cardPreview.setImageResource(R.drawable.card_placeholder);
        cardPreview.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                Bitmap image = ApiClient.fetchCardImage(card.imageUrl);
                mainHandler.post(() -> {
                    if (request == previewRequestNumber.get()) cardPreview.setImageBitmap(image);
                });
            } catch (Exception ignored) {
                // The selected printing and price remain usable without a preview image.
            }
        });
    }

    private void saveSelection() {
        if (!configuringWidget) {
            Toast.makeText(this, "Add the widget from the home screen first.", Toast.LENGTH_LONG).show();
            return;
        }
        int position = printingSpinner.getSelectedItemPosition();
        if (position < 0 || position >= printings.size()) return;

        CardPrinting selected = printings.get(position);
        WidgetStore.saveSelection(this, widgetId, selected);
        WidgetUpdater.showCached(this, widgetId);
        WidgetUpdater.refresh(this, widgetId, null);

        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!busy);
        printingSpinner.setEnabled(!busy);
        status.setText(message);
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "The internet declined to elaborate.";
        }
        if (message.toLowerCase().contains("not found")) {
            return "No card matched that name. Magic has enough cards without us inventing another.";
        }
        return message;
    }

    @Override
    protected void onDestroy() {
        if (autocompleteRunnable != null) mainHandler.removeCallbacks(autocompleteRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }
}
