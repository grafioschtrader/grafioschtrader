import { GlobalparameterService } from '../service/globalparameter.service';

export class PlotlyLocales {

  public static setPlotyLocales(plotly: any, gps: GlobalparameterService): any {
    const config: any = {
      locale: gps.getLocale(),
      locales: {
        'de': {
          dictionary: {
            Autoscale: 'Automatische Skalierung',
            'Box Select': 'Rechteckauswahl',
            'Click to enter Colorscale title': 'Klicken, um den Farbskalatitel einzugeben',
            'Click to enter Component A title': 'Klicken, um den Titel der Komponente A einzugeben',
            'Click to enter Component B title': 'Klicken, um den Titel der Komponente B einzugeben',
            'Click to enter Component C title': 'Klicken, um den Titel der Komponente C einzugeben',
            'Click to enter Plot title': 'Klicken, um den Titel des Graphen einzugeben',
            'Click to enter X axis title': 'Klicken, um den Titel der X-Achse einzugeben',
            'Click to enter Y axis title': 'Klicken, um den Titel der Y-Achse einzugeben',
            'Compare data on hover': 'Über die Daten fahren, um sie zu vergleichen',
            'Double-click on legend to isolate one trace': 'Daten isolieren durch Doppelklick in der Legende',
            'Double-click to zoom back out': 'Herauszoomen durch Doppelklick',
            'Download plot as a png': 'Download als PNG',
            'Edit in Chart Studio': 'Im Chart Studio bearbeiten',
            'IE only supports svg.  Changing format to svg.': 'IE unterstützt nur SVG-Dateien.  Format wird zu SVG gewechselt.',
            'Lasso Select': 'Lassoauswahl',
            'Orbital rotation': 'Orbitalrotation',
            Pan: 'Verschieben',
            'Produced with Plotly': 'Erstellt mit Plotly',
            Reset: 'Zurücksetzen',
            'Reset axes': 'Achsen zurücksetzen',
            'Reset camera to default': 'Kamera auf Standard zurücksetzen',
            'Reset camera to last save': 'Kamera auf letzte Speicherung zurücksetzen',
            'Reset view': 'Ansicht zurücksetzen',
            'Reset views': 'Ansichten zurücksetzen',
            'Show closest data on hover': 'Zeige näheste Daten beim Überfahren',
            'Snapshot succeeded': 'Snapshot erfolgreich',
            'Sorry, there was a problem downloading your snapshot!': 'Es gab ein Problem beim Herunterladen des Snapshots',
            'Taking snapshot - this may take a few seconds': 'Erstelle einen Snapshot - dies kann einige Sekunden dauern',
            Zoom: 'Zoom',
            'Zoom in': 'Hineinzoomen',
            'Zoom out': 'Herauszoomen',
            'close:': 'Schluss:',
            trace: 'Datenspur',
            'lat:': 'Lat.:',
            'lon:': 'Lon.:',
            'q1:': 'q1:',
            'q3:': 'q3:',
            'source:': 'Quelle:',
            'target:': 'Ziel:',
            'lower fence:': 'Untere Schranke:',
            'upper fence:': 'Obere Schranke:',
            'max:': 'Max.:',
            'mean ± σ:': 'Mittelwert ± σ:',
            'mean:': 'Mittelwert:',
            'median:': 'Median:',
            'min:': 'Min.:',
            'Turntable rotation': 'Drehscheibenorbit',
            'Toggle Spike Lines': 'Bezugslinien an-/abschalten',
            'open:': 'Eröffnung:',
            'high:': 'Höchstkurs:',
            'low:': 'Tiefstkurs:',
            'Toggle show closest data on hover': 'Anzeige der nähesten Daten an-/abschalten',
            'incoming flow count:': 'Anzahl eingehender Verbindungen:',
            'outgoing flow count:': 'Anzahl ausgehender Verbindungen:',
            'kde:': 'Dichte:'
          },

          format: {
            days: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
            shortDays: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
            months: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
            shortMonths: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
            date: '%d.%m.%Y',
            decimal: ',',
            thousands: '.'
          }
        },
        'de-CH': {
          dictionary: {},
          format: {
            days: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
            shortDays: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
            months: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
            shortMonths: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
            date: '%d.%m.%Y',
            decimal: '.',
            thousands: '\''
          }
        }
      }
    };

    return config;
  }
}
