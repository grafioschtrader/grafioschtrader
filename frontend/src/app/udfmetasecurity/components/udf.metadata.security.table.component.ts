import {Component, OnDestroy} from '@angular/core';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {AppSettings} from '../../shared/app.settings';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../lib/help/help.ids';
import {UDFMetaTable} from '../../lib/udfmeta/components/udf.metadata.table';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {UDFSpecialTypeDisableUserService} from '../../lib/udfmeta/service/udf.special.type.disable.user.service';
import {UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata.security';

/**
 * Table component for managing security-specific UDF metadata definitions.
 * Displays a comprehensive table of user-defined field metadata for securities with asset class and
 * special instrument type filtering. Unlike general UDF metadata, security metadata includes columns
 * for category types and financial instruments, allowing precise control over field applicability.
 * Supports CRUD operations, sorting, filtering, and special type toggling.
 */
@Component({
    templateUrl: '../../lib/udfmeta/view/udf.metadata.table.html',
    providers: [DialogService],
    standalone: false
})
export class UDFMetadataSecurityTableComponent extends UDFMetaTable<UDFMetadataSecurity> implements OnDestroy {

  /** Parameters for security metadata editing operations including validation exclusions */
  callParam: UDFMetadataSecurityParam = new UDFMetadataSecurityParam();

  /** Flag indicating this is security-specific (not general) metadata editing */
  isSecurityEdit = true;

  /**
   * Creates the UDF metadata security table component.
   *
   * @param udfSpecialTypeDisableUserService - Service for managing disabled special types
   * @param uDFMetadataSecurityService - Service for security UDF metadata CRUD operations
   * @param confirmationService - PrimeNG confirmation dialog service
   * @param messageToastService - Service for displaying user notifications
   * @param activePanelService - Service for managing active panels in the UI
   * @param dialogService - PrimeNG dynamic dialog service
   * @param filterService - PrimeNG filter service for table filtering
   * @param translateService - Translation service for i18n support
   * @param gps - Global parameter service providing user settings and system configuration
   * @param usersettingsService - Service for managing user preferences
   */
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

  /**
   * Adds security-specific columns to the table.
   * Adds asset class types and special instrument types columns after standard metadata columns.
   *
   * @param beforeOthers - True to add columns before standard metadata columns, false for after
   * @protected
   */
  protected override addAdditionalFields(beforeOthers: boolean): void {
    if (!beforeOthers) {
      this.addColumn(DataType.String, 'categoryTypeEnums', AppSettings.ASSETCLASS.toUpperCase(), true, false,
        {translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED, width: 200});
      this.addColumn(DataType.String, 'specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true, false,
        {translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED, width: 200});
    }
  }

  /**
   * Prepares parameters for opening the security metadata edit dialog.
   * Sets up validation exclusions and entity to edit.
   *
   * @param entity - Security UDF metadata entity to edit, or null for new entry
   */
  override prepareCallParam(entity: UDFMetadataSecurity): void {
    this.beforeEdit(entity, this.callParam);
    this.callParam.uDFMetadataSecurity = entity;
  }

  /**
   * Returns the help context identifier for this component.
   *
   * @returns Help context ID for the security UDF metadata help page
   */
  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY;
  }

  /**
   * Loads security UDF metadata from server and clears cached field descriptors.
   * Removing cached descriptors ensures fresh data is loaded when metadata changes.
   */
  override readData(): void {
    super.readData();
    sessionStorage.removeItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY);
  }

  /**
   * Cleanup handler when component is destroyed.
   * Unregisters this component from the active panel service.
   */
  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}

