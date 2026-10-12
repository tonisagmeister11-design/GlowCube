package de.glowcube.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Was die Bots in den Chat schreiben. Die Saetze kommen aus dem Sprachmodell ({@link ChatBrain}),
 * jeder Bot schreibt in seinem eigenen Stil.
 */
public final class BotChat {
   private static final Random RND = new Random();
   private static final List<Pending> QUEUE = new ArrayList<>();
   private static int lastPlayerReply = -1000;
   private static int nextChatter = 1200;

   public enum Event {
      CLUTCH,
      FORTIFIED,
      CUT_BRIDGE,
      HUNT_PLAYER,
      GOING_RAID,
      PLAY_SAFE,
      SORTIE
   }

   private record Pending(int at, BotProfile who, String text, boolean team) {
   }

   private BotChat() {
   }

   public static void reset() {
      QUEUE.clear();
      lastPlayerReply = -1000;
      nextChatter = 1200 + RND.nextInt(800);
   }

   // ====================================================================== Senden

   static Map<String, String> slots(BotProfile p, Object... kv) {
      Map<String, String> m = new HashMap<>();
      m.put("me", p.name.toLowerCase(Locale.ROOT));
      ServerPlayer pl = Game.player();
      if (pl != null) {
         m.put("player", pl.getName().getString());
      }

      for (int i = 0; i + 1 < kv.length; i += 2) {
         if (kv[i + 1] != null) {
            m.put((String)kv[i], String.valueOf(kv[i + 1]));
         }
      }

      return m;
   }

   /** Laesst einen Bot zu einer Absicht etwas Eigenes schreiben. */
   public static boolean speak(BotProfile p, String intent, Map<String, String> slots, int delay, boolean teamOnly) {
      if (p == null) {
         return false;
      } else {
         String text = ChatBrain.compose(intent, slots, 0.3 + p.chattiness * 0.5);
         if (text == null) {
            return false;
         } else {
            enqueue(p, p.voice.apply(text), delay, teamOnly);
            return true;
         }
      }
   }

   /** Sachliche Auskunft (Ressourcen, Standort) - ohne Stil-Spielereien. */
   public static void sayPlain(BotProfile p, String text, int delay, boolean teamOnly) {
      enqueue(p, text, delay, teamOnly);
   }

   private static void enqueue(BotProfile p, String text, int delay, boolean teamOnly) {
      if (p == null || text == null || text.isBlank() || QUEUE.size() >= 5) {
         return;
      }

      int now = Game.tick();
      // tippen dauert: laengere Saetze kommen spaeter
      int at = now + Math.max(5, delay) + text.length() * (2 + RND.nextInt(2)) / 2;

      for (Pending q : QUEUE) {
         if (Math.abs(q.at - at) < 40) {
            at = q.at + 40 + RND.nextInt(30);
         }
      }

      QUEUE.add(new Pending(at, p, text, teamOnly));
      p.lastSpoke = now;
   }

   private static boolean wants(BotProfile p, double base) {
      return p != null && RND.nextDouble() < base * (0.25 + p.chattiness * 0.75) && Game.tick() - p.lastSpoke > 300;
   }

   public static void tick() {
      int now = Game.tick();
      Iterator<Pending> it = QUEUE.iterator();

      while (it.hasNext()) {
         Pending q = it.next();
         if (now >= q.at) {
            it.remove();
            send(q);
            break;
         }
      }

      if (now >= nextChatter) {
         nextChatter = now + 900 + RND.nextInt(1500);
         chatter();
      }
   }

   private static void send(Pending q) {
      Game.TeamState mine = Game.playerTeam();
      if (q.team && mine != q.who.team) {
         return;
      }

      MutableComponent line = Component.empty();
      if (q.team) {
         line.append(Component.literal("[TEAM] ").withStyle(ChatFormatting.DARK_AQUA));
      }

      line.append(Component.literal("<").withStyle(ChatFormatting.GRAY))
         .append(Component.literal(q.who.name).withStyle(q.who.team.team.format))
         .append(Component.literal("> ").withStyle(ChatFormatting.GRAY))
         .append(Component.literal(q.text).withStyle(ChatFormatting.WHITE));
      Game.broadcast(line);
   }

   static BotProfile randomBot(Game.TeamState team, BotProfile not) {
      List<BotProfile> list = new ArrayList<>();

      for (BotProfile p : team.bots) {
         if (!p.eliminated && p != not) {
            list.add(p);
         }
      }

      return list.isEmpty() ? null : list.get(RND.nextInt(list.size()));
   }

   static List<BotProfile> allBots() {
      List<BotProfile> list = new ArrayList<>();

      for (Game.TeamState t : Game.teams()) {
         for (BotProfile p : t.bots) {
            if (!p.eliminated) {
               list.add(p);
            }
         }
      }

      return list;
   }

   static String nameOf(Entity e) {
      if (e instanceof BedwarsBot b && b.profile != null) {
         return b.profile.name.toLowerCase(Locale.ROOT);
      } else {
         return e == null ? "jemand" : e.getName().getString();
      }
   }

   static String teamName(Game.TeamState t) {
      return t == null ? null : t.team.label.toLowerCase(Locale.ROOT);
   }

   /** Ab und zu redet einfach jemand, ohne dass etwas passiert ist. */
   private static void chatter() {
      List<BotProfile> all = allBots();
      if (all.isEmpty()) {
         return;
      }

      BotProfile p = all.get(RND.nextInt(all.size()));
      List<Game.TeamState> others = new ArrayList<>();

      for (Game.TeamState t : Game.teams()) {
         if (t != p.team && t.alive()) {
            others.add(t);
         }
      }

      Game.TeamState t = others.isEmpty() ? null : others.get(RND.nextInt(others.size()));
      if (wants(p, 0.9)) {
         speak(p, p.tilt > 0.6 ? "tilt" : "bored", slots(p, "team", teamName(t)), 10, RND.nextInt(3) == 0);
      }
   }

   // ====================================================================== Ereignisse

   public static void gameStart() {
      List<BotProfile> all = allBots();
      int n = 2 + RND.nextInt(3);

      for (int i = 0; i < n && !all.isEmpty(); i++) {
         BotProfile p = all.remove(RND.nextInt(all.size()));
         speak(p, "start", slots(p), 30 + i * 30, false);
      }

      Game.TeamState pt = Game.playerTeam();
      if (pt != null) {
         BotProfile mate = randomBot(pt, null);
         if (mate != null) {
            Game.TeamState target = null;

            for (Game.TeamState t : Game.teams()) {
               if (t != pt && (target == null || RND.nextBoolean())) {
                  target = t;
               }
            }

            speak(mate, "plan", slots(mate, "team", teamName(target)), 120, true);
         }
      }
   }

   public static void event(BotProfile p, Event e, Object ctx) {
      if (p == null) {
         return;
      }

      String name = ctx instanceof Entity en ? nameOf(en) : null;
      String team = ctx instanceof Game.TeamState t ? teamName(t) : null;
      switch (e) {
         case CLUTCH -> {
            if (wants(p, 0.5)) {
               speak(p, "clutch", slots(p), 10, false);
            }
         }
         case FORTIFIED -> {
            if (wants(p, 0.5)) {
               speak(p, "fortified", slots(p), 10, true);
            }
         }
         case CUT_BRIDGE -> {
            if (wants(p, 0.6)) {
               speak(p, "cutbridge", slots(p, "name", name), 10, true);
            }
         }
         case HUNT_PLAYER -> {
            if (wants(p, 0.7)) {
               speak(p, "hunt", slots(p), 15, false);
            }
         }
         case GOING_RAID -> {
            if (wants(p, 0.5)) {
               speak(p, "raid", slots(p, "team", team), 10, true);
            }
         }
         case PLAY_SAFE -> speak(p, "playsafe", slots(p), 30, true);
         case SORTIE -> speak(p, "sortie", slots(p, "team", team), 10, true);
      }
   }

   public static void purchase(BotProfile p, String item) {
      boolean mine = p.team == Game.playerTeam();
      if (item.equals("Wolle") || item.equals("Endstein") || item.equals("Holz")) {
         return;
      }

      boolean big = item.startsWith("Diamant") || item.startsWith("Eisenr") || item.equals("Feuerball") || item.startsWith("Eisens");
      // Teamkollegen des Spielers erzaehlen oefter, was sie gekauft haben
      if ((big || mine && RND.nextBoolean()) && wants(p, mine ? 0.8 : 0.4)) {
         speak(p, "purchase", slots(p, "item", item.toLowerCase(Locale.ROOT)), 10, mine || RND.nextBoolean());
      }
   }

   public static void upgrade(BotProfile p, String item) {
      speak(p, "upgrade", slots(p, "item", item.toLowerCase(Locale.ROOT)), 20, true);
   }

   public static void alarm(BotProfile p, LivingEntity enemy, boolean atBed) {
      speak(p, atBed ? "alarmbed" : "alarm", slots(p, "name", nameOf(enemy)), 5, true);
   }

   public static void strategySpotted(Game.TeamState us, Game.TeamState them, String label) {
      BotProfile p = randomBot(us, null);
      if (p != null && wants(p, 0.8)) {
         String intent = switch (label) {
            case "rush" -> "stratrush";
            case "eco" -> "strateco";
            default -> "stratbunker";
         };
         speak(p, intent, slots(p, "team", teamName(them)), 20, true);
      }
   }

   public static void bedDestroyed(Game.TeamState victim, Game.TeamState byTeam, BotProfile byBot, ServerPlayer byPlayer) {
      BotProfile sad = randomBot(victim, null);
      if (sad != null) {
         speak(sad, "bedlost", slots(sad), 20, false);
      }

      if (byBot != null && wants(byBot, 0.9)) {
         speak(byBot, "bedbroke", slots(byBot, "team", teamName(victim)), 15, false);
      }

      if (byPlayer != null) {
         BotProfile angry = randomBot(victim, sad);
         if (angry != null && RND.nextBoolean()) {
            speak(angry, "bedrevenge", slots(angry), 50, false);
         }

         Game.TeamState pt = Game.playerTeam();
         BotProfile mate = pt == null ? null : randomBot(pt, null);
         if (mate != null && RND.nextBoolean()) {
            speak(mate, "bedpraise", slots(mate), 35, true);
         }
      }

      for (Game.TeamState t : Game.teams()) {
         if (t != victim && t != byTeam && t.alive() && RND.nextInt(5) == 0) {
            BotProfile other = randomBot(t, null);
            if (other != null) {
               speak(other, "bedother", slots(other, "team", teamName(victim)), 60, false);
               break;
            }
         }
      }
   }

   public static void kill(LivingEntity victim, Entity killer, boolean finalKill) {
      if (killer instanceof BedwarsBot kb && kb.profile != null && wants(kb.profile, finalKill ? 0.6 : 0.25)) {
         speak(kb.profile, finalKill ? "killfinal" : "kill", slots(kb.profile, "name", nameOf(victim)), 10, false);
      }

      if (victim instanceof BedwarsBot vb && vb.profile != null) {
         BotProfile p = vb.profile;
         if (p.tilt > 0.6 && wants(p, 0.6)) {
            speak(p, "tilt", slots(p), 30, false);
         } else if (wants(p, 0.3)) {
            String intent = finalKill ? "deathfinal" : (killer == null ? "deathvoid" : "death");
            speak(p, intent, slots(p, "name", killer == null ? null : nameOf(killer)), 35, false);
         }
      }
   }

   public static void teamEliminated(Game.TeamState t) {
      for (Game.TeamState o : Game.teams()) {
         if (o != t && o.alive() && RND.nextInt(3) == 0) {
            BotProfile p = randomBot(o, null);
            if (p != null) {
               speak(p, "teamout", slots(p, "team", teamName(t)), 40, false);
               return;
            }
         }
      }
   }

   public static void gameOver(Game.TeamState winner) {
      for (BotProfile p : allBots()) {
         if (RND.nextDouble() < 0.45) {
            speak(p, p.team == winner ? "win" : "lose", slots(p), 10 + RND.nextInt(50), false);
         }
      }
   }

   // ====================================================================== Spieler verstehen

   private static Game.TeamState mentionedTeam(String s) {
      String padded = " " + s + " ";

      for (Game.TeamState t : Game.teams()) {
         for (String w : t.team.words) {
            if (padded.contains(" " + ChatBrain.normalize(w) + " ")) {
               return t;
            }
         }
      }

      return null;
   }

   private static BotProfile mentionedBot(String s) {
      String padded = " " + s + " ";

      for (BotProfile p : allBots()) {
         if (padded.contains(" " + p.name.toLowerCase(Locale.ROOT) + " ")) {
            return p;
         }
      }

      return null;
   }

   static String where(BotProfile p) {
      if (p.body == null || !p.body.isAlive()) {
         return "bin grad tot, gleich wieder da";
      } else {
         Arena a = Game.arena();
         BwTeam on = a.islandAt(p.body.position(), 2.0);
         double mid = p.body.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(a.center));
         if (on == p.team.team) {
            return "bin auf unserer insel" + (p.task == BotProfile.Task.DEFEND ? ", pass aufs bett auf" : "");
         } else if (on != null) {
            return "bin bei " + on.label.toLowerCase(Locale.ROOT);
         } else if (mid < Arena.MID_RADIUS + 3) {
            return "bin mitte";
         } else {
            for (net.minecraft.core.BlockPos d : a.diamondIslands) {
               if (p.body.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(d)) < 7.0) {
                  return "bin bei den diamanten";
               }
            }

            return "bin auf ner bruecke";
         }
      }
   }

   /** Schluesselwoerter als Rueckfall, wenn sich der Klassifikator unsicher ist. */
   private static String keywordIntent(String s) {
      String p = " " + s + " ";
      if (p.contains(" verteidig") || p.contains(" def ") || p.contains(" bleibt ")) {
         return "cmd_defend";
      } else if (p.contains(" folg") || p.contains(" kommt mit ")) {
         return "cmd_follow";
      } else if (p.contains(" hilfe ") || p.contains(" help ")) {
         return "cmd_help";
      } else if (p.contains(" dia") || p.contains(" smaragd") || p.contains(" mitte ")) {
         return "cmd_mid";
      } else if (p.contains(" noob") || p.contains(" ez ") || p.contains(" schlecht")) {
         return "insult";
      } else if (p.contains(" gg ")) {
         return "gg";
      } else {
         return "other";
      }
   }

   public static void onPlayerChat(ServerPlayer player, String raw) {
      if (!Game.running() || raw == null || raw.isBlank()) {
         return;
      }

      String s = ChatBrain.normalize(raw);
      ChatBrain.Guess guess = ChatBrain.understand(raw);
      String intent = guess.confidence() >= 0.35 ? guess.intent() : keywordIntent(s);
      Game.TeamState mine = Game.playerTeam();
      Game.TeamState team = mentionedTeam(s);
      BotProfile named = mentionedBot(s);
      boolean addressedMate = named != null && mine != null && named.team == mine;
      BotProfile mate = addressedMate ? named : (mine == null ? null : randomBot(mine, null));
      BotProfile enemyBot = named != null && (mine == null || named.team != mine) ? named : null;
      if (enemyBot == null) {
         List<BotProfile> all = allBots();
         all.removeIf(p -> mine != null && p.team == mine);
         enemyBot = all.isEmpty() ? null : all.get(RND.nextInt(all.size()));
      }

      int delay = 25 + RND.nextInt(30);
      if (Game.debug) {
         System.out.println("[BW versteht] \"" + raw + "\" -> " + intent + String.format(Locale.ROOT, " (%.2f)", guess.confidence()));
      }

      // ---- Befehle an das eigene Team
      if (intent.startsWith("cmd_") && mate != null) {
         TeamBrain.Command c = switch (intent) {
            case "cmd_defend" -> TeamBrain.Command.DEFEND;
            case "cmd_attack" -> TeamBrain.Command.ATTACK;
            case "cmd_follow" -> TeamBrain.Command.FOLLOW;
            case "cmd_help" -> TeamBrain.Command.HELP;
            case "cmd_mid" -> TeamBrain.Command.MID;
            case "cmd_fortify" -> TeamBrain.Command.FORTIFY;
            case "cmd_buy" -> TeamBrain.Command.SHOP;
            default -> TeamBrain.Command.NONE;
         };
         if (c == TeamBrain.Command.ATTACK && team == mine) {
            sayPlain(mate, "wir sind doch " + mine.team.label.toLowerCase(Locale.ROOT) + " haha", delay, true);
            return;
         }

         int seconds = c == TeamBrain.Command.HELP ? 45 : (c == TeamBrain.Command.NONE ? 0 : 100);
         TeamBrain.command(mine, c, c == TeamBrain.Command.ATTACK ? team : null, seconds);
         String ack = switch (intent) {
            case "cmd_defend" -> "ackdefend";
            case "cmd_attack" -> "ackattack";
            case "cmd_follow" -> "ackfollow";
            case "cmd_help" -> "ackhelp";
            case "cmd_mid" -> "ackmid";
            case "cmd_fortify" -> "ackfortify";
            case "cmd_buy" -> "ackbuy";
            default -> "ackstop";
         };
         speak(mate, ack, slots(mate, "team", teamName(team)), c == TeamBrain.Command.HELP ? 10 : delay, true);

         return;
      }

      if (intent.startsWith("cmd_") && mine == null) {
         speak(enemyBot, "denyenemy", slots(enemyBot), delay, false);
         return;
      }

      // ---- Fragen ans Team
      if (mine != null && mate != null) {
         switch (intent) {
            case "ask_res" -> {
               sayPlain(mate, "team: " + mine.iron + " eisen, " + mine.gold + " gold, " + mine.diamond + " dias, " + mine.emerald + " smaragde", delay, true);
               return;
            }
            case "ask_where" -> {
               if (addressedMate) {
                  sayPlain(named, where(named), delay, true);
               } else {
                  int i = 0;

                  for (BotProfile p : mine.bots) {
                     if (!p.eliminated && i < 2) {
                        sayPlain(p, where(p), delay + i * 30, true);
                        i++;
                     }
                  }
               }

               return;
            }
            case "ask_doing" -> {
               BotProfile who = addressedMate ? named : mate;
               sayPlain(who, "ich bin am " + who.taskLabel(), delay, true);
               return;
            }
            case "ask_strategy" -> {
               sayPlain(mate, TeamBrain.explain(mine), delay, true);

               return;
            }
            default -> {
            }
         }
      }

      if (Game.tick() - lastPlayerReply < 50) {
         return;
      }

      lastPlayerReply = Game.tick();

      // ---- Smalltalk: wer antwortet? Ein angesprochener Bot, sonst ein Gegner oder ein Teamkollege
      BotProfile who = named != null ? named : (RND.nextBoolean() && mate != null ? mate : enemyBot);
      if (intent.equals("insult")) {
         if (named == null && enemyBot != null) {
            who = enemyBot;
         }

         if (who != null) {
            who.team.grudgePlayer += 0.1;
         }
      }

      if (who == null) {
         return;
      }

      String reply = switch (intent) {
         case "greet" -> "rgreet";
         case "gg" -> "rgg";
         case "insult" -> "rinsult";
         case "praise" -> "rpraise";
         case "bot" -> "rbot";
         case "truce" -> "rtruce";
         case "laugh" -> "rlaugh";
         case "ask_how" -> "rhow";
         case "thanks" -> "rthanks";
         case "question" -> "rquestion";
         default -> "runknown";
      };
      boolean teamOnly = who.team == mine && !intent.equals("greet") && !intent.equals("gg");
      if (reply.equals("runknown") && named == null && RND.nextDouble() < 0.5) {
         return;
      }

      speak(who, reply, slots(who), delay, teamOnly);
   }
}
