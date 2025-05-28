package grafiosch.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import grafiosch.types.StableEnum;

/**
 * Utility class for operations on Enum types, particularly those implementing the {@link StableEnum} interface. It
 * provides methods for encoding and decoding {@link EnumSet}s into bitmasks, checking for enum presence in a bitmask,
 * and other enum-related helpers.
 */
public abstract class EnumHelper {

  /**
   * Encodes an {@link EnumSet} of {@link StableEnum} into a long bitmask. Each enum constant in the set corresponds to
   * a bit in the long value. The position of the bit is determined by the byte value returned by
   * {@link StableEnum#getValue()}.
   *
   * @param <E> The enum type, which must extend {@link Enum} and implement {@link StableEnum}.
   * @param set The EnumSet to encode.
   * @return A long value representing the bitmask of the EnumSet.
   */
  public static <E extends Enum<E> & StableEnum> long encodeEnumSet(EnumSet<E> set) {
    long ret = 0;
    for (E val : set) {
      ret |= (1 << val.getValue());
    }
    return ret;
  }

  /**
   * Decodes a long bitmask into an {@link EnumSet} of a specified {@link StableEnum} class.
   *
   * @param <E>       The enum type, which must extend {@link Enum} and implement {@link StableEnum}.
   * @param enumClass The Class object of the enum type.
   * @param bitVector The long bitmask to decode.
   * @return An EnumSet containing the enum constants corresponding to the set bits in the bitmask.
   */
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

  /**
   * Checks if a specific {@link StableEnum} constant is present in a long bitmask.
   *
   * @param <E>        The enum type, which must extend {@link Enum} and implement {@link StableEnum}.
   * @param targetEnum The enum constant to check for.
   * @param bitVector  The long bitmask.
   * @return {@code true} if the enum constant is represented in the bitmask, {@code false} otherwise.
   */
  public static <E extends Enum<E> & StableEnum> boolean contains(E targetEnum, long bitVector) {
    long value = targetEnum.getValue();
    return (bitVector & (1L << value)) != 0;
  }

  /**
   * Decodes an integer bitmask (presumably from older code where int was used) into an {@link EnumSet} of a specified
   * {@link StableEnum} class. This method assumes ordinals map directly to bit positions (0 to 30 for int).
   *
   * @param <E>       The enum type, which must extend {@link Enum} and implement {@link StableEnum}.
   * @param encoded   The integer bitmask to decode.
   * @param enumKlazz The Class object of the enum type.
   * @return An EnumSet containing the enum constants corresponding to the set bits in the bitmask. Returns an empty set
   *         if no corresponding enum values are found for the set bits.
   */
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

  /**
   * Finds an enum constant in a given enum class by its string name.
   *
   * @param <E>       The enum type.
   * @param name      The string name of the enum constant.
   * @param enumKlazz The Class object of the enum type.
   * @return The enum constant if found, or {@code null} if no constant matches the name.
   */
  public static <E extends Enum<E>> E enumContainsNameAsString(String name, Class<E> enumKlazz) {
    for (E val : EnumSet.allOf(enumKlazz)) {
      if (val.name().equals(name)) {
        return val;
      }
    }
    return null;
  }

  /**
   * Clones an existing {@link EnumSet} and adds another enum constant to the cloned set.
   *
   * @param <E>             The enum type.
   * @param existingEnumSet The EnumSet to clone.
   * @param addEnum         The enum constant to add to the cloned set.
   * @return A new EnumSet which is a clone of the original with the additional enum constant.
   */
  public static <E extends Enum<E>> EnumSet<E> cloneSetAndAddEnum(EnumSet<E> existingEnumSet, E addEnum) {
    EnumSet<E> cloneEnumSet = existingEnumSet.clone();
    cloneEnumSet.add(addEnum);
    return cloneEnumSet;

  }

}
