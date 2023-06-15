export class ValueKeyHtmlSelectOptions {
  disabled: boolean;

  constructor(public key: number | string, public value: string) {
  }
}

export class GroupItem {
  disabled: boolean;
  children: GroupItem[];

  /**
   *  Description of the constructor.
   *
   * @param key Key which is used intern
   * @param value This value is shown in the dropdown box
   * @param optionsText This value is shown in the list of options
   * @param img Options may have a leading image
   */
  constructor(public key: number | string, public value: string, public optionsText: string, public img: string) {
  }
}
