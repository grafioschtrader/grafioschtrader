import {Component, OnInit} from '@angular/core';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ContentBase, SecuritycurrencyBaseInfoFields} from './securitycurrency.base.info.fields';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityUDFHelper} from '../../securitycurrency/component/security.udf.helper';
import {Security} from '../../entities/security';
import {FieldDescriptorInputAndShowExtendedSecurity} from '../../shared/udfmeta/model/udf.metadata';
import {GlobalSessionNames} from '../../shared/global.session.names';
import {AppSettings} from '../../shared/app.settings';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {OptionalParams} from '../../shared/datashowbase/column.config';


/**
 * Shows all corresponding basic and additional fields grouped together.
 */
@Component({
  selector: 'securitycurrency-udf',
  templateUrl: '../view/securitycurrency.base.info.fields.html'
})
export class SecuritycurrencyUdfComponent extends SecuritycurrencyBaseInfoFields implements OnInit {

  constructor(securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(securityService, translateService, gps);
  }

  ngOnInit(): void {
    super.initializeFields();
    this.addUDFFields();
    this.translateHeadersAndColumns();
    this.content = new ContentUDF(this.securitycurrency)
    this.createTranslatedValueStore([this.content]);
  }

  private addUDFFields(): void {
    const fdSecurityList: FieldDescriptorInputAndShowExtendedSecurity[] = (this.securitycurrency instanceof CurrencypairWatchlist) ?
      JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(fd =>
        fd.entity === AppSettings.CURRENCYPAIR) :
      SecurityUDFHelper.getFieldDescriptorInputAndShowExtendedSecurity((<Security>this.securitycurrency).assetClass, true);
    fdSecurityList.forEach(fds => {
      const optionalParams: OptionalParams = {fieldsetName: 'UDF'}
      switch (DataType[fds.dataType]) {
        case DataType.URLString:
        case DataType.String:
          optionalParams.templateName = 'long';
          break;
      }
      let cc = this.addFieldProperty(DataType[fds.dataType], this.SECURITYCURRENCY + fds.fieldName, fds.description,
        optionalParams);
      cc.headerTooltipTranslated = fds.descriptionHelp;
    });
  }

}


class ContentUDF extends ContentBase {
  constructor(securitycurrency: Securitycurrency) {
    super(securitycurrency);
  }
}
