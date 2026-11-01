import { describe, expect, it } from 'vitest';
import { UDFDataType, UDFMetadataGeneral } from '../model/udf.metadata';
import { UDFMetadataHelper } from './udf.metadata.helper';

describe('UDF field size of fixed-size data types', () => {
  it('recognises a fixed-size type by its form value (name) and by its entity value', () => {
    expect(UDFMetadataHelper.hasFixedFieldSize(UDFDataType[UDFDataType.UDF_Boolean])).toBe(true);
    expect(UDFMetadataHelper.hasFixedFieldSize(UDFDataType.UDF_DateString)).toBe(true);
    expect(UDFMetadataHelper.hasFixedFieldSize(UDFDataType[UDFDataType.UDF_String])).toBe(false);
    expect(UDFMetadataHelper.hasFixedFieldSize(null)).toBe(false);
  });

  it('clears a field size the signal form kept while the field was locked', () => {
    const fixed = new UDFMetadataGeneral();
    fixed.udfDataType = UDFDataType.UDF_URLString;
    fixed.fieldSize = '20';
    UDFMetadataHelper.clearFixedFieldSize(fixed);
    expect(fixed.fieldSize).toBeNull();

    const sized = new UDFMetadataGeneral();
    sized.udfDataType = UDFDataType.UDF_String;
    sized.fieldSize = '20';
    UDFMetadataHelper.clearFixedFieldSize(sized);
    expect(sized.fieldSize).toBe('20');
  });
});
