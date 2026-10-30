(function (root) {
  "use strict";
  const rules = {
    continueWatching(items) {
      return items.filter(s => {
        const episodes = this.ordered(s.episodes);
        return !s.continueHidden && !s.isMovie && episodes.some(e => e.position > 0 || e.watched) &&
          !episodes.every(e => e.watched);
      }).sort((a,b) => (b.addedAt || 0) - (a.addedAt || 0) ||
        String(b.id).localeCompare(String(a.id)));
    },
    ordered(episodes) {
      return episodes
        .filter((e) => e.sourcePresent !== false)
        .slice()
        .sort(
          (a, b) =>
            a.seasonNumber - b.seasonNumber ||
            (Number(a.displayNumber) || Infinity) -
              (Number(b.displayNumber) || Infinity) ||
            a.sourceOrder - b.sourceOrder,
        );
    },
    select(episodes) {
      const rows = this.ordered(episodes);
      const continuing = rows
        .filter((e) => !e.watched && e.position > 10)
        .sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0))[0];
      if (continuing) return continuing;
      const last = rows.map((e) => e.watched).lastIndexOf(true);
      return rows[last + 1] || rows.find((e) => !e.watched) || rows[0];
    },
    progress(position, duration) {
      position = Math.max(0, Number(position) || 0);
      duration = Math.max(0, Number(duration) || 0);
      return {
        position: duration && position >= duration - 10 ? 0 : position,
        duration,
        watched:
          duration > 30 && position >= duration - Math.min(300, duration * 0.1),
      };
    },
    seek(origin, delta, duration) {
      return Math.max(
        0,
        Math.min(
          Number.isFinite(duration) && duration > 0 ? duration : Infinity,
          origin + delta,
        ),
      );
    },
    outro(t, m, duration) {
      return (
        Number.isFinite(duration) &&
        duration > 0 &&
        Number(m.last_titraje_start) > 0 &&
        t >= Number(m.last_titraje_start) &&
        t < duration
      );
    },
    seekStep(heldMs, media = false) {
      return 10;
    },
    highest(variants) {
      return (
        variants
          .slice()
          .sort(
            (a, b) =>
              (Number(b.height) || 0) - (Number(a.height) || 0) ||
              (Number(b.bandwidth) || 0) - (Number(a.bandwidth) || 0),
          )[0] || null
      );
    },
    intro(t, m) {
      return (
        m.first_titraje_start > 0 &&
        m.first_titraje_end > m.first_titraje_start &&
        t >= m.first_titraje_start &&
        t < m.first_titraje_end
      );
    },
  };
  if (typeof module !== "undefined") module.exports = rules;
  else root.ITVRules = rules;
})(typeof window !== "undefined" ? window : this);
