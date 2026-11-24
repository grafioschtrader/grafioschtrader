import {
  afterNextRender,
  Component,
  computed,
  effect,
  inject,
  Injector,
  OnInit,
  signal,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { disabled, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { SelectItem } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
import { Subscription } from 'rxjs';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { SimpleDynamicFormBase } from '../../lib/edit/simple.dynamic.form.base';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TransformedError } from '../../lib/login/service/transformed.error';
import type { LogoScopeName } from '../types/logo.scope';
import { InstrumentLogoSelectTableComponent } from './instrument-logo-select-table.component';
import {
  computeLogoDraftPrefill,
  InstrumentLogoDialogData,
  InstrumentLogoDialogResult,
  InstrumentLogoRefDto,
  InstrumentLogoUpdatePayload,
  InstrumentLogoUploadPayload,
  isSvgFile,
  LogoCatalogTableRow,
  MAX_LOGO_SVG_BYTES,
  normalizeIsinInput,
  utf8ByteLength
} from './instrument-logo.model';
import { InstrumentLogoService } from './instrument-logo.service';

interface LogoSearchFormModel {
  name: string | null;
  isin: string | null;
}

interface DraftLogoState {
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  svgContent: string;
  previewUrl: string;
  isinInUseHint: boolean;
}

interface EditLogoState {
  idInstrumentLogo: number;
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  svgContent: string | null;
  previewUrl: string | null;
}

const DRAFT_ROW_KEY = '__DRAFT__';

/**
 * Modal DynamicDialog (Dialog D) for searching the shared instrument logo catalog and optionally preparing a new
 * drag-and-drop SVG logo. Uses Grafioschtrader's SignalDynamicFormComponent for the search criteria and delegates
 * tabular display/selection to InstrumentLogoSelectTableComponent (ConfigurableTableComponent). New uploads are
 * pre-validated via POST /api/instrumentlogo/validate without writing to the database; persistence happens when
 * the parent security dialog saves.
 */
@Component({
  selector: 'instrument-logo-select-dialog',
  imports: [
    SignalDynamicFormComponent,
    InstrumentLogoSelectTableComponent,
    TranslateModule,
    ButtonModule,
    MessageModule
  ],
  template: `
    <div class="logo-dialog">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        [values]="startValues"
        #form="signalDynamicForm"
        (submitBt)="submit($event)" />

      @if (errorMessage(); as err) {
        <p-message severity="error" [text]="err" />
      }

      @if (draftRow(); as draft) {
        <p-message
          severity="info"
          [text]="
            (dialogData.isTenantPrivate ? 'INSTRUMENT_LOGO_PRIVATE_NAME_HINT' : 'INSTRUMENT_LOGO_SHARED_CATALOG_HINT')
              | translate
          " />
        @if (draft.isinInUseHint) {
          <p-message severity="warn" [text]="'INSTRUMENT_LOGO_ISIN_IN_USE_HINT' | translate" />
        }
      }

      <instrument-logo-select-table
        [rows]="tableRows()"
        [selectedRow]="selectedTableRow()"
        [loading]="loading()"
        [draftActive]="isEditingOrDraft()"
        [scopeOptions]="scopeOptions()"
        [editName]="activeEditName()"
        [editIsin]="activeEditIsin()"
        [editScope]="activeEditScope()"
        [editPreviewUrl]="activeEditPreviewUrl()"
        (rowSelected)="onRowSelected($event)"
        (draftNameChange)="updateEditableName($event)"
        (draftIsinChange)="updateEditableIsin($event)"
        (draftScopeChange)="updateEditableScope($event)" />

      <div
        class="logo-drop-zone"
        [class.logo-drop-zone-active]="dragging()"
        (dragover)="onDragOver($event)"
        (dragleave)="onDragLeave($event)"
        (drop)="onDrop($event)"
        (click)="fileInput.click()">
        <input #fileInput type="file" accept=".svg,image/svg+xml" class="d-none" (change)="onFileInputChange($event)" />
        <i class="pi pi-upload me-2"></i>
        <span>{{
          (editingLogo() !== null ? 'INSTRUMENT_LOGO_EDIT_HINT' : 'INSTRUMENT_LOGO_DROP_HINT') | translate
        }}</span>
      </div>

      <div class="logo-dialog-footer">
        @if (draftRow() !== null) {
          <p-button
            [label]="'INSTRUMENT_LOGO_DISCARD_DRAFT' | translate"
            severity="secondary"
            [disabled]="validating()"
            (onClick)="discardDraft()" />
        } @else if (editingLogo() !== null) {
          <p-button
            [label]="'INSTRUMENT_LOGO_CANCEL_EDIT' | translate"
            severity="secondary"
            [disabled]="validating()"
            (onClick)="cancelEdit()" />
        } @else if (canEditSelected()) {
          <p-button
            [label]="'INSTRUMENT_LOGO_EDIT_BUTTON' | translate"
            severity="secondary"
            [disabled]="validating() || loading()"
            (onClick)="startEditSelected()">
            <i class="pi pi-pencil" pButtonIcon></i>
          </p-button>
          <p-button
            [label]="'INSTRUMENT_LOGO_DELETE_BUTTON' | translate"
            severity="danger"
            [outlined]="true"
            [loading]="deleting()"
            [disabled]="validating() || loading()"
            (onClick)="deleteSelected()">
            <i class="pi pi-trash" pButtonIcon></i>
          </p-button>
        }
        <div class="ms-auto d-flex gap-2">
          <p-button
            [label]="'CANCEL' | translate"
            severity="secondary"
            [disabled]="validating() || deleting()"
            (onClick)="cancel()" />
          <p-button [label]="'APPLY' | translate" [loading]="validating()" [disabled]="!canApply()" (onClick)="apply()">
            <i class="pi pi-check" pButtonIcon></i>
          </p-button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .logo-dialog {
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }

      .logo-drop-zone {
        border: 2px dashed var(--surface-border, #adb5bd);
        border-radius: 6px;
        padding: 0.75rem 1rem;
        text-align: center;
        cursor: pointer;
        transition:
          border-color 0.15s ease,
          background-color 0.15s ease;
      }

      .logo-drop-zone:hover,
      .logo-drop-zone-active {
        border-color: var(--primary-color, #0d6efd);
        background-color: rgba(13, 110, 253, 0.06);
      }

      .logo-dialog-footer {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding-top: 0.25rem;
      }
    `
  ]
})
export class InstrumentLogoSelectDialogComponent
  extends SimpleDynamicFormBase<SignalDynamicFormComponent<LogoSearchFormModel>>
  implements OnInit
{
  private readonly logoService = inject(InstrumentLogoService);
  private readonly injector = inject(Injector);

  readonly dialogData: InstrumentLogoDialogData = (this.dynamicDialogConfig.data as
    InstrumentLogoDialogData | undefined) ?? {
    securityName: null,
    isin: null,
    idInstrumentLogo: null,
    productLink: null,
    specialInvestmentInstrument: null,
    isTenantPrivate: false,
    resolvedLogo: null
  };

  readonly startValues: Partial<LogoSearchFormModel> = {
    name: this.dialogData.securityName?.trim() || null,
    isin: this.dialogData.isin?.trim().toUpperCase() || null
  };

  readonly results = signal<InstrumentLogoRefDto[]>([]);
  readonly loading = signal(false);
  readonly validating = signal(false);
  readonly deleting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly dragging = signal(false);
  readonly draftRow = signal<DraftLogoState | null>(null);
  readonly editingLogo = signal<EditLogoState | null>(null);
  readonly selectedExisting = signal<InstrumentLogoRefDto | null>(null);
  readonly scopeOptions = signal<SelectItem<LogoScopeName>[]>([]);

  readonly isEditingOrDraft = computed(() => this.draftRow() !== null || this.editingLogo() !== null);
  private readonly hasDraft = computed(() => this.draftRow() !== null);
  private readonly editingLogoId = computed(() => this.editingLogo()?.idInstrumentLogo ?? null);

  readonly activeEditName = computed(() => this.draftRow()?.name ?? this.editingLogo()?.name ?? '');
  readonly activeEditIsin = computed(() => this.draftRow()?.isin ?? this.editingLogo()?.isin ?? '');
  readonly activeEditScope = computed<LogoScopeName>(
    () => this.draftRow()?.logoScope ?? this.editingLogo()?.logoScope ?? 'COMPANY'
  );
  readonly activeEditPreviewUrl = computed(() => this.draftRow()?.previewUrl ?? this.editingLogo()?.previewUrl ?? null);

  readonly canEditSelected = computed(() => {
    if (this.isEditingOrDraft()) {
      return false;
    }
    const selected = this.selectedExisting();
    if (!selected) {
      return false;
    }
    return AuditHelper.hasHigherPrivileges(this.gps) || selected.createdBy === this.gps.getIdUser();
  });

  /**
   * Row array kept structurally stable while editing inline fields (Name/ISIN/Scope/SVG) so PrimeNG's table does not
   * recreate the row DOM and drop input focus on every keystroke. Live editable values flow through separate inputs.
   */
  readonly tableRows = computed<LogoCatalogTableRow[]>(() => {
    const rows: LogoCatalogTableRow[] = [];
    const editId = this.editingLogoId();
    if (this.hasDraft()) {
      rows.push({
        rowKey: DRAFT_ROW_KEY,
        isDraft: true,
        isEditable: true,
        name: '',
        isin: null,
        logoScope: 'COMPANY',
        logoScopeKey: 'LOGO_SCOPE_COMPANY',
        sourceKey: 'INSTRUMENT_LOGO_SOURCE_DRAFT',
        logo: null,
        previewUrl: null
      });
    }
    for (const logo of this.results()) {
      const isEditingThis = editId !== null && editId === logo.idInstrumentLogo;
      rows.push({
        rowKey: String(logo.idInstrumentLogo),
        isDraft: false,
        isEditable: isEditingThis,
        name: logo.name,
        isin: logo.isin,
        logoScope: logo.logoScope,
        logoScopeKey: logo.logoScope === 'ISSUER' ? 'LOGO_SCOPE_ISSUER' : 'LOGO_SCOPE_COMPANY',
        sourceKey: logo.assetPath ? 'INSTRUMENT_LOGO_SOURCE_DEPLOYED' : 'INSTRUMENT_LOGO_SOURCE_DB',
        logo,
        previewUrl: null
      });
    }
    return rows;
  });

  readonly selectedTableRow = computed<LogoCatalogTableRow | null>(() => {
    const rows = this.tableRows();
    if (this.hasDraft()) {
      return rows[0] ?? null;
    }
    const editId = this.editingLogoId();
    if (editId !== null) {
      return rows.find((row) => !row.isDraft && row.logo?.idInstrumentLogo === editId) ?? null;
    }
    const existing = this.selectedExisting();
    if (!existing) {
      return null;
    }
    return rows.find((row) => !row.isDraft && row.logo?.idInstrumentLogo === existing.idInstrumentLogo) ?? null;
  });

  readonly canApply = computed(() => {
    if (this.validating() || this.deleting()) {
      return false;
    }
    const draft = this.draftRow();
    if (draft !== null) {
      return draft.name.trim().length > 0;
    }
    const editing = this.editingLogo();
    if (editing !== null) {
      return editing.name.trim().length > 0;
    }
    return this.selectedExisting() !== null;
  });

  readonly rules = (p: SchemaPathTree<LogoSearchFormModel>): void => {
    disabled(p.name, { when: () => this.isEditingOrDraft() });
    disabled(p.isin, { when: () => this.isEditingOrDraft() });
  };

  private lastSearchName: string | null = this.startValues.name ?? null;
  private lastSearchIsin: string | null = this.startValues.isin ?? null;
  private searchSubscription?: Subscription;
  private searchSeq = 0;
  private dropSeq = 0;
  private destroyed = false;
  private formReady = false;
  private handedOffDraftUrl: string | null = null;

  constructor() {
    super(HelpIds.HELP_WATCHLIST_SECURITY);

    this.translateService
      .get(['LOGO_SCOPE_COMPANY', 'LOGO_SCOPE_ISSUER'])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((texts) => {
        this.scopeOptions.set([
          { label: texts['LOGO_SCOPE_COMPANY'], value: 'COMPANY' },
          { label: texts['LOGO_SCOPE_ISSUER'], value: 'ISSUER' }
        ]);
      });

    effect(() => {
      const locked = this.isEditingOrDraft();
      const isLoading = this.loading();
      untracked(() => {
        if (this.formReady) {
          this.form().setButtonState('submit', { disabled: locked || isLoading, loading: isLoading });
        }
      });
    });

    afterNextRender(
      () => {
        this.formReady = true;
        this.form().setButtonState('submit', { disabled: this.isEditingOrDraft() || this.loading() });
      },
      { injector: this.injector }
    );

    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.searchSubscription?.unsubscribe();
      const draft = this.draftRow();
      if (draft && draft.previewUrl !== this.handedOffDraftUrl) {
        URL.revokeObjectURL(draft.previewUrl);
      }
      const editing = this.editingLogo();
      if (editing?.previewUrl) {
        URL.revokeObjectURL(editing.previewUrl);
      }
    });
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 80, false),
      DynamicFieldHelper.createFieldInputStringHeqF('isin', 12, false, { upperCase: true }),
      DynamicFieldHelper.createSubmitButton('SEARCH')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.executeSearch(true, this.lastSearchName, this.lastSearchIsin);
  }

  submit(value: { [name: string]: unknown }): void {
    if (this.isEditingOrDraft()) {
      this.form().enableSubmit();
      return;
    }
    const name = typeof value['name'] === 'string' ? value['name'] : null;
    const isin = typeof value['isin'] === 'string' ? value['isin'] : null;
    this.lastSearchName = name;
    this.lastSearchIsin = isin;
    this.executeSearch(false, name, isin);
  }

  onRowSelected(row: LogoCatalogTableRow | null): void {
    if (this.isEditingOrDraft()) {
      return;
    }
    this.selectedExisting.set(row && !row.isDraft ? row.logo : null);
    this.errorMessage.set(null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.loadDroppedFile(file);
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.loadDroppedFile(file);
    }
  }

  startEditSelected(): void {
    const selected = this.selectedExisting();
    if (!selected || !this.canEditSelected()) {
      return;
    }
    this.errorMessage.set(null);
    this.editingLogo.set({
      idInstrumentLogo: selected.idInstrumentLogo,
      name: selected.name,
      isin: selected.isin,
      logoScope: selected.logoScope,
      svgContent: null,
      previewUrl: null
    });
  }

  cancelEdit(): void {
    const current = this.editingLogo();
    if (current?.previewUrl) {
      URL.revokeObjectURL(current.previewUrl);
    }
    this.editingLogo.set(null);
    this.errorMessage.set(null);
  }

  deleteSelected(): void {
    const selected = this.selectedExisting();
    if (!selected || !this.canEditSelected()) {
      return;
    }
    this.deleting.set(true);
    this.errorMessage.set(null);
    this.logoService
      .delete(selected.idInstrumentLogo)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.results.update((list) => list.filter((item) => item.idInstrumentLogo !== selected.idInstrumentLogo));
          this.selectedExisting.set(null);
        },
        error: (err) => {
          this.deleting.set(false);
          this.errorMessage.set(this.extractErrorMessage(err));
        }
      });
  }

  updateEditableName(name: string): void {
    if (this.draftRow() !== null) {
      this.draftRow.update((draft) => (draft ? { ...draft, name } : null));
    } else {
      this.editingLogo.update((editing) => (editing ? { ...editing, name } : null));
    }
  }

  updateEditableIsin(isin: string): void {
    const value = isin.length > 0 ? isin : null;
    if (this.draftRow() !== null) {
      this.draftRow.update((draft) => (draft ? { ...draft, isin: value, isinInUseHint: false } : null));
    } else {
      this.editingLogo.update((editing) => (editing ? { ...editing, isin: value } : null));
    }
  }

  updateEditableScope(logoScope: LogoScopeName): void {
    if (this.draftRow() !== null) {
      this.draftRow.update((draft) => {
        if (!draft) {
          return null;
        }
        return logoScope === 'ISSUER'
          ? { ...draft, logoScope, isin: null, isinInUseHint: false }
          : { ...draft, logoScope };
      });
    } else {
      this.editingLogo.update((editing) => {
        if (!editing) {
          return null;
        }
        return logoScope === 'ISSUER' ? { ...editing, logoScope, isin: null } : { ...editing, logoScope };
      });
    }
  }

  discardDraft(): void {
    const current = this.draftRow();
    if (current) {
      URL.revokeObjectURL(current.previewUrl);
    }
    this.draftRow.set(null);
    this.errorMessage.set(null);
  }

  cancel(): void {
    this.dynamicDialogRef.close(null);
  }

  apply(): void {
    const editing = this.editingLogo();
    if (editing !== null) {
      const trimmedName = editing.name.trim();
      if (!trimmedName) {
        return;
      }
      const payload: InstrumentLogoUpdatePayload = {
        idInstrumentLogo: editing.idInstrumentLogo,
        name: trimmedName,
        isin: editing.logoScope === 'ISSUER' ? null : normalizeIsinInput(editing.isin),
        logoScope: editing.logoScope,
        svgContent: editing.svgContent
      };
      this.validating.set(true);
      this.errorMessage.set(null);
      this.logoService
        .update(payload)
        .pipe(this.untilDialogClosed())
        .subscribe({
          next: (updated) => {
            this.validating.set(false);
            if (editing.previewUrl) {
              URL.revokeObjectURL(editing.previewUrl);
            }
            this.editingLogo.set(null);
            const result: InstrumentLogoDialogResult = { kind: 'existing', logo: updated };
            this.dynamicDialogRef.close(result);
          },
          error: (err) => {
            this.validating.set(false);
            this.errorMessage.set(this.extractErrorMessage(err));
          }
        });
      return;
    }

    const draft = this.draftRow();
    if (draft === null) {
      const selected = this.selectedExisting();
      if (selected) {
        const result: InstrumentLogoDialogResult = { kind: 'existing', logo: selected };
        this.dynamicDialogRef.close(result);
      }
      return;
    }

    const trimmedName = draft.name.trim();
    if (!trimmedName) {
      return;
    }

    const payload: InstrumentLogoUploadPayload = {
      name: trimmedName,
      isin: draft.logoScope === 'ISSUER' ? null : normalizeIsinInput(draft.isin),
      logoScope: draft.logoScope,
      svgContent: draft.svgContent
    };

    this.validating.set(true);
    this.errorMessage.set(null);
    this.logoService
      .validate(payload)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: () => {
          this.validating.set(false);
          this.handedOffDraftUrl = draft.previewUrl;
          const result: InstrumentLogoDialogResult = {
            kind: 'draft',
            draft: { ...payload, previewUrl: draft.previewUrl }
          };
          this.dynamicDialogRef.close(result);
        },
        error: (err) => {
          this.validating.set(false);
          this.errorMessage.set(this.extractErrorMessage(err));
        }
      });
  }

  private executeSearch(includeSecurityContext: boolean, name: string | null, isin: string | null): void {
    const seq = ++this.searchSeq;
    this.searchSubscription?.unsubscribe();
    this.loading.set(true);
    this.errorMessage.set(null);

    const params = includeSecurityContext
      ? {
          name,
          isin,
          idInstrumentLogo: this.dialogData.idInstrumentLogo,
          productLink: this.dialogData.productLink,
          specialInvestmentInstrument: this.dialogData.specialInvestmentInstrument
        }
      : { name, isin };

    this.searchSubscription = this.logoService
      .search(params)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (logos) => {
          // Ignore stale search results and never disturb an active drag-and-drop draft or inline edit.
          if (seq !== this.searchSeq || this.isEditingOrDraft()) {
            this.loading.set(false);
            this.releaseSearchButton(seq);
            return;
          }
          this.releaseSearchButton(seq);
          this.results.set(logos);
          this.loading.set(false);
          if (includeSecurityContext) {
            const targetId = this.dialogData.resolvedLogo?.idInstrumentLogo ?? this.dialogData.idInstrumentLogo;
            if (targetId != null) {
              const matched = logos.find((item) => item.idInstrumentLogo === targetId) ?? null;
              this.selectedExisting.set(matched);
            }
          } else {
            const currentId = this.selectedExisting()?.idInstrumentLogo;
            this.selectedExisting.set(
              currentId != null ? (logos.find((item) => item.idInstrumentLogo === currentId) ?? null) : null
            );
          }
        },
        error: () => {
          if (seq === this.searchSeq) {
            this.loading.set(false);
          }
          this.releaseSearchButton(seq);
        }
      });
  }

  /**
   * The signal form locks its submit button from the submit until the owner releases it. A search never closes the
   * dialog, so every finished request of the latest search releases it; otherwise only the first search could run.
   */
  private releaseSearchButton(seq: number): void {
    if (seq === this.searchSeq && this.formReady) {
      this.form().enableSubmit();
    }
  }

  private loadDroppedFile(file: File): void {
    this.errorMessage.set(null);
    if (!isSvgFile(file.name, file.type)) {
      this.errorMessage.set(this.translateService.instant('INSTRUMENT_LOGO_ONLY_SVG'));
      return;
    }
    if (file.size > MAX_LOGO_SVG_BYTES) {
      this.errorMessage.set(this.translateService.instant('gt.logo.file.too.large'));
      return;
    }

    const seq = ++this.dropSeq;
    file.text().then(
      (svgContent) => {
        if (this.destroyed || seq !== this.dropSeq) {
          return;
        }
        if (utf8ByteLength(svgContent) > MAX_LOGO_SVG_BYTES) {
          this.errorMessage.set(this.translateService.instant('gt.logo.file.too.large'));
          return;
        }
        const activeEdit = this.editingLogo();
        if (activeEdit !== null) {
          if (activeEdit.previewUrl) {
            URL.revokeObjectURL(activeEdit.previewUrl);
          }
          const previewUrl = URL.createObjectURL(new Blob([svgContent], { type: 'image/svg+xml' }));
          this.editingLogo.set({ ...activeEdit, svgContent, previewUrl });
          return;
        }
        const previous = this.draftRow();
        if (previous) {
          URL.revokeObjectURL(previous.previewUrl);
        }
        const currentSearchName =
          this.formReady && typeof this.form().value('name') === 'string'
            ? (this.form().value('name') as string)
            : this.lastSearchName;
        const currentSearchIsin =
          this.formReady && typeof this.form().value('isin') === 'string'
            ? (this.form().value('isin') as string)
            : this.lastSearchIsin;

        const prefill = computeLogoDraftPrefill({
          securityName: this.dialogData.securityName,
          searchName: currentSearchName,
          securityIsin: this.dialogData.isin,
          searchIsin: currentSearchIsin,
          specialInvestmentInstrument: this.dialogData.specialInvestmentInstrument,
          isTenantPrivate: this.dialogData.isTenantPrivate,
          resolvedLogo: this.dialogData.resolvedLogo,
          catalogResults: this.results()
        });
        const previewUrl = URL.createObjectURL(new Blob([svgContent], { type: 'image/svg+xml' }));
        this.draftRow.set({
          name: prefill.name,
          isin: prefill.isin,
          logoScope: prefill.logoScope,
          svgContent,
          previewUrl,
          isinInUseHint: prefill.isinInUseHint
        });
      },
      () => {
        if (!this.destroyed && seq === this.dropSeq) {
          this.errorMessage.set(this.translateService.instant('gt.logo.svg.invalid'));
        }
      }
    );
  }

  private extractErrorMessage(err: unknown): string {
    if (err instanceof TransformedError) {
      if (err.msgKey) {
        return this.translateService.instant(err.msgKey, err.interpolateParams);
      }
      if (!err.isEmtpy()) {
        return err.msg;
      }
      const nested = err.errorClass?.getTransformedError();
      const nestedMsgOrKey = nested?.getMsgOrKey();
      if (nestedMsgOrKey) {
        return nested?.msgKey ? this.translateService.instant(nested.msgKey, nested.interpolateParams) : nestedMsgOrKey;
      }
    }
    return this.translateService.instant('gt.logo.svg.invalid');
  }
}
