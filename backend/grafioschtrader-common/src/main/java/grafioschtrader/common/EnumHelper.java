package grafioschtrader.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public abstract class EnumHelper {

  public static <E extends Enum<E>> int encode(EnumSet<E> set) {
    int ret = 0;
    for (E val : set) {
      // Bitwise-OR each ordinal value together to encode as single int.
      ret |= (1 << val.ordinal());
    }
    return ret;
  }

  public static <E extends Enum<E>> EnumSet<E> decode(int encoded, Class<E> enumKlazz) {
    // First populate a look-up map of ordinal to Enum value.
    // This is fairly disgusting: Anyone know of a better approach?
    Map<Integer, E> ordinalMap = new HashMap<>();
    for (E val : EnumSet.allOf(enumKlazz)) {
      ordinalMap.put(val.ordinal(), val);
    }

    EnumSet<E> ret = EnumSet.noneOf(enumKlazz);
    int ordinal = 0;

    // Now loop over encoded value by analysing each bit independently.
    // If the bit is set, determine which ordinal that corresponds to
    // (by also maintaining an ordinal counter) and use this to retrieve
    // the correct value from the look-up map.
    for (int i = 1; i != 0; i <<= 1) {
      if ((i & encoded) != 0) {
        ret.add(ordinalMap.get(ordinal));
      }
      ++ordinal;
    }
    return ret;
  }

  public static <E extends Enum<E>> E enumContainsNameAsString(String name, Class<E> enumKlazz) {
    for (E val : EnumSet.allOf(enumKlazz)) {
      if (val.name().equals(name)) {
        return val;
      }
    }
    return null;
  }

}
