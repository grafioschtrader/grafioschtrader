import { UDFMetadataSubscriptionService } from '../service/udf.metadata.subscription.service';
import { LoginService } from '../../login/service/log-in.service';
import { AppHelper } from '../../helper/app.helper';
import { PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UDFMetadata, UDFMetadataParam } from '../model/udf.metadata';
import { MenuItem, SortMeta } from '@openng/optimus-ui/api';
import { DeleteService } from '../../datashowbase/delete.service';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { forkJoin, Observable, Subscription } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ClassConstructor } from 'class-transformer/types/interfaces';
import { TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { UDFSpecialTypeDisableUserService } from '../service/udf.special.type.disable.user.service';
import { UDFSpecialTypeRegistry } from '../model/udf.special.type.registry';

/**
 * Abstract base class for displaying and managing UDF metadata in a table format.
 * Provides common functionality for UDF metadata table components including column configuration,
 * data loading, special type toggling, and edit rights management. Subclasses implement entity-specific
 * column additions and parameter preparation for editing dialogs.
 */
export abstract class UDFMetaTable<T extends UDFMetadata> extends TableCrudSupportMenu<T> {
  protected readonly subscriptionService = inject(UDFMetadataSubscriptionService);
  private readonly loginService = inject(LoginService);
  private readonly nicknames = signal<Record<number, string>>({});
  readonly subscriptionField = signal<T | null>(null);
  subscriptionExcludedOrders: number[] = [];

  isOwn(field: UDFMetadata): boolean {
    return field.idUser === this.gps.getIdUser();
  }
  isSubscribed(field: UDFMetadata): boolean {
    return field.idUDFMetadataSubscription != null;
  }
  isOrphaned(field: UDFMetadata): boolean {
    return this.isSubscribed(field) && field.idUser == null;
  }

  override onComponentClick(event: PanelClickEvent): void {
    if (!event.consumedGT) super.onComponentClick(event);
  }

  override handleEditEntity(field: T | null): void {
    if (field && this.isSubscribed(field)) {
      const param = new UDFMetadataParam();
      this.beforeEdit(field, param);
      this.subscriptionExcludedOrders = param.excludeUiOrders;
      this.subscriptionField.set(field);
    } else {
      super.handleEditEntity(field);
    }
  }

  override handleDeleteEntity(field: T): void {
    const subscriptionId = field.idUDFMetadataSubscription;
    if (subscriptionId != null) {
      AppHelper.confirmationDialog(this.translateService, this.confirmationService, 'UDF_UNSUBSCRIBE', () =>
        this.subscriptionService
          .deleteEntity(subscriptionId)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => this.readData())
      );
    } else if (this.isOwn(field) && field.subscriberCount) {
      const id = field.idUDFMetadata;
      if (id == null) return;
      this.confirmationService.confirm({
        message: this.translateService.instant('UDF_DELETE_SHARED_CONFIRM', { count: field.subscriberCount }),
        header: this.translateService.instant('MSG_GENERAL_HEADER'),
        accept: () =>
          this.deleteReadAllService
            .deleteEntity(id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.readData())
      });
    } else {
      super.handleDeleteEntity(field);
    }
  }

  protected override getEditMenuItems(field: T | null): MenuItem[] {
    const items = super.getEditMenuItems(field);
    if (field && this.isSubscribed(field)) {
      const item = items.find((i) => i.label?.startsWith('DELETE_RECORD|'));
      if (item) item.label = 'UDF_UNSUBSCRIBE';
    }
    return items;
  }

  private udfSpecialTypeDisableUserService: UDFSpecialTypeDisableUserService = inject(UDFSpecialTypeDisableUserService);

  /**
   * Array of UDF special type values that the current user has disabled (hidden in UI).
   * Each entry is a numeric value corresponding to a registered IUDFSpecialType.
   */
  readonly specialTypeDisabledArr = signal<number[]>([]);
  private readonly togglingSpecialType = signal(false);
  private readDataSubscription?: Subscription;

  /**
   * Creates the UDF metadata table base.
   *
   * @param classz - Class constructor for transforming plain objects to typed instances
   * @param deleteReadAllService - Service providing read and delete operations for metadata
   * @param entityName - Entity type name for the metadata being managed
   * @protected
   */
  protected constructor(
    private classz: ClassConstructor<T>,
    private deleteReadAllService: DeleteReadAllService<UDFMetadata>,
    entityName: string
  ) {
    super(entityName, deleteReadAllService);
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
    this.addColumnFeqH(DataType.String, 'udfSpecialType', true, false, {
      translateValues: TranslateValue.NORMAL,
      width: 80
    });
    this.addColumnFeqH(DataType.Boolean, 'udfDisabledUser', true, false, {
      templateName: 'check',
      width: 60,
      fieldValueFN: this.udfDisabled.bind(this)
    });
    this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
    this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false, { width: 150 });
    this.addColumnFeqH(DataType.String, 'udfDataType', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.String, 'fieldSize', true, false, { width: 60 });
    this.addColumnFeqH(DataType.String, 'shareMode', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumn(DataType.String, 'idUser', 'UDF_OWNER', true, false, {
      fieldValueFN: (field: T) =>
        field.idUser == null || field.idUser === 0
          ? ''
          : this.nicknames()[field.idUser]
            ? `${field.idUser} (${this.nicknames()[field.idUser]})`
            : String(field.idUser)
    });
    this.addColumnFeqH(DataType.NumericInteger, 'subscriberCount', true, false);
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
  protected beforeEdit(entity: UDFMetadata | null, uDFMetadataParam: UDFMetadataParam): void {
    uDFMetadataParam.excludeUiOrders = this.entityList()
      .filter((m) => entity == null || entity.uiOrder !== m.uiOrder)
      .map((m) => m.uiOrder)
      .filter((order) => order != null);
    uDFMetadataParam.excludeFieldNames = this.entityList()
      .filter((m) => entity == null || entity.description !== m.description)
      .map((m) => m.description)
      .filter((name) => name != null);
  }

  /**
   * Loads UDF metadata and disabled special types from server.
   * Combines metadata and disabled types into table data with proper type transformation.
   */
  override readData(): void {
    this.readDataSubscription?.unsubscribe();
    this.readDataSubscription = forkJoin([
      this.deleteReadAllService.getAllByIdUser(),
      this.udfSpecialTypeDisableUserService.getDisabledSpecialTypes()
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          const entities = plainToInstance(this.classz, data[0]);
          this.specialTypeDisabledArr.set(data[1]);
          this.createTranslatedValueStoreAndFilterField(entities);
          this.entityList.set(entities);
          if (this.gps.isUserNicknameVisible()) {
            const ids = [
              ...new Set(entities.map((f) => f.idUser).filter((id): id is number => id != null && id !== 0))
            ];
            if (ids.length)
              this.loginService
                .getNicknames(ids)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((names) => {
                  this.nicknames.set(names);
                  this.notifyViewChanged();
                });
          }
          this.togglingSpecialType.set(false);
          this.refreshSelectedEntity();
        },
        error: () => this.finishSpecialTypeToggle()
      });
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
      label: 'UDF_CLAIM_OWNERSHIP',
      disabled: !this.isOrphaned(udfMetaData) || this.gps.isReadOnlyUser(),
      command: () => {
        const id = udfMetaData.idUDFMetadataSubscription;
        if (id != null)
          this.subscriptionService
            .claimOwnership(id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.readData());
      }
    });
    menuItems.push({
      label: 'UDF_TURN_ON_OFF_FIELD_USER0',
      command: (event) => this.turnOnOffFieldUser0(udfMetaData),
      disabled: !this.resolveSpecialType(udfMetaData) || this.togglingSpecialType()
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
    const specialType = this.resolveSpecialType(udfMetaData);
    if (!specialType) {
      console.error(`Unknown UDF special type value: ${udfMetaData.udfSpecialType}`);
      return;
    }

    if (this.togglingSpecialType()) return;
    this.togglingSpecialType.set(true);
    this.resetMenu(this.selectedEntity());
    const update = this.specialTypeDisabledArr().includes(specialType.value)
      ? this.udfSpecialTypeDisableUserService.delete(specialType.value)
      : this.udfSpecialTypeDisableUserService.create(specialType.value);
    update
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.readData(), error: () => this.finishSpecialTypeToggle() });
  }

  private finishSpecialTypeToggle(): void {
    this.togglingSpecialType.set(false);
    if (this.isActivated()) this.resetMenu(this.selectedEntity());
  }

  /** JSON uses enum names; registry consumers also support the numeric values of an already resolved row. */
  private resolveSpecialType(entity: T) {
    const type = entity.udfSpecialType as number | string;
    return typeof type === 'string' ? UDFSpecialTypeRegistry.getByName(type) : UDFSpecialTypeRegistry.getByValue(type);
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
    const specialType = this.resolveSpecialType(entity);
    return specialType !== undefined && this.specialTypeDisabledArr().includes(specialType.value);
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
    return this.isOwn(entity) || this.isSubscribed(entity);
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
    return this.isOwn(entity) || this.isSubscribed(entity);
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
  getAllByIdUser(): Observable<T[]>;
}
