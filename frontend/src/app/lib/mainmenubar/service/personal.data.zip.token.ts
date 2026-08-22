import { InjectionToken } from '@angular/core';

/**
 * Injection token for the file name under which the personal data export archive is saved. The same name is shown
 * in the tooltip of the corresponding menu item (NLS key EXPORT_DATA_SQL_TITLE).
 *
 * The lib layer provides an application-neutral default, matching BaseConstants.PERSONAL_DATA_ZIP_FILENAME of the
 * backend library. The application layer can provide its own name (e.g. 'gtPersonalData.zip' in Grafioschtrader).
 */
export const PERSONAL_DATA_ZIP_NAME = new InjectionToken<string>('PersonalDataZipName', {
  providedIn: 'root',
  factory: () => 'personalData.zip'
});
