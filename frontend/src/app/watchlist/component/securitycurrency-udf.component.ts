import { Component, effect, untracked } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ContentBase, SecuritycurrencyBaseInfoFields } from './securitycurrency.base.info.fields';
import { Securitycurrency } from '../../entities/securitycurrency';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SecurityUDFHelper } from '../../securitycurrency/component/security.udf.helper';
import { Security } from '../../entities/security';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { AppSettings } from '../../shared/app.settings';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { OptionalParams } from '../../lib/datashowbase/column.config';
import { AppHelper } from '../../lib/helper/app.helper';
import { FieldDescriptorInputAndShowExtendedSecurity } from '../../udfmetasecurity/model/udf.metadata.security';

import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ReplacePipe } from '../../shared/pipe/replace.pipe';

/**
 * Shows all corresponding basic and additional fields grouped together.
 */
@Component({
  selector: 'securitycurrency-udf',
  templateUrl: '../view/securitycurrency.base.info.fields.html',
  imports: [TranslateModule, TooltipModule, ReplacePipe]
})
export class SecuritycurrencyUdfComponent extends SecuritycurrencyBaseInfoFields {
  protected readonly AppHelper = AppHelper;

  constructor() {
    super();
    effect(() => {
      this.securitycurrency();
      untracked(() => this.initializeContent());
    });
  }

  private initializeContent(): void {
    super.initializeFields();
    this.addUDFFields();
    this.translateHeadersAndColumns();
    this.content = new ContentUDF(this.securitycurrency());
    this.createTranslatedValueStore([this.content]);
  }

  private addUDFFields(): void {
    const fdSecurityList: FieldDescriptorInputAndShowExtendedSecurity[] =
      this.securitycurrency() instanceof CurrencypairWatchlist
        ? JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(
            (fd) => fd.entity === AppSettings.CURRENCYPAIR
          )
        : SecurityUDFHelper.getFieldDescriptorInputAndShowExtendedSecurity(
            (<Security>this.securitycurrency()).assetClass,
            true
          );
    fdSecurityList.forEach((fds) => {
      let dataType = DataType[fds.dataType];
      const optionalParams: OptionalParams = { fieldsetName: 'UDF' };
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
      let cc = this.addFieldProperty(dataType, this.SECURITYCURRENCY + fds.fieldName, fds.description, optionalParams);
      cc.headerTooltipTranslated = fds.descriptionHelp;
    });
  }
}

class ContentUDF extends ContentBase {
  constructor(securitycurrency: Securitycurrency) {
    super(securitycurrency);
  }
}
