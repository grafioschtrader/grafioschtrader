import {Component, OnDestroy} from '@angular/core';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {AppSettings} from '../../app.settings';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {UserSettingsService} from '../../service/user.settings.service';
import {UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {HelpIds} from '../../help/help.ids';
import {UDFMetaTable} from './udf.metadata.table';
import {GlobalSessionNames} from '../../global.session.names';
import {UDFSpecialTypeDisableUserService} from '../service/udf.special.type.disable.user.service';

/**
 * Custom fields of security is a special implementation, as these are defined according to the asset class.
 * Therefore, there is also a customized table for the tabular display.
 */
@Component({
    templateUrl: '../view/udf.metadata.table.html',
    providers: [DialogService],
    standalone: false
})
export class UDFMetadataSecurityTableComponent extends UDFMetaTable<UDFMetadataSecurity> implements OnDestroy {

  callParam: UDFMetadataSecurityParam = new UDFMetadataSecurityParam();
  isSecurityEdit = true;

  constructor(udfSpecialTypeDisableUserService: UDFSpecialTypeDisableUserService,
    uDFMetadataSecurityService: UDFMetadataSecurityService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(UDFMetadataSecurity, udfSpecialTypeDisableUserService, uDFMetadataSecurityService, AppSettings.UDF_METADATA_SECURITY, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService);
    this.addMetadataBaseFields([{field: 'uiOrder', order: 1}]);
  }

  protected override addAdditionalFields(beforeOthers: boolean): void {
    if (!beforeOthers) {
      this.addColumn(DataType.String, 'categoryTypeEnums', AppSettings.ASSETCLASS.toUpperCase(), true, false,
        {translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED, width: 200});
      this.addColumn(DataType.String, 'specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true, false,
        {translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED, width: 200});
    }
  }

  override prepareCallParam(entity: UDFMetadataSecurity): void {
    this.beforeEdit(entity, this.callParam);
    this.callParam.uDFMetadataSecurity = entity;
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY;
  }

  override readData(): void {
    super.readData();
    sessionStorage.removeItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY);
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}

