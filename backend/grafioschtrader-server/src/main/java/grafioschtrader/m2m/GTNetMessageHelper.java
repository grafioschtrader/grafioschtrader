package grafioschtrader.m2m;

import grafioschtrader.service.GlobalparametersService;

public abstract class GTNetMessageHelper {

  public static Integer getGTNetMyEntryIDOrThrow(GlobalparametersService globalparametersService) {
    Integer myIdGtNet = globalparametersService.getGTNetMyEntryID();
    if (myIdGtNet == null) {
      throw new IllegalArgumentException("Your machine does not have an entry!");
    }
    return globalparametersService.getGTNetMyEntryID();
  }
}
