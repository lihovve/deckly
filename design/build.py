#!/usr/bin/env python3
"""Build the v5 design proposal. Standard library only; no application logic."""
from pathlib import Path
from html import escape
import json

ROOT = Path(__file__).resolve().parent
CSS = (ROOT / 'styles.css').read_text()
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
    'eraser': '<path d="m3 14 9-10 9 8-8 9H9l-6-7Zm4-4 9 8M13 21h8"/>',
    'undo': '<path d="M5 5v6h6M5 11c4-9 15-5 15 2 0 4-3 7-7 7"/>',
    'trash': '<path d="M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
    'file': '<path d="M5 3h9l5 5v13H5V3Zm9 0v6h5M8 14h8m-8 3h5"/>',
    'cards': '<rect x="6" y="6" width="14" height="16" rx="2"/><path d="M16 3H5a2 2 0 0 0-2 2v13"/>',
    'export': '<path d="M12 15V3m-4 4 4-4 4 4M4 13v8h16v-8"/>',
}


def icon(name):
    return f'<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">{PATHS[name]}</svg>'


def btn(label, href=None, cls='', glyph=None, sub='', attrs=''):
    content = (icon(glyph) if glyph else '') + (f'<span>{label}' + (f'<small>{sub}</small>' if sub else '') + '</span>' if label or sub else '')
    if glyph and not label:
        cls += ' icon-only'
    common = f'class="btn {cls}" {attrs}'
    return f'<a {common} href="{href}.html">{content}</a>' if href else f'<button type="button" {common} disabled>{content}</button>'


def head(mode='', metric=None, create=False, menu=None, title=None, left='', right=''):
    if title:
        middle = f'<div class="heading"><h1>{title}</h1></div>'
    else:
        middle = (f'<a class="deck-heading" href="decks.html" aria-label="Немецкий A2. Выбрать колоду">'
                  f'<span class="deck"><span>Немецкий A2</span>{icon("down")}</span></a>')
    if ' · карточка ' in mode:
        label, position = mode.split(' · карточка ', 1)
        right += f'<div class="metric" aria-label="{label}, карточка {position}"><span>{label}</span><strong>{position.replace(" из ", " / ")}</strong></div>'
    if metric:
        right += f'<div class="metric"><span>Успехи</span><strong>{metric}</strong></div>'
    if create:
        right += btn('', 'editor-new', 'quiet square', 'plus', attrs='aria-label="Создать карточку" title="Создать карточку"')
    if menu:
        right += btn('', menu, 'quiet square', 'more', attrs='aria-label="Действия с карточкой"')
    return f'<header class="bar">{left}{middle}{right}</header>'


COUNTS = [112, 40, 23, 9, 0, 61]
ROMAN = ['', 'I', 'II', 'III', 'IV']
LABELS = ['Новые', 'I', 'II', 'III', 'IV', 'Выучено']


def tabs(opened, active=1, counts=None, destinations=None):
    counts = COUNTS if counts is None else counts
    links = ['pool', 'learn-front' if active == 1 else 'browse-e1',
             'learn-e2' if active == 2 else 'browse-e2', 'browse', 'empty-e4', 'learned']
    for section, destination in (destinations or {}).items():
        links[section] = destination
    result = []
    for i, label in enumerate(LABELS):
        cls = 'cell' + (' open' if i == opened else '') + (' active' if i == active else '')
        aria = (' aria-current="page"' if i == opened else '') + f' aria-label="{label}, {counts[i]} карточек' + (', сейчас обучение' if i == active else '') + '"'
        result.append(f'<a class="{cls}" href="{links[i]}.html"{aria}><span>{label}</span><strong>{counts[i]}'
                      + (icon('active') if i == active else '') + '</strong></a>')
    return '<nav class="strip" aria-label="Секции колоды">' + ''.join(result) + '</nav>'


def card(content='', label='Рукописная карточка'):
    corners = ''.join(f'<i class="corner {x}" aria-hidden="true"></i>' for x in ['tl', 'tr', 'bl', 'br'])
    return f'<section class="card" aria-label="{label}">{corners}{content}</section>'


def handwriting(text, small=''):
    return f'<div class="hand-stack"><p class="hand">{text}</p>' + (f'<p class="hand small">{small}</p>' if small else '') + '</div>'


def foot(items, cls=''):
    return f'<footer class="foot {cls}">' + ''.join(items) + '</footer>'


def add(key, title, body, group, note=''):
    assert key not in SCREENS, key
    SCREENS[key] = dict(title=title, body=body, group=group, note=note)


def learn(key, back_key, epoch=1, pos='14 из 40', successes='3 / 4', front='der Apfel', back='яблоко', small='мн. ч. die Äpfel', reason='', counts=None, menu=None, grade_target=None, group='Обучение', destinations=None):
    menu = menu or ('menu-learn-e2' if epoch == 2 else 'menu-learn')
    links = {**(destinations or {}), **({epoch: key} if counts is not None else {})}
    prefix = head(f'Обучение · карточка {pos}', successes, menu=menu) + tabs(epoch, epoch, counts, links)
    seen_key = key + '-seen' if not reason else key
    states = [(False, key, back_key, False), (True, back_key, seen_key, True)]
    if not reason:
        states.append((False, seen_key, back_key, True))
    for is_back, name, target, revealed in states:
        blocked_reason = reason or ('Ответ уже открыт' if revealed else '')
        defer_label = 'В конец прохода' + (f': {blocked_reason.lower()}' if blocked_reason else '')
        defer = btn('', None if blocked_reason else 'learn-postponed', 'off' if blocked_reason else 'quiet', 'defer', attrs=f'aria-label="{defer_label}" title="{defer_label}"')
        flip_label = 'Показать вопрос' if is_back else 'Показать ответ'
        flip = btn('', target, '' if is_back else 'primary', 'flip', attrs=f'aria-label="{flip_label}" title="{flip_label}"')
        next_key = grade_target or ('transition' if reason == 'Последняя' else ('learn-e2' if epoch == 2 else 'learn-front'))
        ratings = [btn('Не знал', next_key, glyph='cross'), btn('Знал', next_key, glyph='check')] if is_back else []
        add(name, ('Ответ' if is_back else 'Вопрос') + f' · {ROMAN[epoch]}' + (f' · {reason.lower()}' if reason else ' · после ответа' if revealed and not is_back else ''),
            prefix + card(handwriting(back, small) if is_back else handwriting(front), 'Ответ' if is_back else 'Вопрос') + foot([defer, flip] + ratings),
            group, 'Ответ уже открыт: перенос в конец заблокирован, в том числе после возврата к вопросу.' if revealed else 'Счётчик карточек и успехи в одном стиле; активная эпоха отмечена ▶.')


learn('learn-front', 'learn-back')
learn('learn-postponed', 'learn-postponed-back', pos='36 из 40', reason='Уже отложена')
learn('learn-last-front', 'learn-last', pos='40 из 40', reason='Последняя')
learn('learn-e2', 'learn-e2-back', epoch=2, pos='1 из 23', successes='2 / 4', front='die Straße', back='улица', small='')
add('transition', 'Следующий проход', head('Проход завершён') + tabs(2, 2)
    + card('<div class="empty-icon">'+icon('check')+'</div><h2 class="big">II</h2><p class="hint">23 карточки</p>', 'Следующий проход: эпоха II')
    + foot([btn('Начать проход', 'learn-e2', 'primary', 'right')], 'one'), 'Обучение', 'Выпуск, добор и перемешивание выполнены до показа перехода.')

# A 100-card pass of IV pauses after 40 graded cards. The inserted pass of I
# starts normally; resuming IV preserves its order and continues at card 41.
LONG_COUNTS = [112, 40, 23, 9, 100, 61]
FLOW_GROUP = 'Новые переходы'
INSERT_LINKS = {1: 'learn-insert', 4: 'browse-e4-insert'}


def pass_transition(epoch, remaining, destination, *, resume=False, destinations=None):
    return (head() + tabs(epoch, epoch, LONG_COUNTS, {**(destinations or {}), epoch: destination})
            + card('<div class="empty-icon">' + icon('undo' if resume else 'right')
                   + '</div><h2 class="big">' + ROMAN[epoch] + '</h2>'
                   + '<p class="hint">' + remaining + '</p>',
                   'Продолжение прохода' if resume else 'Вставной проход')
            + foot([btn('Продолжить' if resume else 'Начать проход', destination, 'primary', 'right')], 'one'))


add('transition-insert', 'Вставной проход · I',
    pass_transition(1, '40 карточек', 'learn-insert', destinations=INSERT_LINKS), FLOW_GROUP,
    'После 40 оценок в IV начинается вставной проход I. Позиция в IV сохранена; pass1 не меняется.')
add('transition-resume', 'Продолжение прохода · IV',
    pass_transition(4, 'Осталось 60 из 100', 'learn-resume', resume=True), FLOW_GROUP,
    'Возврат после вставного прохода I: следующая карточка 41 из 100. Без повторного выпуска и перемешивания.')
learn('learn-insert', 'learn-insert-back', pos='1 из 40', successes='0 / 4',
      counts=LONG_COUNTS, menu='menu-insert', grade_target='learn-insert', group=FLOW_GROUP, destinations=INSERT_LINKS)
learn('learn-resume', 'learn-resume-back', epoch=4, pos='41 из 100', successes='1 / 3',
      front='die Wolke', back='облако', small='', counts=LONG_COUNTS,
      menu='menu-resume', grade_target='learn-resume', group=FLOW_GROUP)


def browse(key, opened, pos, front, back, metric=None, active=1, *, counts=None, destinations=None, group='Просмотр'):
    for is_back in [False, True]:
        name = key + ('-back' if is_back else '')
        flip = key if is_back else key + '-back'
        prefix = head(f'Просмотр · карточка {pos}', metric, opened == 0, 'menu-' + key) + tabs(opened, active, counts, destinations)
        flip_label = 'Показать вопрос' if is_back else 'Показать ответ'
        footer = foot([btn('', key, 'quiet', 'left', attrs='aria-label="Предыдущая карточка" title="Предыдущая карточка"'), btn('', flip, glyph='flip', attrs=f'aria-label="{flip_label}" title="{flip_label}"'), btn('', key, 'quiet next', 'right', attrs='aria-label="Следующая карточка" title="Следующая карточка"')], 'nav')
        add(name, LABELS[opened] + (' · ответ' if is_back else ' · просмотр'), prefix + card(handwriting(back if is_back else front)) + footer,
            group, 'Подчёркивание — открытая секция. ▶ — активная эпоха.')


browse('browse', 3, '2 из 9', 'die Brücke', 'мост', '1 / 4')
browse('browse-e2', 2, '1 из 23', 'die Straße', 'улица', '2 / 4')
browse('browse-e1', 1, '14 из 40', 'der Apfel', 'яблоко', '3 / 4', active=2)
browse('pool', 0, '2 из 112', 'der Baum', 'дерево')
browse('learned', 5, '2 из 61', 'das Haus', 'дом')
browse('browse-e4-insert', 4, '41 из 100', 'die Wolke', 'облако', '1 / 3',
       counts=LONG_COUNTS, destinations=INSERT_LINKS, group=FLOW_GROUP)


def empty(opened, title, text, create=False, counts=None):
    return head(create=create) + tabs(opened, counts=counts) + card('<div class="empty-icon">' + icon('cards') + f'</div><h2>{title}</h2>', 'Пустая секция') + foot([])


add('empty-e4', 'Пустая эпоха', empty(4, 'Нет карточек', ''), 'Просмотр', 'Пустая секция доступна. Листания и действий с карточкой нет.')
add('empty-pool', 'Пустые новые', empty(0, 'Нет новых карточек', '', True, [0,40,23,9,0,61]), 'Просмотр')
add('empty-all', 'Нет карточек для обучения', empty(0, 'Нет новых карточек', '', True, [0,0,0,0,0,61]), 'Просмотр')


def editor(new=True, back=False):
    base = 'editor-new' if new else 'editor-edit'
    left = btn('Отменить' if new else 'Удалить', 'dlg-discard' if new else 'dlg-delete-edit', 'quiet', None if new else 'trash', attrs='aria-label="' + ('Отменить создание' if new else 'Удалить карточку') + '"')
    header = head(title='Новая карточка' if new else 'Редактирование', left=left, right=btn('Сохранить', 'pool', 'primary', 'check'))
    sides = '<nav class="side-tabs" aria-label="Сторона карточки">'
    for is_back, title in [(False, 'Вопрос'), (True, 'Ответ')]:
        selected = is_back == back
        sides += f'<a class="side-tab {"selected" if selected else ""}" href="{base}{"-back" if is_back else ""}.html"' + (' aria-current="page"' if selected else '') + f'>{title}' + '</a>'
    sides += '</nav>'
    content = '' if back and new else handwriting('дерево' if back else 'der Baum')
    toolbar = '<div class="tools" aria-label="Инструменты рисования">'
    # Static controls intentionally do not pretend to draw or change application state.
    toolbar += '<button class="btn icon-only selected" aria-pressed="true" aria-label="Перо" title="Перо">'+icon('pen')+'</button>'
    toolbar += '<button class="btn icon-only quiet" aria-pressed="false" aria-label="Ластик" title="Ластик">'+icon('eraser')+'</button>'
    toolbar += '<div class="thickness" role="group" aria-label="Толщина пера">'
    for i, (name, size) in enumerate([('Тонкая', 2), ('Средняя', 5), ('Толстая', 9)]):
        toolbar += f'<button class="btn {"selected" if i == 1 else ""}" aria-label="{name} линия" aria-pressed="{str(i == 1).lower()}" title="Макет толщины"><i class="line-sample" style="height:{size}px"></i></button>'
    toolbar += '</div><button class="btn icon-only quiet undo" aria-label="Отменить штрих" title="Отменить штрих">'+icon('undo')+'</button></div>'
    return header + sides + card(content, 'Холст. Рисование только пером') + toolbar


for new in [True, False]:
    for back in [False, True]:
        key = ('editor-new' if new else 'editor-edit') + ('-back' if back else '')
        add(key, ('Создание' if new else 'Правка') + (' · ответ' if back else ' · вопрос'), editor(new, back), 'Редактор', 'Тот же лист 928 × 963. Толщина выделена в отдельную группу; пустая сторона допустима.')


def modal(background, title, text, actions):
    # Background is inert even for keyboard navigation; there is no clickable scrim.
    return '<div style="display:contents" inert>' + background + '</div><div class="overlay"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">' + title + '</h2>' + ('<p>' + text + '</p>' if text else '') + foot(actions, 'one' if len(actions) == 1 else 'two') + '</section></div>'


def popup(background, title, items, close, top=108):
    links = ''.join(f'<a href="{dest}.html" class="{"danger" if glyph == "trash" else ""}">{icon(glyph)}<span>{label}</span></a>' for label, dest, glyph in items)
    return '<div style="display:contents" inert>' + background + f'</div><div class="overlay"><a class="scrim" href="{close}.html" aria-label="Закрыть меню"></a><nav class="popup" aria-label="{title}" style="top:{top}px"><h2>{title}</h2>{links}</nav></div>'


for key, section in [('learn-front', 'learn'), ('learn-e2', 'learn-e2'), ('browse', 'browse'), ('browse-e2', 'browse-e2'), ('browse-e1', 'browse-e1'), ('pool', 'pool'), ('learned', 'learned'), ('learn-insert', 'insert'), ('learn-resume', 'resume'), ('browse-e4-insert', 'browse-e4-insert')]:
    background = SCREENS[key]['body']
    items = []
    if section == 'pool':
        items.append(('Редактировать', 'editor-edit', 'pen'))
    elif section != 'learned':
        items.append(('Вернуть в новые', 'dlg-return-' + section, 'undo'))
        add('dlg-return-' + section, 'Вернуть в новые · ' + section,
            modal(background, 'Вернуть в новые?', 'Успехи обнулятся.', [btn('Оставить', key, 'primary'), btn('Вернуть', key)]), 'Диалоги')
    items.append(('Удалить карточку', 'dlg-delete-' + section, 'trash'))
    add('menu-' + section, 'Меню · ' + LABELS[0 if section == 'pool' else 5 if section == 'learned' else 4 if section in ['resume', 'browse-e4-insert'] else 2 if section in ['learn-e2','browse-e2'] else 3 if section == 'browse' else 1],
        popup(background, 'Действия с карточкой', items, key), 'Меню')
    add('dlg-delete-' + section, 'Удалить карточку · ' + section,
        modal(background, 'Удалить карточку?', 'Вопрос и ответ будут удалены безвозвратно.', [btn('Оставить', key, 'primary'), btn('Удалить', key)]), 'Диалоги')

add('dlg-discard', 'Отмена создания', modal(editor(), 'Удалить черновик?', 'Рисунки на обеих сторонах не сохранятся.', [btn('Продолжить', 'editor-new', 'primary'), btn('Удалить черновик', 'pool')]), 'Диалоги')
add('dlg-delete-edit', 'Удаление из редактора', modal(editor(False), 'Удалить карточку?', 'Вопрос и ответ будут удалены безвозвратно.', [btn('Продолжить', 'editor-edit', 'primary'), btn('Удалить', 'pool')]), 'Диалоги')
add('dlg-editor-back', 'Системная кнопка «Назад»', modal(editor(), 'Сохранить и выйти?', '', [btn('Продолжить', 'editor-new', 'primary'), btn('Сохранить и выйти', 'pool')]), 'Диалоги', 'Предложение поведения системной кнопки. Ничего не удаляется молча.')
add('dlg-need-card', 'Нечего повторять', modal(empty(1, '', '', counts=[0,0,0,0,0,61]), 'Пока нечего повторять', 'Нет карточек для обучения.', [btn('Открыть новые', 'empty-all', 'primary')]), 'Диалоги')

# Distinct from an empty deck: II has cards, but its minimum interval has
# not elapsed, while I and NEW are empty. No date or countdown is shown.
WAITING_COUNTS = [0, 0, 23, 9, 0, 61]
WAITING_LINKS = {0: 'pool-waiting', 1: 'empty-e1-waiting', 2: 'dlg-today-done'}
waiting_background = (head() + tabs(2, 2, WAITING_COUNTS, WAITING_LINKS)
                      + card('', 'Ожидающая эпоха II') + foot([]))
add('dlg-today-done', 'На сегодня всё',
    modal(waiting_background, 'На сегодня всё', '',
          [btn('Открыть новые', 'pool-waiting', 'primary', 'right')]),
    FLOW_GROUP, 'II ещё не созрела, I и новые пусты. Закрытие открывает новые; ▶ остаётся у ожидающей II. Дат и таймеров нет.')
add('pool-waiting', 'Новые · ожидание II',
    head(create=True) + tabs(0, 2, WAITING_COUNTS, WAITING_LINKS)
    + card('<div class="empty-icon">' + icon('cards') + '</div><h2>Нет новых карточек</h2>', 'Пустой пул новых')
    + foot([]), FLOW_GROUP,
    'Можно создать карточку. Нажатие II повторяет проверку; пока интервал не прошёл, снова открывается «На сегодня всё».')
add('empty-e1-waiting', 'Пустая I · ожидание II',
    head() + tabs(1, 2, WAITING_COUNTS, WAITING_LINKS)
    + card('<div class="empty-icon">' + icon('cards') + '</div><h2>Нет карточек</h2>', 'Пустая секция I')
    + foot([]), FLOW_GROUP,
    'I пуста; ожидающей остаётся II. Действий с карточкой и листания нет.')


def deck_row(name, info, number, current=False):
    # Secondary decks demonstrate list density, not a separate study session.
    destination = 'learn-front' if current else 'decks'
    menu = 'deck-menu' if current else 'deck-menu-' + str(number)
    return '<div class="item ' + ('current' if current else '') + '"><span class="number">' + (icon('check') if current else f'{number:02}') + '</span><a class="fill" href="' + destination + '.html" title="' + ('Открыть колоду' if current else 'Пример строки списка; отдельная колода в макете не реализована') + '"><h2>' + name + '</h2><p>' + info + '</p></a>' + btn('', menu, 'quiet square', 'more', attrs='aria-label="Меню колоды: ' + name + '"') + '</div>'


deck_header = head(title='Колоды', left=btn('', 'learn-front', 'quiet square', 'left', attrs='aria-label="К карточкам"'))
deck_actions = foot([btn('Новая колода', 'deck-new', 'primary', 'plus'), btn('Импорт из файла', 'decks', 'quiet', 'file', attrs='title="В приложении откроется системный выбор файла. Здесь — макет."')], 'two')
deck_list = '<div class="list">' + deck_row('Немецкий A2', '245 карточек · ' + icon('active') + ' I', 1, True) + deck_row('Английские времена', '86 карточек · ' + icon('active') + ' II', 2) + deck_row('Формулы', '18 карточек · выучены', 3) + '</div>'
decks = deck_header + deck_list + deck_actions
add('decks', 'Колоды', decks, 'Колоды', 'Названия важнее служебных действий. Текущая колода отмечена галочкой.')
add('decks-empty', 'Первый запуск', head(title='Колоды') + card('<div class="empty-icon">'+icon('cards')+'</div><h2>Нет колод</h2>') + deck_actions, 'Колоды')
pages = '<nav class="page-nav" aria-label="Страницы колод">' + btn('', None, 'off square', 'left', attrs='aria-label="Предыдущая страница"') + '<span>Страница 1 из 2</span>' + btn('', 'decks-page2', 'quiet square', 'right', attrs='aria-label="Следующая страница"') + '</nav>'
add('decks-pages', 'Колоды · несколько страниц', deck_header + deck_list + pages + deck_actions, 'Колоды', 'Отдельный пример пагинации; на одной странице стрелки не показываются.')
page2 = '<div class="list">' + deck_row('История искусства', '32 карточки · ' + icon('active') + ' I', 4) + '</div>'
add('decks-page2', 'Колоды · страница 2', deck_header + page2 + '<nav class="page-nav" aria-label="Страницы колод">' + btn('', 'decks-pages', 'quiet square', 'left', attrs='aria-label="Предыдущая страница"') + '<span>Страница 2 из 2</span>' + btn('', None, 'off square', 'right', attrs='aria-label="Следующая страница"') + '</nav>' + deck_actions, 'Колоды')
add('deck-menu', 'Меню колоды', popup(decks, 'Немецкий A2', [('Переименовать', 'deck-rename', 'pen'), ('Экспорт в файл', 'decks', 'export'), ('Удалить колоду', 'dlg-delete-deck', 'trash')], 'decks', 206), 'Колоды')
for number, name, count in [(2, 'Английские времена', 86), (3, 'Формулы', 18), (4, 'История искусства', 32)]:
    context_key = 'decks-page2' if number == 4 else 'decks'
    context = SCREENS[context_key]['body']
    add('deck-menu-' + str(number), 'Меню · ' + name,
        popup(context, name, [('Переименовать', 'deck-rename-' + str(number), 'pen'), ('Экспорт в файл', context_key, 'export'), ('Удалить колоду', 'dlg-delete-deck-' + str(number), 'trash')], context_key, 206 if number == 4 else 206 + (number-1)*148), 'Меню')
    add('deck-rename-' + str(number), 'Переименовать · ' + name,
        head(title='Переименовать', left=btn('Отмена', context_key, 'quiet'), right=btn('Сохранить', context_key, 'primary')) + '<label class="field">Название колоды<input class="input" value="' + name + '"></label><div class="keyboard">Область системной клавиатуры</div>', 'Диалоги')
    add('dlg-delete-deck-' + str(number), 'Удалить · ' + name,
        modal(context, 'Удалить «' + name + '»?', f'Карточки ({count}) и прогресс будут удалены безвозвратно.', [btn('Оставить', context_key, 'primary'), btn('Удалить', context_key)]), 'Диалоги')
for rename in [False, True]:
    title = 'Переименовать' if rename else 'Новая колода'
    body = head(title=title, left=btn('Отмена', 'decks', 'quiet'), right=btn('Сохранить' if rename else 'Создать', 'decks' if rename else None, 'primary' if rename else 'off'))
    body += '<label class="field">Название колоды<input class="input" value="' + ('Немецкий A2' if rename else '') + '" placeholder="Немецкий A2"></label><div class="keyboard">Область системной клавиатуры</div>'
    add('deck-rename' if rename else 'deck-new', title, body, 'Колоды', 'Статичный пример поля; ввод не сохраняет колоду.')
add('dlg-delete-deck', 'Удалить колоду', modal(decks, 'Удалить «Немецкий A2»?', '245 карточек и прогресс будут удалены безвозвратно.', [btn('Оставить', 'decks', 'primary'), btn('Удалить', 'decks')]), 'Колоды')
add('dlg-import-error', 'Ошибка импорта', modal(decks, 'Файл не удалось открыть', 'Неверный формат или файл повреждён.', [btn('Закрыть', 'decks', 'primary')]), 'Колоды')

tokens = head(title='V5 · Ясный лист') + '<div class="spec">'
for title, text in [
    ('Каркас', 'Шапка 64 · вкладки 72 · лист 963 · действия 112.<br>Поля 32, интервалы 16. Фрейм 992 × 1323.'),
    ('Лист', '928 × 963 во всех режимах. Только рукопись.<br>Четыре угла обозначают границу без тяжёлой рамки.'),
    ('Состояния', 'Подчёркивание — открытая секция.<br>▶ — активная эпоха. Секции I, II, III, IV.'),
    ('Типографика', 'Название 28 · подпись 18 · счётчики 26.<br>Кнопки 24–26 · пояснения от 18.'),
    ('Действия', 'Переворот всегда в одном месте.<br>Оценки равного веса, только на ответе.'),
    ('Оформление', 'Чёрный и белый, без теней и анимаций.<br>Тонкие разделители; рамка только по необходимости.'),
    ('Устройство', 'Проверить ≥64 dp для касаний и ≥18 sp для текста.<br>Размеры макета — условные, не физические dp.'),
]:
    tokens += f'<strong>{title}</strong><span>{text}</span>'
add('tokens', 'Правила оформления', tokens + '</div>', 'Система')


def document(title, body, dc=False, name=''):
    style = f'<style>{CSS}</style>' if dc else '<link rel="stylesheet" href="styles.css">'
    return '<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=992"><title>E · ' + title + '</title></head><body>' + ('<x-dc>' if dc else '') + style + f'<main class="screen" data-screen="{name}">' + body + '</main>' + ('</x-dc>' if dc else '') + '</body></html>'


boards = {}
for i, (key, screen) in enumerate(SCREENS.items()):
    (ROOT / f'{key}.html').write_text(document(screen['title'], screen['body'], name=key))
    fn = f'E-{key}.dc.html'
    (ROOT / fn).write_text(document(screen['title'], screen['body'], True, key))
    boards[fn] = dict(x=(i % 4)*1072, y=(i // 4)*1483, w=992, h=1323, title='E · ' + screen['title'])
(ROOT / 'canvas.json').write_text(json.dumps(dict(v=3, title='Карточки · Вариант E / v5', launch={'view':'canvas'}, pages=[], boards=boards, order=list(boards), notes={}, designSystems=[]), ensure_ascii=False, indent=2))

GALLERY_CSS = '''*{box-sizing:border-box}body{margin:0;padding:48px;background:#fff;color:#000;font:18px/1.5 Arial,sans-serif}a{color:inherit}h1,h2,h3,p{margin:0}header{max-width:1200px;border-bottom:2px solid;padding-bottom:32px;margin-bottom:32px}header small{font-size:15px;letter-spacing:2px}h1{font-size:64px;line-height:1.1;letter-spacing:-3px;margin:16px 0}header p{max-width:740px;font-size:21px;margin:20px 0}nav{display:flex;flex-wrap:wrap;gap:24px}nav a{min-height:44px;display:flex;align-items:center}h2{font-size:28px;margin:48px 0 24px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:40px 32px;max-width:1600px}article{max-width:340px}article>a{text-decoration:none}article h3{font-size:20px;margin:16px 0 8px}article p{font-size:16px}.preview{width:299.6px;height:398.9px;border:1px solid #000;overflow:hidden;background:#fff;pointer-events:none}iframe{border:0;width:992px;height:1323px;transform:scale(.3);transform-origin:top left}.pair{display:flex;flex-wrap:wrap;gap:40px;margin-top:24px}.pair .preview{width:398.8px;height:531.2px}.pair iframe{transform:scale(.4)}.pair h3{font-size:18px;margin:12px 0}.pair article{max-width:430px}.pair p{max-width:398px}a:focus-visible{outline:3px solid;outline-offset:4px}@media(max-width:600px){body{padding:24px}h1{font-size:44px;letter-spacing:-1px}.pair .preview{width:299.6px;height:398.9px}.pair iframe{transform:scale(.3)}}'''


def gallery_doc(title, body):
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{title}</title><style>{GALLERY_CSS}</style></head><body>{body}</body></html>'


groups = list(dict.fromkeys(s['group'] for s in SCREENS.values()))
body = '<header><small>КАРТОЧКИ ДЛЯ BOOX · ВАРИАНТ E / V5</small><h1>Ясный лист.</h1><p>Рукописные карточки, римские цифры и компактные значки действий.</p><nav><a href="learn-front.html">Открыть основной экран ↗</a><a href="README.md">Разбор решений</a></nav></header><nav>'
body += ''.join(f'<a href="#g{i}">{g}</a>' for i, g in enumerate(groups)) + '</nav>'
for i, group in enumerate(groups):
    body += f'<section id="g{i}"><h2>{group}</h2><div class="grid">'
    for key, screen in SCREENS.items():
        if screen['group'] != group:
            continue
        body += f'<article><a href="{key}.html"><div class="preview"><iframe src="{key}.html" title="{escape(screen["title"])}" loading="lazy" tabindex="-1"></iframe></div><h3>{screen["title"]}</h3></a><p>{screen["note"]}</p></article>'
    body += '</div></section>'
(ROOT / 'index.html').write_text(gallery_doc('V5 · Ясный лист · галерея', body))

print(f'Generated {len(SCREENS)} screens, {len(boards)} canvas boards and gallery in {ROOT}')
