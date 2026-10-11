package de.glowcube.bedwars;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Die Karte: fuenf roemische Team-Inseln mit Saeulen und Statuen, fuenf kleine
 * Diamant-Inseln dazwischen und in der Mitte ein runder Tempel mit den Smaragd-Spawnern.
 */
public final class Arena {
   public static final int Y = 180;
   public static final int VOID_Y = 150;
   public static final int TEAM_RING = 42;
   public static final int DIAMOND_RING = 29;
   public static final int MID_RADIUS = 11;
   public static final int ISLAND_RADIUS = 8;
   /** Bis hierhin (Abstand zur Mitte) wird beim Start und Ende aufgeraeumt. */
   public static final int EXTENT = 62;
   public static final int MIN_BUILD_Y = Y - 12;
   public static final int MAX_BUILD_Y = Y + 18;
   private static final int FLAGS = 2 | 16 | 32;

   public final ServerLevel level;
   public final BlockPos center;
   /** Bloecke der Karte - die kann niemand abbauen. */
   public final Long2ObjectOpenHashMap<BlockState> map = new Long2ObjectOpenHashMap<>();
   private final LongArrayList mapOrder = new LongArrayList();
   private int repairCursor;
   public final Island[] islands = new Island[BwTeam.values().length];
   public final List<BlockPos> diamondGens = new ArrayList<>();
   public final List<BlockPos> emeraldGens = new ArrayList<>();
   public final List<BlockPos> diamondIslands = new ArrayList<>();

   public Arena(ServerLevel level, BlockPos near) {
      this.level = level;
      this.center = new BlockPos(near.getX() & -16, Y, near.getZ() & -16);

      for (BwTeam team : BwTeam.values()) {
         double angle = Math.toRadians(-90.0 + 72.0 * team.ordinal());
         BlockPos origin = this.center.offset((int)Math.round(Math.cos(angle) * TEAM_RING), 0, (int)Math.round(Math.sin(angle) * TEAM_RING));
         this.islands[team.ordinal()] = new Island(team, origin, towards(origin, this.center));
         double dAngle = angle + Math.toRadians(36.0);
         BlockPos d = this.center.offset((int)Math.round(Math.cos(dAngle) * DIAMOND_RING), 0, (int)Math.round(Math.sin(dAngle) * DIAMOND_RING));
         this.diamondIslands.add(d);
         this.diamondGens.add(d.above());
      }

      this.emeraldGens.add(this.center.offset(2, 2, 0));
      this.emeraldGens.add(this.center.offset(-2, 2, 0));
   }

   private static Direction towards(BlockPos from, BlockPos to) {
      int dx = to.getX() - from.getX();
      int dz = to.getZ() - from.getZ();
      if (Math.abs(dx) >= Math.abs(dz)) {
         return dx > 0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz > 0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   public Island island(BwTeam team) {
      return this.islands[team.ordinal()];
   }

   // ------------------------------------------------------------------ Bereich

   public void forceLoad(boolean load) {
      int r = EXTENT + 8;

      for (int cx = this.center.getX() - r >> 4; cx <= this.center.getX() + r >> 4; cx++) {
         for (int cz = this.center.getZ() - r >> 4; cz <= this.center.getZ() + r >> 4; cz++) {
            this.level.setChunkForced(cx, cz, load);
         }
      }
   }

   public boolean inside(Vec3 pos) {
      return Math.abs(pos.x - this.center.getX()) < EXTENT + 10
         && Math.abs(pos.z - this.center.getZ()) < EXTENT + 10
         && pos.y > Y - 60
         && pos.y < Y + 60;
   }

   public boolean inBuildArea(BlockPos pos) {
      return Math.abs(pos.getX() - this.center.getX()) <= EXTENT
         && Math.abs(pos.getZ() - this.center.getZ()) <= EXTENT
         && pos.getY() >= MIN_BUILD_Y
         && pos.getY() <= MAX_BUILD_Y;
   }

   public AABB bounds() {
      return new AABB(
         this.center.getX() - EXTENT - 8, Y - 70, this.center.getZ() - EXTENT - 8, this.center.getX() + EXTENT + 8, Y + 60, this.center.getZ() + EXTENT + 8
      );
   }

   public boolean isMap(BlockPos pos) {
      return this.map.containsKey(pos.asLong());
   }

   public void build() {
      this.clear();
      this.buildMid();

      for (BlockPos d : this.diamondIslands) {
         this.buildDiamondIsland(d);
      }

      for (Island island : this.islands) {
         this.buildTeamIsland(island);
      }

      this.mapOrder.clear();
      this.mapOrder.addAll(this.map.keySet());
   }

   public void clear() {
      this.map.clear();
      this.mapOrder.clear();
      int r = EXTENT + 4;

      for (int cx = this.center.getX() - r >> 4; cx <= this.center.getX() + r >> 4; cx++) {
         for (int cz = this.center.getZ() - r >> 4; cz <= this.center.getZ() + r >> 4; cz++) {
            this.level.getChunk(cx, cz);
         }
      }

      BlockState air = Blocks.AIR.defaultBlockState();
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = this.center.getX() - r; x <= this.center.getX() + r; x++) {
         for (int z = this.center.getZ() - r; z <= this.center.getZ() + r; z++) {
            for (int y = MIN_BUILD_Y - 4; y <= MAX_BUILD_Y + 4; y++) {
               pos.set(x, y, z);
               if (!this.level.getBlockState(pos).isAir()) {
                  this.level.setBlock(pos, air, FLAGS);
               }
            }
         }
      }
   }

   /** Prueft jeden Tick einen Teil der Karte und setzt zerstoerte Kartenbloecke zurueck (Feuerbaelle). */
   public void repairStep(int budget) {
      int size = this.mapOrder.size();
      if (size == 0) {
         return;
      }

      MutableBlockPos pos = new MutableBlockPos();

      for (int i = 0; i < budget && i < size; i++) {
         if (this.repairCursor >= size) {
            this.repairCursor = 0;
         }

         long key = this.mapOrder.getLong(this.repairCursor++);
         pos.set(key);
         BlockState want = this.map.get(key);
         if (want != null && this.level.getBlockState(pos) != want) {
            this.level.setBlock(pos, want, FLAGS);

            for (ItemEntity item : this.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0))) {
               if (item.getItem().is(want.getBlock().asItem())) {
                  item.discard();
               }
            }
         }
      }
   }

   // ------------------------------------------------------------------ Bauen

   private void put(BlockPos pos, BlockState state) {
      this.level.setBlock(pos, state, FLAGS);
      this.map.put(pos.asLong(), state);
   }

   private void put(BlockPos pos, Block block) {
      this.put(pos, block.defaultBlockState());
   }

   private static boolean octagon(int a, int b, int r) {
      return Math.abs(a) <= r && Math.abs(b) <= r && Math.abs(a) + Math.abs(b) <= r + r / 2;
   }

   /** Haengende Unterseite aus Stein, damit die Inseln wie schwebende Felsen aussehen. */
   private void underside(BlockPos origin, int radius, boolean round) {
      for (int depth = 1; depth <= radius; depth++) {
         int r = radius - (depth * 3 + 1) / 2;
         if (r < 0) {
            break;
         }

         for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
               boolean in = round ? a * a + b * b <= r * r + r : octagon(a, b, r);
               if (in) {
                  int hash = (a * 31 + b * 17 + depth * 7) & 7;
                  Block block = depth == 1 ? Blocks.STONE_BRICKS : (hash < 3 ? Blocks.ANDESITE : (hash < 5 ? Blocks.STONE : Blocks.COBBLESTONE));
                  this.put(origin.offset(a, -depth, b), block);
               }
            }
         }
      }
   }

   private void column(BlockPos base, int height, boolean lantern) {
      this.put(base, Blocks.QUARTZ_BRICKS);

      for (int i = 1; i < height - 1; i++) {
         this.put(base.above(i), Blocks.QUARTZ_PILLAR);
      }

      this.put(base.above(height - 1), Blocks.CHISELED_QUARTZ_BLOCK);
      if (lantern) {
         this.put(base.above(height), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
      }
   }

   /** Kleine roemische Statue mit leuchtendem Speer. right = Richtung der Arme, facing = Blickrichtung. */
   private void statue(BlockPos base, Direction right, Direction facing, Block torso, boolean big) {
      this.put(base, Blocks.CHISELED_STONE_BRICKS);
      int y = 1;
      if (big) {
         this.put(base.above(y++), Blocks.CHISELED_QUARTZ_BLOCK);
      }

      this.put(base.above(y++), Blocks.QUARTZ_PILLAR);
      this.put(base.above(y++), Blocks.QUARTZ_PILLAR);
      BlockPos chest = base.above(y++);
      this.put(chest, torso);
      BlockState arm = Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
      this.put(chest.relative(right), arm);
      this.put(chest.relative(right.getOpposite()), arm);
      this.put(chest.relative(right).above(), Blocks.END_ROD);
      this.put(chest.relative(right).above(2), Blocks.END_ROD);
      this.put(chest.relative(right).below(), Blocks.END_ROD);
      BlockState head = Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(facing));
      this.put(chest.above(), head);
   }

   private void buildTeamIsland(Island is) {
      BwTeam team = is.team;
      int R = ISLAND_RADIUS;

      // Boden: Quarz innen, Steinziegel aussen, Teamfarbe als Weg und Rahmen
      for (int r = -R; r <= R; r++) {
         for (int f = -R; f <= R; f++) {
            if (octagon(r, f, R)) {
               Block block;
               boolean edge = !octagon(r, f, R - 1);
               if (edge) {
                  block = (r + f & 3) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS;
               } else if (!octagon(r, f, R - 2)) {
                  block = Blocks.POLISHED_ANDESITE;
               } else if (r == 0 && f >= -5 && f <= 1) {
                  block = team.concrete;
               } else if (Math.abs(r) <= 2 && f >= 2 && f <= 5 && (Math.abs(r) == 2 || f == 2 || f == 5)) {
                  block = team.terracotta;
               } else {
                  block = (r + f & 1) == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.QUARTZ_BRICKS;
               }

               this.put(is.at(r, 0, f), block);
            }
         }
      }

      this.underside(is.origin, R, false);

      // Generator: Eisenblock im Boden, Goldbloecke daneben
      this.put(is.at(0, 0, -6), Blocks.IRON_BLOCK);
      this.put(is.at(-1, 0, -6), Blocks.GOLD_BLOCK);
      this.put(is.at(1, 0, -6), Blocks.GOLD_BLOCK);
      this.put(is.at(0, 0, -7), Blocks.SEA_LANTERN);

      // Saeulen an den Ecken
      this.column(is.at(-6, 1, -6), 5, true);
      this.column(is.at(6, 1, -6), 5, true);
      this.column(is.at(-6, 1, 5), 4, true);
      this.column(is.at(6, 1, 5), 4, true);

      // Statue rechts vom Spawn, Shop-Pavillon links
      this.statue(is.at(6, 1, -2), is.forward, is.right.getOpposite(), Blocks.CHISELED_QUARTZ_BLOCK, false);
      this.put(is.at(6, 1, -1), Blocks.LANTERN);
      this.put(is.at(6, 1, -3), Blocks.LANTERN);

      for (int f = -3; f <= -1; f += 2) {
         this.put(is.at(-7, 1, f), Blocks.STONE_BRICK_WALL);
         this.put(is.at(-7, 2, f), Blocks.STONE_BRICK_WALL);
         this.put(is.at(-7, 3, f), Blocks.STONE_BRICK_WALL);
      }

      for (int r = -7; r <= -5; r++) {
         for (int f = -3; f <= -1; f++) {
            this.put(is.at(r, 4, f), r == -5 || f != -2 ? team.wool : Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().getBlock());
         }
      }

      this.put(is.at(-6, 4, -2), Blocks.SMOOTH_QUARTZ);
      this.put(is.at(-6, 3, -2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
   }

   private void buildDiamondIsland(BlockPos c) {
      int R = 4;

      for (int a = -R; a <= R; a++) {
         for (int b = -R; b <= R; b++) {
            int d = a * a + b * b;
            if (d <= R * R + 1) {
               Block block = d > (R - 1) * (R - 1) + 1 ? Blocks.STONE_BRICKS : ((a + b & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
               this.put(c.offset(a, 0, b), block);
            }
         }
      }

      this.underside(c, R, true);
      this.put(c, Blocks.DIAMOND_BLOCK);
      this.put(c.offset(1, 0, 1), Blocks.SEA_LANTERN);
      this.put(c.offset(-1, 0, -1), Blocks.SEA_LANTERN);
      this.put(c.offset(1, 0, -1), Blocks.CHISELED_STONE_BRICKS);
      this.put(c.offset(-1, 0, 1), Blocks.CHISELED_STONE_BRICKS);
      // zwei kleine Saeulen auf der Aussenseite
      int ox = c.getX() - this.center.getX();
      int oz = c.getZ() - this.center.getZ();
      Direction out = Math.abs(ox) >= Math.abs(oz) ? (ox > 0 ? Direction.EAST : Direction.WEST) : (oz > 0 ? Direction.SOUTH : Direction.NORTH);
      Direction side = out.getClockWise();
      this.column(c.relative(out, 3).relative(side, 2).above(), 4, true);
      this.column(c.relative(out, 3).relative(side, -2).above(), 4, true);
   }

   private void buildMid() {
      BlockPos c = this.center;
      int R = MID_RADIUS;

      for (int a = -R; a <= R; a++) {
         for (int b = -R; b <= R; b++) {
            int d2 = a * a + b * b;
            if (d2 <= R * R + R) {
               double d = Math.sqrt(d2);
               Block block;
               if (d > R - 1) {
                  block = ((a * 7 + b * 3) & 3) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS;
               } else if (d <= 4.6) {
                  block = Blocks.QUARTZ_BRICKS;
               } else if (Math.abs(d - 6.5) < 0.6) {
                  block = Blocks.CHISELED_QUARTZ_BLOCK;
               } else if (a == 0 || b == 0 || Math.abs(a) == Math.abs(b)) {
                  block = Blocks.POLISHED_ANDESITE;
               } else {
                  block = Blocks.SMOOTH_STONE;
               }

               this.put(c.offset(a, 0, b), block);
            }
         }
      }

      this.underside(c, R, true);

      // Podest in der Mitte (eine Stufe hoch) mit den Smaragd-Spawnern
      for (int a = -3; a <= 3; a++) {
         for (int b = -3; b <= 3; b++) {
            if (a * a + b * b <= 10) {
               this.put(c.offset(a, 1, b), a * a + b * b <= 2 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ);
            }
         }
      }

      this.put(c.offset(2, 1, 0), Blocks.EMERALD_BLOCK);
      this.put(c.offset(-2, 1, 0), Blocks.EMERALD_BLOCK);
      this.put(c.offset(0, 1, 3), Blocks.SEA_LANTERN);
      this.put(c.offset(0, 1, -3), Blocks.SEA_LANTERN);
      // Leuchtfeuer in der Mitte: der Strahl geht durch die offene Kuppel in den Himmel
      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            this.put(c.offset(a, 0, b), (a + b & 1) == 0 ? Blocks.GOLD_BLOCK : Blocks.EMERALD_BLOCK);
         }
      }

      this.put(c.offset(0, 1, 0), Blocks.BEACON);
      this.put(c.offset(1, 1, 0), Blocks.CHISELED_QUARTZ_BLOCK);
      this.put(c.offset(-1, 1, 0), Blocks.CHISELED_QUARTZ_BLOCK);
      this.put(c.offset(0, 1, 1), Blocks.CHISELED_QUARTZ_BLOCK);
      this.put(c.offset(0, 1, -1), Blocks.CHISELED_QUARTZ_BLOCK);
      // Vier goldene Waechter-Statuen schauen auf das Leuchtfeuer
      this.statue(c.offset(4, 1, 4), Direction.EAST, Direction.NORTH, Blocks.GOLD_BLOCK, true);
      this.statue(c.offset(-4, 1, -4), Direction.WEST, Direction.SOUTH, Blocks.GOLD_BLOCK, true);
      this.statue(c.offset(4, 1, -4), Direction.NORTH, Direction.WEST, Blocks.GOLD_BLOCK, true);
      this.statue(c.offset(-4, 1, 4), Direction.SOUTH, Direction.EAST, Blocks.GOLD_BLOCK, true);

      // Rundtempel: zehn Saeulen und ein Ring obendrauf
      for (int i = 0; i < 10; i++) {
         double ang = Math.toRadians(i * 36.0 + 18.0);
         BlockPos base = c.offset((int)Math.round(Math.cos(ang) * 8.5), 1, (int)Math.round(Math.sin(ang) * 8.5));
         this.column(base, 7, false);
         this.put(base.above(7), Blocks.CHISELED_QUARTZ_BLOCK);
      }

      for (int a = -10; a <= 10; a++) {
         for (int b = -10; b <= 10; b++) {
            double d = Math.sqrt(a * a + b * b);
            if (d >= 7.5 && d <= 9.6) {
               BlockPos pos = c.offset(a, 8, b);
               if (!this.map.containsKey(pos.asLong())) {
                  this.put(pos, Blocks.SMOOTH_QUARTZ_SLAB);
               }
            }
         }
      }

      for (int i = 0; i < 10; i += 2) {
         double ang = Math.toRadians(i * 36.0);
         BlockPos pos = c.offset((int)Math.round(Math.cos(ang) * 8.5), 7, (int)Math.round(Math.sin(ang) * 8.5));
         if (!this.map.containsKey(pos.asLong())) {
            this.put(pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
         }
      }
   }

   // ------------------------------------------------------------------ Betten

   public void placeBed(BwTeam team) {
      Island is = this.island(team);
      BlockState bed = team.bed.defaultBlockState().setValue(BedBlock.FACING, is.forward);
      this.level.setBlock(is.bedFoot(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
      this.level.setBlock(is.bedHead(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
   }

   public List<BlockPos> bedBlocks(BwTeam team) {
      Island is = this.island(team);
      List<BlockPos> list = new ArrayList<>(2);

      for (int r = -3; r <= 3; r++) {
         for (int f = 0; f <= 7; f++) {
            for (int u = 1; u <= 3; u++) {
               BlockPos pos = is.at(r, u, f);
               if (this.level.getBlockState(pos).getBlock() == team.bed) {
                  list.add(pos);
               }
            }
         }
      }

      return list;
   }

   public void removeBed(BwTeam team) {
      BlockState air = Blocks.AIR.defaultBlockState();

      for (BlockPos pos : this.bedBlocks(team)) {
         this.level.setBlock(pos, air, FLAGS);
      }
   }

   public boolean hasBed(BwTeam team) {
      return !this.bedBlocks(team).isEmpty();
   }

   public BwTeam bedOwner(BlockPos pos) {
      for (Island is : this.islands) {
         if (Math.abs(pos.getX() - is.origin.getX()) <= ISLAND_RADIUS + 1
            && Math.abs(pos.getZ() - is.origin.getZ()) <= ISLAND_RADIUS + 1
            && Math.abs(pos.getY() - is.origin.getY()) <= 5) {
            return is.team;
         }
      }

      return null;
   }

   /** Steht man hier auf (oder ueber) irgendeiner Insel der Karte? */
   public boolean onAnyIsland(Vec3 pos) {
      if (pos.y < Y - 3 || pos.y > Y + 12) {
         return false;
      } else if (this.islandAt(pos, 1.0) != null) {
         return true;
      } else if (Math.hypot(pos.x - this.center.getX() - 0.5, pos.z - this.center.getZ() - 0.5) < MID_RADIUS + 1) {
         return true;
      } else {
         for (BlockPos d : this.diamondIslands) {
            if (Math.hypot(pos.x - d.getX() - 0.5, pos.z - d.getZ() - 0.5) < 5.5) {
               return true;
            }
         }

         return false;
      }
   }

   /** Zu welcher Team-Insel gehoert diese Stelle (mit etwas Rand)? */
   public BwTeam islandAt(Vec3 pos, double margin) {
      for (Island is : this.islands) {
         double dx = pos.x - (is.origin.getX() + 0.5);
         double dz = pos.z - (is.origin.getZ() + 0.5);
         if (dx * dx + dz * dz <= (ISLAND_RADIUS + margin) * (ISLAND_RADIUS + margin) && pos.y > Y - 8 && pos.y < Y + 20) {
            return is.team;
         }
      }

      return null;
   }

   /** Eine Team-Insel mit eigenem Koordinatensystem: f zeigt zur Mitte, r nach rechts. */
   public static final class Island {
      public final BwTeam team;
      public final BlockPos origin;
      public final Direction forward;
      public final Direction right;

      Island(BwTeam team, BlockPos origin, Direction forward) {
         this.team = team;
         this.origin = origin;
         this.forward = forward;
         this.right = forward.getClockWise();
      }

      public BlockPos at(int r, int u, int f) {
         return this.origin
            .offset(this.right.getStepX() * r + this.forward.getStepX() * f, u, this.right.getStepZ() * r + this.forward.getStepZ() * f);
      }

      public Vec3 atVec(double r, double u, double f) {
         return new Vec3(
            this.origin.getX() + 0.5 + this.right.getStepX() * r + this.forward.getStepX() * f,
            this.origin.getY() + u,
            this.origin.getZ() + 0.5 + this.right.getStepZ() * r + this.forward.getStepZ() * f
         );
      }

      public BlockPos bedFoot() {
         return this.at(0, 1, 3);
      }

      public BlockPos bedHead() {
         return this.at(0, 1, 4);
      }

      public Vec3 bedCenter() {
         return this.atVec(0.0, 1.3, 3.5);
      }

      public BlockPos generator() {
         return this.at(0, 1, -6);
      }

      public Vec3 spawn() {
         return this.atVec(0.0, 1.0, -3.0);
      }

      public Vec3 shop() {
         return this.atVec(-6.0, 1.0, -2.0);
      }

      /** Stelle vor dem Shop, an der ein Bot einkauft. */
      public BlockPos shopFront() {
         return this.at(-4, 1, -2);
      }

      public Vec3 centerVec() {
         return new Vec3(this.origin.getX() + 0.5, this.origin.getY() + 1.0, this.origin.getZ() + 0.5);
      }
   }
}
