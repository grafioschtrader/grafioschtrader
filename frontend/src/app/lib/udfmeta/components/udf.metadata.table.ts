import {Injector} from '@angular/core';
import {UDFMetadata, UDFMetadataParam} from '../model/udf.metadata';
import {ConfirmationService, FilterService, MenuItem, SortMeta} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {DeleteService} from '../../datashowbase/delete.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {combineLatest, Observable} from 'rxjs';
import {plainToInstance} from 'class-transformer';
import {ClassConstructor} from 'class-transformer/types/interfaces';
import {TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {UDFSpecialTypeDisableUserService} from '../service/udf.special.type.disable.user.service';
import {UDFSpecialTypeRegistry} from '../model/udf.special.type.registry';


/**
 * Abstract base class for displaying and managing UDF metadata in a table format.
 * Provides common functionality for UDF metadata table components including column configuration,
 * data loading, special type toggling, and edit rights management. Subclasses implement entity-specific
 * column additions and parameter preparation for editing dialogs.
 */
export abstract class UDFMetaTable<T extends UDFMetadata> extends TableCrudSupportMenu<T> {
  /**
   * Array of UDF special type values that the current user has disabled (hidden in UI).
   * Each entry is a numeric value corresponding to a registered IUDFSpecialType.
   */
  specialTypeDisabledArr: number[] = [];

  /**
   * Creates the UDF metadata table base.
   *
   * @param classz - Class constructor for transforming plain objects to typed instances
   * @param udfSpecialTypeDisableUserService - Service for managing disabled special types
   * @param deleteReadAllService - Service providing read and delete operations for metadata
   * @param entityName - Entity type name for the metadata being managed
   * @param confirmationService - PrimeNG confirmation dialog service
   * @param messageToastService - Service for displaying user notifications
   * @param activePanelService - Service for managing active panels in the UI
   * @param dialogService - PrimeNG dynamic dialog service
   * @param filterService - PrimeNG filter service for table filtering
   * @param translateService - Translation service for i18n support
   * @param gps - Global parameter service providing user settings and system configuration
   * @param usersettingsService - Service for managing user preferences
   * @protected
   */
  protected constructor(private classz: ClassConstructor<T>,
    private udfSpecialTypeDisableUserService: UDFSpecialTypeDisableUserService,
    private deleteReadAllService: DeleteReadAllService<UDFMetadata>,
    entityName: string,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {

    super(entityName, deleteReadAllService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService, injector)
  }

  /**
   * Adds entity-specific columns to the table.
   * Subclasses must implement to add columns specific to their metadata type.
   *
   * @param beforeOthers - True to add columns before standard metadata columns, false for after
   * @protected
   */
  protected abstract addAdditionalFields(beforeOthers: boolean): void;

  /**
   * Configures and adds standard UDF metadata columns to the table.
   * Creates columns for UI order, special type, disabled status, description, help text, data type, and field size.
   * Calls subclass-specific addAdditionalFields before and after standard columns.
   *
   * @param sortMeta - Initial sort configuration for the table
   * @protected
   */
  protected addMetadataBaseFields(sortMeta: SortMeta[]): void {
    this.addAdditionalFields(true);
    this.addColumnFeqH(DataType.String, 'uiOrder', true, false);
    this.addColumnFeqH(DataType.String, 'udfSpecialType', true, false,
      {translateValues: TranslateValue.NORMAL, width: 80});
    this.addColumnFeqH(DataType.Boolean, 'udfDisabledUser', true, false,
      {templateName: 'check', width: 60, fieldValueFN: this.udfDisabled.bind(this)});
    this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
    this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false,
      {width: 150});
    this.addColumnFeqH(DataType.String, 'udfDataType', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'fieldSize', true, false, {width: 60});
    this.addAdditionalFields(false);
    this.multiSortMeta.push(...sortMeta);
    this.prepareTableAndTranslate();
  }

  /**
   * Prepares validation exclusion lists before editing metadata.
   * Builds lists of UI orders and field names to exclude to prevent duplicates.
   *
   * @param entity - UDF metadata entity to edit, or null when creating new
   * @param uDFMetadataParam - Parameter object to populate with exclusion lists
   * @protected
   */
  protected beforeEdit(entity: UDFMetadata, uDFMetadataParam: UDFMetadataParam): void {
    uDFMetadataParam.excludeUiOrders = this.entityList.filter(m => entity == null
      || entity.uiOrder !== m.uiOrder).map(m => m.uiOrder);
    uDFMetadataParam.excludeFieldNames = this.entityList.filter(m => entity == null
      || entity.description !== m.description).map(m => m.description);
  }

  /**
   * Loads UDF metadata and disabled special types from server.
   * Combines metadata and disabled types into table data with proper type transformation.
   */
  override readData(): void {
    combineLatest([this.deleteReadAllService.getAllByIdUser(),
      this.udfSpecialTypeDisableUserService.getDisabledSpecialTypes()]).subscribe((data: [T[], number[]]) => {
      this.entityList = plainToInstance(this.classz, data[0]);
      this.specialTypeDisabledArr = data[1];
      console.log(this.specialTypeDisabledArr);
      this.createTranslatedValueStoreAndFilterField(this.entityList);
    })
  }

  /**
   * Adds context menu item for toggling special type field visibility.
   * Only enabled for metadata with a special type assigned.
   *
   * @param udfMetaData - UDF metadata entity selected for context menu
   * @param menuItems - Menu items array to add custom menu to
   * @protected
   */
  protected override addCustomMenusToSelectedEntity(udfMetaData: T, menuItems: MenuItem[]): void {
    menuItems.push({
      label: 'UDF_TURN_ON_OFF_FIELD_USER0',
      command: (event) => this.turnOnOffFieldUser0(udfMetaData),
      disabled: !udfMetaData.udfSpecialType
    });
  }

  /**
   * Toggles visibility of a special type UDF field for the current user.
   * Enables the field if currently disabled, or disables it if currently enabled.
   * Uses the registry to resolve the special type name from its numeric value.
   *
   * @param udfMetaData - UDF metadata entity to toggle visibility for
   */
  turnOnOffFieldUser0(udfMetaData: T): void {
    const specialType = UDFSpecialTypeRegistry.getByValue(udfMetaData.udfSpecialType);
    if (!specialType) {
      console.error(`Unknown UDF special type value: ${udfMetaData.udfSpecialType}`);
      return;
    }

    if (this.specialTypeDisabledArr.indexOf(udfMetaData.udfSpecialType) >= 0) {
      this.udfSpecialTypeDisableUserService.delete(specialType.name).subscribe(() => this.readData());
    } else {
      this.udfSpecialTypeDisableUserService.create(specialType.name).subscribe(() => this.readData());
    }
  }

  /**
   * Determines if a UDF field is currently disabled for the user.
   * Used for displaying disabled status checkbox in table.
   *
   * @param entity - UDF metadata entity to check
   * @param field - Column configuration
   * @param valueField - Field value (unused)
   * @returns True if the special type is in the disabled array, false otherwise
   */
  udfDisabled(entity: T, field: ColumnConfig, valueField: any): boolean {
    return this.specialTypeDisabledArr.indexOf(entity.udfSpecialType) >= 0;
  }

  /**
   * Checks if user has rights to update a metadata entity.
   * Only user-owned metadata (not system-wide with idUser = 0) can be updated.
   *
   * @param entity - UDF metadata entity to check
   * @returns True if user can update, false for system metadata
   * @protected
   */
  protected override hasRightsForUpdateEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }

  /**
   * Checks if user has rights to delete a metadata entity.
   * Only user-owned metadata (not system-wide with idUser = 0) can be deleted.
   *
   * @param entity - UDF metadata entity to check
   * @returns True if user can delete, false for system metadata
   * @protected
   */
  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }
}

/**
 * Service interface for UDF metadata operations.
 * Extends DeleteService with additional method for retrieving all metadata accessible to the current user.
 */
export interface DeleteReadAllService<T extends UDFMetadata> extends DeleteService {
  /**
   * Retrieves all UDF metadata entries accessible to the current user.
   * Returns both user-specific metadata and system-wide metadata.
   *
   * @returns Observable emitting array of UDF metadata entries
   */
  getAllByIdUser(): Observable<T[]>
}
