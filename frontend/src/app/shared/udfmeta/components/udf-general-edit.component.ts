import {BaseUDFDataEdit} from './base.udf.data.edit';
import {Component, OnInit} from '@angular/core';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFDataService} from '../service/udf.data.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {HelpIds} from '../../help/help.ids';
import {AppHelper} from '../../helper/app.helper';
import {SecurityUDFHelper} from '../../../securitycurrency/component/security.udf.helper';
import {GlobalSessionNames} from '../../global.session.names';
import {UDFMetadataHelper} from './udf.metadata.helper';

/**
 * Edit the content of user-defined fields. This editing is for information classes without a specific extension.
 */
@Component({
  selector: 'udf-general-edit',
  templateUrl: '../view/general.udf.data.edit.html'
})
export class UDFGeneralEditComponent extends BaseUDFDataEdit implements OnInit {

  constructor(
    messageToastService: MessageToastService,
    uDFDataService: UDFDataService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(messageToastService, uDFDataService, translateService, HelpIds.HELP_WATCHLIST_UDF, gps);

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
  }

  ngOnInit(): void {
    super.baseInit(UDFMetadataHelper.getFieldDescriptorByEntity(this.uDFGeneralCallParam.entityName));
  }

}
