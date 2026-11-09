package app.velora.bike;

/** Bezahlen: von Billing.java umgesetzt (nur im Gradle-Build mit der Billing Library vorhanden). */
public interface Pay {
    String info();
    boolean available();
    void buy(String plan);
    void refresh();
    void manage();
}
