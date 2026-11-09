package app.velora.bike;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * BikeOra Plus ueber Google Play Billing.
 * Produkt in der Play Console: Abo "bikeora_plus" mit den Basis-Abos "monthly" und "yearly".
 * Die App fragt beim Start, ob ein aktives Abo besteht; gekuendigte oder abgelaufene Abos liefert Play nicht mehr.
 * Ereignisse gehen an die App-Oberflaeche: VX.billingEv(typ, daten).
 */
public class Billing implements PurchasesUpdatedListener, Pay {
    static final String PRODUCT = "bikeora_plus";

    private final Activity act;
    private final Llm.Out out;
    private final BillingClient client;
    private volatile boolean ready = false;
    private volatile ProductDetails details = null;
    private volatile String lastError = "";

    public Billing(Activity a, Llm.Out o) {
        act = a;
        out = o;
        client = BillingClient.newBuilder(a)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        connect();
    }

    private void ev(String type, JSONObject data) {
        out.js("window.VX&&VX.billingEv&&VX.billingEv(" + JSONObject.quote(type) + "," + data.toString() + ")");
    }

    private static JSONObject o() { return new JSONObject(); }

    private void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    ready = true;
                    loadDetails();
                    refresh();
                } else {
                    lastError = r.getDebugMessage();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                ready = false;
            }
        });
    }

    private void loadDetails() {
        QueryProductDetailsParams p = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()))
                .build();
        client.queryProductDetailsAsync(p, (r, res) -> {
            List<ProductDetails> l = res.getProductDetailsList();
            details = l != null && !l.isEmpty() ? l.get(0) : null;
            if (details == null) lastError = "Produkt " + PRODUCT + " nicht gefunden (in der Play Console anlegen)";
            try { JSONObject d = new JSONObject(info()); ev("info", d); } catch (Exception ignored) { }
        });
    }

    /** Stand fuer die Oberflaeche: verfuegbar, Preise je Basis-Abo. */
    @Override
    public String info() {
        try {
            JSONObject j = o();
            j.put("ready", ready);
            j.put("available", ready && details != null);
            j.put("error", lastError);
            JSONArray plans = new JSONArray();
            ProductDetails d = details;
            if (d != null && d.getSubscriptionOfferDetails() != null) {
                for (ProductDetails.SubscriptionOfferDetails off : d.getSubscriptionOfferDetails()) {
                    if (off.getOfferId() != null) continue; // nur die Basis-Abos, keine Sonderangebote
                    List<ProductDetails.PricingPhase> ph = off.getPricingPhases().getPricingPhaseList();
                    if (ph.isEmpty()) continue;
                    ProductDetails.PricingPhase last = ph.get(ph.size() - 1);
                    JSONObject p = o();
                    p.put("id", off.getBasePlanId());
                    p.put("price", last.getFormattedPrice());
                    p.put("micros", last.getPriceAmountMicros());
                    p.put("currency", last.getPriceCurrencyCode());
                    p.put("period", last.getBillingPeriod());
                    plans.put(p);
                }
            }
            j.put("plans", plans);
            return j.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public boolean available() { return ready && details != null; }

    /** Kauf starten; plan = "monthly" oder "yearly" (Basis-Abo-ID in der Play Console). */
    @Override
    public void buy(final String plan) {
        final ProductDetails d = details;
        if (!ready || d == null || d.getSubscriptionOfferDetails() == null) {
            try { ev("error", o().put("msg", "Der Kauf ist gerade nicht möglich.")); } catch (Exception ignored) { }
            return;
        }
        String token = null;
        // Bevorzugt ein Angebot fuer dieses Basis-Abo (z. B. mit Gratiszeitraum), sonst das Basis-Abo selbst
        for (ProductDetails.SubscriptionOfferDetails off : d.getSubscriptionOfferDetails()) {
            if (!plan.equals(off.getBasePlanId())) continue;
            if (off.getOfferId() != null) { token = off.getOfferToken(); break; }
            if (token == null) token = off.getOfferToken();
        }
        if (token == null) {
            try { ev("error", o().put("msg", "Dieses Abo ist gerade nicht verfügbar.")); } catch (Exception ignored) { }
            return;
        }
        final BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d).setOfferToken(token).build()))
                .build();
        act.runOnUiThread(() -> {
            BillingResult r = client.launchBillingFlow(act, params);
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                try { ev("error", o().put("msg", "Der Kauf konnte nicht gestartet werden.").put("code", r.getResponseCode())); } catch (Exception ignored) { }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult r, List<Purchase> list) {
        int c = r.getResponseCode();
        try {
            if (c == BillingClient.BillingResponseCode.OK && list != null) {
                for (Purchase p : list) handle(p, true);
            } else if (c == BillingClient.BillingResponseCode.USER_CANCELED) {
                ev("cancel", o());
            } else if (c == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refresh();
            } else {
                ev("error", o().put("msg", "Der Kauf hat nicht geklappt.").put("code", c));
            }
        } catch (Exception ignored) { }
    }

    private void handle(final Purchase p, final boolean fresh) throws Exception {
        if (!p.getProducts().contains(PRODUCT)) return;
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            ev("pending", o());
            return;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        // Ohne Bestaetigung erstattet Google den Kauf nach 3 Tagen automatisch
        if (!p.isAcknowledged()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), ar -> { });
        }
        ev(fresh ? "bought" : "status", o().put("on", true).put("token", p.getPurchaseToken()).put("renew", p.isAutoRenewing()));
    }

    /** Aktuellen Abo-Stand von Google Play holen (beim Start und nach dem Zurueckkehren in die App). */
    @Override
    public void refresh() {
        if (!ready) return;
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(), (r, purchases) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            boolean on = false;
            try {
                for (Purchase p : purchases) {
                    if (p.getProducts().contains(PRODUCT) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { on = true; handle(p, false); }
                }
                if (!on) ev("status", o().put("on", false));
            } catch (Exception ignored) { }
        });
    }

    /** Abo im Play Store verwalten oder kuendigen. */
    @Override
    public void manage() {
        try {
            Uri u = Uri.parse("https://play.google.com/store/account/subscriptions?sku=" + PRODUCT + "&package=" + act.getPackageName());
            act.startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) { }
    }
}
