import { Directive, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { forkJoin, Observable } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuItem } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { IGlobalMenuAttach, PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { TranslateHelper } from '../../helper/translate.helper';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { BaseSettings } from '../../base.settings';
import { UDFMetadata, UDFMetadataGeneral } from '../model/udf.metadata';
import { ProcessedActionData } from '../../types/processed.action.data';
import { LoginService } from '../../login/service/log-in.service';
import { ProcessedAction } from '../../types/processed.action';

export const UDF_SHARED_TABLE_TEMPLATE = `
  <div class="data-container" (click)="onComponentClick($event)"
    [class.active-border]="isActivated()" [class.passiv-border]="!isActivated()">
    <configurable-table [data]="entityList()" [fields]="fields" [fieldsRevision]="fieldsRevision()"
      dataKey="idUDFMetadata" [(selection)]="selectedEntity" selectionMode="single"
      [multiSortMeta]="multiSortMeta" sortMode="multiple" [customSortFn]="customSorter" [valueGetterFn]="valueGetter"
      [stripedRows]="true" [showGridlines]="true" [contextMenuEnabled]="true"
      [contextMenuItems]="contextMenuItems()" [contextMenuAppendTo]="'body'" [showContextMenu]="isActivated()"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ title | translate }}</h4>
    </configurable-table>
  </div>
  @if (subscriptionField(); as field) {
    <udf-subscription-edit [visibleDialog]="true" [field]="field" [excludeUiOrders]="excludedOrders(field)" [rows]="subscriptionRows(field)"
      [noticeKey]="subscriptionNotice(field)" (closeDialog)="closeSubscription($event)" />
  }
`;

@Directive()
export abstract class UDFMetadataSharedTable<T extends UDFMetadata>
  extends TableConfigBase
  implements OnInit, IGlobalMenuAttach
{
  protected readonly destroyRef = inject(DestroyRef);
  protected readonly activePanelService = inject(ActivePanelService);
  readonly entityList = signal<T[]>([]);
  readonly selectedEntity = signal<T | null>(null);
  readonly contextMenuItems = signal<MenuItem[]>([]);
  readonly subscriptionField = signal<T | null>(null);
  private own: T[] = [];

  abstract readonly title: string;
  protected abstract readonly service: { getShared(): Observable<T[]>; getAllByIdUser(): Observable<T[]> };
  protected abstract readonly cacheKey: string;
  private readonly loginService = inject(LoginService);
  private readonly nicknames = signal<Record<number, string>>({});

  protected constructor() {
    super();
    this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
    this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false);
    this.addColumnFeqH(DataType.String, 'udfDataType', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.String, 'fieldSize', true, false);
    this.addColumnFeqH(DataType.String, 'shareMode', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumn(DataType.String, 'idUser', 'UDF_OWNER', true, false, {
      fieldValueFN: (field: T) =>
        field.idUser == null || field.idUser === 0
          ? ''
          : this.nicknames()[field.idUser]
            ? `${field.idUser} (${this.nicknames()[field.idUser]})`
            : String(field.idUser)
    });
  }

  ngOnInit(): void {
    this.readData();
  }

  subscriptionRows(_field: T): { label: string; value: string | null }[] {
    return [];
  }

  /**
   * Returns the translation key of a notice shown in the adoption dialog of a field, for an application whose fields
   * entail something the user must know before adopting.
   *
   * @param _field - The field to adopt
   * @returns The translation key, or null for no notice
   */
  subscriptionNotice(_field: T): string | null {
    return null;
  }

  readData(): void {
    forkJoin([this.service.getShared(), this.service.getAllByIdUser()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([shared, own]) => {
        this.own = own;
        const ids = [...new Set(shared.map((f) => f.idUser).filter((id): id is number => id != null && id !== 0))];
        if (this.gps.isUserNicknameVisible() && ids.length) {
          this.loginService
            .getNicknames(ids)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((names) => {
              this.nicknames.set(names);
              this.notifyViewChanged();
            });
        }
        this.createTranslatedValueStoreAndFilterField(shared);
        this.entityList.set(shared);
        this.selectedEntity.set(null);
        sessionStorage.removeItem(this.cacheKey);
        if (this.isActivated()) this.resetMenu();
        this.notifyViewChanged();
      });
  }

  private group(field: T): T[] {
    return this.own.filter(
      (f) => (f as unknown as UDFMetadataGeneral).entity === (field as unknown as UDFMetadataGeneral).entity
    );
  }
  excludedOrders(field: T): number[] {
    return this.group(field)
      .map((f) => f.uiOrder)
      .filter((v): v is number => v != null);
  }
  closeSubscription(result: ProcessedActionData): void {
    this.subscriptionField.set(null);
    if (result.action !== ProcessedAction.NO_CHANGE) this.readData();
  }
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }
  hideContextMenu(): void {}
  callMeDeactivate(): void {}
  getHelpContextId(): string | null {
    return null;
  }
  onComponentClick(event: PanelClickEvent): void {
    if (!event.consumedGT) this.resetMenu();
  }
  private resetMenu(): void {
    const field = this.selectedEntity();
    const menu: MenuItem[] = [
      {
        label: 'UDF_SUBSCRIBE' + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled:
          !field || this.gps.isReadOnlyUser() || this.group(field).some((f) => f.description === field.description),
        command: () => {
          if (field) this.subscriptionField.set(field);
        }
      }
    ];
    TranslateHelper.translateMenuItems$(menu, this.translateService).subscribe((items) => {
      this.contextMenuItems.set(items);
      this.activePanelService.activatePanel(this, { editMenu: items, showMenu: null });
    });
  }
}
