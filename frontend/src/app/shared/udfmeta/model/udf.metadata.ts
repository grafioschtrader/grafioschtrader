import {AssetclassType} from '../../types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../types/special.investment.instruments';
import {BaseID} from '../../../lib/entities/base.id';
import {Exclude} from 'class-transformer';
import {FieldDescriptorInputAndShowExtended} from '../../../lib/dynamicfield/field.descriptor.input.and.show';

export abstract class UDFMetadata implements BaseID {
  idUDFMetadata: number = null;
  idUser: number = null;
  udfSpecialType: UDFSpecialType| number;
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

export enum UDFSpecialType {
  UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY = 1,
  UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK = 2,
  UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE = 3,
  UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE = 4,
  UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK = 5
}

export class UDFMetadataGeneral extends UDFMetadata {
  entity: string = null;
}

export class UDFMetadataSecurity extends UDFMetadata {
  categoryTypeEnums: AssetclassType[] = [];
  specialInvestmentInstrumentEnums: SpecialInvestmentInstruments[] = [];
}

export class UDFMetadataParam {
  excludeFieldNames: string[];
  excludeUiOrders: number[];
}

export class UDFMetadataSecurityParam extends UDFMetadataParam {
  uDFMetadataSecurity: UDFMetadataSecurity
}

export class UDFMetadataGeneralParam extends UDFMetadataParam {
  uDFMetadataGeneral: UDFMetadataGeneral
}

export class UDFData {
  constructor(public uDFDataKey: UDFDataKey, public jsonValues: any) {
  }
}

export class UDFDataKey {
  constructor(public idUser: number, public entity: string, public idEntity: number) {
  }
}

export interface FieldDescriptorInputAndShowExtendedSecurity extends FieldDescriptorInputAndShowExtended {
  categoryTypeEnums: AssetclassType[];
  specialInvestmentInstrumentEnums: SpecialInvestmentInstruments[];
}

export interface FieldDescriptorInputAndShowExtendedGeneral extends FieldDescriptorInputAndShowExtended {
  entity: string;
}

export class UDFGeneralCallParam {
  constructor(public entityName: string, public selectedEntity: BaseID, public udfData: UDFData, public titleKey: string) {
  }
}

export class UDFSpecialTypeDisableUserId
{
  idUser: number;
  udfSpecialType: UDFSpecialType;
}

export class UDFSpecialTypeDisableUser {
  id: UDFSpecialTypeDisableUserId;
}

export enum UDFDataType {
  UDF_Numeric = 1,
  UDF_NumericInteger = 4,
  UDF_String = 7,
  UDF_DateTimeNumeric = 8,
  UDF_DateString = 10,
  UDF_Boolean = 13,
  UDF_URLString = 20
}
