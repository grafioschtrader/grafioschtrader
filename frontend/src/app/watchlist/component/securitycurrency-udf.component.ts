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
import {OptionalParams} from '../../lib/datashowbase/column.config';
import {WatchlistService} from '../service/watchlist.service';


/**
 * Shows all corresponding basic and additional fields grouped together.
 */
@Component({
    selector: 'securitycurrency-udf',
    templateUrl: '../view/securitycurrency.base.info.fields.html',
    standalone: false
})
export class SecuritycurrencyUdfComponent extends SecuritycurrencyBaseInfoFields implements OnInit {

  constructor(watchlistService: WatchlistService,
    securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(watchlistService, securityService, translateService, gps);
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
      let dataType = DataType[fds.dataType];
      const optionalParams: OptionalParams = {fieldsetName: 'UDF'}
      switch (DataType[fds.dataType]) {
        case DataType.Boolean:
          optionalParams.templateName = 'check';
          break;
        case DataType.URLString:
        case DataType.String:
          optionalParams.templateName = 'long';
          break;
        case DataType.DateTimeNumeric:
          dataType = DataType.DateTimeString;
          break;
      }
      let cc = this.addFieldProperty(dataType, this.SECURITYCURRENCY + fds.fieldName, fds.description,
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
