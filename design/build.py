#!/usr/bin/env python3
"""Build the v5 design proposal. Standard library only; no application logic."""
from pathlib import Path
from html import escape
import json

ROOT = Path(__file__).resolve().parent
CSS = (ROOT / 'styles.css').read_text()
SHAPE_SCRIPT = (ROOT / 'editor-shapes.js').read_text()
SCREENS = {}
PATHS = {
    'more': '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    'down': '<path d="m6 9 6 6 6-6"/>',
    'left': '<path d="M20 12H4m6-6-6 6 6 6"/>',
    'right': '<path d="M4 12h16m-6-6 6 6-6 6"/>',
    'flip': '<path d="M4 8h15m-4-4 4 4-4 4M20 16H5m4-4-4 4 4 4"/>',
    'plus': '<path d="M12 4v16M4 12h16"/>',
    'check': '<path d="m4 12 5 5L20 6"/>',
    'cross': '<path d="m6 6 12 12M18 6 6 18"/>',
    'defer': '<path d="M3 12h13m-5-5 5 5-5 5M21 5v14"/>',
    'active': '<path d="m8 5 11 7-11 7Z" fill="currentColor" stroke="none"/>',
    'pen': '<path d="m4 16 11-11 4 4L8 20H4v-4Zm9-9 4 4m-1-7 1-1 4 4-1 1"/>',
    'undo': '<path d="M5 5v6h6M5 11c4-9 15-5 15 2 0 4-3 7-7 7"/>',
    'trash': '<path d="M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
    'file': '<path d="M5 3h9l5 5v13H5V3Zm9 0v6h5M8 14h8m-8 3h5"/>',
    'cards': '<rect x="6" y="6" width="14" height="16" rx="2"/><path d="M16 3H5a2 2 0 0 0-2 2v13"/>',
    'export': '<path d="M12 15V3m-4 4 4-4 4 4M4 13v8h16v-8"/>',
    'shapes': '<circle cx="7" cy="8" r="4"/><path d="m16 5 6 12H10Z M3 16v5h6"/>',
}

SHAPES = {
    'line': ('Line', '<path d="M12 50H88"/>'),
    'arrow': ('Arrow', '<path d="M12 50H88L68 30M88 50 68 70"/>'),
    'double-arrow': ('Double arrow', '<path d="M12 50H88M32 30 12 50 32 70M68 30 88 50 68 70"/>'),
    'rectangle': ('Rectangle', '<rect x="14" y="20" width="72" height="60"/>'),
    'oval': ('Oval', '<ellipse cx="50" cy="50" rx="38" ry="30"/>'),
    'triangle': ('Triangle', '<path d="M50 12 89 85H11Z"/>'),
    'diamond': ('Diamond', '<path d="M50 10 86 50 50 90 14 50Z"/>'),
    'hexagon': ('Hexagon', '<path d="M30 15H70L90 50 70 85H30L10 50Z"/>'),
    'star': ('Star', '<path d="m50 10 12 27 29 3-22 20 6 30-25-15-25 15 6-30L9 40l29-3Z"/>'),
    'moon': ('Moon', '<path d="M65 12A39 39 0 1 0 87 70 38 38 0 0 1 65 12Z"/>'),
    'sun': ('Sun', '<circle cx="50" cy="50" r="23"/><path d="M50 7V16M50 84V93M7 50H16M84 50H93M20 20 27 27M73 73 80 80M20 80 27 73M73 27 80 20"/>'),
    'heart': ('Heart', '<path d="M50 86 16 53C-5 28 25 4 50 29 75 4 105 28 84 53Z"/>'),
    'cloud': ('Cloud', '<path d="M25 77A20 20 0 0 1 19 38 27 27 0 0 1 71 32a23 23 0 0 1 8 45H25Z"/>'),
    'bolt': ('Lightning', '<path d="M57 8 20 56H46L39 92 82 40H55Z"/>'),
    'drop': ('Drop', '<path d="M50 10C43 25 20 48 20 62A30 30 0 0 0 80 62C80 48 57 25 50 10Z"/>'),
}


def build_shape(shape, width=3):
    return (f'<svg class="shape-graphic" viewBox="0 0 100 100" preserveAspectRatio="none" '
            f'fill="none" stroke="currentColor" stroke-width="{width}" aria-hidden="true">'
            + SHAPES[shape][1] + '</svg>')


def build_linear_shape(shape, width, height, stroke=3):
    start, end, middle = width * 0.12, width * 0.88, height / 2
    head = min(24, (end - start) / 3)
    path = f'M{start} {middle}H{end}'
    if shape != 'line':
        path += f'M{end-head} {middle-16}L{end} {middle} {end-head} {middle+16}'
    if shape == 'double-arrow':
        path += f'M{start+head} {middle-16}L{start} {middle} {start+head} {middle+16}'
    return (f'<svg class="shape-graphic" viewBox="0 0 {width} {height}" preserveAspectRatio="none" '
            f'fill="none" stroke="currentColor" stroke-width="{stroke}" aria-hidden="true"><path d="{path}"/></svg>')


def icon(name):
    return f'<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">{PATHS[name]}</svg>'


def btn(label, href=None, cls='', glyph=None, sub='', attrs='', *, static=False):
    content = (icon(glyph) if glyph else '') + (f'<span>{label}' + (f'<small>{sub}</small>' if sub else '') + '</span>' if label or sub else '')
    if glyph and not label:
        cls += ' icon-only'
    common = f'class="btn {cls}" {attrs}'
    disabled = '' if static else ' disabled'
    return f'<a {common} href="{href}.html">{content}</a>' if href else f'<button type="button" {common}{disabled}>{content}</button>'


def build_metric_value(value):
    current, total = value.split(' / ')
    return f'<strong class="metric-value"><span>{current}</span><span> / </span><span>{total}</span></strong>'


def head(mode='', metric=None, create=False, menu=None, title=None, left='', right=''):
    if title:
        middle = f'<div class="heading"><h1>{title}</h1></div>'
    else:
        middle = (f'<a class="deck-heading" href="decks.html" aria-label="English A2. Choose deck">'
                  f'<span class="deck"><span>English A2</span>{icon("down")}</span></a>')
    if ' · card ' in mode:
        label, position = mode.split(' · card ', 1)
        value = build_metric_value(position.replace(' of ', ' / '))
        right += f'<div class="metric" aria-label="{label}, card {position}"><span>{label}</span>{value}</div>'
    if metric:
        right += '<div class="metric"><span>Successes</span>' + build_metric_value(metric) + '</div>'
    if create:
        right += btn('', 'editor-new', 'quiet square', 'plus', attrs='aria-label="Create card" title="Create card"')
    if menu:
        right += btn('', menu, 'quiet square', 'more', attrs='aria-label="Card actions"')
    return f'<header class="bar">{left}{middle}{right}</header>'


COUNTS = [112, 40, 23, 9, 0, 61]
ROMAN = ['', 'I', 'II', 'III', 'IV']
LABELS = ['New', 'I', 'II', 'III', 'IV', 'Learned']


def tabs(opened, active=1, counts=None, destinations=None):
    counts = COUNTS if counts is None else counts
    links = ['pool', 'learn-front' if active == 1 else 'browse-e1',
             'learn-e2' if active == 2 else 'browse-e2', 'browse', 'empty-e4', 'learned']
    for section, destination in (destinations or {}).items():
        links[section] = destination
    result = []
    for i, label in enumerate(LABELS):
        cls = 'cell' + (' open' if i == opened else '') + (' active' if i == active else '')
        aria = (' aria-current="page"' if i == opened else '') + f' aria-label="{label}, {counts[i]} cards' + (', currently learning' if i == active else '') + '"'
        result.append(f'<a class="{cls}" href="{links[i]}.html"{aria}><span>{label}</span><strong>{counts[i]}'
                      + (icon('active') if i == active else '') + '</strong></a>')
    return '<nav class="strip" aria-label="Deck sections">' + ''.join(result) + '</nav>'


def card(content='', label='Handwritten card'):
    corners = ''.join(f'<i class="corner {x}" aria-hidden="true"></i>' for x in ['tl', 'tr', 'bl', 'br'])
    return f'<section class="card" aria-label="{label}">{corners}{content}</section>'


def handwriting(text, small=''):
    return f'<div class="hand-stack"><p class="hand">{text}</p>' + (f'<p class="hand small">{small}</p>' if small else '') + '</div>'


def foot(items, cls=''):
    return f'<footer class="foot {cls}">' + ''.join(items) + '</footer>'


def add(key, title, body, group, note=''):
    assert key not in SCREENS, key
    SCREENS[key] = dict(title=title, body=body, group=group, note=note)


def learn(key, back_key, epoch=1, pos='14 of 40', successes='3 / 4', front='apple', back='fruit', small='plural: apples', reason='', counts=None, menu=None, grade_target=None, group='Learning', destinations=None):
    menu = menu or ('menu-learn-e2' if epoch == 2 else 'menu-learn')
    links = {**(destinations or {}), **({epoch: key} if counts is not None else {})}
    prefix = head(f'Learning · card {pos}', successes, menu=menu) + tabs(epoch, epoch, counts, links)
    seen_key = key + '-seen' if not reason else key
    states = [(False, key, back_key, False), (True, back_key, seen_key, True)]
    if not reason:
        states.append((False, seen_key, back_key, True))
    for is_back, name, target, revealed in states:
        blocked_reason = reason or ('Answer already revealed' if revealed else '')
        defer_label = 'Move to end of pass' + (f': {blocked_reason.lower()}' if blocked_reason else '')
        defer = btn('', None if blocked_reason else 'learn-postponed', 'off' if blocked_reason else 'quiet', 'defer', attrs=f'aria-label="{defer_label}" title="{defer_label}"')
        flip_label = 'Show question' if is_back else 'Show answer'
        flip = btn('', target, '' if is_back else 'primary', 'flip', attrs=f'aria-label="{flip_label}" title="{flip_label}"')
        next_key = grade_target or ('transition' if reason == 'Last card' else ('learn-e2' if epoch == 2 else 'learn-front'))
        ratings = [btn('Missed it', next_key, glyph='cross'), btn('Nailed it', next_key, glyph='check')] if is_back else []
        add(name, ('Answer' if is_back else 'Question') + f' · {ROMAN[epoch]}' + (f' · {reason.lower()}' if reason else ' · after revealing the answer' if revealed and not is_back else ''),
            prefix + card(handwriting(back, small) if is_back else handwriting(front), 'Answer' if is_back else 'Question') + foot([defer, flip] + ratings),
            group, 'The answer has been revealed. Moving to the end stays disabled, even after returning to the question.' if revealed else 'Card position and success count share the same style; ▶ marks the active epoch.')


learn('learn-front', 'learn-back')
learn('learn-postponed', 'learn-postponed-back', pos='36 of 40', reason='Already postponed')
learn('learn-last-front', 'learn-last', pos='40 of 40', reason='Last card')
learn('learn-e2', 'learn-e2-back', epoch=2, pos='1 of 23', successes='2 / 4', front='street', back='road', small='')


def build_next_pass(epoch, *, preview=False):
    counts = COUNTS.copy()
    if epoch == 4:
        counts[4] = 100
    links = {epoch: f'transition-preview-e{epoch}'} if preview else None
    return (head('Pass complete') + tabs(epoch, epoch, counts, links)
            + card('<div class="empty-icon">' + icon('check')
                   + '</div><h2 class="big next-epoch">' + ROMAN[epoch]
                   + f'</h2><p class="hint">{counts[epoch]} cards</p>', 'Next pass: epoch ' + ROMAN[epoch])
            + foot([btn('Start pass', None if preview else 'learn-e2', 'primary', 'right', static=preview)], 'one'))


add('transition', 'Next pass', build_next_pass(2), 'Learning',
    'Promotion, refill and shuffle happen before this transition appears.')

# A 100-card pass of IV pauses after 40 graded cards. The inserted pass of I
# starts normally; resuming IV preserves its order and continues at card 41.
LONG_COUNTS = [112, 40, 23, 9, 100, 61]
FLOW_GROUP = 'New transitions'
INSERT_LINKS = {1: 'learn-insert', 4: 'browse-e4-insert'}


def pass_transition(epoch, remaining, destination, *, resume=False, destinations=None):
    return (head() + tabs(epoch, epoch, LONG_COUNTS, {**(destinations or {}), epoch: destination})
            + card('<div class="empty-icon">' + icon('undo' if resume else 'right')
                   + '</div><h2 class="big">' + ROMAN[epoch] + '</h2>'
                   + '<p class="hint">' + remaining + '</p>',
                   'Resume pass' if resume else 'Extra pass')
            + foot([btn('Continue' if resume else 'Start pass', destination, 'primary', 'right')], 'one'))


add('transition-insert', 'Extra pass · I',
    pass_transition(1, '40 cards', 'learn-insert', destinations=INSERT_LINKS), FLOW_GROUP,
    'After 40 ratings in IV, an extra pass through I begins. The position in IV is saved; the pass count for I is unchanged.')
add('transition-resume', 'Resume pass · IV',
    pass_transition(4, '60 of 100 remaining', 'learn-resume', resume=True), FLOW_GROUP,
    'Returning from the extra pass through I: next is card 41 of 100. No repeated promotion or shuffle.')
learn('learn-insert', 'learn-insert-back', pos='1 of 40', successes='0 / 4',
      counts=LONG_COUNTS, menu='menu-insert', grade_target='learn-insert', group=FLOW_GROUP, destinations=INSERT_LINKS)
learn('learn-resume', 'learn-resume-back', epoch=4, pos='41 of 100', successes='1 / 3',
      front='cloud', back='water vapor', small='', counts=LONG_COUNTS,
      menu='menu-resume', grade_target='learn-resume', group=FLOW_GROUP)


def browse(key, opened, pos, front, back, metric=None, active=1, *, counts=None, destinations=None, group='Browse'):
    for is_back in [False, True]:
        name = key + ('-back' if is_back else '')
        flip = key if is_back else key + '-back'
        prefix = head(f'Browse · card {pos}', metric, opened == 0, 'menu-' + key) + tabs(opened, active, counts, destinations)
        flip_label = 'Show question' if is_back else 'Show answer'
        footer = foot([btn('', key, 'quiet', 'left', attrs='aria-label="Previous card" title="Previous card"'), btn('', flip, glyph='flip', attrs=f'aria-label="{flip_label}" title="{flip_label}"'), btn('', key, 'quiet next', 'right', attrs='aria-label="Next card" title="Next card"')], 'nav')
        add(name, LABELS[opened] + (' · answer' if is_back else ' · browse'), prefix + card(handwriting(back if is_back else front)) + footer,
            group, 'The underline marks the open section. ▶ marks the active epoch.')


browse('browse', 3, '2 of 9', 'bridge', 'crossing', '1 / 4')
browse('browse-e2', 2, '1 of 23', 'street', 'road', '2 / 4')
browse('browse-e1', 1, '14 of 40', 'apple', 'fruit', '3 / 4', active=2)
browse('pool', 0, '2 of 112', 'tree', 'plant')
browse('learned', 5, '2 of 61', 'house', 'home')
browse('browse-e4-insert', 4, '41 of 100', 'cloud', 'water vapor', '1 / 3',
       counts=LONG_COUNTS, destinations=INSERT_LINKS, group=FLOW_GROUP)


def empty(opened, title, text, create=False, counts=None):
    return head(create=create) + tabs(opened, counts=counts) + card('<div class="empty-icon">' + icon('cards') + f'</div><h2>{title}</h2>', 'Empty section') + foot([])


add('empty-e4', 'Empty epoch', empty(4, 'No cards', ''), 'Browse', 'The empty section can be opened. There are no paging controls or card actions.')
add('empty-pool', 'Empty new cards', empty(0, 'No new cards', '', True, [0,40,23,9,0,61]), 'Browse')
add('empty-all', 'No cards to study', empty(0, 'No new cards', '', True, [0,0,0,0,0,61]), 'Browse')


def editor(new=True, back=False, shape_state=""):
    context = ("editor-new" if new else "editor-edit") + ("-back" if back else "")
    base = 'editor-new' if new else 'editor-edit'
    left = btn('Cancel' if new else 'Delete', 'dlg-discard' if new else 'dlg-delete-edit', 'quiet', None if new else 'trash', attrs='aria-label="' + ('Cancel card creation' if new else 'Delete card') + '"')
    header = head(title='New card' if new else 'Edit card', left=left, right=btn('Save', 'pool', 'primary', 'check'))
    sides = '<nav class="side-tabs" aria-label="Card side">'
    for is_back, title in [(False, 'Question'), (True, 'Answer')]:
        selected = is_back == back
        sides += f'<a class="side-tab {"selected" if selected else ""}" href="{base}{"-back" if is_back else ""}.html"' + (' aria-current="page"' if selected else '') + f'>{title}' + '</a>'
    sides += '</nav>'
    content = '' if back and new else handwriting('plant' if back else 'tree')
    toolbar = '<div class="tools" aria-label="Drawing tools">'
    # Static controls intentionally do not pretend to draw or change application state.
    toolbar += '<div class="thickness" role="group" aria-label="Pen width">'
    for i, (name, size) in enumerate([('Thin', 2), ('Medium', 7), ('Thick', 15)]):
        toolbar += f'<button class="btn {"selected" if i == 1 else ""}" aria-label="{name} line" aria-pressed="{str(i == 1).lower()}" title="Pen width preview"><i class="line-sample" style="height:{size}px"></i></button>'
    toolbar += '</div>' + btn('', context + '-shapes', 'quiet square', 'shapes', attrs='aria-label="Shapes" title="Shapes" data-open-shapes')
    toolbar += '<button class="btn icon-only quiet undo" aria-label="Undo stroke" title="Undo stroke">'+icon('undo')+'</button></div>'
    # Every state has a static board; the script lets the same design be tried with a pen or mouse.
    state = shape_state or 'idle'
    bounds = {'ready': (354, 371, 220, 220), 'selected': (320, 410, 220, 220), 'moved': (510, 490, 220, 220),
              'resized': (420, 390, 360, 300), 'placed': (420, 390, 360, 300),
              'arrow': (320, 410, 320, 80), 'rotated': (320, 410, 220, 220)}
    x, y, width, height = bounds.get(state, (320, 410, 220, 220))
    shape = 'arrow' if state == 'arrow' else 'star'
    angle = -35 if state == 'arrow' else 30 if state == 'rotated' else 0
    has_shape = state in bounds
    selected = has_shape and state != 'placed'
    handles = ''.join(f'<button type="button" class="shape-handle {corner}" data-resize="{corner}" aria-label="{label}"></button>'
                      for corner, label in [('nw', 'Resize: top left corner'), ('ne', 'Resize: top right corner'),
                                            ('sw', 'Resize: bottom left corner'), ('se', 'Resize: bottom right corner')])
    if shape == 'arrow':
        handles = ''.join(f'<button type="button" class="shape-handle {endpoint}" data-endpoint="{endpoint}" aria-label="Move {endpoint} point"></button>'
                          for endpoint in ['start', 'end'])
        graphic = build_linear_shape(shape, width, height)
        hit_graphic = build_linear_shape(shape, width, height, 19)
    else:
        handles += '<button type="button" class="shape-rotate" data-rotate aria-label="Rotate shape">' + icon('undo') + '</button>'
        graphic = build_shape(shape)
        hit_graphic = build_shape(shape, 19)
    hit_shape = hit_graphic.replace('class="shape-graphic"', 'class="shape-graphic shape-hit"')
    object_body = (f'<div class="shape-object{" is-linear" if shape == "arrow" else ""}{" is-selected" if selected else ""}" data-object="0" '
                   f'style="left:{x}px;top:{y}px;width:{width}px;height:{height}px;transform:rotate({angle}deg)" role="group" aria-label="{SHAPES[shape][0]}">'
                   + graphic + hit_shape + '<div class="shape-handles"' + ('' if selected else ' hidden') + '>' + handles + '</div></div>')
    stage = (f'<div class="shape-stage" data-shape-editor data-state="{state}" data-context="{context}" data-kind="{shape}" data-angle="{angle}" '
             f'data-bounds="{x},{y},{width},{height}">' + (object_body if has_shape else '') + '</div>')
    normal_hidden = ' hidden' if state in ('ready', 'selected', 'moved', 'resized', 'arrow', 'rotated') else ''
    toolbar = toolbar.replace('<div class="tools"', '<div class="tools" data-normal-tools' + normal_hidden, 1)
    edit_tools = ('<div class="tools shape-edit-tools" data-shape-tools' + ('' if normal_hidden else ' hidden') + '>'
                  + btn('', context, 'quiet', 'cross', attrs='aria-label="Cancel shape insertion or changes" title="Cancel" data-cancel-shape')
                  + '<button type="button" class="btn quiet" data-open-shapes aria-label="Shape and outline width" title="Shape and outline width">'
                  + build_shape(shape) + '</button>'
                  + '<button type="button" class="btn primary apply-shape" data-apply-shape aria-label="Apply shape" title="Apply"'
                  + ('' if selected else ' disabled') + '>' + icon('check') + '</button></div>')
    palette = '<div class="overlay shape-overlay" data-shape-palette' + ('' if state == 'palette' else ' hidden') + '>'
    palette += f'<a class="scrim" href="{context}.html" data-close-shapes aria-label="Close shape menu"></a>'
    palette += '<section class="shape-palette" role="dialog" aria-modal="true" aria-labelledby="shape-palette-title" tabindex="-1">'
    palette += '<header><h2 id="shape-palette-title">Shapes</h2>' + btn('', context, 'quiet square', 'cross', attrs='aria-label="Close shape menu" data-close-shapes') + '</header>'
    palette += '<div class="shape-grid">'
    for shape, (label, _) in SHAPES.items():
        palette += (f'<a class="shape-choice" href="{context}-shape-ready.html" data-choose-shape="{shape}" '
                    f'aria-label="{label}" title="{label}">' + build_shape(shape) + '</a>')
    palette += '</div><div class="shape-width"><label for="shape-width">Outline width</label><output for="shape-width" data-width-value>3</output>'
    palette += '<svg class="stroke-preview" viewBox="0 0 80 24" aria-hidden="true"><path d="M8 12H72" stroke="currentColor" stroke-width="3" fill="none" data-width-preview/></svg>'
    palette += '<input id="shape-width" type="range" min="1" max="12" step="1" value="3" aria-label="Outline width"></div></section></div>'
    frame = '<div class="shape-editor-frame" data-editor-frame' + (' inert' if state == 'palette' else '') + '>'
    return frame + header + sides + card(content + stage, 'Canvas. Draw with the pen only') + toolbar + edit_tools + '</div>' + palette


for new in [True, False]:
    for back in [False, True]:
        key = ('editor-new' if new else 'editor-edit') + ('-back' if back else '')
        add(key, ('Create' if new else 'Edit') + (' · answer' if back else ' · question'), editor(new, back), 'Editor', 'The same 928 × 963 sheet. Tools: shapes, pen width and undo; the stylus side button erases. Either side may be blank.')


def modal(background, title, text, actions):
    # Background is inert even for keyboard navigation; there is no clickable scrim.
    return '<div style="display:contents" inert>' + background + '</div><div class="overlay"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">' + title + '</h2>' + ('<p>' + text + '</p>' if text else '') + foot(actions, 'one' if len(actions) == 1 else 'two') + '</section></div>'


def popup(background, title, items, close, top=108):
    links = ''.join(f'<a href="{dest}.html" class="{"danger" if glyph == "trash" else ""}">{icon(glyph)}<span>{label}</span></a>' for label, dest, glyph in items)
    return '<div style="display:contents" inert>' + background + f'</div><div class="overlay"><a class="scrim" href="{close}.html" aria-label="Close menu"></a><nav class="popup" aria-label="{title}" style="top:{top}px"><h2>{title}</h2>{links}</nav></div>'


for key, section in [('learn-front', 'learn'), ('learn-e2', 'learn-e2'), ('browse', 'browse'), ('browse-e2', 'browse-e2'), ('browse-e1', 'browse-e1'), ('pool', 'pool'), ('learned', 'learned'), ('learn-insert', 'insert'), ('learn-resume', 'resume'), ('browse-e4-insert', 'browse-e4-insert')]:
    background = SCREENS[key]['body']
    items = []
    if section == 'pool':
        items.append(('Edit', 'editor-edit', 'pen'))
    elif section != 'learned':
        items.append(('Return to New', 'dlg-return-' + section, 'undo'))
        add('dlg-return-' + section, 'Return to New · ' + section,
            modal(background, 'Return to New?', 'Your success count will reset.', [btn('Keep', key, 'primary'), btn('Return', key)]), 'Dialogs')
    items.append(('Delete card', 'dlg-delete-' + section, 'trash'))
    add('menu-' + section, 'Menu · ' + LABELS[0 if section == 'pool' else 5 if section == 'learned' else 4 if section in ['resume', 'browse-e4-insert'] else 2 if section in ['learn-e2','browse-e2'] else 3 if section == 'browse' else 1],
        popup(background, 'Card actions', items, key), 'Menu')
    add('dlg-delete-' + section, 'Delete card · ' + section,
        modal(background, 'Delete card?', "Both sides will be deleted. You can't undo this.", [btn('Keep', key, 'primary'), btn('Delete', key)]), 'Dialogs')

add('dlg-discard', 'Cancel creation', modal(editor(), 'Discard this draft?', "Your drawings on both sides won't be saved.", [btn('Keep editing', 'editor-new', 'primary'), btn('Discard draft', 'pool')]), 'Dialogs')
add('dlg-delete-edit', 'Delete from editor', modal(editor(False), 'Delete card?', "Both sides will be deleted. You can't undo this.", [btn('Keep editing', 'editor-edit', 'primary'), btn('Delete', 'pool')]), 'Dialogs')
add('dlg-editor-back', 'System Back button', modal(editor(), 'Save and exit?', '', [btn('Keep editing', 'editor-new', 'primary'), btn('Save and exit', 'pool')]), 'Dialogs', 'Proposed system Back behavior. Nothing is silently deleted.')
add('dlg-need-card', 'Nothing to review', modal(empty(1, '', '', counts=[0,0,0,0,0,61]), 'Nothing to review yet', 'No cards to study.', [btn('Open New', 'empty-all', 'primary')]), 'Dialogs')

# Distinct from an empty deck: II has cards, but its minimum interval has
# not elapsed, while I and NEW are empty. No date or countdown is shown.
WAITING_COUNTS = [0, 0, 23, 9, 0, 61]
WAITING_LINKS = {0: 'pool-waiting', 1: 'empty-e1-waiting', 2: 'dlg-today-done'}
waiting_background = (head() + tabs(2, 2, WAITING_COUNTS, WAITING_LINKS)
                      + card('', 'Waiting epoch II') + foot([]))
add('dlg-today-done', 'All done for today',
    modal(waiting_background, 'All done for today', '',
          [btn('Open New', 'pool-waiting', 'primary', 'right')]),
    FLOW_GROUP, 'II is not due yet; I and New are empty. Closing opens New; ▶ stays on the waiting epoch II. No dates or timers.')
add('pool-waiting', 'New · waiting for II',
    head(create=True) + tabs(0, 2, WAITING_COUNTS, WAITING_LINKS)
    + card('<div class="empty-icon">' + icon('cards') + '</div><h2>No new cards</h2>', 'Empty new card pool')
    + foot([]), FLOW_GROUP,
    'A card can be created here. Tapping II checks again; until the interval has elapsed, All done for today appears again.')
add('empty-e1-waiting', 'Empty I · waiting for II',
    head() + tabs(1, 2, WAITING_COUNTS, WAITING_LINKS)
    + card('<div class="empty-icon">' + icon('cards') + '</div><h2>No cards</h2>', 'Empty section I')
    + foot([]), FLOW_GROUP,
    'I is empty; II remains the waiting epoch. No card actions or paging controls.')


def deck_row(name, info, number, current=False):
    # Secondary decks demonstrate list density, not a separate study session.
    destination = 'learn-front' if current else 'decks'
    menu = 'deck-menu' if current else 'deck-menu-' + str(number)
    return '<div class="item ' + ('current' if current else '') + '"><span class="number">' + (icon('check') if current else f'{number:02}') + '</span><a class="fill" href="' + destination + '.html" title="' + ('Open deck' if current else 'Sample list row; this deck has no separate session in the mockup') + '"><h2>' + name + '</h2><p>' + info + '</p></a>' + btn('', menu, 'quiet square', 'more', attrs='aria-label="Deck menu: ' + name + '"') + '</div>'


deck_header = head(title='Decks', left=btn('', 'learn-front', 'quiet square', 'left', attrs='aria-label="Back to cards"'))
deck_actions = foot([btn('New deck', 'deck-new', 'primary', 'plus'), btn('Import from file', 'decks', 'quiet', 'file', attrs='title="The app opens the system file picker. This is a mockup."')], 'two')
deck_list = '<div class="list">' + deck_row('English A2', '245 cards · ' + icon('active') + ' I', 1, True) + deck_row('English tenses', '86 cards · ' + icon('active') + ' II', 2) + deck_row('Formulas', '18 cards · learned', 3) + '</div>'
decks = deck_header + deck_list + deck_actions
add('decks', 'Decks', decks, 'Decks', 'Deck names take priority over secondary actions. A check marks the current deck.')
add('decks-empty', 'First launch', head(title='Decks') + card('<div class="empty-icon">'+icon('cards')+'</div><h2>No decks</h2>') + deck_actions, 'Decks')
pages = '<nav class="page-nav" aria-label="Deck pages">' + btn('', None, 'off square', 'left', attrs='aria-label="Previous page"') + '<span>Page 1 of 2</span>' + btn('', 'decks-page2', 'quiet square', 'right', attrs='aria-label="Next page"') + '</nav>'
add('decks-pages', 'Decks · multiple pages', deck_header + deck_list + pages + deck_actions, 'Decks', 'A separate pagination example; arrows are hidden for a single page.')
page2 = '<div class="list">' + deck_row('Art history', '32 cards · ' + icon('active') + ' I', 4) + '</div>'
add('decks-page2', 'Decks · page 2', deck_header + page2 + '<nav class="page-nav" aria-label="Deck pages">' + btn('', 'decks-pages', 'quiet square', 'left', attrs='aria-label="Previous page"') + '<span>Page 2 of 2</span>' + btn('', None, 'off square', 'right', attrs='aria-label="Next page"') + '</nav>' + deck_actions, 'Decks')
add('deck-menu', 'Deck menu', popup(decks, 'English A2', [('Rename', 'deck-rename', 'pen'), ('Export to file', 'decks', 'export'), ('Delete deck', 'dlg-delete-deck', 'trash')], 'decks', 206), 'Decks')
for number, name, count in [(2, 'English tenses', 86), (3, 'Formulas', 18), (4, 'Art history', 32)]:
    context_key = 'decks-page2' if number == 4 else 'decks'
    context = SCREENS[context_key]['body']
    add('deck-menu-' + str(number), 'Menu · ' + name,
        popup(context, name, [('Rename', 'deck-rename-' + str(number), 'pen'), ('Export to file', context_key, 'export'), ('Delete deck', 'dlg-delete-deck-' + str(number), 'trash')], context_key, 206 if number == 4 else 206 + (number-1)*148), 'Menu')
    add('deck-rename-' + str(number), 'Rename · ' + name,
        head(title='Rename', left=btn('Cancel', context_key, 'quiet'), right=btn('Save', context_key, 'primary')) + '<label class="field">Deck name<input class="input" value="' + name + '"></label><div class="keyboard">System keyboard area</div>', 'Dialogs')
    add('dlg-delete-deck-' + str(number), 'Delete · ' + name,
        modal(context, 'Delete “' + name + '”?', f"All {count} cards and their progress will be deleted. You can't undo this.", [btn('Keep', context_key, 'primary'), btn('Delete', context_key)]), 'Dialogs')
for rename in [False, True]:
    title = 'Rename' if rename else 'New deck'
    body = head(title=title, left=btn('Cancel', 'decks', 'quiet'), right=btn('Save' if rename else 'Create', 'decks' if rename else None, 'primary' if rename else 'off'))
    body += '<label class="field">Deck name<input class="input" value="' + ('English A2' if rename else '') + '" placeholder="English A2"></label><div class="keyboard">System keyboard area</div>'
    add('deck-rename' if rename else 'deck-new', title, body, 'Decks', 'Static field example; typing does not save a deck.')
add('dlg-delete-deck', 'Delete deck', modal(decks, 'Delete “English A2”?', "All 245 cards and their progress will be deleted. You can't undo this.", [btn('Keep', 'decks', 'primary'), btn('Delete', 'decks')]), 'Decks')
add('dlg-import-error', 'Import error', modal(decks, "Couldn't open file", "This file is damaged or uses a format we can't open.", [btn('Close', 'decks', 'primary')]), 'Decks')

tokens = head(title='V5 · Clear Sheet') + '<div class="spec">'
for title, text in [
    ('Layout', 'Header 64 · tabs 72 · sheet 963 · actions 112.<br>Margins 32, gaps 16. Frame 992 × 1323.'),
    ('Sheet', '928 × 963 in every mode. Handwriting and outline shapes.<br>Four corners mark the edge without a heavy border.'),
    ('States', 'Underline: open section.<br>▶: active epoch. Sections I, II, III, IV.'),
    ('Typography', 'Title 28 · label 18 · counters 26.<br>Buttons 24–26 · notes at least 18.'),
    ('Actions', 'Flip stays in the same position.<br>Rating buttons have equal weight and appear only on the answer.'),
    ('Appearance', 'Black and white, no shadows or animation.<br>Thin dividers; borders only where needed.'),
    ('Device', 'Check touch targets ≥64 dp and text ≥18 sp.<br>Mockup dimensions are design units, not device dp.'),
]:
    tokens += f'<strong>{title}</strong><span>{text}</span>'
add('tokens', 'Design guidelines', tokens + '</div>', 'System')

# Startup states already implemented by design-e. System actions are enabled
# visual samples: a browser mockup cannot open Android settings or close the app.
STARTUP_GROUP = 'Startup and errors'
for key, title, text, action in [
    ('startup-file-access', 'We need file access',
     'Your decks are in the Deckly folder. Allow file access to open them.', 'Open settings'),
    ('startup-database-damaged', 'Database damaged',
     "Couldn't open Deckly/deckly.db. We haven't deleted anything.", 'Close'),
    ('startup-database-missing', 'Database not found',
     "We can't find deckly.db in the Deckly folder. Your drawings are still there.", 'Close'),
]:
    add(key, title,
        modal('', title, text, [btn(action, cls='primary', static=True)]),
        STARTUP_GROUP, 'Startup dialog on a white background, without a deck header. The mockup does not perform the system action.')

add('startup-loading', 'Loading the app', '<div class="loading-brand"><h1>Deckly</h1></div>', STARTUP_GROUP,
    'Deckly centered on a white screen while storage opens. No indicator, buttons or automatic transition in the mockup.')
add('card-damaged', 'Card · drawing unavailable',
    head('Learning · card 14 of 40', '3 / 4', menu='menu-learn')
    + tabs(1)
    + card('', 'Drawing unavailable')
    + foot([
        btn('', 'learn-postponed', 'quiet', 'defer', attrs='aria-label="Move to end of pass" title="Move to end of pass"'),
        btn('', cls='primary', glyph='flip', attrs='aria-label="Show answer" title="Show answer"', static=True),
    ]), STARTUP_GROUP,
    'An existing card with a blank sheet; Drawing unavailable is an accessibility description only. '
    'The question is shown; flipping is not simulated here. Menu and navigation links open the base static examples.')


for new in [True, False]:
    for back in [False, True]:
        context = ('editor-new' if new else 'editor-edit') + ('-back' if back else '')
        label = ('New card' if new else 'Edit') + (' · answer' if back else ' · question')
        for suffix, state, title, note in [
            ('shapes', 'palette', 'Shapes', '15 outline shapes. A slider controls outline width independently of the pen; no fill.'),
            ('shape-ready', 'ready', 'Inserted shape', 'Choosing a shape closes the palette and inserts it at the center with the remembered outline width. No extra pen tap is needed.'),
            ('shape-selected', 'selected', 'Selection', 'Drag near the outline to move. Corners resize; the upper handle rotates. Touch away from the outline to keep the shape and clear selection, including inside the hollow figure.'),
            ('shape-moved', 'moved', 'Moving', 'The shape has moved within the sheet. Its size and outline width are unchanged.'),
            ('shape-resized', 'resized', 'Resizing', 'Width and height change independently; stretching does not thicken the outline.'),
            ('shape-placed', 'placed', 'After placement', 'The selection frame is hidden and the normal toolbar returns. Tap near the outline to select the shape again; the interior stays free for writing.'),
            ('shape-arrow', 'arrow', 'Arrow direction', 'Drag either endpoint to change the length and direction. Drag the shaft to move the whole arrow. Outline width and arrowhead size stay constant.'),
            ('shape-rotated', 'rotated', 'Rotation', 'Drag the handle above the frame to rotate the shape. Corner handles resize along its rotated axes. Outline width stays constant.'),
        ]:
            add(context + '-' + suffix, title + ' · ' + label, editor(new, back, state), 'Shapes in the editor', note)


for epoch in range(1, 5):
    add(f'transition-preview-e{epoch}', 'Next pass · ' + ROMAN[epoch],
        build_next_pass(epoch, preview=True), 'Next pass · I–IV',
        'Numeral shifted 6 units left below the check mark. Visual comparison; Start pass is a static control.')


def document(title, body, dc=False, name=''):
    style = f'<style>{CSS}</style>' if dc else '<link rel="stylesheet" href="styles.css">'
    return '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=992"><title>E · ' + title + '</title></head><body>' + ('<x-dc>' if dc else '') + style + f'<main class="screen" data-screen="{name}">' + body + '</main>' + ('<script>' + SHAPE_SCRIPT + '</script>' if 'data-shape-editor' in body else '') + ('</x-dc>' if dc else '') + '</body></html>'


boards = {}
for i, (key, screen) in enumerate(SCREENS.items()):
    (ROOT / f'{key}.html').write_text(document(screen['title'], screen['body'], name=key))
    fn = f'E-{key}.dc.html'
    (ROOT / fn).write_text(document(screen['title'], screen['body'], True, key))
    boards[fn] = dict(x=(i % 4)*1072, y=(i // 4)*1483, w=992, h=1323, title='E · ' + screen['title'])
(ROOT / 'canvas.json').write_text(json.dumps(dict(v=3, title='Flashcards · Design E / v5', launch={'view':'canvas'}, pages=[], boards=boards, order=list(boards), notes={}, designSystems=[]), ensure_ascii=False, indent=2))

GALLERY_CSS = '''*{box-sizing:border-box}body{margin:0;padding:48px;background:#fff;color:#000;font:18px/1.5 Arial,sans-serif}a{color:inherit}h1,h2,h3,p{margin:0}header{max-width:1200px;border-bottom:2px solid;padding-bottom:32px;margin-bottom:32px}header small{font-size:15px;letter-spacing:2px}h1{font-size:64px;line-height:1.1;letter-spacing:-3px;margin:16px 0}header p{max-width:740px;font-size:21px;margin:20px 0}nav{display:flex;flex-wrap:wrap;gap:24px}nav a{min-height:44px;display:flex;align-items:center}h2{font-size:28px;margin:48px 0 24px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:40px 32px;max-width:1600px}article{max-width:340px}article>a{text-decoration:none}article h3{font-size:20px;margin:16px 0 8px}article p{font-size:16px}.preview{width:299.6px;height:398.9px;border:1px solid #000;overflow:hidden;background:#fff;pointer-events:none}iframe{border:0;width:992px;height:1323px;transform:scale(.3);transform-origin:top left}.pair{display:flex;flex-wrap:wrap;gap:40px;margin-top:24px}.pair .preview{width:398.8px;height:531.2px}.pair iframe{transform:scale(.4)}.pair h3{font-size:18px;margin:12px 0}.pair article{max-width:430px}.pair p{max-width:398px}a:focus-visible{outline:3px solid;outline-offset:4px}@media(max-width:600px){body{padding:24px}h1{font-size:44px;letter-spacing:-1px}.pair .preview{width:299.6px;height:398.9px}.pair iframe{transform:scale(.3)}}'''


def gallery_doc(title, body):
    return f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{title}</title><style>{GALLERY_CSS}</style></head><body>{body}</body></html>'


groups = list(dict.fromkeys(s['group'] for s in SCREENS.values()))
body = '<header><small>FLASHCARDS FOR BOOX · DESIGN E / V5</small><h1>Clear Sheet.</h1><p>Handwritten flashcards, Roman numerals and compact action icons.</p><nav><a href="learn-front.html">Open main screen ↗</a><a href="README.md">Design notes</a></nav></header><nav>'
body += ''.join(f'<a href="#g{i}">{g}</a>' for i, g in enumerate(groups)) + '</nav>'
for i, group in enumerate(groups):
    body += f'<section id="g{i}"><h2>{group}</h2><div class="grid">'
    for key, screen in SCREENS.items():
        if screen['group'] != group:
            continue
        body += f'<article><a href="{key}.html"><div class="preview"><iframe src="{key}.html" title="{escape(screen["title"])}" loading="lazy" tabindex="-1"></iframe></div><h3>{screen["title"]}</h3></a><p>{screen["note"]}</p></article>'
    body += '</div></section>'
(ROOT / 'index.html').write_text(gallery_doc('V5 · Clear Sheet · gallery', body))

print(f'Generated {len(SCREENS)} screens, {len(boards)} canvas boards and gallery in {ROOT}')
