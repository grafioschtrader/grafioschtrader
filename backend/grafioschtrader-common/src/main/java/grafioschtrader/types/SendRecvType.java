package grafioschtrader.types;

public enum SendRecvType {
  SEND('S'), RECEIVE('R');

  private final char value;

  private SendRecvType(char value) {
    this.value = value;
  }

  public char getValue() {
    return this.value;
  }

  public static SendRecvType getSendRecvType(char value) {
    return value == 'S' ? SEND : RECEIVE;
  }
}
