package de.glowcube.bedwars;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Die Chat-KI der Bots. Sie reagiert auf das Spielgeschehen (Betten, Kills, Clutches, Kaeufe),
 * spricht sich im Team ab und versteht, was der Spieler schreibt: Begruessungen, Sprueche,
 * Fragen ("wo seid ihr", "wie viel eisen") und Befehle an das eigene Team
 * ("verteidigt", "greift blau an", "folgt mir", "holt dias", "baut das bett zu").
 * Jeder Bot hat eigenen Charakter: mal gespraechig, mal still, mal frech, mal nett.
 */
public final class BotChat {
   private static final Random RND = new Random();
   private static final List<Pending> QUEUE = new ArrayList<>();
   private static int lastSent = -1000;
   private static int lastPlayerReply = -1000;

   public enum Event {
      START,
      CLUTCH,
      FORTIFIED,
      CUT_BRIDGE,
      HUNT_PLAYER,
      GOING_RAID,
      PLAY_SAFE,
      SORTIE,
      KILL,
      FINAL_KILL,
      DIED,
      OWN_BED_LOST,
      BED_BROKEN,
      OTHER_BED,
      WIN,
      LOSE,
      ELIMINATED
   }

   private record Pending(int at, BotProfile who, String text, boolean team) {
   }

   private BotChat() {
   }

   public static void reset() {
      QUEUE.clear();
      lastSent = -1000;
      lastPlayerReply = -1000;
   }

   // ====================================================================== Senden

   private static String pick(String... options) {
      return options[RND.nextInt(options.length)];
   }

   /** Schreibstil: klein, Smileys, ab und zu ein Tippfehler - je nach Charakter. */
   private static String style(BotProfile p, String text) {
      String s = text;
      if (RND.nextDouble() < 0.55) {
         s = s.toLowerCase(Locale.ROOT);
      }

      if (s.endsWith(".") && RND.nextBoolean()) {
         s = s.substring(0, s.length() - 1);
      }

      if (p.cheek > 0.6 && RND.nextDouble() < 0.25) {
         s = s + pick(" xD", " lol", " :P", " haha", " ez");
      } else if (p.cheek < 0.3 && RND.nextDouble() < 0.15) {
         s = s + pick(" :)", " ^^", " :D");
      }

      if (s.length() > 6 && RND.nextDouble() < 0.06) {
         int i = 1 + RND.nextInt(s.length() - 3);
         char[] c = s.toCharArray();
         char t = c[i];
         c[i] = c[i + 1];
         c[i + 1] = t;
         s = new String(c);
      }

      return s;
   }

   /** Sachliche Auskunft (Ressourcen, Standort) - ohne Smileys und Tippfehler. */
   public static void sayPlain(BotProfile p, String text, int delay, boolean teamOnly) {
      say(p, text, delay, teamOnly, false);
   }

   public static void say(BotProfile p, String text, int delay, boolean teamOnly) {
      say(p, text, delay, teamOnly, true);
   }

   private static void say(BotProfile p, String text, int delay, boolean teamOnly, boolean styled) {
      if (p == null || text == null || text.isEmpty()) {
         return;
      }

      int now = Game.tick();
      int at = now + Math.max(5, delay) + text.length() / 2;
      if (QUEUE.size() >= 4) {
         return;
      }

      for (Pending q : QUEUE) {
         if (Math.abs(q.at - at) < 40) {
            at = q.at + 40 + RND.nextInt(30);
         }
      }

      QUEUE.add(new Pending(at, p, styled ? style(p, text) : text, teamOnly));
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
            lastSent = now;
            return;
         }
      }
   }

   private static void send(Pending q) {
      ServerPlayer pl = Game.player();
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

   private static BotProfile randomBot(Game.TeamState team, BotProfile not) {
      List<BotProfile> list = new ArrayList<>();

      for (BotProfile p : team.bots) {
         if (!p.eliminated && p != not) {
            list.add(p);
         }
      }

      return list.isEmpty() ? null : list.get(RND.nextInt(list.size()));
   }

   private static List<BotProfile> allBots() {
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

   private static String nameOf(Entity e) {
      if (e instanceof BedwarsBot b && b.profile != null) {
         return b.profile.name;
      } else {
         return e == null ? "jemand" : e.getName().getString();
      }
   }

   // ====================================================================== Ereignisse

   public static void gameStart() {
      List<BotProfile> all = allBots();
      int n = 2 + RND.nextInt(3);

      for (int i = 0; i < n && !all.isEmpty(); i++) {
         BotProfile p = all.remove(RND.nextInt(all.size()));
         say(p, pick("gl hf", "glhf", "viel glueck allen", "los gehts!", "diesmal gewinn ich", "hi", "gl", "wer rusht mich zuerst?", "hallo zusammen", "endlich wieder bedwars"), 30 + i * 30, false);
      }

      Game.TeamState pt = Game.playerTeam();
      if (pt != null) {
         BotProfile mate = randomBot(pt, null);
         if (mate != null) {
            say(mate, pick("ich bau unser bett zu, ihr holt eisen", "wer geht rushen?", "lasst uns zuerst " + pick("rechts", "links") + " angreifen", "schreib einfach was wir machen sollen, z.b. 'verteidigt' oder 'greift blau an'", "ich hol erstmal wolle"), 120, true);
         }
      }
   }

   public static void event(BotProfile p, Event e, Object ctx) {
      if (p == null) {
         return;
      }

      switch (e) {
         case CLUTCH -> {
            if (wants(p, 0.5)) {
               say(p, pick("clutch!", "puh knapp", "fast runtergefallen", "nice clutch", "nicht mit mir"), 10, false);
            }
         }
         case FORTIFIED -> {
            if (wants(p, 0.5)) {
               say(p, pick("bett ist zugebaut", "bett ist sicher", "hab das bett mit endstein zugemacht", "verteidigung steht"), 10, true);
            }
         }
         case CUT_BRIDGE -> {
            if (wants(p, 0.6) && ctx instanceof Entity en) {
               say(p, pick("ich reiss die bruecke von " + nameOf(en) + " ab", "bruecke weg haha", "da baut einer zu uns, ich kuemmer mich"), 10, true);
            }
         }
         case HUNT_PLAYER -> {
            if (wants(p, 0.7) && ctx instanceof ServerPlayer sp) {
               say(p, pick("ich komm dich holen " + sp.getName().getString(), "hab dich gesehen", "lauf lieber", sp.getName().getString() + " du bist dran", "da ist " + sp.getName().getString() + ", ich geh drauf"), 15, false);
            }
         }
         case GOING_RAID -> {
            if (wants(p, 0.5) && ctx instanceof Game.TeamState t) {
               say(p, pick("ich geh auf " + t.team.label, t.team.label + " ist dran", "ich rush " + t.team.label, "wer kommt mit zu " + t.team.label + "?", "bett von " + t.team.label + " sieht schwach aus"), 10, true);
            }
         }
         case PLAY_SAFE -> say(p, pick("bett ist weg, jetzt vorsichtig spielen", "kein bett mehr - erstmal ausruesten, nicht einzeln rein", "wir bleiben zusammen, keiner stirbt sinnlos", "ruhig bleiben, erst ruestung dann angreifen"), 30, true);
         case SORTIE -> {
            if (ctx instanceof Game.TeamState t) {
               say(p, pick("alle zusammen auf " + t.team.label, "jetzt gemeinsam rein, " + t.team.label + " ist schwach", "folgt mir, wir holen " + t.team.label), 10, true);
            }
         }
         default -> {
         }
      }
   }

   public static void purchase(BotProfile p, String item) {
      if ((item.startsWith("Diamant") || item.startsWith("Eisenr") || item.equals("Feuerball")) && wants(p, 0.45)) {
         say(p, pick("hab " + item + "!", item + " gekauft", "jetzt mit " + item + " :)", "endlich " + item), 10, !item.startsWith("Diamant") || RND.nextBoolean());
      }
   }

   public static void alarm(BotProfile p, LivingEntity enemy, boolean atBed) {
      String who = nameOf(enemy);
      say(p, atBed
         ? pick("HILFE " + who + " ist an unserem bett!!", who + " am bett!!", "BETT! " + who + " baut ab!", "schnell zum bett, " + who + "!!")
         : pick(who + " ist auf unserer insel", "gegner auf der insel!", "achtung " + who + " kommt", "da ist " + who + " bei uns"), 5, true);
   }

   public static void bedDestroyed(Game.TeamState victim, Game.TeamState byTeam, BotProfile byBot, ServerPlayer byPlayer) {
      BotProfile sad = randomBot(victim, null);
      if (sad != null) {
         say(sad, pick("NEIN unser bett", "bett ist weg!!", "waaas wer hat nicht aufgepasst", "na super, kein bett mehr", "ok jetzt wirds ernst", "wie konnte das passieren"), 20, false);
      }

      if (byBot != null && wants(byBot, 0.9)) {
         say(byBot, pick("bett weg :D", "ez bett", victim.team.label + " hat kein bett mehr", "noch eins", "das war einfach", "hab das bett von " + victim.team.label), 15, false);
      }

      if (byPlayer != null) {
         BotProfile reaction = sad != null ? sad : randomBot(victim, null);
         if (reaction != null && RND.nextBoolean()) {
            say(reaction, pick(byPlayer.getName().getString() + " du sack", "wie bist du da reingekommen " + byPlayer.getName().getString(), "warte nur " + byPlayer.getName().getString(), "das gibt rache"), 50, false);
         }

         Game.TeamState pt = Game.playerTeam();
         BotProfile mate = pt == null ? null : randomBot(pt, null);
         if (mate != null && RND.nextBoolean()) {
            say(mate, pick("nice bett!", "stark gemacht", "gg bett", "weiter so"), 35, true);
         }
      }

      for (Game.TeamState t : Game.teams()) {
         if (t != victim && t != byTeam && t.alive() && RND.nextInt(5) == 0) {
            BotProfile other = randomBot(t, null);
            if (other != null) {
               say(other, pick("rip " + victim.team.label, "uff", "ein bett weniger", "gut so"), 60, false);
               break;
            }
         }
      }
   }

   public static void kill(LivingEntity victim, Entity killer, boolean finalKill) {
      if (killer instanceof BedwarsBot kb && kb.profile != null && wants(kb.profile, finalKill ? 0.6 : 0.25)) {
         String v = nameOf(victim);
         say(kb.profile, finalKill
            ? pick("tschuess " + v, "final!", v + " ist raus", "und weg", "gg " + v)
            : pick("ez", "zu langsam " + v, "nice", "sorry " + v, "1", "der naechste bitte"), 10, false);
      }

      if (victim instanceof BedwarsBot vb && vb.profile != null && wants(vb.profile, 0.3)) {
         if (killer == null) {
            say(vb.profile, finalKill ? pick("ich bin runtergefallen... gg", "nein die leere", "gg") : pick("ups", "wer hat mich geschubst", "die leere hat mich", "ich kann nicht bridgen lol", "verrutscht"), 35, false);
         } else {
            String k = nameOf(killer);
            say(vb.profile, finalKill
               ? pick("gg", "gg wp", "naja", "das wars fuer mich", "nicht fair")
               : pick("lag", "glueck gehabt " + k, "boah", "das war knapp", "ok rache kommt", "wie hat der mich getroffen"), 35, false);
         }
      }
   }

   public static void teamEliminated(Game.TeamState t) {
      for (Game.TeamState o : Game.teams()) {
         if (o != t && o.alive() && RND.nextInt(3) == 0) {
            BotProfile p = randomBot(o, null);
            if (p != null) {
               say(p, pick(t.team.label + " ist raus", "einer weniger", "bye " + t.team.label), 40, false);
               return;
            }
         }
      }
   }

   public static void gameOver(Game.TeamState winner) {
      for (BotProfile p : allBots()) {
         if (RND.nextDouble() < 0.45) {
            say(p, p.team == winner ? pick("gg ez", "gg!", "gewonnen!!", "gg wp alle", "ja man") : pick("gg", "gg wp", "naechste runde", "gg das war knapp"), 10 + RND.nextInt(50), false);
         }
      }
   }

   // ====================================================================== Spieler verstehen

   private static String normalize(String raw) {
      String s = raw.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
      s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      return " " + s.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
   }

   private static boolean has(String s, String... words) {
      for (String w : words) {
         if (s.contains(" " + w + " ") || w.endsWith("*") && s.contains(" " + w.substring(0, w.length() - 1))) {
            return true;
         }
      }

      return false;
   }

   private static Game.TeamState mentionedTeam(String s) {
      for (Game.TeamState t : Game.teams()) {
         for (String w : t.team.words) {
            if (s.contains(" " + w + " ")) {
               return t;
            }
         }
      }

      return null;
   }

   private static BotProfile mentionedBot(String s) {
      for (BotProfile p : allBots()) {
         if (s.contains(" " + p.name.toLowerCase(Locale.ROOT) + " ")) {
            return p;
         }
      }

      return null;
   }

   private static String where(BotProfile p) {
      if (p.body == null || !p.body.isAlive()) {
         return pick("bin grad tot", "respawne gleich", "warte auf respawn");
      } else {
         Arena a = Game.arena();
         BwTeam on = a.islandAt(p.body.position(), 2.0);
         double mid = p.body.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(a.center));
         if (on == p.team.team) {
            return pick("bin auf unserer insel", "zu hause", "bei uns auf der insel") + (p.task == BotProfile.Task.DEFEND ? ", pass aufs bett auf" : "");
         } else if (on != null) {
            return "bin bei " + on.label;
         } else if (mid < Arena.MID_RADIUS + 3) {
            return pick("in der mitte", "mitte bei den smaragden");
         } else {
            for (net.minecraft.core.BlockPos d : a.diamondIslands) {
               if (p.body.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(d)) < 7.0) {
                  return "bei den diamanten";
               }
            }

            return pick("auf ner bruecke", "unterwegs", "zwischen den inseln");
         }
      }
   }

   public static void onPlayerChat(ServerPlayer player, String raw) {
      if (!Game.running() || raw == null) {
         return;
      }

      String s = normalize(raw);
      Game.TeamState mine = Game.playerTeam();
      Game.TeamState team = mentionedTeam(s);
      BotProfile named = mentionedBot(s);
      String pn = player.getName().getString();
      boolean addressedTeam = named != null && mine != null && named.team == mine;
      BotProfile mate = addressedTeam ? named : (mine == null ? null : randomBot(mine, null));
      BotProfile enemyBot = named != null && (mine == null || named.team != mine) ? named : null;
      if (enemyBot == null) {
         List<BotProfile> all = allBots();
         all.removeIf(p -> mine != null && p.team == mine);
         enemyBot = all.isEmpty() ? null : all.get(RND.nextInt(all.size()));
      }

      int delay = 25 + RND.nextInt(30);

      // ---- Befehle ans eigene Team
      if (mine != null && mate != null) {
         if (has(s, "verteidig*", "def", "defend", "bewach*", "schuetzt", "schuetz") || has(s, "bleibt") && has(s, "bett", "zuhause", "insel")) {
            TeamBrain.command(mine, TeamBrain.Command.DEFEND, null, 90);
            say(mate, pick("ok wir bleiben beim bett", "alles klar, verteidigen", "jo, keiner kommt an unser bett", "machen wir"), delay, true);
            return;
         }

         if (has(s, "angriff", "angreifen", "greift", "greif", "rush", "rusht", "attack", "attackiert", "holt", "zerstoert", "zerstoeren", "los") && (team != null || has(s, "bett", "angriff", "rush", "attack"))) {
            if (team == mine) {
               say(mate, pick("das ist unser eigenes team haha", "wir sind " + mine.team.label + "..."), delay, true);
               return;
            }

            TeamBrain.command(mine, TeamBrain.Command.ATTACK, team, 120);
            say(mate, team == null ? pick("ok wir greifen an", "los gehts", "bin dabei") : pick("ok auf " + team.team.label + "!", team.team.label + " wird zerlegt", "bin unterwegs zu " + team.team.label, "alles klar, " + team.team.label), delay, true);
            return;
         }

         if (has(s, "folgt", "folg", "mitkommen", "begleitet", "begleit") || has(s, "kommt") && has(s, "mit")) {
            TeamBrain.command(mine, TeamBrain.Command.FOLLOW, null, 90);
            say(mate, pick("bin hinter dir", "ok ich folge dir", "komme mit", "wohin gehts?"), delay, true);
            return;
         }

         if (has(s, "hilfe", "help", "hilf", "helft") || has(s, "komm", "kommt") && has(s, "her", "schnell")) {
            TeamBrain.command(mine, TeamBrain.Command.HELP, null, 45);
            say(mate, pick("komme!", "bin unterwegs", "halt durch", "wo bist du? ich komm"), 10, true);
            return;
         }

         if (has(s, "dias", "diamanten", "diamant", "smaragde", "smaragd", "emeralds", "ems", "mitte", "mid")) {
            TeamBrain.command(mine, TeamBrain.Command.MID, null, 120);
            say(mate, pick("ich hol die dias", "ok ich geh mitte", "smaragde holen, alles klar"), delay, true);
            return;
         }

         if (has(s, "zubauen", "zumachen", "einbauen", "verbarrikadier*") || has(s, "bau", "baut") && has(s, "bett")) {
            TeamBrain.command(mine, TeamBrain.Command.FORTIFY, null, 90);
            say(mate, pick("ich bau das bett zu", "mach ich, mit endstein", "ok verteidigung kommt"), delay, true);
            return;
         }

         if (has(s, "stop", "stopp", "normal", "egal") || has(s, "macht") && has(s, "was", "ihr", "wollt")) {
            TeamBrain.command(mine, TeamBrain.Command.NONE, null, 0);
            say(mate, pick("ok wir machen unser ding", "alles klar", "ok"), delay, true);
            return;
         }

         if (has(s, "wie", "was", "status") && has(s, "eisen", "gold", "ressourcen", "resourcen", "habt", "viel", "viele")) {
            sayPlain(mate, "team: " + mine.iron + " eisen, " + mine.gold + " gold, " + mine.diamond + " dias, " + mine.emerald + " smaragde", delay, true);
            return;
         }

         if (has(s, "wo") && has(s, "seid", "bist", "ihr", "du")) {
            if (addressedTeam) {
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

         if (has(s, "was") && has(s, "macht", "machst", "tut")) {
            BotProfile who = addressedTeam ? named : mate;
            say(who, pick("bin am ", "ich bin grad am ", "") + who.taskLabel(), delay, true);
            return;
         }
      }

      if (Game.tick() - lastPlayerReply < 60) {
         return;
      }

      lastPlayerReply = Game.tick();

      // ---- Smalltalk
      if (has(s, "bot", "bots", "ki", "npc", "ai") && has(s, "bist", "seid", "du", "ihr", "ein")) {
         BotProfile who = named != null ? named : (enemyBot != null ? enemyBot : mate);
         if (who != null) {
            say(who, pick("ich bin doch kein bot", "selber bot", "beep boop... nein spass", "sagt der, der nicht bridgen kann", "nein?? ich bin " + who.name), delay, false);
         }
      } else if (has(s, "gg", "ggwp", "gut gespielt", "good game")) {
         for (int i = 0; i < 2; i++) {
            BotProfile p = RND.nextBoolean() && mate != null ? mate : enemyBot;
            if (p != null) {
               say(p, pick("gg", "gg wp", "ggs", "gg " + pn), delay + i * 25, false);
            }
         }
      } else if (has(s, "noob*", "n00b*", "nub", "nubs", "schlecht*", "ez", "easy", "trash", "lappen", "bad", "kacke", "lol", "loser")) {
         BotProfile who = named != null ? named : enemyBot;
         if (who != null) {
            who.team.grudgePlayer += 0.1;
            say(who, pick("selber noob", "sagt der richtige", "warte bis ich dein bett hab " + pn, "erst mal bridgen lernen", "wir sehen uns gleich " + pn, "du redest viel fuer jemanden mit wolle-ruestung", "aha"), delay, false);
         }
      } else if (has(s, "hi", "hallo", "hey", "moin", "servus", "huhu", "yo", "gruss gott", "hello")) {
         BotProfile who = named != null ? named : (RND.nextBoolean() && mate != null ? mate : enemyBot);
         say(who, pick("hi " + pn, "hey", "moin", "servus", "hallo :)", "yo"), delay, false);
      } else if (has(s, "team", "teamen", "frieden", "buendnis", "truce", "zusammen")) {
         BotProfile who = named != null ? named : enemyBot;
         say(who, pick("teamen ist verboten", "netter versuch", "nur wenn du mir dein bett gibst", "nein danke"), delay, false);
      } else if (has(s, "nice", "gut", "krass", "stark", "wow", "geil", "cool")) {
         BotProfile who = named != null ? named : (mate != null ? mate : enemyBot);
         say(who, pick("danke", "ich weiss :D", "jo", "danke dir"), delay, false);
      } else if (has(s, "haha", "xd", "lmao", "hahaha")) {
         if (RND.nextBoolean()) {
            say(enemyBot, pick("was lachst du", "xD", "haha"), delay, false);
         }
      } else if (named != null) {
         say(named, pick("was?", "hm?", "ja?", "ich bin beschaeftigt", "spaeter", "?"), delay, named.team == mine);
      } else if (raw.trim().endsWith("?") && RND.nextBoolean()) {
         BotProfile who = mate != null && RND.nextBoolean() ? mate : enemyBot;
         say(who, pick("keine ahnung", "vielleicht", "frag mich nicht", "ja", "nein", "hm"), delay, false);
      }
   }
}
