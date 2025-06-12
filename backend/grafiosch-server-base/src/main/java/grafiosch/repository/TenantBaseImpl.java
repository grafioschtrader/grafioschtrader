package grafiosch.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.exportdelete.MySqlDeleteMyData;
import grafiosch.exportdelete.MySqlExportMyData;
import grafiosch.rest.helper.RestHelper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Abstract base implementation for tenant-specific data operations. Provides concrete implementations for data deletion
 * and export functionality.
 * 
 * @param <T> the entity type handled by this repository
 */
public abstract class TenantBaseImpl<T> extends BaseRepositoryImpl<T> implements TenantBaseCustom {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Value("${gt.demo.account.pattern.de}")
  private String demoAccountPatternDE;

  @Value("${gt.demo.account.pattern.en}")
  private String demoAccountPatternEN;

  @Autowired
  private ResourceLoader resourceLoader;

  @Override
  public void deleteMyDataAndUserAccount() throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    RestHelper.isDemoAccount(demoAccountPatternDE, user.getUsername());
    RestHelper.isDemoAccount(demoAccountPatternEN, user.getUsername());

    MySqlDeleteMyData mySqlDeleteMyData = new MySqlDeleteMyData(jdbcTemplate, user);
    mySqlDeleteMyData.deleteMyData();
  }

  /**
   * Exports personal data for the currently authenticated user as a ZIP file. The ZIP file contains two SQL scripts:
   * <ol>
   * <li>A DDL (Data Definition Language) script (e.g., "gt_ddl.sql") for table structures.</li>
   * <li>A DML (Data Manipulation Language) script ("gt_data.sql") containing the user's personal data as INSERT
   * statements.</li>
   * </ol>
   * The method retrieves the user from the security context, generates the DML data using
   * MySqlExportMyData#exportDataMyData(), and then streams the DDL file and the generated DML data into a ZIP file
   * directly to the {@link HttpServletResponse}.
   *
   * @param response The {@link HttpServletResponse} to which the ZIP file will be written. The response headers will be
   *                 set for a file attachment named "gt.zip".
   * @throws Exception if any error occurs during data export, resource loading, ZIP stream creation, or writing to the
   *                   response stream. This can include {@link java.io.IOException} during stream operations or
   *                   exceptions from {@link MySqlExportMyData#exportDataMyData()}.
   */
  @Override
  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    String ddlFileName = "gt_ddl.sql";
    Resource resourceDdl = resourceLoader.getResource("classpath:db/migration/" + ddlFileName);

    StringBuilder sqlStatement = new MySqlExportMyData(jdbcTemplate, user).exportDataMyData();

    // setting headers
    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Content-Disposition", "attachment; filename=\"gt.zip\"");

    ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
    addZipEntry(zipOutputStream, resourceDdl.getInputStream(), ddlFileName);
    InputStream dmlInputStream = new ByteArrayInputStream(sqlStatement.toString().getBytes());
    addZipEntry(zipOutputStream, dmlInputStream, "gt_data.sql");
    zipOutputStream.close();
  }

  /**
   * Adds a new entry to the ZIP output stream from an input stream. Reads data from the input stream and writes it to
   * the ZIP entry using a buffer.
   * 
   * @param zos       the ZIP output stream to write to
   * @param in        the input stream to read data from
   * @param entryName the name of the ZIP entry
   * @throws IOException if an I/O error occurs during reading or writing
   */
  private void addZipEntry(ZipOutputStream zos, InputStream in, String entryName) throws IOException {
    byte buffer[] = new byte[16384];
    zos.putNextEntry(new ZipEntry(entryName));
    int length;
    while ((length = in.read(buffer)) >= 0) {
      zos.write(buffer, 0, length);
    }
    in.close();
    zos.closeEntry();
  }
}
