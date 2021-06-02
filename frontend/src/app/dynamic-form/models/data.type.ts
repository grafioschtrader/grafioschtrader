/**
 * This types are used for the html table as well for the form input.
 *
 */
export enum DataType {
  /**
   * Input: For buttons
   */
  None = 0,

  /**
   * Input, Table: Decimal
   */
  Numeric = 1,

  /**
   * Show Zero, Input, Table: Decimal
   */
  NumericShowZero = 2,

  /**
   * Input: Show all
   */
  NumericRaw = 3,

  /**
   * Table: Integer
   */
  NumericInteger = 4,

  /**
   * Input: Password
   */
  Password = 5,

  /**
   * Input: eMail.
   */
  Email = 6,

  /**
   * Input, Table: String
   */
  String = 7,

  /**
   * Input, Table: By the server, the date is represented as timestamp.
   */
  DateTimeNumeric = 8,

  /**
   * Input, Table: By the server, the date is represented as timestamp with time set to zero.
   */
  DateNumeric = 9,

  /**
   * Table: By the server, the date is represented as string in the format "yyyy-mm-dd"
   */
  DateString = 10,

  /**
   * Input: By the server, the date is represented as string in the format "yyyymmdd"
   */
  DateStringShortUS = 11,

  /**
   * Input, Table: Only Time as string
   */
  TimeString = 12,

  /**
   * Input, Table: Boolean
   */
  Boolean = 13,

  /**
   * Input: Single file upload
   */
  File = 14,

  /**
   * Input: Multiple file upload
   */
  Files = 15,

  DateTimeString = 16,

  DateTimeSecondString = 17
}
