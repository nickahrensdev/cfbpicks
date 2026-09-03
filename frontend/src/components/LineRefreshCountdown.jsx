import { useCallback, useEffect, useState } from 'react';

import { api } from '../api/client.js';

/** "4m 12s", or "12s" under a minute. Never a bare "0". */
function remaining(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

/**
 * When the lines on this board are next due to be refreshed.
 *
 * <p>The numbers on a card are not live - they are whatever the last
 * scheduled pull stored - and a spread about to move is worth waiting for
 * rather than picking against. This says how long that wait is.
 *
 * <p>Renders nothing at all when refreshes are switched off, or before the
 * job has ever run. A countdown to something that is not going to happen is
 * worse than no countdown, and "never" is not a duration.
 *
 * <p>Ticks locally from a single fetch rather than polling the API each
 * second. When it reaches zero it asks once for the new schedule, with a
 * small delay so the refresh it is waiting on has actually landed.
 */
export default function LineRefreshCountdown({ className = '' }) {
  const [status, setStatus] = useState(null);
  const [now, setNow] = useState(() => Date.now());

  const load = useCallback(() => {
    api.lineRefresh().then(setStatus).catch(() => setStatus(null));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // One interval for the whole component, running only while there is
  // something to count down to.
  useEffect(() => {
    if (!status?.enabled || !status.nextRunAt) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [status]);

  const dueAt = status?.nextRunAt ? new Date(status.nextRunAt).getTime() : null;
  const left = dueAt == null ? null : dueAt - now;

  // Past due: the schedule has fired but this page has not heard yet. Ask
  // again, once, a few seconds later - immediately would race the run itself.
  useEffect(() => {
    if (left == null || left > 0) return undefined;
    const timer = setTimeout(load, 5000);
    return () => clearTimeout(timer);
  }, [left, load]);

  if (!status?.enabled || dueAt == null) {
    return null;
  }

  return (
    <span className={`small text-body-tertiary text-nowrap ${className}`}>
      {left > 0 ? (
        <>Lines refresh in {remaining(left)}</>
      ) : (
        // Between the due time and the next successful run. Saying "0s" for
        // however long that takes would look stuck.
        <>Refreshing lines…</>
      )}
    </span>
  );
}
