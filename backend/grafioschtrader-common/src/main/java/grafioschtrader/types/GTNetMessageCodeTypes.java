package grafioschtrader.types;

public enum GTNetMessageCodeTypes {
  LASTPRICE_REQUET((short) 0),
  LASTPRICE_ACCEPT((short) 1),
  LASTPRICE_REFUSED((short) 2),
  ENTITY_REQUEST((short) 3),
  BOTH_REQUEST((short) 4);
  
  private final short value;

  private GTNetMessageCodeTypes(short value) {
    this.value = value;
  }

  public Short getValue() {
    return this.value;
  }

  public static GTNetMessageCodeTypes getGTNetMessageCodeTypes(short value) {
    for (GTNetMessageCodeTypes gtNetMessageCodeTypes : GTNetMessageCodeTypes.values()) {
      if (gtNetMessageCodeTypes.getValue() == value) {
        return gtNetMessageCodeTypes;
      }
    }
    return null;
  }
}
