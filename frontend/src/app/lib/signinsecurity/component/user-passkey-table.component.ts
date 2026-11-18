import { Component, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Subscription } from 'rxjs';
import { TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { DataType } from '../../dynamic-form/models/data.type';
import { HelpIds } from '../../help/help.ids';
import { ProcessedActionData } from '../../types/processed.action.data';
import { UserPasskey } from '../model/sign-in-security';
import { UserPasskeyService } from '../service/user-passkey.service';
import { UserPasskeyEditDialogComponent } from './user-passkey-edit-dialog.component';

/**
 * The passkeys of the user: name, registration, last sign-in and whether it was synchronized across devices when it was
 * registered. Adding asks for the proof of a new sign-in method and then for the device; renaming and deleting need no
 * proof. Adding and deleting end the other sessions of the user.
 */
@Component({
  selector: 'user-passkey-table',
  imports: [ConfigurableTableComponent, TranslateModule, ButtonModule, UserPasskeyEditDialogComponent],
  template: `
    <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem">
      <p-button [label]="'PASSKEY_ADD' | translate" (onClick)="handleEditEntity(null)" />
      @if (selectedEntity(); as selected) {
        <p-button [label]="'EDIT' | translate" severity="secondary" (onClick)="handleEditEntity(selected)" />
        <p-button [label]="'DELETE' | translate" severity="danger" (onClick)="handleDeleteEntity(selected)" />
      }
    </div>
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      selectionMode="single"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [scrollable]="false"
      [showContextMenu]="!!contextMenuItems()"
      [contextMenuItems]="contextMenuItems()"
      [contextMenuAppendTo]="'body'"
      (componentClick)="onComponentClick($event)" />
    @if (visibleDialog()) {
      <user-passkey-edit-dialog
        [visibleDialog]="visibleDialog()"
        [passkey]="callParam"
        [totpEnabled]="totpEnabled()"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `
})
export class UserPasskeyTableComponent extends TableCrudSupportMenu<UserPasskey> {
  private readonly userPasskeyService = inject(UserPasskeyService);

  /** While an authenticator app is enabled, adding a passkey needs one of its codes. */
  readonly totpEnabled = input(false);
  /** Emitted after the passkeys were read anew, so the owner can refresh its counts. */
  readonly changed = output<void>();
  callParam: UserPasskey | null = null;
  private readDataSub?: Subscription;

  constructor() {
    super(UserPasskeyService.USER_PASSKEY, inject(UserPasskeyService));
    this.addColumn(DataType.String, 'label', 'PASSKEY_LABEL', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'createdAt', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'lastUsedAt', true, false);
    this.addColumnFeqH(DataType.Boolean, 'synced', true, false, { templateName: 'check' });
    this.multiSortMeta.push({ field: 'createdAt', order: 1 });
    this.prepareTableAndTranslate();
  }

  protected override readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.userPasskeyService
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.entityList.set(rows);
        this.createTranslatedValueStoreAndFilterField(rows);
        this.refreshSelectedEntity();
        if (!this.selectedEntity() && this.isActivated()) this.resetMenu(null);
        this.changed.emit();
      });
  }

  override handleCloseDialog(processedActionData: ProcessedActionData): void {
    super.handleCloseDialog(processedActionData);
    this.callParam = null;
  }

  protected override prepareCallParam(entity: UserPasskey | null): void {
    this.callParam = entity;
  }

  /** Every passkey in this table belongs to the signed-in user. */
  protected override hasRightsForCreateEntity(_: UserPasskey | null): boolean {
    return true;
  }

  protected override hasRightsForUpdateEntity(_: UserPasskey): boolean {
    return true;
  }

  protected override hasRightsForDeleteEntity(_: UserPasskey): boolean {
    return true;
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_INTRO_SETTINGS;
  }
}
