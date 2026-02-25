export enum EndpointOption {
  SKIP_WEEKEND_DATA = 'SKIP_WEEKEND_DATA'
}

export const ENDPOINT_OPTION_BY_FEED: Record<string, EndpointOption[]> = {
  FS_HISTORY: [EndpointOption.SKIP_WEEKEND_DATA],
  FS_INTRA: []
};
