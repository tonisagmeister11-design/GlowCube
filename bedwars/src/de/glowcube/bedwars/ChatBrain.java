package de.glowcube.bedwars;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Die kleine Sprach-KI der Bots, ganz ohne Internet.
 *
 * <p>Schreiben: Fuer jede Absicht (Kill, Bett weg, Begruessung ...) lernt ein Wort-Trigramm-Modell
 * aus dem Korpus, welche Woerter aufeinander folgen. Daraus zieht es neue Saetze, bewertet mehrere
 * Kandidaten nach Fluessigkeit und Neuheit und nimmt einen davon. Danach schreibt jeder Bot ihn in
 * seinem eigenen Stil: Slang, Abkuerzungen, Smileys, Grossschreibung, Tippfehler.
 *
 * <p>Verstehen: Ein Naive-Bayes-Klassifikator lernt aus Beispielsaetzen, was der Spieler meint
 * (Befehl, Frage, Beleidigung, Gruss ...), und liefert dazu, wie sicher er sich ist.
 */
public final class ChatBrain {
   private static final String START = "<s>";
   private static final String END = "</s>";
   private static final Random RND = new Random();
   private static final Map<String, Model> MODELS = new HashMap<>();
   private static final Set<String> CORPUS_LINES = new HashSet<>();
   /** Zuletzt geschriebene Saetze - niemand wiederholt dauernd dasselbe. */
   private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();
   private static Classifier classifier;
   private static boolean loaded;

   private ChatBrain() {
   }

   /** Ein Trigramm-Modell fuer eine Absicht. */
   private static final class Model {
      final Map<String, Map<String, Integer>> tri = new HashMap<>();
      final Map<String, Map<String, Integer>> bi = new HashMap<>();
      int lines;

      void learn(String[] w) {
         this.lines++;
         String a = START;
         String b = START;

         for (int i = 0; i <= w.length; i++) {
            String c = i < w.length ? w[i] : END;
            this.tri.computeIfAbsent(a + " " + b, k -> new HashMap<>()).merge(c, 1, Integer::sum);
            this.bi.computeIfAbsent(b, k -> new HashMap<>()).merge(c, 1, Integer::sum);
            a = b;
            b = c;
         }
      }
   }

   private static void load() {
      if (loaded) {
         return;
      }

      loaded = true;
      for (String line : readLines("/assets/glowcube_bedwars/chat/corpus.txt")) {
         int bar = line.indexOf('|');
         if (bar > 0) {
            String intent = line.substring(0, bar).trim();
            String text = line.substring(bar + 1).trim().toLowerCase(Locale.ROOT);
            CORPUS_LINES.add(text);
            MODELS.computeIfAbsent(intent, k -> new Model()).learn(text.split("\\s+"));
         }
      }

      classifier = new Classifier();
      for (String line : readLines("/assets/glowcube_bedwars/chat/intents.txt")) {
         int bar = line.indexOf('|');
         if (bar > 0) {
            classifier.learn(line.substring(0, bar).trim(), normalize(line.substring(bar + 1)));
         }
      }
   }

   private static List<String> readLines(String res) {
      List<String> out = new ArrayList<>();

      try (InputStream in = ChatBrain.class.getResourceAsStream(res)) {
         if (in != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;

            while ((line = r.readLine()) != null) {
               if (!line.isBlank() && !line.startsWith("#")) {
                  out.add(line);
               }
            }
         }
      } catch (Exception e) {
         System.err.println("[Bedwars] Chat-Daten nicht lesbar: " + e);
      }

      return out;
   }

   // ======================================================================== Schreiben

   private static String sample(Map<String, Integer> options, double temperature) {
      double total = 0.0;

      for (int c : options.values()) {
         total += Math.pow(c, 1.0 / temperature);
      }

      double x = RND.nextDouble() * total;

      for (Map.Entry<String, Integer> e : options.entrySet()) {
         x -= Math.pow(e.getValue(), 1.0 / temperature);
         if (x <= 0.0) {
            return e.getKey();
         }
      }

      return END;
   }

   private record Draft(List<String> words, double logProb, int backoffs) {
   }

   private static Draft draft(Model m, double temperature) {
      List<String> out = new ArrayList<>();
      String a = START;
      String b = START;
      double lp = 0.0;
      int backoffs = 0;

      for (int i = 0; i < 18; i++) {
         Map<String, Integer> next = m.tri.get(a + " " + b);
         if (next == null || next.isEmpty()) {
            next = m.bi.get(b);
            backoffs++;
            if (next == null) {
               break;
            }
         }

         String c = sample(next, temperature);
         int total = next.values().stream().mapToInt(Integer::intValue).sum();
         lp += Math.log(next.getOrDefault(c, 1) / (double)total);
         if (c.equals(END)) {
            break;
         }

         out.add(c);
         a = b;
         b = c;
      }

      return new Draft(out, lp, backoffs);
   }

   /**
    * Bildet einen neuen Satz fuer diese Absicht. Platzhalter werden mit den Werten aus slots
    * gefuellt; Saetze mit Platzhaltern, fuer die es keinen Wert gibt, werden verworfen.
    */
   public static String compose(String intent, Map<String, String> slots, double creativity) {
      load();
      Model m = MODELS.get(intent);
      if (m == null) {
         return null;
      } else {
         String best = null;
         double bestScore = -1.0E9;

         for (int i = 0; i < 10; i++) {
            Draft d = draft(m, 0.8 + creativity * 0.6);
            if (d.words.isEmpty() || d.words.size() > 14) {
               continue;
            }

            String text = String.join(" ", d.words);
            boolean ok = true;

            for (String w : d.words) {
               if (w.contains("{") && !slots.containsKey(w.replaceAll("[^a-z]", ""))) {
                  ok = false;
               }
            }

            if (!ok) {
               continue;
            }

            // fluessig, aber nicht abgeschrieben: neue Kombinationen werden bevorzugt
            double score = d.logProb / Math.max(1, d.words.size()) * 1.2 - d.backoffs * 0.8;
            if (!CORPUS_LINES.contains(text)) {
               score += 0.6 * creativity;
            }

            if (RECENT.contains(text)) {
               score -= 4.0;
            }

            score += RND.nextGaussian() * 0.3;
            if (score > bestScore) {
               bestScore = score;
               best = text;
            }
         }

         if (best == null) {
            return null;
         } else {
            RECENT.addLast(best);
            if (RECENT.size() > 40) {
               RECENT.removeFirst();
            }

            for (Map.Entry<String, String> e : slots.entrySet()) {
               best = best.replace("{" + e.getKey() + "}", e.getValue());
            }

            return best;
         }
      }
   }

   // ======================================================================== Schreibstil

   /** Die Schreibgewohnheiten eines Bots - einmal ausgewuerfelt, dann bleiben sie. */
   public static final class Voice {
      final double lowercase;
      final double emoji;
      final String[] emojis;
      final double slang;
      final String[] slangWords;
      final double abbreviate;
      final double typo;
      final double shout;
      final boolean noPunctuation;

      public Voice(Random r, double cheek, Hands.Style style) {
         this.lowercase = 0.4 + r.nextDouble() * 0.6;
         this.emoji = r.nextDouble() * (0.15 + cheek * 0.25);
         String[][] sets = new String[][]{{":D", "xD", "lol"}, {":)", "^^", ":P"}, {"xd", "lmao", "haha"}, {"!", "!!", ":O"}};
         this.emojis = sets[r.nextInt(sets.length)];
         this.slang = r.nextDouble() * 0.25;
         String[][] slangs = new String[][]{{"digga", "bro"}, {"alter", "junge"}, {"bruder", "safe"}, {"leute", "ey"}, {"jo", "na"}};
         this.slangWords = slangs[r.nextInt(slangs.length)];
         this.abbreviate = r.nextDouble() * 0.6;
         this.typo = style == Hands.Style.NOOB ? 0.08 : (style == Hands.Style.SWEAT ? 0.015 : 0.035);
         this.shout = cheek * 0.08;
         this.noPunctuation = r.nextBoolean();
      }

      public String apply(String text) {
         String s = text;
         if (RND.nextDouble() < this.abbreviate) {
            s = (" " + s + " ").replace(" nicht ", " nich ").replace(" habe ", " hab ").replace(" einen ", " nen ").replace(" ich bin ", " bin ")
               .replace(" ist ", RND.nextBoolean() ? " is " : " ist ").replace(" schon ", " schon ").trim();
         }

         if (RND.nextDouble() < this.slang) {
            String w = this.slangWords[RND.nextInt(this.slangWords.length)];
            s = RND.nextBoolean() ? w + " " + s : s + " " + w;
         }

         if (RND.nextDouble() < this.emoji) {
            s = s + " " + this.emojis[RND.nextInt(this.emojis.length)];
         }

         if (s.length() > 5 && RND.nextDouble() < this.typo) {
            char[] c = s.toCharArray();
            int i = 1 + RND.nextInt(c.length - 2);
            if (Character.isLetter(c[i]) && Character.isLetter(c[i + 1])) {
               char t = c[i];
               c[i] = c[i + 1];
               c[i + 1] = t;
            }

            s = new String(c);
         }

         if (RND.nextDouble() < this.shout) {
            s = s.toUpperCase(Locale.ROOT);
         } else if (RND.nextDouble() > this.lowercase && !s.isEmpty()) {
            s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
         }

         return s;
      }
   }

   // ======================================================================== Verstehen

   public static String normalize(String raw) {
      String s = raw.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
      s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      return s.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
   }

   /** Was der Spieler (vermutlich) meint, und wie sicher das Modell sich ist. */
   public record Guess(String intent, double confidence) {
   }

   public static Guess understand(String raw) {
      load();
      return classifier.classify(normalize(raw));
   }

   /** Multinomialer Naive Bayes ueber Woerter, Wortpaare und Wortanfaenge. */
   private static final class Classifier {
      final Map<String, Map<String, Integer>> counts = new HashMap<>();
      final Map<String, Integer> totals = new HashMap<>();
      final Map<String, Integer> docs = new HashMap<>();
      final Set<String> vocab = new HashSet<>();
      int allDocs;

      static List<String> features(String text) {
         List<String> f = new ArrayList<>();
         String[] w = text.split(" ");

         for (int i = 0; i < w.length; i++) {
            if (w[i].isEmpty()) {
               continue;
            }

            f.add(w[i]);
            if (w[i].length() > 4) {
               f.add(w[i].substring(0, 4) + "*");
            }

            if (i + 1 < w.length) {
               f.add(w[i] + "_" + w[i + 1]);
            }
         }

         return f;
      }

      void learn(String label, String text) {
         this.allDocs++;
         this.docs.merge(label, 1, Integer::sum);
         Map<String, Integer> c = this.counts.computeIfAbsent(label, k -> new HashMap<>());

         for (String f : features(text)) {
            c.merge(f, 1, Integer::sum);
            this.totals.merge(label, 1, Integer::sum);
            this.vocab.add(f);
         }
      }

      Guess classify(String text) {
         List<String> f = features(text);
         if (f.isEmpty()) {
            return new Guess("other", 0.0);
         }

         Map<String, Double> logp = new HashMap<>();
         double max = -1.0E18;
         int known = 0;

         for (String feat : f) {
            if (this.vocab.contains(feat)) {
               known++;
            }
         }

         for (String label : this.counts.keySet()) {
            double lp = Math.log(this.docs.get(label) / (double)this.allDocs);
            Map<String, Integer> c = this.counts.get(label);
            double denom = this.totals.getOrDefault(label, 0) + this.vocab.size() * 0.5;

            for (String feat : f) {
               if (this.vocab.contains(feat)) {
                  lp += Math.log((c.getOrDefault(feat, 0) + 0.5) / denom);
               }
            }

            logp.put(label, lp);
            max = Math.max(max, lp);
         }

         double sum = 0.0;
         String best = "other";
         double bestP = 0.0;

         for (Map.Entry<String, Double> e : logp.entrySet()) {
            sum += Math.exp(e.getValue() - max);
         }

         for (Map.Entry<String, Double> e : logp.entrySet()) {
            double p = Math.exp(e.getValue() - max) / sum;
            if (p > bestP) {
               bestP = p;
               best = e.getKey();
            }
         }

         // unbekannte Woerter senken die Sicherheit
         double coverage = (double)known / f.size();
         return new Guess(known == 0 ? "other" : best, bestP * coverage);
      }
   }
}
