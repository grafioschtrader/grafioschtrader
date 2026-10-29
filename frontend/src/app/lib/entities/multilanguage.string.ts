// We don't use a Map, because JSON.stringify does not work
export class MultilanguageString {
  map: MultilanguageStrings = new MultilanguageStrings();
}

export class MultilanguageStrings {
  [language: string]: string;
  de: string;
  en: string;
}
