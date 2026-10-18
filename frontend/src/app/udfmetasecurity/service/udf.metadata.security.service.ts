import { Injectable } from '@angular/core';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { LoginService } from '../../lib/login/service/log-in.service';
import { HttpClient } from '@angular/common/http';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { DeleteReadAllService } from '../../lib/udfmeta/components/udf.metadata.table';
import { BaseSettings } from '../../lib/base.settings';
import {
  FieldDescriptorInputAndShowExtendedSecurity,
  RuleProposalPreview,
  UDFFillProposal,
  UDFFillProposalDecision,
  UDFFillProposalDecisionResult,
  RuleProposalResult,
  UDFFillSourcesOffer,
  UDFFillStatus,
  UDFFillTestRequest,
  UDFFillTestResult,
  UDFMetadataSecurity,
  UDFSuggestTestCandidate,
  UDFSuggestTestResult
} from '../model/udf.metadata.security';

/**
 * Service for managing security-specific UDF metadata definitions via REST API.
 * Handles CRUD operations for user-defined field metadata applicable to securities and financial instruments.
 * Supports asset class and instrument type filtering to ensure UDF fields appear only for relevant security types.
 */
@Injectable()
export class UDFMetadataSecurityService
  extends AuthServiceWithLogout<UDFMetadataSecurity>
  implements DeleteReadAllService<UDFMetadataSecurity>, ServiceEntityUpdate<UDFMetadataSecurity>
{
  /**
   * Creates the UDF metadata security service.
   *
   * @param loginService - Service for authentication and session management
   * @param httpClient - HTTP client for REST API calls
   * @param messageToastService - Service for displaying user notifications
   */
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all security UDF metadata entries accessible to the current user.
   * Returns both user-specific metadata (owned by the current user) and system-wide metadata (idUser = 0).
   *
   * @returns Observable emitting array of security UDF metadata entries
   */
  public getAllByIdUser(): Observable<UDFMetadataSecurity[]> {
    return <Observable<UDFMetadataSecurity[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Retrieves field descriptors for active security UDF fields, ordered by UI display order.
   * Returns only enabled fields (excludes user-disabled special types) for dynamic form generation.
   * Used when displaying UDF fields in security editing forms.
   *
   * @returns Observable emitting array of field descriptors for enabled security UDF fields, sorted by UI order
   */
  public getAllByIdUserInOrderByUiOrderExcludeDisabled(): Observable<FieldDescriptorInputAndShowExtendedSecurity[]> {
    return <Observable<FieldDescriptorInputAndShowExtendedSecurity[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/fielddescriptor`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Retrieves the fill sources this instance offers, each with the data types of a field it can fill, and the
   * resolvers of the fill source RESOLVER.
   *
   * @returns Observable emitting the offer
   */
  public getFillSources(): Observable<UDFFillSourcesOffer> {
    return <Observable<UDFFillSourcesOffer>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/fillsources`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Evaluates a draft fill rule against the tenant scope of the user. Nothing is written.
   *
   * @param request - The draft rule with the field properties that influence the result
   * @returns Observable emitting the counters, mismatches and samples
   */
  public testFillRule(request: UDFFillTestRequest): Observable<UDFFillTestResult> {
    return <Observable<UDFFillTestResult>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/testfillrule`,
          request,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Picks the up to 5 securities on which a draft description for the language model is tested. Nothing is sent.
   *
   * @param request - The draft of the form; its fill rule is the description for the model
   * @returns Observable emitting the securities in test order, those with a value first
   */
  public getSuggestTestCandidates(request: UDFFillTestRequest): Observable<UDFSuggestTestCandidate[]> {
    return <Observable<UDFSuggestTestCandidate[]>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/testsuggest/candidates`,
          request,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Tests a draft description for the language model on one security: paid requests as in a run, nothing is stored.
   *
   * @param request - The draft of the form; its fill rule is the description for the model
   * @param idSecuritycurrency - A security from {@link getSuggestTestCandidates}
   * @returns Observable emitting the answer of the model and the outcome of its check
   */
  public testSuggestion(request: UDFFillTestRequest, idSecuritycurrency: number): Observable<UDFSuggestTestResult> {
    return <Observable<UDFSuggestTestResult>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/testsuggest/${idSecuritycurrency}`,
          request,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Returns what a rule proposal of the language model would send for a field. Nothing is sent.
   *
   * @param idUDFMetadata - The field
   * @returns Observable emitting provider, model and the examples
   */
  public previewRuleProposal(idUDFMetadata: number): Observable<RuleProposalPreview> {
    return <Observable<RuleProposalPreview>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}/proposerule/preview`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Sends the selected examples to the language model; one paid request. The rule is evaluated, never saved.
   *
   * @param idUDFMetadata - The field
   * @param idSecuritycurrencies - The examples the user kept from the preview
   * @returns Observable emitting the rule with its parse error or its counters
   */
  public proposeRule(idUDFMetadata: number, idSecuritycurrencies: number[]): Observable<RuleProposalResult> {
    return <Observable<RuleProposalResult>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}/proposerule`,
          idSecuritycurrencies,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Starts a paid run in which a language model proposes values of a field.
   *
   * @param idUDFMetadata - A field with the fill source LLM
   * @returns Observable completing when the run is queued
   */
  public suggestValues(idUDFMetadata: number): Observable<void> {
    return <Observable<void>>(
      this.httpClient
        .post(`${this.fieldUrl(idUDFMetadata)}/suggest`, null, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Lists the verified proposals of a field awaiting the decision of the user.
   *
   * @param idUDFMetadata - The field
   * @returns Observable emitting the proposals
   */
  public getProposals(idUDFMetadata: number): Observable<UDFFillProposal[]> {
    return <Observable<UDFFillProposal[]>>(
      this.httpClient
        .get(`${this.fieldUrl(idUDFMetadata)}/proposals`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Accepts proposals the user saw; idempotent.
   *
   * @param idUDFMetadata - The field
   * @param decisions - Security, rule version and value per proposal
   * @returns Observable emitting the outcome per proposal
   */
  public acceptProposals(
    idUDFMetadata: number,
    decisions: UDFFillProposalDecision[]
  ): Observable<UDFFillProposalDecisionResult[]> {
    return <Observable<UDFFillProposalDecisionResult[]>>(
      this.httpClient
        .post(`${this.fieldUrl(idUDFMetadata)}/proposals/accept`, decisions, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Rejects proposals the user saw; idempotent.
   *
   * @param idUDFMetadata - The field
   * @param decisions - Security, rule version and value per proposal
   * @returns Observable emitting the outcome per proposal
   */
  public rejectProposals(
    idUDFMetadata: number,
    decisions: UDFFillProposalDecision[]
  ): Observable<UDFFillProposalDecisionResult[]> {
    return <Observable<UDFFillProposalDecisionResult[]>>(
      this.httpClient
        .post(`${this.fieldUrl(idUDFMetadata)}/proposals/reject`, decisions, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Lets the next run ask again for rejected, discarded and not found cells.
   *
   * @param idUDFMetadata - The field
   * @returns Observable emitting the number of cells that are asked again
   */
  public retryProposals(idUDFMetadata: number): Observable<number> {
    return <Observable<number>>(
      this.httpClient
        .post(`${this.fieldUrl(idUDFMetadata)}/proposals/retry`, null, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  private fieldUrl(idUDFMetadata: number): string {
    return `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}`;
  }

  /**
   * Retrieves the fill status of a field of the user.
   *
   * @param idUDFMetadata - The field
   * @returns Observable emitting the counts per fill state and whether a run is pending
   */
  public getFillStatus(idUDFMetadata: number): Observable<UDFFillStatus> {
    return <Observable<UDFFillStatus>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}/fillstatus`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Takes a cell over manually: nothing automatic touches it afterwards, whether it holds a value or not.
   *
   * @param idUDFMetadata - The field
   * @param idSecuritycurrency - The security
   * @returns Observable completing when the cell is locked
   */
  public lockCell(idUDFMetadata: number, idSecuritycurrency: number): Observable<void> {
    return <Observable<void>>(
      this.httpClient
        .put(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}/fill/${idSecuritycurrency}/lock`,
          null,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Lets a locked cell be filled automatically again; a template evaluates it at once.
   *
   * @param idUDFMetadata - The field
   * @param idSecuritycurrency - The security
   * @returns Observable completing when the lock is removed
   */
  public unlockCell(idUDFMetadata: number, idSecuritycurrency: number): Observable<void> {
    return <Observable<void>>(
      this.httpClient
        .delete(
          `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}/fill/${idSecuritycurrency}/lock`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Updates an existing security UDF metadata entry.
   * Can only update metadata owned by the current user (not system-wide entries).
   *
   * @param udfMetadataSecurity - Security UDF metadata entity with updated values
   * @returns Observable emitting the updated security UDF metadata entity
   */
  public update(udfMetadataSecurity: UDFMetadataSecurity): Observable<UDFMetadataSecurity> {
    return this.updateEntity(
      udfMetadataSecurity,
      udfMetadataSecurity.idUDFMetadata,
      AppSettings.UDF_METADATA_SECURITY_KEY
    );
  }

  /**
   * Deletes a security UDF metadata entry by ID.
   * Can only delete metadata owned by the current user (not system-wide entries).
   *
   * @param idUDFMetadata - Unique identifier of the security UDF metadata to delete
   * @returns Observable completing when deletion is successful
   */
  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
