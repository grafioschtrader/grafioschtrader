import {Observable} from 'rxjs';
import {FieldConfig} from '../../dynamic-form/models/field.config';

/**
 * Configuration parameter object for the file upload dialog component. This class encapsulates all settings
 * required to configure and customize the file upload dialog, including file type restrictions, CSV format
 * options, and the upload service to use.
 */
export class FileUploadParam {

  /**
   * Creates a new file upload parameter configuration.
   *
   * @param helpId - Identifier for context-sensitive help system, used to display relevant help content
   * @param additionalFieldConfig - Optional configuration for additional form fields beyond the standard file
   *                                upload control, allowing custom input fields and validation
   * @param acceptFileType - MIME type or file extension filter for file selection (e.g., ".csv", "image/*",
   *                         ".pdf,.doc"). Used by the browser's file picker to restrict selectable files
   * @param title - Translation key for the dialog header title, displayed at the top of the upload dialog
   * @param multiple - Whether to allow selection of multiple files (true) or restrict to single file (false)
   * @param uploadService - Service implementing the upload functionality, must provide an uploadFiles method
   *                        that accepts entity ID and FormData and returns an Observable
   * @param entityId - Unique identifier of the entity to which the uploaded files will be associated
   *                   (e.g., transaction ID, security ID)
   * @param supportedCSVFormats - Optional configuration for CSV file format settings (decimal/thousand
   *                              separators, date formats). When provided, adds format selection fields to the dialog
   * @param persistenceCSVKey - Optional key for persisting user's CSV format preferences in browser storage.
   *                            Used to restore previous format selections in subsequent uploads
   */
  public constructor(public helpId: string,
                     public additionalFieldConfig: AdditionalFieldConfig,
                     public acceptFileType: string,
                     public title: string,
                     public multiple: boolean,
                     public uploadService: UploadServiceFunction,
                     public entityId: number,
                     public supportedCSVFormats?: SupportedCSVFormats,
                     public persistenceCSVKey?: string
  ) {
  }
}

/**
 * Service interface for handling file upload operations. Implementations of this interface are responsible
 * for transferring files to the backend server via HTTP multipart/form-data requests.
 */
export interface UploadServiceFunction {
  /**
   * Uploads one or more files associated with a specific entity to the backend server.
   *
   * @param idTransactionHead - Unique identifier of the entity (e.g., transaction head ID) to associate
   *                            uploaded files with
   * @param formData - FormData object containing the file(s) and any additional parameters for the upload request
   * @returns Observable that emits the server response upon successful upload or error information on failure
   */
  uploadFiles(idTransactionHead: number, formData: FormData): Observable<any>;
}

/**
 * Configuration for additional form fields that can be included in the file upload dialog beyond the
 * standard file selection control. This allows dialogs to collect supplementary information from users
 * during the upload process.
 */
export class AdditionalFieldConfig {
  /**
   * Creates additional field configuration for the upload dialog.
   *
   * @param fieldConfig - Array of field definitions to be added to the upload form (e.g., text inputs,
   *                      dropdowns, checkboxes). These fields are rendered above the file upload control
   * @param submitPrepareFN - Callback function invoked before form submission to process additional field
   *                          values and append them to the FormData object. Receives current form values,
   *                          the FormData instance, and field configurations
   */
  constructor(public fieldConfig: FieldConfig[],
              public submitPrepareFN: (value: { [name: string]: any }, formData: FormData, fieldConfig: FieldConfig[]) => void) {
  }
}

/**
 * User-selected CSV format configuration for parsing uploaded CSV files. This class stores the user's
 * chosen format settings which are persisted to browser storage and reused in subsequent uploads for
 * improved user experience.
 */
export class SupportedCSVFormat {
  /** Character used to separate decimal places in numbers (e.g., "." for 1.23, "," for 1,23) */
  decimalSeparator: string = null;

  /** Character used to separate thousands in numbers (e.g., "," for 1,000, "." for 1.000, " " for 1 000) */
  thousandSeparator: string = null;

  /** Date format pattern string (e.g., "dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd") */
  dateFormat: string = null;
}

/**
 * Available CSV format options that the user can choose from in the upload dialog. These options define
 * the allowed separators and date formats for CSV parsing, enabling support for various regional CSV formats.
 */
export interface SupportedCSVFormats {
  /** Array of allowed thousand separator characters (e.g., [",", ".", " ", ""]) */
  thousandSeparators: string[];

  /** Array of supported date format patterns (e.g., ["dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd"]) */
  dateFormats: string[];

  /** Array of allowed decimal separator characters (e.g., [".", ","]) */
  decimalSeparators: string[];
}

/**
 * Result object returned by the server after uploading and processing historical quote data from CSV files.
 * Contains statistics about the import operation including success counts, validation errors, and duplicates.
 * This interface is specific to historical quote imports but represents a pattern for upload result reporting.
 */
export interface UploadHistoryquotesSuccess {
  /** Number of records successfully imported and saved to the database */
  success: number;

  /** Number of records that were not imported because they would override existing data with newer timestamps */
  notOverridden: number;

  /** Number of records that failed validation checks (e.g., invalid data types, missing required fields) */
  validationErrors: number;

  /** Number of records that appear multiple times within the uploaded file(s) and were deduplicated */
  duplicatedInImport: number;

  /** Number of records that fall outside the acceptable date range for the entity being imported */
  outOfDateRange: number;
}
