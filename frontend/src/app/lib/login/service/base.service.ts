import { HttpHeaders } from '@angular/common/http';

export abstract class BaseService {
  getHeaders(): { headers: HttpHeaders } {
    return { headers: this.prepareHeaders() };
  }

  prepareHeaders(contentType = 'application/json'): HttpHeaders {
    const header = {
      'Content-Type': 'application/json',
      Accept: contentType
    };
    return new HttpHeaders(header);
  }

  getMultipartHeaders(): { headers: HttpHeaders } {
    return { headers: this.prepareMultipartHeaders() };
  }

  prepareMultipartHeaders(): HttpHeaders {
    const header = {
      // The browser sets Content-Type with the multipart boundary.
      Accept: 'application/json'
    };
    return new HttpHeaders(header);
  }
}
