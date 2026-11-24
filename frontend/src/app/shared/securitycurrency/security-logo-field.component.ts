import { Component, computed, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { forkJoin, Observable, of, Subject, Subscription } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
import { Security } from '../../entities/security';
import { DynamicDialogHelper } from '../../lib/dynamicdialog/component/dynamicDialogHelper';
import type { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { InstrumentLogoSelectDialogComponent } from './instrument-logo-select-dialog.component';
import {
  InstrumentLogoDialogData,
  InstrumentLogoDialogResult,
  InstrumentLogoRefDto,
  InstrumentLogoResolveParams,
  InstrumentLogoUploadDraft,
  toSpecialInvestmentInstrumentName
} from './instrument-logo.model';
import { InstrumentLogoService } from './instrument-logo.service';

/**
 * Compact clickable logo control shared by SecurityEditComponent and SecurityDerivedEditComponent.
 *
 * Shows the currently resolved SVG (deployed, DB-stored or in-memory upload draft), opens Dialog (D) as a
 * DynamicDialog on click, updates automatic resolution live while the user edits master data, supports resetting to
 * the automatic default, and shows current vs. proposed logos side-by-side in PROPOSAL_REVIEW mode.
 */
@Component({
  selector: 'security-logo-field',
  imports: [TranslateModule, ButtonModule, TooltipModule],
  template: `
    <div class="security-logo-bar">
      <span class="security-logo-label">{{ 'LOGO' | translate }}</span>

      @if (proposalLogoChanged()) {
        <div class="security-logo-proposal">
          <div class="security-logo-proposal-item">
            <span class="security-logo-sublabel">{{ 'INSTRUMENT_LOGO_CURRENT' | translate }}</span>
            <div class="security-logo-badge security-logo-badge-readonly" [pTooltip]="currentLogoSummary()">
              @if (proposalCurrentUrl(); as url) {
                <img [src]="url" width="64" height="24" alt="" />
              } @else {
                <span class="security-logo-empty">{{ 'INSTRUMENT_LOGO_NONE' | translate }}</span>
              }
            </div>
            <span class="security-logo-meta">{{ currentLogoSummary() }}</span>
          </div>

          <i class="pi pi-arrow-right text-muted"></i>

          <div class="security-logo-proposal-item">
            <span class="security-logo-sublabel">{{ 'INSTRUMENT_LOGO_PROPOSED' | translate }}</span>
            <button
              type="button"
              class="security-logo-badge"
              [pTooltip]="activeLogoTooltip()"
              (click)="openSelectDialog()">
              @if (activeLogoUrl(); as url) {
                <img [src]="url" width="64" height="24" alt="" />
              } @else {
                <span class="security-logo-empty">{{ 'INSTRUMENT_LOGO_NONE' | translate }}</span>
              }
            </button>
            <span class="security-logo-meta">{{ activeLogoSummary() }}</span>
          </div>
        </div>
      } @else {
        <button type="button" class="security-logo-badge" [pTooltip]="activeLogoTooltip()" (click)="openSelectDialog()">
          @if (activeLogoUrl(); as url) {
            <img [src]="url" width="64" height="24" alt="" />
          } @else {
            <span class="security-logo-empty">{{ 'INSTRUMENT_LOGO_NONE' | translate }}</span>
          }
        </button>

        @if (activeLogoSummary(); as summary) {
          <span class="security-logo-meta">{{ summary }}</span>
        }
      }

      @if (canReset()) {
        <p-button
          type="button"
          severity="secondary"
          text="true"
          size="small"
          [pTooltip]="'INSTRUMENT_LOGO_RESET_DEFAULT' | translate"
          (onClick)="resetToDefault()">
          <i class="pi pi-undo" pButtonIcon></i>
        </p-button>
      }
    </div>
  `,
  styles: [
    `
      .security-logo-bar {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.6rem;
        margin: 0 0.75rem 0.5rem 0.75rem;
        padding: 0.35rem 0.6rem;
        border: 1px solid var(--surface-border, #dee2e6);
        border-radius: 4px;
      }

      .security-logo-label {
        font-weight: 600;
        min-width: 4rem;
      }

      .security-logo-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 72px;
        height: 32px;
        padding: 2px 4px;
        border-radius: 4px;
        border: 1px solid var(--surface-border, #ced4da);
        background: #ffffff;
        cursor: pointer;
      }

      .security-logo-badge:hover {
        border-color: var(--primary-color, #0d6efd);
      }

      .security-logo-badge-readonly {
        cursor: default;
        opacity: 0.85;
      }

      .security-logo-badge-readonly:hover {
        border-color: var(--surface-border, #ced4da);
      }

      .security-logo-badge img {
        max-width: 64px;
        max-height: 24px;
        object-fit: contain;
      }

      .security-logo-empty {
        font-size: 0.7rem;
        color: #6c757d;
        text-align: center;
        line-height: 1.1;
      }

      .security-logo-meta {
        font-size: 0.82rem;
        color: var(--text-color-secondary, #6c757d);
      }

      .security-logo-proposal {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex-wrap: wrap;
      }

      .security-logo-proposal-item {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 0.15rem;
      }

      .security-logo-sublabel {
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--text-color-secondary, #6c757d);
      }
    `
  ]
})
export class SecurityLogoFieldComponent {
  private readonly logoService = inject(InstrumentLogoService);
  private readonly dialogService = inject(DialogService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly securityName = input<string | null | undefined>(null);
  readonly isin = input<string | null | undefined>(null);
  readonly productLink = input<string | null | undefined>(null);
  readonly specialInvestmentInstrument = input<string | null | undefined>(null);
  readonly isTenantPrivate = input<boolean>(false);
  readonly dataLoaded = input<boolean>(false);

  /** Emitted whenever the user picks a logo, attaches a draft or resets to automatic default. */
  readonly logoChanged = output<void>();

  private readonly explicitLogoId = signal<number | null>(null);
  private readonly resolvedLogo = signal<InstrumentLogoRefDto | null>(null);
  private readonly pendingLogoUpload = signal<InstrumentLogoUploadDraft | null>(null);
  private readonly proposalCurrentLogo = signal<InstrumentLogoRefDto | null>(null);
  readonly proposalLogoChanged = signal<boolean>(false);

  readonly canReset = computed(() => this.explicitLogoId() !== null || this.pendingLogoUpload() !== null);

  readonly activeLogoUrl = computed<string | null>(() => {
    const draft = this.pendingLogoUpload();
    if (draft) {
      return draft.previewUrl;
    }
    this.logoService.revision();
    return this.logoService.getLogoUrl(this.resolvedLogo());
  });

  readonly proposalCurrentUrl = computed<string | null>(() => {
    this.logoService.revision();
    return this.logoService.getLogoUrl(this.proposalCurrentLogo());
  });

  readonly activeLogoSummary = computed<string>(() => {
    const draft = this.pendingLogoUpload();
    if (draft) {
      return this.formatDraftSummary(draft);
    }
    return this.formatRefSummary(this.resolvedLogo());
  });

  readonly currentLogoSummary = computed<string>(() => this.formatRefSummary(this.proposalCurrentLogo()));

  readonly activeLogoTooltip = computed<string>(() => {
    const summary = this.activeLogoSummary();
    const hint = this.translateService.instant('INSTRUMENT_LOGO_CLICK_TO_CHANGE');
    return summary ? `${summary} — ${hint}` : hint;
  });

  private readonly liveResolve$ = new Subject<InstrumentLogoResolveParams>();
  private proposalSub?: Subscription;
  private immediateResolveSub?: Subscription;
  private logoDialogRef: DynamicDialogRef | null = null;
  private initializedForCurrentSession = false;

  constructor() {
    this.liveResolve$
      .pipe(
        debounceTime(300),
        switchMap((params) => this.logoService.resolve(params)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((resolved) => {
        if (this.explicitLogoId() === null && this.pendingLogoUpload() === null) {
          this.resolvedLogo.set(resolved);
        }
      });

    // Live-resolve automatic logo whenever master data inputs change while on automatic default.
    effect(() => {
      const loaded = this.dataLoaded();
      const explicitId = this.explicitLogoId();
      const pending = this.pendingLogoUpload();
      const name = this.securityName() ?? null;
      const isin = this.isTenantPrivate() ? null : (this.isin() ?? null);
      const productLink = this.productLink() ?? null;
      const specialInvestmentInstrument = this.specialInvestmentInstrument() ?? null;

      untracked(() => {
        if (loaded && this.initializedForCurrentSession && explicitId === null && pending === null) {
          this.liveResolve$.next({
            idInstrumentLogo: null,
            isin,
            name,
            productLink,
            specialInvestmentInstrument
          });
        }
      });
    });

    this.destroyRef.onDestroy(() => this.resetState());
  }

  /**
   * Initializes the logo state once a security dialog finishes loading its data. Resolves both original and proposed
   * logos in PROPOSAL_REVIEW mode when idInstrumentLogo changed (including resets to null).
   */
  initializeForEntity(
    security: Security | null | undefined,
    proposal: ProposeChangeEntityWithEntity | null | undefined
  ): void {
    this.discardPendingUpload();
    this.proposalSub?.unsubscribe();
    this.immediateResolveSub?.unsubscribe();

    const originalEntity = (proposal?.entity as Security | undefined) ?? null;
    const activeEntity = (proposal?.proposedEntity as Security | undefined) ?? security ?? null;
    const activeExplicitId = activeEntity?.idInstrumentLogo ?? null;

    this.explicitLogoId.set(activeExplicitId);
    this.resolvedLogo.set(activeEntity?.resolvedLogo ?? null);
    this.proposalCurrentLogo.set(null);

    const hasProposalLogoDiff =
      proposal != null &&
      ((originalEntity?.idInstrumentLogo ?? null) !== (activeEntity?.idInstrumentLogo ?? null) ||
        proposal.proposeChangeEntity.proposeChangeFieldList.some((field) => field.field === 'idInstrumentLogo'));
    this.proposalLogoChanged.set(hasProposalLogoDiff);
    this.initializedForCurrentSession = true;

    if (hasProposalLogoDiff && originalEntity && activeEntity) {
      this.proposalSub = forkJoin([
        this.logoService.resolve(this.resolveParamsFromSecurity(originalEntity)),
        this.logoService.resolve(this.resolveParamsFromSecurity(activeEntity))
      ])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(([currentResolved, proposedResolved]) => {
          this.proposalCurrentLogo.set(currentResolved);
          if (this.pendingLogoUpload() === null && this.explicitLogoId() === activeExplicitId) {
            this.resolvedLogo.set(proposedResolved);
          }
        });
    } else if (activeEntity && (!activeEntity.resolvedLogo || activeExplicitId !== null)) {
      this.immediateResolveSub = this.logoService
        .resolve(this.resolveParamsFromSecurity(activeEntity))
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((resolved) => {
          if (this.pendingLogoUpload() === null && this.explicitLogoId() === activeExplicitId) {
            this.resolvedLogo.set(resolved);
          }
        });
    }
  }

  /** Opens Dialog (D) as a DynamicDialog and applies the confirmed selection or validated upload draft. */
  openSelectDialog(): void {
    const dialogData: InstrumentLogoDialogData = {
      securityName: this.securityName() ?? null,
      isin: this.isTenantPrivate() ? null : (this.isin() ?? null),
      idInstrumentLogo: this.explicitLogoId(),
      productLink: this.productLink() ?? null,
      specialInvestmentInstrument: this.specialInvestmentInstrument() ?? null,
      isTenantPrivate: this.isTenantPrivate(),
      resolvedLogo: this.resolvedLogo()
    };

    this.logoDialogRef?.close();
    new DynamicDialogHelper(
      this.translateService,
      this.dialogService,
      InstrumentLogoSelectDialogComponent,
      'INSTRUMENT_LOGO_SELECT'
    )
      .openDynamicDialog(780, dialogData)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((ref) => {
        this.logoDialogRef = ref;
        ref.onClose
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((result: InstrumentLogoDialogResult | null | undefined) => {
            if (this.logoDialogRef === ref) {
              this.logoDialogRef = null;
            }
            if (!result) {
              return;
            }
            if (result.kind === 'existing') {
              this.discardPendingUpload();
              this.explicitLogoId.set(result.logo.idInstrumentLogo);
              this.resolvedLogo.set({ ...result.logo, explicit: true });
              this.logoChanged.emit();
            } else if (result.kind === 'draft') {
              this.discardPendingUpload();
              this.pendingLogoUpload.set(result.draft);
              this.logoChanged.emit();
            }
          });
      });
  }

  /** Resets an explicit logo or pending upload draft back to automatic backend resolution. */
  resetToDefault(): void {
    this.discardPendingUpload();
    this.explicitLogoId.set(null);
    this.immediateResolveSub?.unsubscribe();
    this.immediateResolveSub = this.logoService
      .resolve({
        idInstrumentLogo: null,
        isin: this.isTenantPrivate() ? null : (this.isin() ?? null),
        name: this.securityName() ?? null,
        productLink: this.productLink() ?? null,
        specialInvestmentInstrument: this.specialInvestmentInstrument() ?? null
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resolved) => {
        if (this.explicitLogoId() === null && this.pendingLogoUpload() === null) {
          this.resolvedLogo.set(resolved);
        }
      });
    this.logoChanged.emit();
  }

  /**
   * Persists a pending upload draft via POST /api/instrumentlogo if one exists, clears the draft so a retry after a
   * security validation failure does not re-upload it, and emits the final idInstrumentLogo (or null) for saving on
   * Security.
   */
  prepareLogoIdForSave(): Observable<number | null> {
    const draft = this.pendingLogoUpload();
    if (!draft) {
      return of(this.explicitLogoId());
    }
    return this.logoService
      .create({
        name: draft.name,
        isin: draft.isin,
        logoScope: draft.logoScope,
        svgContent: draft.svgContent
      })
      .pipe(
        map((created) => {
          this.discardPendingUpload();
          this.explicitLogoId.set(created.idInstrumentLogo);
          this.resolvedLogo.set(created);
          return created.idInstrumentLogo;
        })
      );
  }

  /** Discards any unpersisted draft, revokes its blob URL and closes an open child dialog. */
  resetState(): void {
    this.initializedForCurrentSession = false;
    this.proposalSub?.unsubscribe();
    this.immediateResolveSub?.unsubscribe();
    this.logoDialogRef?.close();
    this.logoDialogRef = null;
    this.discardPendingUpload();
    this.explicitLogoId.set(null);
    this.resolvedLogo.set(null);
    this.proposalCurrentLogo.set(null);
    this.proposalLogoChanged.set(false);
  }

  private discardPendingUpload(): void {
    const draft = this.pendingLogoUpload();
    if (draft) {
      URL.revokeObjectURL(draft.previewUrl);
      this.pendingLogoUpload.set(null);
    }
  }

  private resolveParamsFromSecurity(security: Security): InstrumentLogoResolveParams {
    return {
      idInstrumentLogo: security.idInstrumentLogo ?? null,
      isin: security.idTenantPrivate != null ? null : (security.isin ?? null),
      name: security.name ?? null,
      productLink: security.productLink ?? null,
      specialInvestmentInstrument: toSpecialInvestmentInstrumentName(security.assetClass?.specialInvestmentInstrument)
    };
  }

  private formatRefSummary(ref: InstrumentLogoRefDto | null): string {
    if (!ref) {
      return this.translateService.instant('INSTRUMENT_LOGO_NONE');
    }
    const mode = this.translateService.instant(ref.explicit ? 'INSTRUMENT_LOGO_EXPLICIT' : 'INSTRUMENT_LOGO_AUTO');
    const isinPart = ref.isin ? ` (${ref.isin})` : '';
    return `${ref.name}${isinPart} · ${mode}`;
  }

  private formatDraftSummary(draft: InstrumentLogoUploadDraft): string {
    const pendingLabel = this.translateService.instant('INSTRUMENT_LOGO_PENDING_UPLOAD');
    const isinPart = draft.isin ? ` (${draft.isin})` : '';
    return `${draft.name}${isinPart} · ${pendingLabel}`;
  }
}
