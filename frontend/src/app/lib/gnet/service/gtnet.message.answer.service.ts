import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { GTNetMessageAnswer } from '../model/gtnet.message.answer';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { DeleteService } from '../../datashowbase/delete.service';
import { Observable } from 'rxjs/internal/Observable';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../base.settings';

/**
 * Service for managing GTNetMessageAnswer entities via REST API.
 */
@Service()
export class GTNetMessageAnswerService
  extends AuthServiceWithLogout<GTNetMessageAnswer>
  implements ServiceEntityUpdate<GTNetMessageAnswer>, DeleteService
{
  /**
   * Retrieves all GTNetMessageAnswer entities from the server.
   */
  getAllGTNetMessageAnswers(): Observable<GTNetMessageAnswer[]> {
    return this.httpClient
      .get<GTNetMessageAnswer[]>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_ANSWER_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates or updates a GTNetMessageAnswer entity.
   */
  update(gtNetMessageAnswer: GTNetMessageAnswer): Observable<GTNetMessageAnswer> {
    return this.updateEntity(
      gtNetMessageAnswer,
      gtNetMessageAnswer.idGtNetMessageAnswer,
      BaseSettings.GT_NET_MESSAGE_ANSWER_KEY
    );
  }

  /**
   * Deletes a GTNetMessageAnswer entity by ID.
   */
  deleteEntity(idGtNetMessageAnswer: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_ANSWER_KEY}/${idGtNetMessageAnswer}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
