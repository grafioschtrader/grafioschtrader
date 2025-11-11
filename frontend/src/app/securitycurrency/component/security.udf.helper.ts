import {Assetclass} from '../../entities/assetclass';

import {GlobalSessionNames} from '../../lib/global.session.names';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {FieldDescriptorInputAndShowExtendedSecurity} from '../../udfmetasecurity/model/udf.metadata.security';

export class SecurityUDFHelper {
  /**
   * Return of the definition of user-defined fields according to the asset class.
   * @param assetclass The asset class on the basis of which the definitions are compiled.
   * @param includeEveryUser The definition with user ID 0 can be applied to all users. If true, these are also returned.
   */
  public static getFieldDescriptorInputAndShowExtendedSecurity(assetclass: Assetclass, includeEveryUser: boolean):
    FieldDescriptorInputAndShowExtendedSecurity[] {
    return JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)).filter(
      (fd: FieldDescriptorInputAndShowExtendedSecurity) => (includeEveryUser || !includeEveryUser && fd.idUser !== 0 ) &&
        (fd.specialInvestmentInstrumentEnums.indexOf(<SpecialInvestmentInstruments>assetclass.specialInvestmentInstrument) >= 0
        &&  fd.categoryTypeEnums.indexOf(<AssetclassType>assetclass.categoryType) >= 0))
  }
}
