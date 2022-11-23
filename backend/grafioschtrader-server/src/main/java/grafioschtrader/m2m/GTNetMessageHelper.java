package grafioschtrader.m2m;

import grafioschtrader.repository.GlobalparametersJpaRepository;

public abstract class GTNetMessageHelper {
  
  
  public static Integer getGTNetMyEntryIDOrThrow(GlobalparametersJpaRepository gp) {
    Integer myIdGtNet = gp.getGTNetMyEntryID();
    if (myIdGtNet == null) {
      throw new IllegalArgumentException("Your machine does not have an entry!");
    }
    return gp.getGTNetMyEntryID();
  }
}
