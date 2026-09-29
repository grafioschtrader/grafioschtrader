import { HttpHeaders } from '@angular/common/http';
import { BaseSettings } from '../../base.settings';

export abstract class BaseService {
  getHeaders(): { headers: HttpHeaders } {
    return { headers: this.prepareHeaders() };
  }

  prepareHeaders(contentType = 'application/json'): HttpHeaders {
    const header = {
      'Content-Type': 'application/json',
      Accept: contentType
    };
    this.addToken(header);
    return new HttpHeaders(header);
  }

  getMultipartHeaders(): { headers: HttpHeaders } {
    return { headers: this.prepareMultipartHeaders() };
  }

  prepareMultipartHeaders(): HttpHeaders {
    const header = {
      // 'Content-Type': 'multipart/form-data',
      Accept: 'application/json'
    };
    this.addToken(header);
    return new HttpHeaders(header);
  }

  protected addToken(header) {
    if (sessionStorage.getItem('jwt')) {
      header['x-auth-token'] = sessionStorage.getItem('jwt');
      header[BaseSettings.TIME_ZONE_HEADER] = Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
  }
}
