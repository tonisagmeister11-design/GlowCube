package de.glowcube.bedwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Multiplayer-Lobby: wer "bedwars 2" oder "bedwars multiplayer" in den Chat schreibt, laedt alle auf dem
 * Server (z.B. ueber Essential geteilte Welt) ein. Wer annimmt, spielt mit - jeder echte Spieler
 * ersetzt einen Bot, die Runde bleibt also bei 5 Teams mit je 4 Spielern.
 */
public final class Lobby {
   private static final int WAIT_TICKS = 20 * 30;

   private static MinecraftServer server;
   private static UUID host;
   private static final Set<UUID> accepted = new LinkedHashSet<>();
   private static final Set<UUID> declined = new LinkedHashSet<>();
   private static final Set<UUID> invited = new LinkedHashSet<>();
   private static boolean together = true;
   private static int startAt = -1;
   private static boolean open;

   private Lobby() {
   }

   public static boolean open() {
      return open;
   }

   // ====================================================================== Chat erkennen

   /** "bedwars 2", "bad wars multiplayer", "bedwars zu zweit", "bw mp" ... */
   static boolean isTrigger(String raw) {
      String c = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
      return c.matches(".*(bed|bad|bet|bett|bat)wars(2|zwei|mp|multi|multiplayer|zuzweit|zusammen|mitfreunden|online).*")
         || c.matches("(multiplayer|mp)(bed|bad|bett)wars.*")
         || c.equals("bw2")
         || c.equals("bwmp");
   }

   private static boolean isYes(String s) {
      return s.matches("(ja+|jo+|jaa*\\s*klar|klar|gerne?|yes|yep|ok|okay|bin dabei|ich bin dabei|dabei|ich|ich auch|los|bin am start|safe|na klar|jap|jup)[!. ]*");
   }

   private static boolean isNo(String s) {
      return s.matches("(nein|ne+|noe|nö|no|nope|keine lust|lieber nicht|nicht jetzt|spaeter|später)[!. ]*");
   }

   /** @return true, wenn die Nachricht fuer die Lobby war (dann reagieren die Bots nicht darauf). */
   public static boolean onChat(ServerPlayer p, String raw) {
      if (raw == null) {
         return false;
      }

      if (isTrigger(raw)) {
         request(p);
         return true;
      }

      if (!open) {
         return false;
      }

      String s = raw.toLowerCase(Locale.ROOT).trim();
      UUID id = p.getUUID();
      if (id.equals(host) && s.matches("(los|start|starten|jetzt|go|los gehts)[!. ]*")) {
         startNow(p);
         return true;
      }

      if (invited.contains(id) && !accepted.contains(id) && !declined.contains(id)) {
         if (isYes(s)) {
            accept(p);
            return true;
         } else if (isNo(s)) {
            decline(p);
            return true;
         }
      }

      return false;
   }

   // ====================================================================== Ablauf

   /** Jemand will eine Multiplayer-Runde. */
   public static void request(ServerPlayer p) {
      MinecraftServer srv = p.level().getServer();
      if (open) {
         if (!accepted.contains(p.getUUID())) {
            invited.add(p.getUUID());
            accept(p);
         } else {
            p.sendSystemMessage(Component.literal("Die Einladung laeuft schon - gleich geht's los.").withStyle(ChatFormatting.YELLOW));
         }

         return;
      }

      if (Game.running() && Game.human(p) == null) {
         p.sendSystemMessage(Component.literal("Gerade laeuft eine Runde ohne dich. Sobald sie vorbei ist, kannst du mit \"bedwars 2\" eine neue starten.").withStyle(ChatFormatting.YELLOW));
         return;
      }

      List<ServerPlayer> others = new ArrayList<>();
      for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
         if (o != p) {
            others.add(o);
         }
      }

      if (others.isEmpty()) {
         p.sendSystemMessage(header("BEDWARS MULTIPLAYER"));
         p.sendSystemMessage(Component.literal("Gerade ist niemand ausser dir online.").withStyle(ChatFormatting.YELLOW));
         p.sendSystemMessage(Component.literal("Lade deine Freunde ueber Essential in deine Welt ein (Esc -> Welt teilen / Freunde einladen) und schreib dann nochmal ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("bedwars 2").withStyle(ChatFormatting.GOLD)).append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
         return;
      }

      server = srv;
      open = true;
      host = p.getUUID();
      together = true;
      accepted.clear();
      declined.clear();
      invited.clear();
      accepted.add(host);
      startAt = srv.getTickCount() + WAIT_TICKS;

      for (ServerPlayer o : others) {
         invite(o, p);
      }

      p.sendSystemMessage(header("BEDWARS MULTIPLAYER"));
      p.sendSystemMessage(Component.literal("Einladung an " + others.size() + (others.size() == 1 ? " Spieler" : " Spieler") + " verschickt. Start in 30 Sekunden.").withStyle(ChatFormatting.YELLOW));
      if (Game.running()) {
         p.sendSystemMessage(Component.literal("Die laufende Runde wird beim Start beendet.").withStyle(ChatFormatting.GRAY));
      }

      p.sendSystemMessage(hostButtons());
      p.sendSystemMessage(Component.literal("Jeder echte Spieler ersetzt einen Bot - es bleiben 5 Teams mit je " + Game.TEAM_SIZE + ".").withStyle(ChatFormatting.DARK_GRAY));
      ping(p, 1.6F);
   }

   private static void invite(ServerPlayer o, ServerPlayer from) {
      invited.add(o.getUUID());
      o.sendSystemMessage(header("BEDWARS EINLADUNG"));
      o.sendSystemMessage(Component.literal(from.getName().getString()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
         .append(Component.literal(" will mit dir Bedwars spielen!").withStyle(ChatFormatting.YELLOW)));
      o.sendSystemMessage(Component.literal("Modus: ").withStyle(ChatFormatting.GRAY).append(modeName().withStyle(ChatFormatting.AQUA)));
      o.sendSystemMessage(
         Component.empty()
            .append(button("[✔ JA, ICH SPIEL MIT]", "/bedwars ja", "Mitspielen", ChatFormatting.GREEN))
            .append(Component.literal("   "))
            .append(button("[✖ NEIN]", "/bedwars nein", "Diesmal nicht", ChatFormatting.RED))
      );
      o.sendSystemMessage(Component.literal("(oder schreib einfach \"ja\" oder \"nein\" in den Chat)").withStyle(ChatFormatting.DARK_GRAY));
      Game.title(o, Component.literal("EINLADUNG").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
         Component.literal(from.getName().getString() + " will Bedwars spielen").withStyle(ChatFormatting.YELLOW));
      ping(o, 1.2F);
   }

   public static void accept(ServerPlayer p) {
      if (!open) {
         p.sendSystemMessage(Component.literal("Gerade gibt es keine Einladung. Schreib \"bedwars 2\", um selbst eine zu starten.").withStyle(ChatFormatting.YELLOW));
         return;
      }

      if (accepted.size() >= Game.TEAM_SIZE * BwTeam.values().length) {
         p.sendSystemMessage(Component.literal("Die Runde ist schon voll.").withStyle(ChatFormatting.RED));
         return;
      }

      declined.remove(p.getUUID());
      if (accepted.add(p.getUUID())) {
         tellLobby(Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
            .append(Component.literal(" ist dabei! (" + accepted.size() + " Spieler)").withStyle(ChatFormatting.GREEN)));
         ping(p, 2.0F);
         ServerPlayer h = hostPlayer();
         if (h != null && h != p) {
            ping(h, 2.0F);
         }

         maybeAllAnswered();
      }
   }

   public static void decline(ServerPlayer p) {
      if (!open) {
         return;
      }

      if (p.getUUID().equals(host)) {
         cancel(p);
         return;
      }

      accepted.remove(p.getUUID());
      if (declined.add(p.getUUID())) {
         p.sendSystemMessage(Component.literal("Ok, du spielst diesmal nicht mit.").withStyle(ChatFormatting.GRAY));
         ServerPlayer h = hostPlayer();
         if (h != null) {
            h.sendSystemMessage(Component.literal("✖ " + p.getName().getString() + " spielt diesmal nicht mit.").withStyle(ChatFormatting.GRAY));
         }

         maybeAllAnswered();
      }
   }

   public static void toggleMode(ServerPlayer p) {
      if (!open || !p.getUUID().equals(host)) {
         p.sendSystemMessage(Component.literal("Den Modus stellt der ein, der eingeladen hat.").withStyle(ChatFormatting.YELLOW));
         return;
      }

      together = !together;
      tellLobby(Component.literal("Modus: ").withStyle(ChatFormatting.GRAY).append(modeName().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
      p.sendSystemMessage(hostButtons());
   }

   public static void startNow(ServerPlayer p) {
      if (!open) {
         p.sendSystemMessage(Component.literal("Gerade gibt es keine Einladung.").withStyle(ChatFormatting.YELLOW));
      } else if (!p.getUUID().equals(host)) {
         p.sendSystemMessage(Component.literal("Starten kann nur, wer eingeladen hat.").withStyle(ChatFormatting.YELLOW));
      } else {
         startAt = Math.min(startAt, server.getTickCount() + 60);
         tellLobby(Component.literal("Gleich geht's los...").withStyle(ChatFormatting.GOLD));
      }
   }

   public static void cancel(ServerPlayer p) {
      if (open && p.getUUID().equals(host)) {
         tellLobby(Component.literal("Die Einladung wurde abgebrochen.").withStyle(ChatFormatting.GRAY));
         close();
      }
   }

   private static void close() {
      open = false;
      startAt = -1;
      accepted.clear();
      declined.clear();
      invited.clear();
      host = null;
   }

   /** Haben alle geantwortet, wartet niemand mehr 30 Sekunden. */
   private static void maybeAllAnswered() {
      for (UUID id : invited) {
         if (!accepted.contains(id) && !declined.contains(id) && server.getPlayerList().getPlayer(id) != null) {
            return;
         }
      }

      startAt = Math.min(startAt, server.getTickCount() + 60);
   }

   public static void tick(MinecraftServer srv) {
      if (!open) {
         return;
      }

      int now = srv.getTickCount();
      int left = startAt - now;
      if (left > 0 && left % 20 == 0) {
         int sec = left / 20;
         if (sec == 20 || sec == 10 || sec <= 3) {
            for (ServerPlayer p : players(accepted)) {
               p.sendOverlayMessage(Component.literal("Bedwars startet in " + sec + "...").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
               if (sec <= 3) {
                  ping(p, 1.0F + (3 - sec) * 0.25F);
               }
            }

            if (sec == 10) {
               for (ServerPlayer p : waiting()) {
                  p.sendSystemMessage(Component.literal("Noch 10 Sekunden, um mitzuspielen: ").withStyle(ChatFormatting.YELLOW)
                     .append(button("[✔ JA]", "/bedwars ja", "Mitspielen", ChatFormatting.GREEN)));
               }
            }
         }
      }

      if (left <= 0) {
         List<ServerPlayer> ps = players(accepted);
         boolean mode = together;
         close();
         if (ps.isEmpty()) {
            return;
         }

         if (ps.size() == 1) {
            ps.get(0).sendSystemMessage(Component.literal("Niemand hat angenommen - du spielst mit Bots.").withStyle(ChatFormatting.YELLOW));
         }

         Game.start(ps, mode);
      }
   }

   public static void onDisconnect(ServerPlayer p) {
      if (!open) {
         return;
      }

      accepted.remove(p.getUUID());
      invited.remove(p.getUUID());
      if (p.getUUID().equals(host)) {
         List<ServerPlayer> rest = players(accepted);
         rest.remove(p);
         if (rest.isEmpty()) {
            close();
         } else {
            host = rest.get(0).getUUID();
            tellLobby(Component.literal(rest.get(0).getName().getString() + " leitet jetzt die Einladung.").withStyle(ChatFormatting.GRAY));
            rest.get(0).sendSystemMessage(hostButtons());
         }
      }
   }

   /** Jemand kommt dazu, waehrend eingeladen oder gespielt wird. */
   public static void greetLateJoiner(ServerPlayer p) {
      if (open) {
         ServerPlayer h = hostPlayer();
         if (h != null) {
            invite(p, h);
         }
      } else {
         p.sendSystemMessage(Component.literal("Hier laeuft gerade eine Bedwars-Runde. Wenn sie vorbei ist, schreib ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("bedwars 2").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" in den Chat, dann spielt ihr zusammen.").withStyle(ChatFormatting.YELLOW)));
      }
   }

   /** Kurzer Hinweis beim Betreten der Welt - die Runde startet erst, wenn man es will. */
   public static void hint(ServerPlayer p) {
      p.sendSystemMessage(Component.literal("GlowCube Bedwars ist bereit. ").withStyle(ChatFormatting.GOLD)
         .append(button("[▶ ALLEIN MIT BOTS]", "/bedwars start", "Runde mit 19 Bots starten", ChatFormatting.GREEN))
         .append(Component.literal("  "))
         .append(button("[MULTIPLAYER]", "/bedwars multiplayer", "Alle auf dem Server einladen", ChatFormatting.AQUA)));
      p.sendSystemMessage(Component.literal("Oder schreib ").withStyle(ChatFormatting.GRAY)
         .append(Component.literal("bedwars 2").withStyle(ChatFormatting.GOLD))
         .append(Component.literal(" in den Chat, um mit deinen Freunden zu spielen.").withStyle(ChatFormatting.GRAY)));
   }

   // ====================================================================== Texte

   static MutableComponent lineup(Game.Human me) {
      MutableComponent line = Component.literal("Dein Team: ").withStyle(ChatFormatting.GRAY);
      boolean first = true;

      for (Game.Human h : me.team.humans) {
         line.append(Component.literal((first ? "" : ", ") + h.name).withStyle(me.team.team.format, ChatFormatting.BOLD));
         first = false;
      }

      for (BotProfile b : me.team.bots) {
         line.append(Component.literal((first ? "" : ", ") + b.name).withStyle(me.team.team.format));
         first = false;
      }

      line.append(Component.literal(" (fett = echte Spieler)").withStyle(ChatFormatting.DARK_GRAY));
      List<Game.Human> rivals = new ArrayList<>();
      for (Game.Human h : Game.humans()) {
         if (h.team != me.team) {
            rivals.add(h);
         }
      }

      if (!rivals.isEmpty()) {
         line.append(Component.literal("\nEchte Gegner: ").withStyle(ChatFormatting.GRAY));
         for (int i = 0; i < rivals.size(); i++) {
            Game.Human r = rivals.get(i);
            line.append(Component.literal((i == 0 ? "" : ", ") + r.name + " (" + r.team.team.label + ")").withStyle(r.team.team.format));
         }
      }

      return line;
   }

   static MutableComponent scoreboard() {
      List<Game.Human> list = new ArrayList<>(Game.humans());
      list.sort(Comparator.comparingInt((Game.Human h) -> h.kills + h.finals * 2 + h.beds * 3).reversed());
      MutableComponent c = header("ERGEBNIS");

      for (int i = 0; i < list.size(); i++) {
         Game.Human h = list.get(i);
         c.append(Component.literal("\n" + (i + 1) + ". ").withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY))
            .append(Component.literal(h.name).withStyle(h.team.team.format, ChatFormatting.BOLD))
            .append(Component.literal("  " + h.kills + " Kills, " + h.finals + " Finals, " + h.beds + " Betten, " + h.deaths + " Tode").withStyle(ChatFormatting.GRAY));
      }

      return c;
   }

   static MutableComponent rematchButton() {
      return Component.literal("Nochmal zusammen? ").withStyle(ChatFormatting.GRAY)
         .append(button("[↻ REVANCHE]", "/bedwars multiplayer", "Alle wieder einladen", ChatFormatting.GOLD));
   }

   private static MutableComponent hostButtons() {
      return Component.empty()
         .append(button("[▶ JETZT STARTEN]", "/bedwars los", "Nicht mehr warten", ChatFormatting.GREEN))
         .append(Component.literal("  "))
         .append(button("[⇄ " + (together ? "ZUSAMMEN" : "GEGENEINANDER") + "]", "/bedwars modus",
            "Umschalten: zusammen in einem Team / jeder in einem eigenen Team", ChatFormatting.AQUA))
         .append(Component.literal("  "))
         .append(button("[✖ ABBRECHEN]", "/bedwars abbrechen", "Einladung zuruecknehmen", ChatFormatting.RED));
   }

   private static MutableComponent modeName() {
      return Component.literal(together ? "zusammen in einem Team (max. " + Game.TEAM_SIZE + ", Bots fuellen auf)" : "gegeneinander - jeder in einem eigenen Team");
   }

   private static MutableComponent header(String title) {
      return Component.literal("━━━━━ " + title + " ━━━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
   }

   private static MutableComponent button(String label, String command, String hover, ChatFormatting color) {
      return Component.literal(label).withStyle(s -> s.withColor(color).withBold(true)
         .withClickEvent(new ClickEvent.RunCommand(command))
         .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
   }

   private static void tellLobby(Component msg) {
      Set<UUID> to = new LinkedHashSet<>(invited);
      to.addAll(accepted);
      for (ServerPlayer p : players(to)) {
         p.sendSystemMessage(msg);
      }
   }

   private static List<ServerPlayer> waiting() {
      List<ServerPlayer> list = new ArrayList<>();
      for (UUID id : invited) {
         if (!accepted.contains(id) && !declined.contains(id)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
               list.add(p);
            }
         }
      }

      return list;
   }

   private static List<ServerPlayer> players(Set<UUID> ids) {
      List<ServerPlayer> list = new ArrayList<>();
      if (server != null) {
         for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !p.hasDisconnected()) {
               list.add(p);
            }
         }
      }

      return list;
   }

   private static ServerPlayer hostPlayer() {
      return host == null || server == null ? null : server.getPlayerList().getPlayer(host);
   }

   private static void ping(ServerPlayer p, float pitch) {
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.MASTER, 1.0F, pitch);
   }
}
