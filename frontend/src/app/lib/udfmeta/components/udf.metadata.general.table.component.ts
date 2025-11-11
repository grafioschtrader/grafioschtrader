import {Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {UDFMetadataGeneral, UDFMetadataGeneralParam} from '../model/udf.metadata';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {UDFMetadataGeneralService} from '../service/udf.metadata.general.service';
import {HelpIds} from '../../help/help.ids';
import {UDFMetaTable} from './udf.metadata.table';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {GlobalSessionNames} from '../../global.session.names';
import {UDFSpecialTypeDisableUserService} from '../service/udf.special.type.disable.user.service';
import {BaseSettings} from '../../base.settings';

/**
 * Table component for managing UDF metadata definitions across multiple entity types.
 * Displays a comprehensive table of user-defined field metadata for general entities (portfolios, watchlists, etc.)
 * without specific extensions. Supports CRUD operations, sorting, filtering, and special type toggling.
 * Users can create custom fields, define data types, and manage field visibility across entity types.
 */
@Component({
    // Selector is not used
    selector: 'udf-metadata-general-table',
    templateUrl: '../view/udf.metadata.table.html',
    providers: [DialogService],
    standalone: false
})
export class UDFMetadataGeneralTableComponent extends UDFMetaTable<UDFMetadataGeneral> implements OnDestroy {
  /** Parameters for metadata editing operations including validation exclusions */
  callParam: UDFMetadataGeneralParam = new UDFMetadataGeneralParam();

  /** Flag indicating this is general (not security-specific) metadata editing */
  isSecurityEdit = false;

  /**
   * Creates the UDF metadata general table component.
   *
   * @param udfSpecialTypeDisableUserService - Service for managing disabled special types
   * @param uDFMetadataGeneralService - Service for UDF metadata CRUD operations
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
    uDFMetadataGeneralService: UDFMetadataGeneralService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(UDFMetadataGeneral, udfSpecialTypeDisableUserService, uDFMetadataGeneralService, BaseSettings.UDF_METADATA_GENERAL,
      confirmationService, messageToastService, activePanelService, dialogService, filterService, translateService, gps, usersettingsService);
    this.addMetadataBaseFields([{field: 'entity', order: 1}, {field: 'uiOrder', order: 1}]);
  }

  /**
   * Adds entity-specific column to the table.
   * Called during table initialization to add the entity type column.
   *
   * @param beforeOthers - True to add column before standard metadata columns, false for after
   * @protected
   */
  protected override addAdditionalFields(beforeOthers: boolean): void {
    if (beforeOthers) {
      this.addColumnFeqH(DataType.String, 'entity', true, false,
        {translateValues: TranslateValue.UPPER_CASE});
    }
  }

  /**
   * Prepares parameters for opening the metadata edit dialog.
   * Sets up validation exclusions and entity to edit.
   *
   * @param entity - UDF metadata entity to edit, or null for new entry
   */
  override prepareCallParam(entity: UDFMetadataGeneral): void {
    this.beforeEdit(entity, this.callParam);
    this.callParam.uDFMetadataGeneral = entity;
  }

  /**
   * Returns the help context identifier for this component.
   *
   * @returns Help context ID for the UDF metadata general help page
   */
  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL;
  }

  /**
   * Loads UDF metadata from server and clears cached field descriptors.
   * Removing cached descriptors ensures fresh data is loaded when metadata changes.
   */
  override readData(): void {
    super.readData();
    sessionStorage.removeItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL);
  }

  /**
   * Cleanup handler when component is destroyed.
   * Unregisters this component from the active panel service.
   */
  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
