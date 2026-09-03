import { Fragment, useState } from 'react';
import { Button, Card, Col, Form, Nav, Row } from 'react-bootstrap';

/**
 * Every group setting, in one controlled component.
 *
 * <p>Contract matches GameFilters: the parent owns the value and gets a whole
 * new object back on every change. That keeps this file free of state except
 * for which tab is showing, so the create and edit pages can submit whatever
 * they are holding without asking the form for it.
 *
 * <p>Four sections rather than one long scroll. A group has around twenty knobs
 * and most of them are only interesting once, so putting the ones with sensible
 * defaults behind a section is kinder than a wall of inputs.
 *
 * <p>The sections are presented two ways. Creating a group runs them as a
 * <b>stepper</b>: a first-time author has no idea which settings exist, and
 * being walked through them in order is the difference between a considered
 * league and whatever the defaults were. Editing one uses <b>tabs</b>, because
 * by then you know what you came to change and marching through four screens to
 * reach it is a tax.
 *
 * <p>In stepper mode the step is owned by the parent, the same way {@code value}
 * is - the parent needs it to label its own Back/Next/Create buttons, and to
 * stop a stray Enter keypress in a text field from submitting the whole form
 * from step one.
 */

/**
 * The format settings each group type starts from.
 *
 * <p>The two types want genuinely different shapes - a pick'em is a weekly
 * board with no cap worth enforcing, a survivor pool is a daily single pick -
 * so choosing the type sets the format rather than leaving someone to discover
 * four fields that do not suit what they picked.
 */
export const TYPE_DEFAULTS = {
  PICKEM: {
    cadence: 'WEEKLY',
    lengthType: 'PER_YEAR',
    lockLeadMinutes: 30,
    maxPicksPerCadence: null,
    minPicksPerCadence: 0,
    strikesAllowed: null,
  },
  ELIMINATION: {
    cadence: 'DAILY',
    lengthType: 'PER_YEAR',
    lockLeadMinutes: 30,
    maxPicksPerCadence: 1,
    minPicksPerCadence: 1,
    strikesAllowed: 2,
  },
};

/** A new group: a pick'em, on its type defaults. */
export const DEFAULT_SETTINGS = {
  name: '',
  description: '',
  visibility: 'PUBLIC',
  joinPassword: '',

  groupType: 'PICKEM',
  // Not asked for on the form any more - a group starts in the year it is
  // created, which is the only answer anyone was ever going to give.
  startSeason: new Date().getFullYear(),
  ...TYPE_DEFAULTS.PICKEM,

  multiplePicksPerGame: true,
  requireApproval: false,
  shareableByMembers: false,

  moneylineEnabled: false,
  spreadEnabled: true,
  totalEnabled: true,

  // Null on every one of these means "no limit", which is what a group that
  // never touches this step should get.
  moneylineMinPerCadence: null,
  moneylineMaxPerCadence: null,
  spreadMinPerCadence: null,
  spreadMaxPerCadence: null,
  totalMinPerCadence: null,
  totalMaxPerCadence: null,

  moneylineWinPoints: 1,
  moneylineLossPoints: 0,
  moneylinePushPoints: 0.5,
  spreadWinPoints: 1,
  spreadLossPoints: 0,
  spreadPushPoints: 0.5,
  totalWinPoints: 1,
  totalLossPoints: 0,
  totalPushPoints: 0.5,

  teamPickLimit: null,
  teamPickLimitScope: null,
};

/**
 * The sections, in the order the stepper walks them.
 *
 * <p>Five rather than four, and regrouped by what a setting <em>does</em>
 * rather than where it happened to be added:
 *
 * <ul>
 *   <li><b>Basics</b> - what the group is called.
 *   <li><b>Access</b> - who can get in. Visibility and the join password used
 *       to be here while approval and member sharing sat on Format, which
 *       split one question across two screens.
 *   <li><b>Format</b> - what kind of game it is, and when picks close.
 *   <li><b>Scoring</b> - which markets are played and what each outcome pays.
 *   <li><b>Limits</b> - every cap and floor in one place. These were spread
 *       across three steps: the overall maximum on Format, the overall
 *       minimum on Rules and the per-market ones on Scoring - so the check
 *       that the minimums fit inside the maximum reported an error about a
 *       field two steps behind the one being read.
 * </ul>
 */
export const SETTINGS_STEPS = [
  ['basics', 'Basics'],
  ['access', 'Access'],
  ['format', 'Format'],
  ['scoring', 'Scoring'],
  ['limits', 'Limits'],
];

/**
 * Why the given step cannot be left yet, or null when it can.
 *
 * <p>Only covers what the step itself can see. The rest of the cross-field
 * rules stay on the server, which is the authority - this exists so the stepper
 * does not walk someone to the end before telling them the name was blank.
 */
export function stepIssue(settings, stepIndex) {
  const key = SETTINGS_STEPS[stepIndex]?.[0];

  if (key === 'basics' && !settings.name?.trim()) {
    return 'Give the group a name to continue.';
  }
  if (key === 'scoring'
      && !settings.moneylineEnabled && !settings.spreadEnabled && !settings.totalEnabled) {
    return 'Turn on at least one pick option - a group with none has nothing to pick.';
  }

  // Every one of these now reads fields on the step it fires from. They used
  // to run on Scoring while the cap they compared against lived on Format,
  // so the message named a number that was not on screen.
  if (key === 'limits') {
    const period = settings.cadence === 'DAILY' ? 'day' : 'week';
    const live = MARKETS.filter(([market]) => settings[`${market}Enabled`]);
    const cap = settings.maxPicksPerCadence;
    const floor = settings.minPicksPerCadence;

    if (cap != null && cap !== '' && floor != null && floor !== '' && floor > cap) {
      return `The fewest picks per ${period} cannot be more than the most.`;
    }

    const backwards = live.find(([market]) => {
      const min = settings[`${market}MinPerCadence`];
      const max = settings[`${market}MaxPerCadence`];
      return min != null && max != null && min > max;
    });
    if (backwards) {
      return `The minimum ${backwards[1]} picks per ${period} cannot be more than the maximum.`;
    }

    // The same arithmetic the server does. Worth repeating here because the
    // stepper would otherwise walk someone to the end before mentioning that
    // their minimums add up to more picks than the group allows.
    if (cap != null && cap !== '') {
      const required = live.reduce(
        (total, [market]) => total + (settings[`${market}MinPerCadence`] ?? 0),
        0,
      );
      if (required > cap) {
        return `The per-market minimums add up to ${required} picks per ${period}, but the group `
          + `only allows ${cap}. No one could satisfy them all.`;
      }
    }

    // The same impossibility from the other side: maximums so tight that the
    // overall minimum cannot be reached. Only checkable when every enabled
    // market has a maximum - one left blank is an unbounded ceiling.
    if (floor > 0 && live.length > 0
        && live.every(([market]) => settings[`${market}MaxPerCadence`] != null)) {
      const available = live.reduce(
        (total, [market]) => total + settings[`${market}MaxPerCadence`],
        0,
      );
      if (available < floor) {
        return `The per-option maximums only allow ${available} picks per ${period}, but the `
          + `group asks for at least ${floor}. No one could reach it.`;
      }
    }
  }
  return null;
}

const MARKETS = [
  ['moneyline', 'Moneyline', 'Pick the team to win outright.'],
  ['spread', 'Spread', 'Pick a team against the posted line.'],
  ['total', 'Over/Under', 'Pick the game total over or under.'],
];

/** Label above, help text below, control between - used by every setting here. */
function Setting({ label, help, children }) {
  return (
    <Form.Group className="mb-3">
      <Form.Label className="mb-1">{label}</Form.Label>
      {children}
      {help && <Form.Text className="text-body-secondary d-block">{help}</Form.Text>}
    </Form.Group>
  );
}

export default function GroupSettingsForm({
  value,
  onChange,
  disabled = false,
  creating = false,
  mode = 'tabs',
  step = 0,
  onStepChange,
}) {
  const [tab, setTab] = useState('basics');
  // How far the author has reached, so the numbered steps behind them stay
  // clickable while the ones ahead do not.
  const [furthest, setFurthest] = useState(0);

  const stepper = mode === 'stepper';
  const activeKey = stepper ? (SETTINGS_STEPS[step]?.[0] ?? 'basics') : tab;

  const goToStep = (next) => {
    setFurthest((seen) => Math.max(seen, next));
    onStepChange?.(next);
  };

  const update = (patch) => onChange({ ...value, ...patch });

  /**
   * Numbers arrive from the DOM as strings, and an emptied box has to become
   * null rather than 0 - "no maximum" and "a maximum of zero" are different
   * settings, and only one of them is legal.
   */
  // What one period of this group is called, so the per-market limits read as
  // "Max per week" or "Max per day" rather than as an abstract "cadence".
  const periodNoun = value.cadence === 'DAILY' ? 'day' : 'week';

  const setNumber = (key, { nullable = false } = {}) => (event) => {
    const raw = event.target.value;
    if (raw === '') {
      update({ [key]: nullable ? null : '' });
      return;
    }
    update({ [key]: Number(raw) });
  };

  /**
   * Choosing a type resets the format fields to that type's defaults.
   *
   * <p>Switching between a points league and a survivor pool changes what the
   * format fields should say, so carrying the old cadence and cap across would
   * leave a half-converted group. It also keeps elimination on PER_YEAR, which
   * the server requires - a continuous pool can never start over once everyone
   * is out.
   *
   * <p>Only the format fields move. Name, description, visibility and scoring
   * are the author's, and are left alone.
   */
  const setGroupType = (groupType) => {
    update({ groupType, ...(TYPE_DEFAULTS[groupType] ?? {}) });
  };

  const isElimination = value.groupType === 'ELIMINATION';

  return (
    <div>
      {stepper ? (
        /* A named progress track rather than a row of numbered pills.
           The pills hid their labels below sm to fit, which left a phone
           showing "1 → 2 → 3 → 4" - an accurate count of steps and no clue
           what any of them was about. Naming the current step above a
           segmented bar says where you are in words at every width, and adding
           a fifth section costs the bar a segment rather than squeezing every
           pill narrower. */
        <div className="mb-3">
          <div className="d-flex justify-content-between align-items-baseline mb-2 gap-2">
            <span className="fw-semibold text-truncate">{SETTINGS_STEPS[step]?.[1]}</span>
            <span className="small text-body-secondary text-nowrap">
              Step {step + 1} of {SETTINGS_STEPS.length}
            </span>
          </div>

          <ol className="stepper-track list-unstyled mb-0">
            {SETTINGS_STEPS.map(([key, label], index) => {
              const reached = index <= furthest;
              return (
                <li key={key} className="stepper-seg">
                  <button
                    type="button"
                    disabled={!reached}
                    onClick={() => goToStep(index)}
                    aria-current={index === step ? 'step' : undefined}
                    // The segment is a 4px bar, far too small to hit and with
                    // no text of its own, so the name is the accessible one.
                    aria-label={`Step ${index + 1}: ${label}`}
                    title={label}
                    className={[
                      'stepper-seg-hit',
                      index < step ? 'is-done' : '',
                      index === step ? 'is-current' : '',
                    ].join(' ')}
                  />
                </li>
              );
            })}
          </ol>
        </div>
      ) : (
        // flex-nowrap and a sideways scroll: five tabs no longer fit a phone,
        // and a wrapped second row of tabs reads as a second toolbar.
        <Nav
          variant="tabs"
          activeKey={tab}
          onSelect={setTab}
          className="mb-3 flex-nowrap overflow-auto"
          // Belt and braces with the caller's own minmax(0, 1fr): a scroll
          // container still needs to be allowed to shrink below its content,
          // or it grows its parent instead of scrolling.
          style={{ minWidth: 0 }}
        >
          {SETTINGS_STEPS.map(([key, label]) => (
            <Nav.Item key={key}>
              <Nav.Link eventKey={key} className="text-nowrap">
                {label}
              </Nav.Link>
            </Nav.Item>
          ))}
        </Nav>
      )}

      {activeKey === 'basics' && (
        <Card body>
          <Setting label="Name" help="What members will see in search and on the leaderboard.">
            <Form.Control
              value={value.name}
              onChange={(event) => update({ name: event.target.value })}
              maxLength={60}
              required
              disabled={disabled}
              placeholder="The Office League"
            />
          </Setting>

          <Setting label="Description" help="Optional. Shown in search results.">
            <Form.Control
              as="textarea"
              rows={2}
              value={value.description ?? ''}
              onChange={(event) => update({ description: event.target.value })}
              maxLength={500}
              disabled={disabled}
            />
          </Setting>

        </Card>
      )}

      {activeKey === 'access' && (
        <Card body>
          <p className="text-body-secondary small">
            Four separate gates, in the order someone meets them: whether they can find the group,
            whether they need a password, whether you have to approve them, and whether members
            can invite anyone at all.
          </p>

          <Setting
            label="Visibility"
            help="Public groups turn up in search. Private ones are unlisted - you add members yourself."
          >
            <Form.Select
              value={value.visibility}
              onChange={(event) => update({ visibility: event.target.value })}
              disabled={disabled}
            >
              <option value="PUBLIC">Public - listed in search</option>
              <option value="PRIVATE">Private - unlisted</option>
            </Form.Select>
          </Setting>

          {/* Independent of visibility. A private group's link still gets
              forwarded, and a password is the second thing standing between a
              stranger and the league. */}
          <Setting
            label="Join password"
            help="Leave blank to let anyone with access join. Set one and they need it to get in."
          >
            <Form.Control
              value={value.joinPassword ?? ''}
              onChange={(event) => update({ joinPassword: event.target.value })}
              maxLength={60}
              disabled={disabled}
              placeholder="No password"
            />
          </Setting>

          {/* Applies however someone arrives - search or a shared link - so
              the setting means the same thing whichever route they took. */}
          <Form.Check
            type="switch"
            id="require-approval"
            label="An owner has to approve people joining"
            checked={value.requireApproval}
            onChange={(event) => update({ requireApproval: event.target.checked })}
            disabled={disabled}
            className="mb-3"
          />

          {/* Only meaningful for a private group. A public one is findable by
              search already, so sharing it adds convenience rather than
              access, and any member may do it. */}
          {value.visibility === 'PRIVATE' && (
            <Form.Group className="mb-0">
              <Form.Check
                type="switch"
                id="shareable-by-members"
                label="Let members share this private group"
                checked={value.shareableByMembers}
                onChange={(event) => update({ shareableByMembers: event.target.checked })}
                disabled={disabled}
              />
              <Form.Text className="text-body-secondary">
                Off by default: a private group is unlisted because you chose that, and a
                member&apos;s link would let anyone holding it in. Owners can always share.
              </Form.Text>
            </Form.Group>
          )}
        </Card>
      )}

      {activeKey === 'format' && (
        <Card body>
          <Row>
            <Col md={6}>
              {/* Fixed once the group exists. The two types score, cap and
                  eliminate differently, so switching an established league
                  would re-interpret picks that were made under other rules.
                  The server rejects a change too - this is the explanation,
                  not the enforcement. */}
              <Setting
                label="Group type"
                help={
                  creating
                    ? isElimination
                      ? 'Members are knocked out after a set number of wrong picks.'
                      : 'Members accumulate points all season.'
                    : 'The group type cannot be changed after creation.'
                }
              >
                <Form.Select
                  value={value.groupType}
                  onChange={(event) => setGroupType(event.target.value)}
                  disabled={disabled || !creating}
                >
                  <option value="PICKEM">Pick&apos;em - play for points</option>
                  <option value="ELIMINATION">Elimination - last one standing</option>
                </Form.Select>
              </Setting>
            </Col>
            <Col md={6}>
              <Setting label="Pick cadence" help="The period pick limits are counted against.">
                <Form.Select
                  value={value.cadence}
                  onChange={(event) => update({ cadence: event.target.value })}
                  disabled={disabled}
                >
                  <option value="WEEKLY">Weekly</option>
                  <option value="DAILY">Daily</option>
                </Form.Select>
              </Setting>
            </Col>
          </Row>

          <Row>
            <Col md={6}>
              <Setting
                label="Leaderboard length"
                help={
                  isElimination
                    ? 'Elimination groups always reset each year - once everyone is out a continuous pool cannot start over.'
                    : 'Continuous keeps one all-time board. Per year gives each season its own, with past seasons still readable.'
                }
              >
                <Form.Select
                  value={value.lengthType}
                  onChange={(event) => update({ lengthType: event.target.value })}
                  disabled={disabled || isElimination}
                >
                  <option value="CONTINUOUS">Continuous - one all-time board</option>
                  <option value="PER_YEAR">Per year - a board per season</option>
                </Form.Select>
              </Setting>
            </Col>
            {/* "First season" was here. It is still sent - the API requires
                it - but nothing reads it back except one line in the group
                info modal, so asking for it was a decision with no
                consequence. New groups take the current year from
                DEFAULT_SETTINGS; an existing one keeps whatever it was
                created with, since the edit form round-trips the value it
                loaded rather than re-defaulting it. */}
          </Row>

          <Row>
            <Col md={6}>
              <Setting
                label="Picks close"
                help="Minutes before each game's own kickoff that it stops being pickable."
              >
                <Form.Control
                  type="number"
                  value={value.lockLeadMinutes ?? ''}
                  onChange={setNumber('lockLeadMinutes')}
                  min={0}
                  disabled={disabled}
                />
              </Setting>
            </Col>
          </Row>

          {/* Here rather than with the other numbers on Limits: strikes are
              how this format ends, not a cap on picking. It is also the one
              field that only exists for one of the two group types, so it
              belongs beside the control that chooses them. */}
          {isElimination && (
            <div className="border-top pt-3 mt-1">
              <Row>
                <Col md={6}>
                  <Setting
                    label="Wrong picks allowed"
                    help="How many losses before a member is out. Zero means one wrong pick ends it."
                  >
                    <Form.Control
                      type="number"
                      value={value.strikesAllowed ?? ''}
                      onChange={setNumber('strikesAllowed')}
                      min={0}
                      disabled={disabled}
                    />
                  </Setting>
                </Col>
              </Row>
            </div>
          )}
        </Card>
      )}

      {activeKey === 'scoring' && (
        <Card body>
          <p className="text-body-secondary small">
            Turn on the pick options this group plays, and say what each outcome is worth. Values
            can be negative or fractional - a group might want &minus;1 for a loss.
          </p>

          {MARKETS.map(([key, label, help]) => {
            const enabled = value[`${key}Enabled`];
            return (
              <div key={key} className="border-top pt-3 mt-3">
                <Form.Check
                  type="switch"
                  id={`${key}-enabled`}
                  label={label}
                  checked={enabled}
                  onChange={(event) => update({ [`${key}Enabled`]: event.target.checked })}
                  disabled={disabled}
                />
                <Form.Text className="text-body-secondary d-block mb-2">
                  {help}
                  {key === 'moneyline' && ' Configurable now; moneyline picks arrive in a later release.'}
                </Form.Text>

                <Row className="g-2">
                  {[
                    ['Win', 'Win'],
                    ['Loss', 'Loss'],
                    ['Push', 'Push (tie)'],
                  ].map(([suffix, outcome]) => (
                    <Col xs={4} key={suffix}>
                      <Form.Label className="small mb-1">{outcome}</Form.Label>
                      <Form.Control
                        type="number"
                        step="0.5"
                        size="sm"
                        value={value[`${key}${suffix}Points`] ?? ''}
                        onChange={setNumber(`${key}${suffix}Points`)}
                        disabled={disabled || !enabled}
                      />
                    </Col>
                  ))}
                </Row>

              </div>
            );
          })}
        </Card>
      )}

      {activeKey === 'limits' && (
        <Card body>
          <p className="text-body-secondary small">
            Every cap and floor in one place. These used to be spread over three steps, which
            made it hard to see whether they could all be met at once.
          </p>

          <Row>
            <Col md={6}>
              <Setting
                label={`Most picks per ${periodNoun}`}
                help="Leave blank for no limit."
              >
                <Form.Control
                  type="number"
                  value={value.maxPicksPerCadence ?? ''}
                  onChange={setNumber('maxPicksPerCadence', { nullable: true })}
                  min={1}
                  placeholder="No limit"
                  disabled={disabled}
                />
              </Setting>
            </Col>
            {/* Applies to either group type. It was elimination-only back when
                the only consequence of missing it was being knocked out; now an
                unmet minimum is charged as losses, which a points league
                carries perfectly well. */}
            <Col md={6}>
              <Setting
                label={`Fewest picks per ${periodNoun}`}
                help={isElimination
                  ? 'Zero lets members sit one out. Any higher and falling short counts as losses, which can eliminate them.'
                  : 'Zero lets members sit one out. Any higher and falling short counts as losses when the period closes.'}
              >
                <Form.Control
                  type="number"
                  value={value.minPicksPerCadence ?? ''}
                  onChange={setNumber('minPicksPerCadence')}
                  min={0}
                  disabled={disabled}
                />
              </Setting>
            </Col>
          </Row>

          {/* Beside the overall allowance they have to fit inside, rather than
              back on Scoring. Without per-market limits, a group playing all
              three markets on one scoring table has a dominant strategy: take
              heavy favourites to win and never touch a spread. The overall cap
              cannot fix that, because it does not care which market a pick
              came from. */}
          <div className="border-top pt-3 mt-1">
            <p className="fw-semibold mb-1">Per pick option</p>
            <p className="text-body-secondary small">
              Blank means no limit. A minimum is checked when the {periodNoun} closes - falling
              short counts as a loss.
            </p>

            {/* Only the markets this group actually plays. A limit on a market
                nobody can pick is a number with no effect, and three disabled
                rows made the ones that mattered harder to find. */}
            {MARKETS.filter(([key]) => value[`${key}Enabled`]).map(([key, label]) => (
              <Row className="g-2 align-items-end mb-2" key={key}>
                <Col xs={12} sm={4}>
                  <span className="small fw-semibold">{label}</span>
                </Col>
                {[
                  ['Min', `Min per ${periodNoun}`],
                  ['Max', `Max per ${periodNoun}`],
                ].map(([suffix, outcome]) => (
                  <Col xs={6} sm={4} key={suffix}>
                    <Form.Label className="small mb-1">{outcome}</Form.Label>
                    <Form.Control
                      type="number"
                      min="0"
                      size="sm"
                      placeholder="Any"
                      value={value[`${key}${suffix}PerCadence`] ?? ''}
                      onChange={setNumber(`${key}${suffix}PerCadence`, { nullable: true })}
                      disabled={disabled}
                    />
                  </Col>
                ))}
              </Row>
            ))}
          </div>

          <div className="border-top pt-3 mt-3">
            <Row>
              <Col md={6}>
                <Setting
                  label="Times a team can be picked"
                  help="Leave blank to let members pick the same team as often as they like."
                >
                  <Form.Control
                    type="number"
                    value={value.teamPickLimit ?? ''}
                    onChange={(event) => {
                      const raw = event.target.value;
                      if (raw === '') {
                        // The limit and its scope only mean anything together.
                        update({ teamPickLimit: null, teamPickLimitScope: null });
                        return;
                      }
                      update({
                        teamPickLimit: Number(raw),
                        teamPickLimitScope: value.teamPickLimitScope ?? 'BOTH',
                      });
                    }}
                    min={1}
                    placeholder="No limit"
                    disabled={disabled}
                  />
                </Setting>
              </Col>
              <Col md={6}>
                <Setting label="Counted against" help="Which pick options the limit applies to.">
                  <Form.Select
                    value={value.teamPickLimitScope ?? ''}
                    onChange={(event) => update({ teamPickLimitScope: event.target.value })}
                    disabled={disabled || value.teamPickLimit == null}
                  >
                    <option value="MONEYLINE">Moneyline picks only</option>
                    <option value="SPREAD">Spread picks only</option>
                    <option value="BOTH">Both</option>
                  </Form.Select>
                </Setting>
              </Col>
            </Row>

            <Form.Check
              type="switch"
              id="multiple-picks-per-game"
              label="Allow more than one pick on the same game"
              checked={value.multiplePicksPerGame}
              onChange={(event) => update({ multiplePicksPerGame: event.target.checked })}
              disabled={disabled}
            />
            <Form.Text className="text-body-secondary">
              For example taking the spread and the over on the same game.
            </Form.Text>
          </div>
        </Card>
      )}
    </div>
  );
}
