import {HelpIds} from '../../help/help.ids';
import {Observable} from 'rxjs';
import {FieldConfig} from '../../../dynamic-form/models/field.config';

export class FileUploadParam {

  public constructor(public helpId: HelpIds,
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

export interface UploadServiceFunction {
  uploadFiles(idTransactionHead: number, formData: FormData): Observable<any>;
}

export class AdditionalFieldConfig {
  constructor(public fieldConfig: FieldConfig[],
              public submitPrepareFN: (value: { [name: string]: any }, formData: FormData, fieldConfig: FieldConfig[]) => void) {
  }
}

export class SupportedCSVFormat {
  decimalSeparator: string = null;
  thousandSeparator: string = null;
  dateFormat: string = null;
}

export interface SupportedCSVFormats {
  thousandSeparators: string[];
  dateFormats: string[];
  decimalSeparators: string[];
}

export interface UploadHistoryquotesSuccess {
  success: number;
  notOverridden: number;
  validationErrors: number;
  duplicatedInImport: number;
  outOfDateRange: number;
}
