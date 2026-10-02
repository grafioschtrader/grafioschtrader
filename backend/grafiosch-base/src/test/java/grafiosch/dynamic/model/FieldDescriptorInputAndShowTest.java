package grafiosch.dynamic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FieldDescriptorInputAndShowTest {
  @Test
  void primitiveAndBoxedIntegerProduceNumericInputs() {
    assertEquals(DataType.NumericInteger, new FieldDescriptorInputAndShow("count", int.class).dataType);
    assertEquals(DataType.NumericInteger, new FieldDescriptorInputAndShow("count", Integer.class).dataType);
  }
}
