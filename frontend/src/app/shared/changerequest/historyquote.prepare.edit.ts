import {Historyquote} from '../../entities/historyquote';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {combineLatest} from 'rxjs';
import {HistoryquoteSecurityCurrency} from '../../historyquote/component/historyquote-table.component';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {BasePrepareEdit} from '../../lib/proposechange/component/base.prepare.edit';
import {EntityMapping, PrepareCallParam} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for Historyquote entities in the propose change workflow.
 * Loads the associated security or currency pair to provide context for the history quote
 * being edited. History quotes are always linked to either a Security or Currencypair.
 */
export class HistoryquotePrepareEdit extends BasePrepareEdit<Historyquote> implements PrepareCallParam<Historyquote> {

  /**
   * Creates a history quote preparation handler.
   *
   * @param securityService - Service for loading security data
   * @param currencypairService - Service for loading currency pair data
   */
  constructor(private securityService: SecurityService, private currencypairService: CurrencypairService) {
    super();
  }

  /**
   * Prepares a history quote for editing by loading its associated security or currency pair.
   * Makes parallel requests for both entity types and uses whichever one exists.
   * The loaded entity provides context about the instrument being quoted.
   *
   * @param entity - The history quote to prepare for editing
   * @param entityMapping - Container for dialog state and parameters to be populated
   */
  prepareForEditEntity(entity: Historyquote, entityMapping: EntityMapping): void {
    const cpObservable = this.currencypairService.getCurrencypairByIdSecuritycurrency(entity.idSecuritycurrency);
    const securityObservable = this.securityService.getSecurityByIdSecuritycurrency(entity.idSecuritycurrency);

    combineLatest([cpObservable, securityObservable]).subscribe((data: any[]) => {
      const historyquote = new Historyquote();
      Object.assign(historyquote, entity);
      entityMapping.callParam = data[0] ? new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Currencypair(), data[0])) :
        new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Security(), data[1]));
      entityMapping.visibleDialog = true;
    });
  }
}
