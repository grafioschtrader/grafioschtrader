import {Component, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {UDFDataService} from '../../shared/udfmeta/service/udf.data.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {Assetclass} from '../../entities/assetclass';
import {SecurityUDFHelper} from './security.udf.helper';
import {BaseUDFDataEdit} from '../../shared/udfmeta/components/base.udf.data.edit';
import {Security} from '../../entities/security';

/**
 * Allows you to edit additional fields for the instruments. The input fields are created entirely from metadata.
 * The input fields offered may vary depending on the asset class and the financial instrument.
 */
@Component({
  selector: 'udf-security-edit',
  templateUrl: '../../shared/udfmeta/view/general.udf.data.edit.html'
})
export class SecurityUDFEditComponent extends BaseUDFDataEdit implements OnInit {

  constructor(
    messageToastService: MessageToastService,
    uDFDataService: UDFDataService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(messageToastService, uDFDataService, translateService, HelpIds.HELP_WATCHLIST_UDF, gps);

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
  }

  ngOnInit(): void {
    const assetclass: Assetclass = (<Security>this.uDFGeneralCallParam.selectedEntity).assetClass;
    super.baseInit(SecurityUDFHelper.getFieldDescriptorInputAndShowExtendedSecurity(assetclass, false))
  }

}
