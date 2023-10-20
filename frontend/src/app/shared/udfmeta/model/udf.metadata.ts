import {DataType} from '../../../dynamic-form/models/data.type';
import {AssetclassType} from '../../types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../types/special.investment.instruments';
import {BaseID} from '../../../entities/base.id';
import {Exclude} from 'class-transformer';

export abstract class UDFMetadata implements BaseID {
  idUDFMetadata: number = null;
  idUser: number = null;
  description: string = null;
  descriptionHelp: string = null;
  udfDataType: UDFDataType = null;
  fieldSize: string = null;
  uiOrder: number = null;

  @Exclude()
  getId(): number {
    return this.idUDFMetadata;
  }
}

export class UDFMetadataGeneral extends UDFMetadata {
  entity: string = null;
}

export class UDFMetadataSecurity extends UDFMetadata  {
  categoryType: AssetclassType | string = null;
  specialInvestmentInstrument: SpecialInvestmentInstruments | string = null;
}


export class UDFMetadataSecurityParam {
  uDFMetadataSecurity: UDFMetadataSecurity
  excludeFieldNames: string[];
  excludeUiOrders: number[]
}

export enum UDFDataType {
  UDF_Numeric = 1,
  UDF_NumericInteger= 4,
  UDF_String = 7,
  UDF_DateTimeNumeric = 8,
  UDF_DateString = 10,
  UDF_Boolean= 13,
  UDF_URLString= 20
}
