import { Service } from '@angular/core';
import { MobileWatchlistGroup } from '../watchlist/mobile.watchlist.rows';
import { MobileSessionCache } from './mobile.session.cache';

/** The content of the watchlists last read, by watchlist id, shared by the watchlist and the instrument page. */
@Service()
export class MobileWatchlistStore extends MobileSessionCache<number, MobileWatchlistGroup> {}
