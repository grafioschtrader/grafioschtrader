/** Error body of the backend: the simple name of its error class and that class serialized. */
export interface ErrorWrapper {
  className: string;
  error: any;
}
