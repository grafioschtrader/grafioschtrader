/**
 * Optimus UI widget locale data, kept on the client on purpose.
 *
 * These texts label Optimus UI's own filter and calendar widgets rather than anything Grafioschtrader owns, so a
 * second frontend built on another technology has no use for them. Two properties of the data also make a backend
 * .properties file the wrong container: five German entries are string ARRAYS (day and month names), which .properties
 * cannot express, and the German set is larger than the English one, which would fail the EN/DE parity guard for a
 * reason that is not a missing translation.
 *
 * Merged last by MultiTranslateHttpLoader, so LoginService.setGlobalLang() keeps resolving
 * translateService.get('optimus') into the object Optimus UI's setTranslation() expects.
 *
 * See GitHub issue #214.
 */
export const OPTIMUS_TRANSLATIONS: Record<string, {optimus: Record<string, string | string[]>}> = {
  en: {
    optimus: {
      startsWith: "Starts with",
      contains: "Contains",
      notContains: "Not contains",
      endsWith: "Ends with",
      equals: "Equals",
      notEquals: "Not equals",
      noFilter: "No Filter",
      lt: "Less than",
      lte: "Less than or equal to",
      gt: "Greater than",
      gte: "Great then or equals",
      is: "Is",
      isNot: "Is not",
      before: "Before",
      after: "After",
      clear: "Clear",
      apply: "Apply",
      matchAll: "Match All",
      matchAny: "Match Any",
      addRule: "Add Rule",
      removeRule: "Remove Rule",
      accept: "Yes",
      reject: "No",
      choose: "Choose",
      upload: "Upload",
      cancel: "Cancel",
      weekHeader: "Wk",
      selectionMessage: "{0} items selected",
      emptyMessage: "No available options",
      emptyFilterMessage: "No results found"
    }
  },
  de: {
    optimus: {
      startsWith: "Startet mit",
      contains: "Enthält",
      notContains: "Enthält nicht",
      endsWith: "Endet mit",
      equals: "Gleich",
      notEquals: "Ungleich",
      noFilter: "Kein Filter",
      lt: "Kleiner als",
      lte: "Kleiner oder gleich",
      gt: "Grösser als",
      gte: "Grösser oder gleich",
      is: "Ist",
      isNot: "Ist nicht",
      before: "Vor",
      after: "Nach",
      clear: "Löschen",
      apply: "Anwenden",
      matchAll: "UND Bedingung",
      matchAny: "ODER Bedingung",
      addRule: "Regel hinzufügen",
      removeRule: "Regel löschen",
      accept: "Ja",
      reject: "Nein",
      choose: "Wähle",
      upload: "Upload",
      cancel: "Abbrechen",
      dayNames: [
        "Sonntag",
        "Montag",
        "Dienstag",
        "Mittwoch",
        "Donnerstag",
        "Freitag",
        "Samstag"
      ],
      dayNamesShort: [
        "So",
        "Mo",
        "Di",
        "Mi",
        "Do",
        "Fr",
        "Sa"
      ],
      dayNamesMin: [
        "So",
        "Mo",
        "Di",
        "Mi",
        "Do",
        "Fr",
        "Sa"
      ],
      monthNames: [
        "Januar",
        "Februar",
        "März",
        "April",
        "Mai",
        "Juni",
        "Juli",
        "August",
        "September",
        "Oktober",
        "November",
        "Dezember"
      ],
      monthNamesShort: [
        "Jan",
        "Feb",
        "Mär",
        "Apr",
        "Mai",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Okt",
        "Nov",
        "Dez"
      ],
      today: "Heute",
      weekHeader: "Wk",
      selectionMessage: "{0} Elemente ausgewählt",
      emptyMessage: "Keine Resultat gefunden",
      emptyFilterMessage: "Keine Resultat gefunden"
    }
  }
};
