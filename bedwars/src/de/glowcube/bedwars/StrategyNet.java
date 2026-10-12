package de.glowcube.bedwars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Das kleine neuronale Netz, das die Strategie bestimmt: es bekommt die Lage aus Sicht eines Bots
 * (Bett, Ressourcen, Gegner auf der Insel, was die Teamkollegen schon machen, wie die Gegner spielen ...)
 * und bewertet jede moegliche Aufgabe. Trainiert wurde es erst durch Nachahmen des alten Regelwerks,
 * danach durch Selbstspiel (Evolution) auf einem Testserver - siehe tools/train_brain.py.
 */
public final class StrategyNet {
   public static final int IN = 36;
   public static final int OUT = Plan.values().length;

   public enum Plan {
      GATHER,
      SHOP,
      FORTIFY,
      DEFEND,
      RAID,
      MID,
      HUNT,
      ESCORT,
      REGROUP
   }

   private final int hidden;
   private final double[] w1;
   private final double[] b1;
   private final double[] w2;
   private final double[] b2;
   private final double[] mean;
   private final double[] std;
   public final String name;

   private StrategyNet(JsonObject o) {
      this.hidden = o.get("hidden").getAsInt();
      this.w1 = arr(o, "w1");
      this.b1 = arr(o, "b1");
      this.w2 = arr(o, "w2");
      this.b2 = arr(o, "b2");
      this.mean = o.has("mean") ? arr(o, "mean") : new double[IN];
      this.std = o.has("std") ? arr(o, "std") : ones(IN);
      this.name = o.has("name") ? o.get("name").getAsString() : "?";
      if (o.get("in").getAsInt() != IN || o.get("out").getAsInt() != OUT || this.w1.length != IN * this.hidden || this.w2.length != this.hidden * OUT) {
         throw new IllegalArgumentException("Netz passt nicht zu dieser Fassung");
      }
   }

   private static double[] ones(int n) {
      double[] d = new double[n];
      java.util.Arrays.fill(d, 1.0);
      return d;
   }

   private static double[] arr(JsonObject o, String key) {
      JsonArray a = o.getAsJsonArray(key);
      double[] d = new double[a.size()];

      for (int i = 0; i < d.length; i++) {
         d[i] = a.get(i).getAsDouble();
      }

      return d;
   }

   public static StrategyNet parse(JsonElement e) {
      return new StrategyNet(e.getAsJsonObject());
   }

   public static StrategyNet fromFile(Path p) throws Exception {
      return parse(JsonParser.parseString(Files.readString(p)));
   }

   private static StrategyNet builtIn;
   private static boolean builtInLoaded;

   /** Das mitgelieferte, fertig trainierte Netz (oder null, dann gilt das Regelwerk). */
   public static StrategyNet builtIn() {
      if (!builtInLoaded) {
         builtInLoaded = true;

         try (InputStream in = StrategyNet.class.getResourceAsStream("/assets/glowcube_bedwars/brain.json")) {
            if (in != null) {
               builtIn = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
               System.out.println("[Bedwars] Strategie-Netz geladen (" + builtIn.name + ", " + IN + "-" + builtIn.hidden + "-" + OUT + ")");
            }
         } catch (Exception e) {
            System.err.println("[Bedwars] Strategie-Netz nicht lesbar, nutze Regelwerk: " + e);
         }
      }

      return builtIn;
   }

   /** Bewertet alle Aufgaben fuer diese Lage. */
   public double[] forward(double[] x) {
      double[] h = new double[this.hidden];

      for (int j = 0; j < this.hidden; j++) {
         double s = this.b1[j];

         for (int i = 0; i < IN; i++) {
            s += (x[i] - this.mean[i]) / this.std[i] * this.w1[i * this.hidden + j];
         }

         h[j] = Math.tanh(s);
      }

      double[] out = new double[OUT];

      for (int k = 0; k < OUT; k++) {
         double s = this.b2[k];

         for (int j = 0; j < this.hidden; j++) {
            s += h[j] * this.w2[j * OUT + k];
         }

         out[k] = s;
      }

      return out;
   }
}
