package grafiosch.exportdelete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Guards the one thing that makes an export re-importable: a {@code tinyint(1)} column holding an enum ordinal must be
 * exported as its number.
 *
 * <p>
 * MariaDB reports such a column as {@link Types#BOOLEAN} and its driver hands {@code getObject} a
 * {@code java.lang.Boolean}, so 1 and 2 both arrive as {@code true} and the generated INSERT wrote the literal
 * {@code true} for both. On {@code gt_net_entity} that made two rows of the same peer collide on
 * {@code UQ_gt_net_entity_kind}; on {@code historyquote.create_type} it silently rewrote the origin of every quote.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Export of tinyint(1) columns holding enum ordinals")
class MySqlInsertStatementGeneratorTest {

  private static final String TABLE = "gt_net_entity";
  private static final String[] COLUMNS = { "id_gt_net_entity", "id_gt_net", "entity_kind" };

  @Mock
  private ResultSet resultSet;

  @Mock
  private ResultSetMetaData metaData;

  @Test
  @DisplayName("Reads entity_kind 2 as the number 2, not as true")
  void readsEnumOrdinalAsNumber() throws SQLException {
    givenGtNetEntityMetaData();
    when(resultSet.getObject(1)).thenReturn(17);
    when(resultSet.getObject(2)).thenReturn(9);
    when(resultSet.getInt(3)).thenReturn(2);
    when(resultSet.wasNull()).thenReturn(false);

    Map<String, Object> row = MySqlInsertStatementGenerator.EXPORT_ROW_MAPPER.mapRow(resultSet, 0);

    assertThat(row).containsEntry("entity_kind", 2);
  }

  @Test
  @DisplayName("A NULL in a tinyint(1) column stays NULL")
  void keepsNullOfBooleanColumn() throws SQLException {
    givenGtNetEntityMetaData();
    when(resultSet.getObject(1)).thenReturn(17);
    when(resultSet.getObject(2)).thenReturn(9);
    when(resultSet.getInt(3)).thenReturn(0);
    when(resultSet.wasNull()).thenReturn(true);

    Map<String, Object> row = MySqlInsertStatementGenerator.EXPORT_ROW_MAPPER.mapRow(resultSet, 0);

    assertThat(row).containsEntry("entity_kind", null);
  }

  @Test
  @DisplayName("The three exchange kinds of one peer produce three distinct INSERT values")
  void writesDistinctEntityKindsPerPeer() throws SQLException {
    givenGtNetEntityMetaData();
    List<Map<String, Object>> rows = List.of(rowOf(15, 9, 0), rowOf(16, 9, 1), rowOf(17, 9, 2));

    String sql = MySqlInsertStatementGenerator.createInsertStatements(TABLE, metaData, rows).toString();

    assertThat(sql).contains("(15, 9, 0)").contains("(16, 9, 1)").contains("(17, 9, 2)");
    assertThat(sql).doesNotContain("true").doesNotContain("false");
  }

  private Map<String, Object> rowOf(int idGtNetEntity, int idGtNet, int entityKind) {
    return Map.of(COLUMNS[0], idGtNetEntity, COLUMNS[1], idGtNet, COLUMNS[2], entityKind);
  }

  /** {@code entity_kind} is the {@code tinyint(1)} the driver reports as {@link Types#BOOLEAN}. */
  private void givenGtNetEntityMetaData() throws SQLException {
    when(metaData.getColumnCount()).thenReturn(COLUMNS.length);
    for (int index = 1; index <= COLUMNS.length; index++) {
      when(metaData.getColumnName(index)).thenReturn(COLUMNS[index - 1]);
      when(metaData.getColumnLabel(index)).thenReturn(COLUMNS[index - 1]);
      when(metaData.getColumnType(index)).thenReturn(index == COLUMNS.length ? Types.BOOLEAN : Types.INTEGER);
    }
    when(resultSet.getMetaData()).thenReturn(metaData);
  }
}
