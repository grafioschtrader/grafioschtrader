import {Component, OnInit} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {UDFDataService} from '../../lib/udfmeta/service/udf.data.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {Assetclass} from '../../entities/assetclass';
import {SecurityUDFHelper} from './security.udf.helper';
import {BaseUDFDataEdit} from '../../lib/udfmeta/components/base.udf.data.edit';
import {Security} from '../../entities/security';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Allows you to edit additional fields for the instruments. The input fields are created entirely from metadata.
 * The input fields offered may vary depending on the asset class and the financial instrument.
 */
@Component({
    selector: 'udf-security-edit',
    templateUrl: '../../lib/udfmeta/view/general.udf.data.edit.html',
    standalone: true,
    imports: [
      TranslateModule,
      DialogModule,
      DynamicFormComponent
    ]
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
