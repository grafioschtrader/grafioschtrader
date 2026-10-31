import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Portfolio } from '../../entities/portfolio';
import { Cashaccount } from '../../entities/cashaccount';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { Currencypair } from '../../entities/currencypair';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HistoryquoteService } from '../../historyquote/service/historyquote.service';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import moment from 'moment';
import { afterNextRender, AfterRenderRef, DestroyRef, Directive, inject, Injector, signal } from '@angular/core';
import { MonoTypeOperatorFunction, Subscription } from 'rxjs';
import { untilSessionEnds } from '../../lib/edit/dialog.session';
import { FormBase } from '../../lib/edit/form.base';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';

/**
 * Abstract base class providing common operations for transaction handling, including cash account lookups,
 * currency exchange rate calculations, and form field management. Serves as a foundation for various
 * transaction-related components that need standardized portfolio and currency conversion functionality.
 */
@Directive()
export abstract class TransactionBaseOperations extends FormBase<FieldFormGroup> {
  public messageToastService: MessageToastService = inject(MessageToastService);
  public currencypairService: CurrencypairService = inject(CurrencypairService);
  public historyquoteService: HistoryquoteService = inject(HistoryquoteService);
  public translateService: TranslateService = inject(TranslateService);
  protected gps: GlobalparameterService = inject(GlobalparameterService);

  protected readonly destroyRef = inject(DestroyRef);
  protected readonly injector = inject(Injector);
  private dialogSession = new Subscription();
  private initializeRender?: AfterRenderRef;
  private readonly requests = new Map<string, Subscription>();
  /** Current currency pair used for exchange rate calculations */
  protected currencypair?: Currencypair | null;

  /** Indicates if the transaction is locked due to being within a closed period */
  readonly transactionLocked = signal(false);

  /** Message displayed when the transaction is locked */
  readonly transactionLockedMessage = signal('');

  /**
   * Creates a new TransactionBaseOperations instance with required services.
   */
  constructor() {
    super();
    this.destroyRef.onDestroy(() => this.endDialogSession());
  }

  protected initializeAfterRender(initialize: () => void): void {
    this.endDialogSession();
    this.dialogSession = new Subscription();
    this.initializeRender = afterNextRender(() => initialize(), { injector: this.injector });
  }

  protected endDialogSession(): void {
    this.initializeRender?.destroy();
    this.dialogSession.unsubscribe();
    this.requests.clear();
  }

  protected replaceDialogRequest(key: string, subscribe: () => Subscription): void {
    this.cancelDialogRequest(key);
    const request = subscribe();
    this.requests.set(key, request);
    this.dialogSession.add(request);
  }

  protected cancelDialogRequest(key: string): void {
    this.requests.get(key)?.unsubscribe();
    this.requests.delete(key);
  }

  protected untilDialogClosed<T>(): MonoTypeOperatorFunction<T> {
    return untilSessionEnds<T>(this.dialogSession);
  }

  /** Returns whether the transaction dialog is currently visible */
  abstract isVisibleDialog(): boolean;

  /**
   * Searches for a cash account across multiple portfolios by ID.
   * @param portfolios Array of portfolios to search through
   * @param idSecuritycashaccount ID of the cash account to find
   * @returns Object containing the found cash account and its portfolio, or null if not found
   */
  getCashaccountByIdCashaccountFormPortfolios(
    portfolios: Portfolio[],
    idSecuritycashaccount: number | null | undefined
  ): {
    cashaccount: Cashaccount;
    portfolio: Portfolio;
  } | null {
    if (idSecuritycashaccount != null) {
      for (const portfolio of portfolios) {
        const found = this.getCashaccountByIdCashaccountFromPortfolio(portfolio, idSecuritycashaccount);
        if (found) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Finds a cash account by ID within a collection of portfolios.
   * @param portfolios Array of portfolios to search
   * @param idSecuritycashaccount ID of the cash account to locate
   * @returns Object with cash account and portfolio reference, or null if not found
   */
  getCashaccountByIdCashaccountFromPortfolios(
    portfolios: Portfolio[],
    idSecuritycashaccount: number | null | undefined
  ): {
    cashaccount: Cashaccount;
    portfolio: Portfolio;
  } | null {
    if (idSecuritycashaccount == null) return null;
    for (const portfolio of portfolios) {
      const found = this.getCashaccountByIdCashaccountFromPortfolio(portfolio, idSecuritycashaccount);
      if (found) {
        return found;
      }
    }
    return null;
  }

  /**
   * Locates a specific cash account within a single portfolio.
   * @param portfolio Portfolio to search within
   * @param idSecuritycashaccount ID of the cash account to find
   * @returns Object containing the cash account and portfolio, or null if not found
   */
  getCashaccountByIdCashaccountFromPortfolio(
    portfolio: Portfolio,
    idSecuritycashaccount: number
  ): {
    cashaccount: Cashaccount;
    portfolio: Portfolio;
  } | null {
    for (const cashaccount of portfolio.cashaccountList ?? []) {
      if (cashaccount.idSecuritycashAccount === idSecuritycashaccount) {
        return { cashaccount, portfolio };
      }
    }
    return null;
  }

  /**
   * Calculates and sets the inverse of the current exchange rate.
   * @param event Event object from user interaction
   */
  oneOverX(event: unknown): void {
    requireFormControl(this.configObject.currencyExRate).setValue(
      1 / requireFormControl(this.configObject.currencyExRate).value
    );
  }

  /**
   * Retrieves and sets the appropriate exchange rate based on transaction timing.
   * @param event Event object from user interaction
   */
  getTimeDependingExchangeRate(event: unknown): void {
    const transactionTime: Date = requireFormControl(this.configObject.transactionTime).value;
    const currencypair = this.currencypair;
    if (!currencypair || transactionTime == null) return;
    const id = currencypair.idSecuritycurrency;
    if (id != null) {
      this.replaceDialogRequest('exchangeRate', () =>
        BusinessHelper.setHistoryquoteCloseToFormControl(
          this.messageToastService,
          this.historyquoteService,
          this.gps,
          transactionTime,
          id,
          false,
          requireFormControl(this.configObject.currencyExRate)
        )
      );
    } else {
      this.replaceDialogRequest('exchangeRate', () =>
        BusinessHelper.getAndSetQuotationCurrencypair(
          this.currencypairService,
          currencypair,
          transactionTime,
          requireFormControl(this.configObject.currencyExRate)
        )
      );
    }
  }

  /**
   * Creates form field configurations for exchange rate operation buttons.
   * @returns Array of field configurations for exchange rate buttons
   */
  protected createExRateButtons(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFunctionButtonFieldName('oneOverX', 'ONE_OVER_X', (e) => this.oneOverX(e), {
        buttonInForm: true,
        usedLayoutColumns: 1
      }),
      DynamicFieldHelper.createFunctionButtonFieldName(
        'exRateButton',
        'EX_RATE_SYMBOL',
        (e) => this.getTimeDependingExchangeRate(e),
        {
          buttonInForm: true,
          usedLayoutColumns: 1
        }
      )
    ];
  }

  /** Updates the enabled/disabled state of exchange rate buttons based on form validity */
  protected disableEnableExchangeRateButtons(): void {
    this.configObject.exRateButton.disabled =
      requireFormControl(this.configObject.currencyExRate).disabled ||
      !requireFormControl(this.configObject.transactionTime).valid;
    this.configObject.oneOverX.disabled = requireFormControl(this.configObject.currencyExRate).disabled;
  }

  /**
   * Resets the transaction locked state. Should be called when initializing for a new transaction.
   */
  protected resetTransactionLocked(): void {
    this.transactionLocked.set(false);
    this.transactionLockedMessage.set('');
    if (this.configObjectIfInitialized?.submit) {
      this.configObject.submit.invisible = false;
    }
  }

  /**
   * Checks if an existing transaction is within the closed period and locks the form if so.
   * When locked, sets the transactionLocked flag, displays a message, and hides the submit button.
   *
   * @param effectiveClosedUntil The effective closedUntil date from portfolio or tenant
   * @param transactionTime The transaction time to check (from existing transaction)
   */
  protected checkTransactionLocked(
    effectiveClosedUntil: Date | null,
    transactionTime: Date | string | number | null
  ): void {
    if (effectiveClosedUntil && transactionTime) {
      const transactionDate = new Date(transactionTime);
      // Compare dates only (ignore time component)
      const transactionDateOnly = new Date(
        transactionDate.getFullYear(),
        transactionDate.getMonth(),
        transactionDate.getDate()
      );
      const closedUntilDateOnly = new Date(
        effectiveClosedUntil.getFullYear(),
        effectiveClosedUntil.getMonth(),
        effectiveClosedUntil.getDate()
      );

      if (transactionDateOnly <= closedUntilDateOnly) {
        this.transactionLocked.set(true);
        const formattedDate = moment(effectiveClosedUntil).format(this.gps.getDateFormat());
        this.translateService
          .get('TRANSACTION_LOCKED_MESSAGE', { date: formattedDate })
          .pipe(this.untilDialogClosed())
          .subscribe((msg) => this.transactionLockedMessage.set(msg));
        // Hide the submit button
        if (this.configObject.submit) {
          this.configObject.submit.invisible = true;
        }
      }
    }
  }
}
