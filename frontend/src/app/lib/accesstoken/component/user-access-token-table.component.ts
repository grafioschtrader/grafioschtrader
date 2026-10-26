import { Component, Injector, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Subscription } from 'rxjs';
import { TableCrudSupportMenuSignal } from '../../datashowbase/table.crud.support.menu.signal';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { MessageToastService } from '../../message/message.toast.service';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { HelpIds } from '../../help/help.ids';
import { BaseSettings } from '../../base.settings';
import { UserAccessToken } from '../model/user-access-token';
import { UserAccessTokenService } from '../service/user-access-token.service';
import { UserAccessTokenCreateDialogComponent } from './user-access-token-create-dialog.component';

/**
 * The personal access tokens of the user. Tokens are never edited: the menu offers creating and revoking only. Unlike
 * other tenant data, both stay available to a user whose home tenant is read-only, because a token belongs to the
 * account; the server lets such a user create reading tokens and revoke any of his tokens.
 */
@Component({
  selector: 'user-access-token-table',
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, ButtonModule, UserAccessTokenCreateDialogComponent],
  template: `
    <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem">
      <p-button
        [label]="'ACCESS_TOKEN_CREATE' | translate"
        [disabled]="!formDefinition()"
        (onClick)="handleEditEntity(null)" />
      @if (selectedEntity()) {
        <p-button [label]="'ACCESS_TOKEN_REVOKE' | translate" (onClick)="handleDeleteEntity(selectedEntity())" />
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
      <user-access-token-create-dialog
        [visibleDialog]="visibleDialog()"
        [formDefinition]="formDefinition()"
        (tokenCreated)="readData()"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `
})
export class UserAccessTokenTableComponent extends TableCrudSupportMenuSignal<UserAccessToken> {
  readonly formDefinition = signal<ClassDescriptorInputAndShow>(null);
  private readDataSub?: Subscription;

  constructor(
    private userAccessTokenService: UserAccessTokenService,
    confirmation: ConfirmationService,
    messages: MessageToastService,
    panels: ActivePanelService,
    dialogs: DialogService,
    filters: FilterService,
    translate: TranslateService,
    gps: GlobalparameterService,
    settings: UserSettingsService,
    injector: Injector
  ) {
    super(
      UserAccessTokenService.USER_ACCESS_TOKEN,
      userAccessTokenService,
      confirmation,
      messages,
      panels,
      dialogs,
      filters,
      translate,
      gps,
      settings,
      injector
    );
    this.addColumnFeqH(DataType.String, 'tokenName', true, false);
    this.addColumnFeqH(DataType.String, 'tokenPrefix', true, false);
    this.addColumnFeqH(DataType.String, 'scope', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.DateTimeString, 'createdAt', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'expiresAt', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'lastUsedAt', true, false);
    this.addColumnFeqH(DataType.Boolean, 'expired', true, false, { templateName: 'check' });
    this.multiSortMeta.push({ field: 'createdAt', order: -1 });
    this.prepareTableAndTranslate();
  }

  protected override initialize(): void {
    this.gps
      .getEntityFormDefinition('UserAccessTokenCreateRequest')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((definition) => {
        this.formDefinition.set(definition);
        this.readData();
      });
  }

  protected override readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.userAccessTokenService
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.entityList.set(rows);
        this.createTranslatedValueStoreAndFilterField(rows);
        this.refreshSelectedEntity();
        if (!this.selectedEntity() && this.isActivated()) this.resetMenu(null);
      });
  }

  protected override prepareCallParam(_: UserAccessToken): void {}

  /** Creating and revoking only, and also for a read-only home tenant (see the class comment). */
  protected override getEditMenuItems(entity: UserAccessToken): MenuItem[] {
    const menuItems: MenuItem[] = [
      {
        label: 'ACCESS_TOKEN_CREATE' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleEditEntity(null),
        disabled: !this.formDefinition()
      }
    ];
    if (entity) {
      menuItems.push({ label: 'ACCESS_TOKEN_REVOKE', command: () => this.handleDeleteEntity(entity) });
    }
    return menuItems;
  }

  protected override hasRightsForDeleteEntity(_: UserAccessToken): boolean {
    return true;
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_INTRO_SETTINGS_AI_ASSISTANT;
  }
}
