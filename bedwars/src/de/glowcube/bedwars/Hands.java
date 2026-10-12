package de.glowcube.bedwars;

import java.util.Random;

/**
 * Wie ein Mensch an Maus und Tastatur: Reaktionszeit, wie schnell und wie sauber er zielt,
 * wie er klickt, wie gut er strafed und baut - und wie oft er Fehler macht.
 * Jeder Bot bekommt eigene Werte, abhaengig von seinem Spielertyp.
 */
public final class Hands {
   public enum Style {
      /** Laeuft geradeaus drauf, spammt Klicks, faellt beim Bruecken runter. */
      NOOB("Anfaenger"),
      /** Spielt ab und zu, mal gut, mal schlecht. */
      CASUAL("Gelegenheitsspieler"),
      /** Kann strafen, timt seine Schlaege meistens. */
      SOLID("solide"),
      /** Schwitzt: W-Tap, Krits, schnelles Bruecken - aber auch kein Roboter. */
      SWEAT("Schwitzer");

      public final String label;

      Style(String label) {
         this.label = label;
      }
   }

   public final Style style;
   /** Wie viele Ticks er hinterherhinkt, bis er eine Bewegung des Gegners sieht (4 = 200 ms). */
   public final int reaction;
   /** Wie viele Grad er pro Tick das Fadenkreuz hoechstens bewegt. */
   public final double aimSpeed;
   /** Wie stark er nachzieht (Anteil der Abweichung pro Tick). */
   public final double aimGain;
   /** Zittern beim Zielen in Grad. */
   public final double aimNoise;
   /** Ueberschiessen beim Herumreissen der Maus. */
   public final double overshoot;
   /** 0..1: wartet er die Schlag-Abklingzeit ab (1) oder spammt er (0)? */
   public final double timing;
   /** Klicks pro Sekunde, wenn er spammt. */
   public final double cps;
   /** Ab welcher Entfernung er schon (zu frueh) losklickt. */
   public final double clickRange;
   public final double strafe;
   public final double wtap;
   public final double crits;
   /** Wie gut er beim Kaempfen auf die Kante achtet (0 = gar nicht). */
   public final double edge;
   /** Ticks zwischen zwei gesetzten Bloecken beim Bruecken. */
   public final int bridgeDelay;
   /** Chance pro Bruecken-Block, sich zu verschaetzen und runterzufallen. */
   public final double bridgeSlip;
   public final double clutch;
   /** Chance, beim Kampf einen Moment abzuschalten (stehen bleiben, falsch laufen). */
   public final double brainfart;
   /** Chance, in Not den Goldapfel zu vergessen. */
   public final double forgetGapple;
   /** Entdeckt er Gegner frueh oder erst spaet? */
   public final double awareness;
   /** Kann er sich in Not hochbauen? */
   public final double pillar;

   private Hands(Style s, Random r) {
      this.style = s;
      double j = r.nextGaussian() * 0.12;
      switch (s) {
         case NOOB -> {
            this.reaction = 9 + r.nextInt(5);
            this.aimSpeed = 7.0 + r.nextDouble() * 5.0;
            this.aimGain = 0.22 + r.nextDouble() * 0.12;
            this.aimNoise = 4.5 + r.nextDouble() * 3.5;
            this.overshoot = 0.35;
            this.timing = 0.05 + r.nextDouble() * 0.2;
            this.cps = 5.0 + r.nextDouble() * 5.0;
            this.clickRange = 4.5;
            this.strafe = 0.05 + r.nextDouble() * 0.15;
            this.wtap = 0.0;
            this.crits = 0.05;
            this.edge = 0.55;
            this.bridgeDelay = 8 + r.nextInt(4);
            this.bridgeSlip = 0.035;
            this.clutch = 0.03;
            this.brainfart = 0.02;
            this.forgetGapple = 0.6;
            this.awareness = 0.6;
            this.pillar = 0.1;
         }
         case CASUAL -> {
            this.reaction = 6 + r.nextInt(4);
            this.aimSpeed = 11.0 + r.nextDouble() * 6.0;
            this.aimGain = 0.3 + r.nextDouble() * 0.15;
            this.aimNoise = 2.8 + r.nextDouble() * 2.2;
            this.overshoot = 0.25;
            this.timing = 0.3 + r.nextDouble() * 0.3;
            this.cps = 6.0 + r.nextDouble() * 3.0;
            this.clickRange = 3.9;
            this.strafe = 0.3 + r.nextDouble() * 0.25;
            this.wtap = 0.1;
            this.crits = 0.2;
            this.edge = 0.85;
            this.bridgeDelay = 6 + r.nextInt(3);
            this.bridgeSlip = 0.012;
            this.clutch = 0.15;
            this.brainfart = 0.008;
            this.forgetGapple = 0.3;
            this.awareness = 0.8;
            this.pillar = 0.35;
         }
         case SOLID -> {
            this.reaction = 4 + r.nextInt(3);
            this.aimSpeed = 16.0 + r.nextDouble() * 7.0;
            this.aimGain = 0.42 + r.nextDouble() * 0.15;
            this.aimNoise = 1.7 + r.nextDouble() * 1.3;
            this.overshoot = 0.15;
            this.timing = 0.65 + r.nextDouble() * 0.2;
            this.cps = 7.0 + r.nextDouble() * 3.0;
            this.clickRange = 3.5;
            this.strafe = 0.6 + r.nextDouble() * 0.2;
            this.wtap = 0.45;
            this.crits = 0.45;
            this.edge = 0.95;
            this.bridgeDelay = 4 + r.nextInt(2);
            this.bridgeSlip = 0.004;
            this.clutch = 0.45;
            this.brainfart = 0.003;
            this.forgetGapple = 0.1;
            this.awareness = 0.95;
            this.pillar = 0.7;
         }
         default -> {
            this.reaction = 3 + r.nextInt(2);
            this.aimSpeed = 22.0 + r.nextDouble() * 8.0;
            this.aimGain = 0.55 + r.nextDouble() * 0.15;
            this.aimNoise = 1.1 + r.nextDouble() * 0.9;
            this.overshoot = 0.1;
            this.timing = 0.85 + r.nextDouble() * 0.12;
            this.cps = 8.0 + r.nextDouble() * 4.0;
            this.clickRange = 3.3;
            this.strafe = 0.8 + r.nextDouble() * 0.15;
            this.wtap = 0.75;
            this.crits = 0.7;
            this.edge = 0.98;
            this.bridgeDelay = 3 + r.nextInt(2);
            this.bridgeSlip = 0.0008;
            this.clutch = 0.7;
            this.brainfart = 0.001;
            this.forgetGapple = 0.03;
            this.awareness = 1.0;
            this.pillar = 0.9;
         }
      }

      // kleine persoenliche Abweichung, damit nicht zwei Bots gleich spielen
      this.aimSpeedJitter = 1.0 + j;
   }

   private final double aimSpeedJitter;

   public double aimSpeed() {
      return this.aimSpeed * this.aimSpeedJitter;
   }

   /** Ein Wert 0..1 fuer alles, was grob "wie gut" heisst (Bauen, Abbauen, Entscheidungen). */
   public double skill() {
      return switch (this.style) {
         case NOOB -> 0.25;
         case CASUAL -> 0.5;
         case SOLID -> 0.72;
         case SWEAT -> 0.9;
      };
   }

   /** Wuerfelt einen Spielertyp passend zur eingestellten Schwierigkeit. */
   public static Hands roll(Random r, int difficulty) {
      double[] p = switch (difficulty) {
         case 0 -> new double[]{0.45, 0.4, 0.13, 0.02};
         case 2 -> new double[]{0.05, 0.3, 0.45, 0.2};
         case 3 -> new double[]{0.0, 0.1, 0.45, 0.45};
         default -> new double[]{0.2, 0.45, 0.28, 0.07};
      };
      double x = r.nextDouble();
      Style s = Style.SWEAT;
      double acc = 0.0;

      for (int i = 0; i < 4; i++) {
         acc += p[i];
         if (x < acc) {
            s = Style.values()[i];
            break;
         }
      }

      return new Hands(s, r);
   }
}
