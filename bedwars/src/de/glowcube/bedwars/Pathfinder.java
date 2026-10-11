package de.glowcube.bedwars;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A*-Wegsuche auf dem Blockraster. Ein Knoten ist die Luftzelle, in der die Fuesse stehen.
 * Neben Laufen, Springen und Fallen kennt sie Bruecken bauen, Hochbauen und Bloecke abbauen -
 * jeweils mit Kosten, so dass vorhandene Bruecken und Wege bevorzugt werden.
 */
public final class Pathfinder {
   public enum Move {
      WALK,
      JUMP,
      DROP,
      BRIDGE,
      PILLAR,
      BREAK
   }

   public static final class Step {
      public final BlockPos pos;
      public final Move move;
      /** Block, der vor dem Schritt gesetzt werden muss (Bruecke/Hochbauen), sonst null. */
      public final BlockPos place;
      /** Bloecke, die vor dem Schritt weg muessen. */
      public final List<BlockPos> breaks;

      Step(BlockPos pos, Move move, BlockPos place, List<BlockPos> breaks) {
         this.pos = pos;
         this.move = move;
         this.place = place;
         this.breaks = breaks;
      }
   }

   public interface Goal {
      boolean reached(int x, int y, int z);

      double estimate(int x, int y, int z);
   }

   public static final class Options {
      public int blocks;
      public int pickaxe;
      public boolean shears;
      public int maxNodes = 4000;
      public boolean allowBreak = true;
      public double bridgeCost = 2.6;
      public double heuristicWeight = 1.35;
   }

   public static final class Result {
      public final List<Step> steps;
      public final boolean complete;
      public final int blocksUsed;

      Result(List<Step> steps, boolean complete, int blocksUsed) {
         this.steps = steps;
         this.complete = complete;
         this.blocksUsed = blocksUsed;
      }
   }

   private record Entry(double f, Node node) implements Comparable<Entry> {
      public int compareTo(Entry o) {
         return Double.compare(this.f, o.f);
      }
   }

   private static final class Node {
      final int x;
      final int y;
      final int z;
      Node parent;
      Move move;
      BlockPos place;
      List<BlockPos> breaks;
      double g;
      double f;
      double h;
      int blocks;
      boolean closed;

      Node(int x, int y, int z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }

   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
   private final ServerLevel level;
   private final Arena arena;
   private final Options opt;
   private final Goal goal;
   private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
   private final PriorityQueue<Entry> open = new PriorityQueue<>();
   private final BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

   private Pathfinder(ServerLevel level, Arena arena, Goal goal, Options opt) {
      this.level = level;
      this.arena = arena;
      this.goal = goal;
      this.opt = opt;
   }

   public static Result find(ServerLevel level, Arena arena, BlockPos start, Goal goal, Options opt) {
      return new Pathfinder(level, arena, goal, opt).run(start);
   }

   // ----------------------------------------------------------------- Ziele

   public static Goal near(BlockPos target, double radius) {
      return new Goal() {
         public boolean reached(int x, int y, int z) {
            double dx = x - target.getX();
            double dy = y - target.getY();
            double dz = z - target.getZ();
            return dx * dx + dz * dz <= radius * radius && Math.abs(dy) <= Math.max(1.5, radius);
         }

         public double estimate(int x, int y, int z) {
            double dx = x - target.getX();
            double dy = y - target.getY();
            double dz = z - target.getZ();
            return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - radius);
         }
      };
   }

   /**
    * Ziel: auf gleicher Hoehe direkt neben einem der Bloecke stehen (Fuesse beruehren ihn)
    * oder direkt darauf - z.B. am Bett. Schraeg darueber zaehlt nicht, da liegt die Verteidigung dazwischen.
    */
   public static Goal adjacent(List<BlockPos> targets) {
      return new Goal() {
         public boolean reached(int x, int y, int z) {
            for (BlockPos t : targets) {
               int dx = Math.abs(x - t.getX());
               int dz = Math.abs(z - t.getZ());
               int dy = y - t.getY();
               if (dx + dz == 1 && dy == 0 || dx == 0 && dz == 0 && dy == 1) {
                  return true;
               }
            }

            return false;
         }

         public double estimate(int x, int y, int z) {
            double best = Double.MAX_VALUE;

            for (BlockPos t : targets) {
               double dx = x - t.getX();
               double dy = y - t.getY();
               double dz = z - t.getZ();
               best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }

            return Math.max(0.0, best - 1.0);
         }
      };
   }

   // ----------------------------------------------------------------- Welt

   private BlockState state(int x, int y, int z) {
      return this.level.getBlockState(this.tmp.set(x, y, z));
   }

   /** Kann man hier durch (Fuesse oder Kopf)? */
   private boolean free(int x, int y, int z) {
      BlockState s = this.state(x, y, z);
      if (s.isAir()) {
         return true;
      } else if (!s.getFluidState().isEmpty()) {
         return false;
      } else {
         return s.getCollisionShape(this.level, this.tmp).isEmpty();
      }
   }

   /** Kann man auf diesem Block stehen? Mauern und Zaeune (hoeher als 1) zaehlen nicht. */
   private boolean floor(int x, int y, int z) {
      BlockState s = this.state(x, y, z);
      if (s.isAir()) {
         return false;
      } else {
         VoxelShape shape = s.getCollisionShape(this.level, this.tmp);
         return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0 && shape.max(Direction.Axis.Y) >= 0.5;
      }
   }

   private boolean tall(int x, int y, int z) {
      BlockState s = this.state(x, y, z);
      if (s.isAir()) {
         return false;
      } else {
         VoxelShape shape = s.getCollisionShape(this.level, this.tmp);
         return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 1.0;
      }
   }

   /** Kosten, um diesen Block abzubauen, oder -1 wenn das nicht geht. */
   private double breakCost(int x, int y, int z) {
      if (!this.opt.allowBreak) {
         return -1.0;
      } else {
         this.tmp.set(x, y, z);
         if (!this.arena.inBuildArea(this.tmp) || this.arena.isMap(this.tmp)) {
            return -1.0;
         } else {
            BlockState s = this.level.getBlockState(this.tmp);
            if (s.isAir() || s.getBlock() instanceof BedBlock || !s.getFluidState().isEmpty()) {
               return -1.0;
            } else {
               int ticks = breakTicks(s, this.opt.pickaxe, this.opt.shears);
               return ticks < 0 ? -1.0 : 1.0 + ticks / 6.0;
            }
         }
      }
   }

   /** Wie viele Ticks ein Bot fuer diesen Block braucht (angelehnt an die echten Abbauzeiten). */
   public static int breakTicks(BlockState s, int pickaxe, boolean shears) {
      float hardness = s.getBlock().defaultDestroyTime();
      if (hardness < 0.0F) {
         return -1;
      } else if (s.is(net.minecraft.tags.BlockTags.WOOL)) {
         return shears ? 5 : 18;
      } else if (s.is(Blocks.END_STONE)) {
         return switch (pickaxe) {
            case 0 -> 140;
            case 1 -> 34;
            case 2 -> 17;
            default -> 11;
         };
      } else if (s.is(net.minecraft.tags.BlockTags.PLANKS)) {
         return pickaxe >= 2 ? 22 : 45;
      } else if (s.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
         return 6;
      } else {
         return Math.max(4, (int)(hardness * (pickaxe > 0 ? 12 : 30)));
      }
   }

   // ----------------------------------------------------------------- Suche

   private Node node(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      Node n = this.nodes.get(key);
      if (n == null) {
         n = new Node(x, y, z);
         n.g = Double.MAX_VALUE;
         n.h = this.goal.estimate(x, y, z);
         this.nodes.put(key, n);
      }

      return n;
   }

   private void offer(Node from, int x, int y, int z, double cost, Move move, BlockPos place, List<BlockPos> breaks) {
      if (y >= Arena.MIN_BUILD_Y && y <= Arena.MAX_BUILD_Y) {
         int blocks = from.blocks + (place != null ? 1 : 0);
         if (place == null || blocks <= this.opt.blocks) {
            Node n = this.node(x, y, z);
            if (!n.closed) {
               double g = from.g + cost;
               if (g < n.g) {
                  n.g = g;
                  n.f = g + n.h * this.opt.heuristicWeight;
                  n.parent = from;
                  n.move = move;
                  n.place = place;
                  n.breaks = breaks;
                  n.blocks = blocks;
                  this.open.add(new Entry(n.f, n));
               }
            }
         }
      }
   }

   private Result run(BlockPos start) {
      Node s = this.node(start.getX(), start.getY(), start.getZ());
      s.g = 0.0;
      s.f = s.h;
      this.open.add(new Entry(s.f, s));
      Node best = s;
      int expanded = 0;

      while (!this.open.isEmpty() && expanded < this.opt.maxNodes) {
         Node n = this.open.poll().node;
         if (n.closed) {
            continue;
         }

         n.closed = true;
         expanded++;
         if (this.goal.reached(n.x, n.y, n.z)) {
            return this.build(n, true);
         }

         if (n.h < best.h || n.h == best.h && n.g < best.g) {
            best = n;
         }

         this.expand(n);
      }

      return this.build(best, false);
   }

   private void expand(Node n) {
      int x = n.x;
      int y = n.y;
      int z = n.z;
      boolean headroom = this.free(x, y + 2, z);
      boolean[] open4 = new boolean[4];

      for (int i = 0; i < 4; i++) {
         Direction d = HORIZONTAL[i];
         int tx = x + d.getStepX();
         int tz = z + d.getStepZ();
         if (Math.abs(tx - this.arena.center.getX()) > Arena.EXTENT || Math.abs(tz - this.arena.center.getZ()) > Arena.EXTENT) {
            continue;
         }

         boolean feet = this.free(tx, y, tz);
         boolean head = this.free(tx, y + 1, tz);
         if (feet && head) {
            open4[i] = true;
            if (this.floor(tx, y - 1, tz)) {
               this.offer(n, tx, y, tz, 1.0, Move.WALK, null, null);
            } else {
               int drop = 0;

               for (int k = 1; k <= 3; k++) {
                  if (!this.free(tx, y - k, tz)) {
                     break;
                  }

                  if (this.floor(tx, y - k - 1, tz)) {
                     drop = k;
                     break;
                  }
               }

               if (drop > 0) {
                  this.offer(n, tx, y - drop, tz, 1.0 + 0.35 * drop, Move.DROP, null, null);
               }

               if (this.opt.blocks > 0 && !this.tall(tx, y - 1, tz) && this.free(tx, y - 1, tz)) {
                  BlockPos place = new BlockPos(tx, y - 1, tz);
                  if (this.arena.inBuildArea(place)) {
                     this.offer(n, tx, y, tz, 1.0 + this.opt.bridgeCost, Move.BRIDGE, place, null);
                  }
               }
            }
         } else if (!feet && this.floor(tx, y, tz) && !this.tall(tx, y, tz) && headroom) {
            boolean up1 = this.free(tx, y + 1, tz);
            boolean up2 = this.free(tx, y + 2, tz);
            if (up1 && up2) {
               this.offer(n, tx, y + 1, tz, 1.6, Move.JUMP, null, null);
            } else {
               double c1 = up1 ? 0.0 : this.breakCost(tx, y + 1, tz);
               double c2 = up2 ? 0.0 : this.breakCost(tx, y + 2, tz);
               if (c1 >= 0.0 && c2 >= 0.0) {
                  List<BlockPos> br = new ArrayList<>(2);
                  if (!up1) {
                     br.add(new BlockPos(tx, y + 1, tz));
                  }

                  if (!up2) {
                     br.add(new BlockPos(tx, y + 2, tz));
                  }

                  this.offer(n, tx, y + 1, tz, 1.6 + c1 + c2, Move.BREAK, null, br);
               }
            }

            if (this.opt.allowBreak) {
               this.offerBreakThrough(n, tx, y, tz, feet, head);
            }
         } else if (this.opt.allowBreak) {
            this.offerBreakThrough(n, tx, y, tz, feet, head);
         }
      }

      // diagonal laufen, wenn beide Nachbarn frei sind
      for (int i = 0; i < 4; i++) {
         int j = (i + 1) & 3;
         if (open4[i] && open4[j]) {
            Direction a = HORIZONTAL[i];
            Direction b = HORIZONTAL[j];
            int tx = x + a.getStepX() + b.getStepX();
            int tz = z + a.getStepZ() + b.getStepZ();
            if (this.free(tx, y, tz) && this.free(tx, y + 1, tz) && this.floor(tx, y - 1, tz)
               && this.floor(x + a.getStepX(), y - 1, z + a.getStepZ()) && this.floor(x + b.getStepX(), y - 1, z + b.getStepZ())) {
               this.offer(n, tx, y, tz, 1.42, Move.WALK, null, null);
            }
         }
      }

      // hochbauen
      if (this.opt.blocks > 0 && headroom && this.free(x, y + 1, z)) {
         BlockPos place = new BlockPos(x, y, z);
         if (this.arena.inBuildArea(place.above())) {
            this.offer(n, x, y + 1, z, 1.2 + this.opt.bridgeCost, Move.PILLAR, place, null);
         }
      }
   }

   /** Durch eine Wand aus gesetzten Bloecken hindurch abbauen. */
   private void offerBreakThrough(Node n, int tx, int y, int tz, boolean feet, boolean head) {
      double c1 = feet ? 0.0 : this.breakCost(tx, y, tz);
      double c2 = head ? 0.0 : this.breakCost(tx, y + 1, tz);
      if (c1 >= 0.0 && c2 >= 0.0 && (!feet || !head)) {
         if (this.floor(tx, y - 1, tz)) {
            List<BlockPos> br = new ArrayList<>(2);
            if (!feet) {
               br.add(new BlockPos(tx, y, tz));
            }

            if (!head) {
               br.add(new BlockPos(tx, y + 1, tz));
            }

            this.offer(n, tx, y, tz, 1.0 + c1 + c2, Move.BREAK, null, br);
         }
      }
   }

   private Result build(Node end, boolean complete) {
      List<Step> steps = new ArrayList<>();
      Node n = end;

      while (n.parent != null) {
         steps.add(new Step(new BlockPos(n.x, n.y, n.z), n.move, n.place, n.breaks == null ? List.of() : n.breaks));
         n = n.parent;
      }

      Collections.reverse(steps);
      return new Result(steps, complete, end.blocks);
   }
}
