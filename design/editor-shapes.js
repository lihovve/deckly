/* Interactive design demo only. No application data or drawing is saved. */
function clampShapeValue(value, minimum, maximum) {
    return Math.max(minimum, Math.min(maximum, value));
}

function isLinearShape(kind) {
    return ['line', 'arrow', 'double-arrow'].includes(kind);
}

function findShapePoint(box, x, y) {
    const angle = (box.angle || 0) * Math.PI / 180;
    return {x: box.x + box.w / 2 + x * Math.cos(angle) - y * Math.sin(angle),
            y: box.y + box.h / 2 + x * Math.sin(angle) + y * Math.cos(angle)};
}

function canFitShape(box, width, height) {
    const points = [-1, 1].flatMap(x => [-1, 1].map(y => findShapePoint(box, x * box.w / 2, y * box.h / 2)));
    if (!isLinearShape(box.kind)) points.push(findShapePoint(box, 0, -box.h / 2 - 48));
    return points.every(point => point.x >= 32 && point.x <= width - 32 && point.y >= 32 && point.y <= height - 32);
}

function limitShapeTransform(box, candidate, width, height) {
    if (canFitShape(candidate, width, height)) return candidate;
    let low = 0, high = 1;
    let result = box;
    for (let i = 0; i < 20; i++) {
        const ratio = (low + high) / 2;
        const trial = {...box};
        for (const key of ['x', 'y', 'w', 'h', 'angle']) {
            trial[key] = (box[key] || 0) + ((candidate[key] || 0) - (box[key] || 0)) * ratio;
        }
        if (canFitShape(trial, width, height)) { low = ratio; result = trial; }
        else high = ratio;
    }
    return result;
}

function transformShape(box, dx, dy, corner, width, height) {
    if (!corner) return limitShapeTransform(box, {...box, x: box.x + dx, y: box.y + dy}, width, height);
    const angle = (box.angle || 0) * Math.PI / 180;
    const localX = dx * Math.cos(angle) + dy * Math.sin(angle);
    const localY = -dx * Math.sin(angle) + dy * Math.cos(angle);
    let left = -box.w / 2, right = box.w / 2;
    let top = -box.h / 2, bottom = box.h / 2;
    if (corner.includes('w')) left = Math.min(left + localX, right - 80);
    if (corner.includes('e')) right = Math.max(right + localX, left + 80);
    if (corner.includes('n')) top = Math.min(top + localY, bottom - 80);
    if (corner.includes('s')) bottom = Math.max(bottom + localY, top + 80);
    const center = findShapePoint(box, (left + right) / 2, (top + bottom) / 2);
    const candidate = {...box, w: right - left, h: bottom - top,
        x: center.x - (right - left) / 2, y: center.y - (bottom - top) / 2};
    return limitShapeTransform(box, candidate, width, height);
}

function rotateShape(box, start, point, width, height) {
    const cx = box.x + box.w / 2, cy = box.y + box.h / 2;
    const delta = Math.atan2(point.y - cy, point.x - cx) - Math.atan2(start.y - cy, start.x - cx);
    const turn = Math.atan2(Math.sin(delta), Math.cos(delta)) * 180 / Math.PI;
    return limitShapeTransform(box, {...box, angle: (box.angle || 0) + turn}, width, height);
}

function resizeShapeEndpoint(box, point, endpoint, width, height) {
    const moving = findShapePoint(box, (endpoint === 'start' ? -0.38 : 0.38) * box.w, 0);
    const fixed = findShapePoint(box, (endpoint === 'start' ? 0.38 : -0.38) * box.w, 0);
    function buildEndpointBox(target) {
        let dx = target.x - fixed.x, dy = target.y - fixed.y;
        let length = Math.hypot(dx, dy);
        if (length < 64) {
            const angle = length > 0 ? Math.atan2(dy, dx) : Math.atan2(moving.y - fixed.y, moving.x - fixed.x);
            dx = Math.cos(angle) * 64; dy = Math.sin(angle) * 64; length = 64;
        }
        const w = length / 0.76;
        return {...box, x: fixed.x + dx / 2 - w / 2, y: fixed.y + dy / 2 - 40, w, h: 80,
            angle: Math.atan2(endpoint === 'start' ? -dy : dy, endpoint === 'start' ? -dx : dx) * 180 / Math.PI};
    }
    const candidate = buildEndpointBox(point);
    if (canFitShape(candidate, width, height)) return candidate;
    let low = 0, high = 1, result = box;
    for (let i = 0; i < 20; i++) {
        const ratio = (low + high) / 2;
        const trial = buildEndpointBox({x: moving.x + (point.x - moving.x) * ratio, y: moving.y + (point.y - moving.y) * ratio});
        if (canFitShape(trial, width, height)) { low = ratio; result = trial; } else high = ratio;
    }
    return result;
}

function buildLinearShape(kind, box, lineWidth) {
    const start = box.w * 0.12, end = box.w * 0.88, y = box.h / 2;
    const head = Math.min(24, (end - start) / 3);
    let path = `M${start} ${y}H${end}`;
    if (kind !== 'line') path += `M${end - head} ${y - 16}L${end} ${y} ${end - head} ${y + 16}`;
    if (kind === 'double-arrow') path += `M${start + head} ${y - 16}L${start} ${y} ${start + head} ${y + 16}`;
    return `<svg class="shape-graphic" viewBox="0 0 ${box.w} ${box.h}" preserveAspectRatio="none" fill="none" stroke="currentColor" stroke-width="${lineWidth}" aria-hidden="true"><path d="${path}"/></svg>`;
}

(function initializeShapeEditor() {
    const stage = document.querySelector('[data-shape-editor]');
    if (!stage) return;
    const screen = stage.closest('main');
    const frame = screen.querySelector('[data-editor-frame]');
    const palette = screen.querySelector('[data-shape-palette]');
    const normalTools = screen.querySelector('[data-normal-tools]');
    const shapeTools = screen.querySelector('[data-shape-tools]');
    const slider = screen.querySelector('#shape-width');
    const apply = screen.querySelector('[data-apply-shape]');
    const choices = [...palette.querySelectorAll('[data-choose-shape]')];
    const templates = Object.fromEntries(choices.map(choice => [choice.dataset.chooseShape, {
        label: choice.getAttribute('aria-label'), svg: choice.querySelector('svg').outerHTML
    }]));
    const [x, y, w, h] = stage.dataset.bounds.split(',').map(Number);
    const initial = stage.dataset.state;
    let objects = ['selected', 'moved', 'resized', 'placed', 'arrow', 'rotated'].includes(initial)
        ? [{kind: stage.dataset.kind, angle: Number(stage.dataset.angle), width: 3, x, y, w, h}] : [];
    let selected = objects.length && initial !== 'placed' ? 0 : -1;
    let savedObject = null;
    let pending = initial === 'ready';
    let kind = stage.dataset.kind;
    let lineWidth = 3;
    let gesture = null;
    let returnFocus = null;
    const corners = {nw: 'top left', ne: 'top right', sw: 'bottom left', se: 'bottom right'};

    function renderShapeEditor() {
        stage.innerHTML = objects.map((item, index) => {
            const node = document.createElement('div');
            node.className = 'shape-object' + (isLinearShape(item.kind) ? ' is-linear' : '') + (index === selected ? ' is-selected' : '');
            node.dataset.object = index;
            node.style.cssText = `left:${item.x}px;top:${item.y}px;width:${item.w}px;height:${item.h}px;transform:rotate(${item.angle || 0}deg)`;
            node.setAttribute('role', 'group');
            node.setAttribute('aria-label', templates[item.kind].label);
            node.innerHTML = isLinearShape(item.kind) ? buildLinearShape(item.kind, item, item.width) : templates[item.kind].svg;
            node.querySelector('svg').setAttribute('stroke-width', item.width);
            // A transparent, wider stroke is the only selection target.
            // The hollow interior and selection rectangle let pen input pass through.
            const hit = node.querySelector('svg').cloneNode(true);
            hit.classList.add('shape-hit');
            hit.setAttribute('stroke-width', item.width + 16);
            node.append(hit);
            if (index === selected) {
                for (const [corner, label] of Object.entries(isLinearShape(item.kind) ? {start: 'start point', end: 'end point'} : corners)) {
                    const handle = document.createElement('button');
                    handle.type = 'button';
                    handle.className = 'shape-handle ' + corner;
                    if (isLinearShape(item.kind)) {
                        handle.dataset.endpoint = corner;
                        handle.setAttribute('aria-label', 'Move ' + label);
                    } else {
                        handle.dataset.resize = corner;
                        handle.setAttribute('aria-label', 'Resize: ' + label + ' corner');
                    }
                    node.append(handle);
                }
                if (!isLinearShape(item.kind)) {
                    const rotate = document.createElement('button');
                    rotate.type = 'button';
                    rotate.className = 'shape-rotate';
                    rotate.dataset.rotate = '';
                    rotate.setAttribute('aria-label', 'Rotate shape');
                    rotate.innerHTML = '<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5v6h6M5 11c4-9 15-5 15 2 0 4-3 7-7 7"/></svg>';
                    node.append(rotate);
                }
            }
            return node.outerHTML;
        }).join('');
        stage.classList.toggle('is-ready', pending);
        normalTools.hidden = pending || selected >= 0;
        shapeTools.hidden = !normalTools.hidden;
        apply.disabled = selected < 0;
        const preview = shapeTools.querySelector('[data-open-shapes]');
        preview.innerHTML = templates[kind].svg;
        preview.querySelector('svg').setAttribute('stroke-width', lineWidth);
        choices.forEach(choice => choice.setAttribute('aria-current', String(choice.dataset.chooseShape === kind)));
    }

    function updateShapeWidth() {
        lineWidth = Number(slider.value);
        palette.querySelector('[data-width-value]').textContent = lineWidth;
        palette.querySelector('[data-width-preview]').setAttribute('stroke-width', lineWidth);
        choices.forEach(choice => choice.querySelector('svg').setAttribute('stroke-width', lineWidth));
        if (selected >= 0) objects[selected].width = lineWidth;
        renderShapeEditor();
    }

    function closeShapePalette() {
        palette.hidden = true;
        frame.inert = false;
        if (returnFocus && !returnFocus.closest('[hidden]')) returnFocus.focus();
    }

    screen.querySelectorAll('[data-open-shapes]').forEach(button => button.addEventListener('click', event => {
        event.preventDefault();
        returnFocus = button;
        slider.value = lineWidth;
        updateShapeWidth();
        palette.hidden = false;
        frame.inert = true;
        choices.find(choice => choice.dataset.chooseShape === kind).focus();
    }));
    screen.querySelectorAll('[data-close-shapes]').forEach(button => button.addEventListener('click', event => {
        event.preventDefault();
        closeShapePalette();
    }));
    choices.forEach(choice => choice.addEventListener('click', event => {
        event.preventDefault();
        kind = choice.dataset.chooseShape;
        if (selected >= 0) {
            const previous = objects[selected];
            objects[selected] = {...previous, kind, width: lineWidth, h: isLinearShape(kind) ? 80 : previous.h};
        }
        else pending = true;
        renderShapeEditor();
        closeShapePalette();
    }));
    slider.addEventListener('input', updateShapeWidth);
    apply.addEventListener('click', () => {
        selected = -1;
        savedObject = null;
        pending = false;
        renderShapeEditor();
    });
    screen.querySelector('[data-cancel-shape]').addEventListener('click', event => {
        event.preventDefault();
        if (selected >= 0) {
            if (savedObject) objects[selected] = savedObject;
            else objects.splice(selected, 1);
        }
        selected = -1;
        savedObject = null;
        pending = false;
        gesture = null;
        renderShapeEditor();
    });

    function findSheetPoint(event) {
        const rect = stage.getBoundingClientRect();
        return {x: (event.clientX - rect.left) * stage.clientWidth / rect.width,
                y: (event.clientY - rect.top) * stage.clientHeight / rect.height};
    }

    stage.addEventListener('pointerdown', event => {
        if (event.button !== 0 || !event.isPrimary || frame.inert) return;
        const point = findSheetPoint(event);
        const target = event.target.closest('[data-object]');
        if (pending) {
            objects.push({kind, angle: 0, width: lineWidth, x: clampShapeValue(point.x - 110, 32, stage.clientWidth - 252),
                y: clampShapeValue(point.y - (isLinearShape(kind) ? 40 : 110), 80, stage.clientHeight - 252), w: 220, h: isLinearShape(kind) ? 80 : 220});
            selected = objects.length - 1;
            savedObject = null;
            pending = false;
        } else if (target) {
            const index = Number(target.dataset.object);
            // Finish any previous figure before selecting another.
            if (selected !== index) savedObject = {...objects[index]};
            selected = index;
            kind = objects[index].kind;
            lineWidth = objects[index].width;
        } else return;
        gesture = {point, box: {...objects[selected]}, corner: event.target.closest('[data-resize]')?.dataset.resize,
                   endpoint: event.target.closest('[data-endpoint]')?.dataset.endpoint,
                   rotate: Boolean(event.target.closest('[data-rotate]')), pointerId: event.pointerId};
        event.preventDefault();
        stage.setPointerCapture(event.pointerId);
        renderShapeEditor();
    });
    stage.addEventListener('pointermove', event => {
        if (!gesture || event.pointerId !== gesture.pointerId) return;
        const point = findSheetPoint(event);
        if (gesture.endpoint) objects[selected] = resizeShapeEndpoint(gesture.box, point, gesture.endpoint, stage.clientWidth, stage.clientHeight);
        else if (gesture.rotate) objects[selected] = rotateShape(gesture.box, gesture.point, point, stage.clientWidth, stage.clientHeight);
        else objects[selected] = transformShape(gesture.box, point.x - gesture.point.x, point.y - gesture.point.y,
            gesture.corner, stage.clientWidth, stage.clientHeight);
        renderShapeEditor();
    });
    stage.addEventListener('pointerup', event => {
        if (!gesture || event.pointerId !== gesture.pointerId) return;
        gesture = null;
        if (stage.hasPointerCapture(event.pointerId)) stage.releasePointerCapture(event.pointerId);
    });
    stage.addEventListener('pointercancel', () => {
        if (gesture) objects[selected] = gesture.box;
        gesture = null;
        renderShapeEditor();
    });
    screen.addEventListener('keydown', event => {
        if (palette.hidden) return;
        if (event.key === 'Escape') { event.preventDefault(); closeShapePalette(); }
        if (event.key === 'Tab') {
            const controls = [...palette.querySelectorAll('a, input')];
            const first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    renderShapeEditor();
    if (!palette.hidden && !palette.closest('[inert]')) choices.find(choice => choice.dataset.chooseShape === kind).focus();
})();
