import {Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {UDFMetadataGeneral, UDFMetadataGeneralParam} from '../model/udf.metadata';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {UserSettingsService} from '../../service/user.settings.service';
import {AppSettings} from '../../app.settings';
import {UDFMetadataGeneralService} from '../service/udf.metadata.general.service';
import {HelpIds} from '../../help/help.ids';
import {UDFMetaTable} from './udf.metadata.table';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateValue} from '../../../lib/datashowbase/column.config';
import {GlobalSessionNames} from '../../global.session.names';
import {UDFSpecialTypeDisableUserService} from '../service/udf.special.type.disable.user.service';

/**
 * This table display is intended for the metadata of different information classes.
 * It contains the metadata of all information classes that do not have specific extensions,
 * for example for currency pairs.
 */
@Component({
    // Selector is not used
    selector: 'udf-metadata-general-table',
    templateUrl: '../view/udf.metadata.table.html',
    providers: [DialogService],
    standalone: false
})
export class UDFMetadataGeneralTableComponent extends UDFMetaTable<UDFMetadataGeneral> implements OnDestroy {
  callParam: UDFMetadataGeneralParam = new UDFMetadataGeneralParam();
  isSecurityEdit = false;

  constructor(udfSpecialTypeDisableUserService: UDFSpecialTypeDisableUserService,
    uDFMetadataGeneralService: UDFMetadataGeneralService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(UDFMetadataGeneral, udfSpecialTypeDisableUserService, uDFMetadataGeneralService, AppSettings.UDF_METADATA_GENERAL,
      confirmationService, messageToastService, activePanelService, dialogService, filterService, translateService, gps, usersettingsService);
    this.addMetadataBaseFields([{field: 'entity', order: 1}, {field: 'uiOrder', order: 1}]);
  }

  protected override addAdditionalFields(beforeOthers: boolean): void {
    if (beforeOthers) {
      this.addColumnFeqH(DataType.String, 'entity', true, false,
        {translateValues: TranslateValue.UPPER_CASE});
    }
  }

  override prepareCallParam(entity: UDFMetadataGeneral): void {
    this.beforeEdit(entity, this.callParam);
    this.callParam.uDFMetadataGeneral = entity;
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL;
  }

  override readData(): void {
    super.readData();
    sessionStorage.removeItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL);
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
