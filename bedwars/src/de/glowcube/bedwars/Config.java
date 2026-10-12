package de.glowcube.bedwars;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Einstellungen in config/glowcube_bedwars.properties. */
public final class Config {
   public static final String[] DIFFICULTIES = new String[]{"leicht", "normal", "schwer", "profi"};
   /** 0 leicht, 1 normal, 2 schwer, 3 profi */
   public static int difficulty = 1;

   private Config() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("glowcube_bedwars.properties");
   }

   private static Properties read() {
      Properties p = new Properties();

      try {
         Path f = file();
         if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f)) {
               p.load(r);
            }
         }
      } catch (Exception e) {
         System.err.println("[Bedwars] Einstellungen unlesbar: " + e);
      }

      return p;
   }

   public static void load() {
      Properties p = read();

      String d = p.getProperty("schwierigkeit", "normal").trim().toLowerCase();

      for (int i = 0; i < DIFFICULTIES.length; i++) {
         if (DIFFICULTIES[i].equals(d)) {
            difficulty = i;
         }
      }

      save(p);
   }

   public static void save() {
      save(read());
   }

   private static void save(Properties p) {
      p.setProperty("schwierigkeit", DIFFICULTIES[difficulty]);

      try (Writer w = Files.newBufferedWriter(file())) {
         p.store(w, "GlowCube Bedwars - schwierigkeit: leicht|normal|schwer|profi");
      } catch (Exception e) {
         System.err.println("[Bedwars] Einstellungen nicht gespeichert: " + e);
      }
   }
}
