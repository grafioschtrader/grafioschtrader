package grafiosch.repository;

import jakarta.servlet.http.HttpServletResponse;

public interface TenantBaseCustom {

  void deleteMyDataAndUserAccount() throws Exception;

  void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception;
}
