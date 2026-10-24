import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { AppHelper } from '../helper/app.helper';
import { InfoLevelType } from '../message/info.leve.type';
import { MessageToastService } from '../message/message.toast.service';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { DynamicDialogs } from '../dynamicdialog/component/dynamic.dialogs';
import { ProcessedActionData } from '../types/processed.action.data';
import { BaseSettings } from '../base.settings';
import saveAs from '../filesaver/filesaver';
import { ColumnConfig } from './column.config';
import { DeleteService } from './delete.service';

/**
 * Behaviour shared by TableCrudSupportMenu and TableCrudSupportMenuSignal. Everything here leaves the template state
 * of the table alone; the two classes differ only in how they hold that state.
 */

export enum CrudMenuOptions {
  Allow_Create,
  Allow_Edit,
  Allow_Delete,
  /**
   *  Avoid to add this menu directly
   */
  ParentControl
}

/** All CRUD operations, the default of both table base classes. */
export const ALLOW_ALL_CRUD_OPERATIONS: CrudMenuOptions[] = [
  CrudMenuOptions.Allow_Create,
  CrudMenuOptions.Allow_Edit,
  CrudMenuOptions.Allow_Delete
];

/** Callbacks and permissions the standard CRUD menu is built from. */
export interface CrudMenuDefinition<T> {
  crudMenuOptions: CrudMenuOptions[];
  entityNameUpper: string;
  readOnly: boolean;
  onEdit: (entity: T) => void;
  onDelete: (entity: T) => void;
  canCreate: (entity: T) => boolean;
  canUpdate: (entity: T) => boolean;
  canDelete: (entity: T) => boolean;
}

/**
 * Builds the untranslated create, edit and delete entries. A read-only user (managed client account, or advisor in a
 * READ-level tenant) cannot create, update or delete tenant data, so these entries are not shown at all rather than
 * shown disabled. The backend enforces this regardless.
 *
 * @param definition - Options, permissions and callbacks of the table
 * @param entity - The selected entity, or null; edit and delete need one
 * @returns The menu entries, possibly empty
 */
export function createCrudMenuItems<T>(definition: CrudMenuDefinition<T>, entity: T): MenuItem[] {
  const menuItems: MenuItem[] = [];
  const allows = (option: CrudMenuOptions) => !definition.readOnly && definition.crudMenuOptions.indexOf(option) >= 0;
  if (allows(CrudMenuOptions.Allow_Create)) {
    menuItems.push({
      label: 'CREATE|' + definition.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => definition.onEdit(null),
      disabled: !definition.canCreate(entity)
    });
  }
  if (entity) {
    if (allows(CrudMenuOptions.Allow_Edit)) {
      menuItems.push({
        label: 'EDIT_RECORD|' + definition.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => definition.onEdit(entity),
        disabled: !definition.canUpdate(entity)
      });
    }
    if (allows(CrudMenuOptions.Allow_Delete)) {
      menuItems.push({
        label: 'DELETE_RECORD|' + definition.entityNameUpper,
        command: () => definition.onDelete(entity),
        disabled: !definition.canDelete(entity)
      });
    }
  }
  return menuItems;
}

/**
 * Asks for confirmation and deletes the entity. An error branch is required: the toast is written by
 * BaseAuthService.handleError, but without it the rejected observable is rethrown as an unhandled error and the menu
 * keeps offering the failed action.
 *
 * @param services - Services the dialog, the request and the toast need
 * @param entityNameUpper - NLS key of the entity, used in the confirmation and the success message
 * @param id - Primary key of the entity to delete, resolved once the user confirmed
 * @param onDeleted - Called after a successful deletion
 * @param onFailed - Called after a failed deletion
 */
export function confirmAndDeleteEntity(
  services: {
    translateService: TranslateService;
    confirmationService: ConfirmationService;
    deleteService: DeleteService;
    messageToastService: MessageToastService;
  },
  entityNameUpper: string,
  id: () => number,
  onDeleted: () => void,
  onFailed: () => void
): void {
  AppHelper.confirmationDialog(
    services.translateService,
    services.confirmationService,
    'MSG_CONFIRM_DELETE_RECORD|' + entityNameUpper,
    () =>
      services.deleteService.deleteEntity(id()).subscribe({
        next: () => {
          services.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: entityNameUpper
          });
          onDeleted();
        },
        error: () => onFailed()
      })
  );
}

/**
 * Opens the dialog for requesting a higher limit when the edit dialog closed because of a limit violation.
 *
 * @param processedActionData - Result of the closed edit dialog
 * @param translateService - Translation service for the dialog
 * @param dialogService - Dialog service opening it
 * @param entityName - Entity whose limit was exceeded
 */
export function openLimitRequestOnLimitError(
  processedActionData: ProcessedActionData,
  translateService: TranslateService,
  dialogService: DialogService,
  entityName: string
): void {
  if (processedActionData.transformedError?.errorClass instanceof LimitEntityTransactionError) {
    DynamicDialogs.getOpenedLimitTransactionRequestDynamicComponent(translateService, dialogService, entityName);
  }
}

/**
 * Exports table data to a CSV file. Only columns marked with the export flag are written, null values are left
 * empty. Uses the platform-specific line separator.
 *
 * @param data - Rows to export
 * @param columnByField - Resolves the column configuration of a property name
 * @param entityName - Gives the file its name
 */
export function downloadCsv(data: any[], columnByField: (field: string) => ColumnConfig, entityName: string): void {
  const lineSeparator = navigator.appVersion.indexOf('Win') !== -1 ? '\r\n' : '\n';
  const replacer = (key, value) => (value === null ? undefined : value);
  const header: string[] = Object.keys(data[0]).filter((name) => columnByField(name)?.export);
  const csv = data.map((row) => header.map((fieldName) => JSON.stringify(row[fieldName], replacer)).join(';'));
  csv.unshift(header.join(';'));
  const blob = new Blob([csv.join(lineSeparator)], { type: 'text/csv' });
  saveAs(blob, entityName.toLocaleLowerCase() + '.csv');
}
