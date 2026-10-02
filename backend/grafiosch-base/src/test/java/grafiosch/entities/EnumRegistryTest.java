package grafiosch.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.types.IBaseEnum;

class EnumRegistryTest {

  private enum BaseKind implements IBaseEnum<Byte> {
    FIRST((byte) 1), SECOND((byte) 2);

    private final Byte value;

    BaseKind(Byte value) {
      this.value = value;
    }

    @Override
    public Byte getValue() {
      return value;
    }

    @Override
    public Enum<?>[] getValues() {
      return values();
    }
  }

  private enum ExtendedKind implements IBaseEnum<Byte> {
    THIRD((byte) 3), CLASH((byte) 2);

    private final Byte value;

    ExtendedKind(Byte value) {
      this.value = value;
    }

    @Override
    public Byte getValue() {
      return value;
    }

    @Override
    public Enum<?>[] getValues() {
      return values();
    }
  }

  @Test
  @DisplayName("Registering the same constants again, as a second application startup in one JVM does, is a no-op")
  void registeringTheSameConstantsAgainIsIdempotent() {
    EnumRegistry<Byte, IBaseEnum<Byte>> registry = new EnumRegistry<>(BaseKind.values());
    registry.addTypes(BaseKind.values());
    registry.addTypes(new ExtendedKind[] { ExtendedKind.THIRD });
    registry.addTypes(new ExtendedKind[] { ExtendedKind.THIRD });
    assertThat(registry.getTypeByValue((byte) 2)).isEqualTo(BaseKind.SECOND);
    assertThat(registry.getTypeByValue((byte) 3)).isEqualTo(ExtendedKind.THIRD);
  }

  @Test
  @DisplayName("A different constant with a value already taken is still rejected")
  void differentConstantWithTakenValueIsRejected() {
    EnumRegistry<Byte, IBaseEnum<Byte>> registry = new EnumRegistry<>(BaseKind.values());
    assertThatThrownBy(() -> registry.addTypes(new ExtendedKind[] { ExtendedKind.CLASH }))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("CLASH").hasMessageContaining("SECOND");
  }
}
