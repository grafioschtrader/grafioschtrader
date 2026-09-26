package grafioschtrader.tools;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.*;

import org.junit.jupiter.api.Test;

/** The audit must neither bootstrap an application nor commit/write while scanning legacy rows. */
class YamlConfigurationAuditTest {
  @Test
  void scansReadOnlyAndRedactsMalformedSource() throws Exception {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet rows = mock(ResultSet.class);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getInt(1)).thenReturn(17);
    when(rows.getString(2)).thenReturn("secret: [confidential");
    var bytes = new ByteArrayOutputStream();
    assertThat(YamlConfigurationAudit.audit(connection, new PrintStream(bytes))).isEqualTo(1);
    assertThat(bytes.toString()).contains("id=17", "SYNTAX", "line=").doesNotContain("confidential");
    var ordered = inOrder(connection, statement);
    ordered.verify(connection).setReadOnly(true);
    ordered.verify(connection).setAutoCommit(false);
    ordered.verify(connection).createStatement();
    ordered.verify(statement).executeQuery(startsWith("SELECT "));
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(statement, never()).executeUpdate(anyString());
  }
}
