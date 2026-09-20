import moment from 'moment';
import { GroupItem } from '../../lib/dynamic-form/models/value.key.html.select.options';

/** The dates remain calendar dates; the injected formatter supplies the user's GT date format. */
export interface AlgoSecurityOptionData {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
}

/** Builds searchable labels and independently highlighted dates without coupling presentation to eligibility. */
export function createAlgoSecurityOptions(
  securities: AlgoSecurityOptionData[],
  referenceDate: Date | string | undefined,
  formatDate: (security: AlgoSecurityOptionData, field: 'activeFromDate' | 'activeToDate') => string,
  labels: { activeFrom: string; activeTo: string; startsAfterReference: string; ended: string },
  today: string = moment().format('YYYY-MM-DD')
): GroupItem[] {
  const reference = referenceDate ? moment(referenceDate).format('YYYY-MM-DD') : undefined;
  return securities.map((security) => {
    const lateStart = !!reference && security.activeFromDate > reference;
    const ended = !!security.activeToDate && security.activeToDate < today;
    const segments = [
      { text: `${security.name} / ${security.currency} — ` },
      {
        text: formatDate(security, 'activeFromDate') || '—',
        cssClass: lateStart ? 'text-danger' : undefined,
        title: lateStart ? `${labels.activeFrom}: ${labels.startsAfterReference}` : labels.activeFrom
      },
      { text: ' – ' },
      {
        text: formatDate(security, 'activeToDate') || '—',
        cssClass: ended ? 'text-danger' : undefined,
        title: ended ? `${labels.activeTo}: ${labels.ended}` : labels.activeTo
      }
    ];
    const text = segments.map((segment) => segment.text).join('');
    const option = new GroupItem(security.idSecuritycurrency, text, text, null);
    option.textSegments = segments;
    return option;
  });
}
