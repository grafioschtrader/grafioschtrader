package grafiosch.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import grafiosch.types.StableEnum;

public abstract class EnumHelper {

  public static <E extends Enum<E> & StableEnum> long encodeEnumSet(EnumSet<E> set) {
    long ret = 0;
    for (E val : set) {
      ret |= (1 << val.getValue());
    }
    return ret;
  }

  
  public static <E extends Enum<E> & StableEnum> EnumSet<E> decodeEnumSet(Class<E> enumClass, long bitVector) {
    EnumSet<E> set = EnumSet.noneOf(enumClass);
    for (E element : enumClass.getEnumConstants()) {
      long value = element.getValue();
      if ((bitVector & (1L << value)) != 0) {
        set.add(element);
      }
    }
    return set;
  }
  
  public static <E extends Enum<E> & StableEnum> boolean contains(E targetEnum, long bitVector) {
    long value = targetEnum.getValue();
    return (bitVector & (1L << value)) != 0;
  }
  
  
  public static <E extends Enum<E> & StableEnum> EnumSet<E> decode(int encoded, Class<E> enumKlazz) {
    // First populate a look-up map of ordinal to Enum value.
    // This is fairly disgusting: Anyone know of a better approach?
    Map<Byte, E> ordinalMap = new HashMap<>();
    for (E val : EnumSet.allOf(enumKlazz)) {
      ordinalMap.put(val.getValue(), val);
    }

    EnumSet<E> ret = EnumSet.noneOf(enumKlazz);
    byte ordinal = 0;

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

  public static <E extends Enum<E>> EnumSet<E> cloneSetAndAddEnum(EnumSet<E> existingEnumSet, E addEnum) {
    EnumSet<E> cloneEnumSet = existingEnumSet.clone();
    cloneEnumSet.add(addEnum);
    return cloneEnumSet;

  }

}
