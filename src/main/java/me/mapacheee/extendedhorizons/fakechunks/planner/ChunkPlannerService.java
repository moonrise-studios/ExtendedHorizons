package me.mapacheee.extendedhorizons.fakechunks.planner;

import com.thewinterframework.service.annotation.Service;
import me.mapacheee.extendedhorizons.fakechunks.util.ChunkKeyCodec;

import java.util.Arrays;

@Service
public final class ChunkPlannerService {

  public static final int MAX_CHUNK_DISTANCE = 128;
  private static final int MAX_RADIUS_INDEX = MAX_CHUNK_DISTANCE + 2;
  private static final int EDGE_BUFFER = 2;

  // Lazily computed per radius: the full table for all 130 radii holds ~2.3M
  // longs (~18 MB) while servers typically only ever use one or two radii.
  // The benign data race on the slot is fine — computeRadius is idempotent.
  private static final long[][] RADIUS_ITERATION_LIST = new long[MAX_RADIUS_INDEX + 1][];

  public static long[] radiusIterationList(int radius) {
    int index = Math.clamp(radius, 0, MAX_RADIUS_INDEX);
    long[] cached = RADIUS_ITERATION_LIST[index];
    if (cached == null) {
      RADIUS_ITERATION_LIST[index] = cached = computeRadius(index);
    }
    return cached;
  }

  private static long[] computeRadius(int radius) {
    // isWithinRange accepts offsets up to radius + 1 on each axis.
    int extent = radius + 1;
    int diameter = extent * 2 + 1;
    long[] sortable = new long[diameter * diameter];
    int count = 0;
    for (int x = -extent; x <= extent; x++) {
      for (int z = -extent; z <= extent; z++) {
        if (!isWithinRange(x, z, radius)) {
          continue;
        }
        // Sort key (distance squared) in the high 32 bits, dense index into
        // the offset grid in the low 32 bits so the sort is stable per ring.
        long distSq = (long) x * x + (long) z * z;
        int gridIndex = (x + extent) * diameter + (z + extent);
        sortable[count++] = (distSq << 32) | gridIndex;
      }
    }
    Arrays.sort(sortable, 0, count);

    long[] result = new long[count];
    for (int i = 0; i < count; i++) {
      int gridIndex = (int) sortable[i];
      int x = gridIndex / diameter - extent;
      int z = gridIndex % diameter - extent;
      result[i] = ChunkKeyCodec.pack(x, z);
    }
    return result;
  }

  public static boolean isWithinRange(int posX, int posZ, int viewDistance) {
    int absX = Math.abs(posX);
    int absZ = Math.abs(posZ);
    int outerBound = Math.max(absX, absZ);
    if (outerBound > viewDistance + 1) {
      return false;
    }
    long effectiveX = Math.max(0, absX - EDGE_BUFFER);
    long effectiveZ = Math.max(0, absZ - EDGE_BUFFER);
    long distSq = effectiveX * effectiveX + effectiveZ * effectiveZ;
    return distSq < (long) viewDistance * viewDistance;
  }
}

