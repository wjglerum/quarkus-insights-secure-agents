(function () {
    'use strict';

    // Everything from the server (model output, tool results, guardrail labels) is untrusted:
    // it only ever reaches the page through textContent, never innerHTML.

    const transcript = document.getElementById('transcript');
    const composer = document.getElementById('composer');
    const promptInput = document.getElementById('prompt');
    const guardsEl = document.getElementById('guards');
    const userName = document.getElementById('who').textContent;

    const STEP_UP_TOOLS = new Set(['view_session_attendees', 'cancel_my_session']);
    const DESTRUCTIVE_TOOLS = new Set(['cancel_my_session']);
    const ATTENDEE_TOOLS = new Set(['bookmark_session', 'unbookmark_session', 'rate_session']);
    // Not every card is an MCP call: send_email runs in the chat app, and the input and output
    // guardrails block a whole turn. Label each card for what it is.
    const LOCAL_TOOLS = new Set(['send_email']);
    const TURN_GUARDS = new Set(['input guardrail', 'output guardrail']);

    function cardLabels(tool) {
        if (TURN_GUARDS.has(tool)) return ['guardrail.check', 'guardrail.verdict'];
        if (LOCAL_TOOLS.has(tool)) return ['local.tool_call', 'local.tool_result'];
        return ['mcp.tool_call', 'mcp.tool_result'];
    }

    function el(tag, className, text) {
        const node = document.createElement(tag);
        node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    // --- Prompts -----------------------------------------------------------

    document.getElementById('quick-prompts').addEventListener('click', (ev) => {
        const btn = ev.target.closest('.quick-prompt');
        if (!btn) return;
        promptInput.value = btn.dataset.label;
        composer.requestSubmit();
    });

    composer.addEventListener('submit', (ev) => {
        ev.preventDefault();
        const text = promptInput.value.trim();
        if (!text) return;
        promptInput.value = '';
        sendPrompt(text);
    });

    async function sendPrompt(text) {
        const hero = transcript.querySelector('.hero-empty');
        if (hero) hero.remove();
        addUserBubble(text);
        try {
            const res = await fetch('/api/chat/send', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: text })
            });
            if (!res.ok) {
                addAssistantBubble('Backend returned HTTP ' + res.status + '.');
                return;
            }
            const turn = await res.json();
            const results = Array.isArray(turn.results) ? turn.results : [];
            if (!results.length) {
                addAssistantBubble(turn.note);
                return;
            }
            // One card per tool call, in order, with the answer on the last one.
            results.forEach((result, i) => {
                const last = i === results.length - 1;
                addAssistantBubble(last ? turn.note : '', result, turn.prompt);
            });
        } catch (e) {
            addAssistantBubble('Network error: ' + e.message);
        }
    }

    // --- Transcript --------------------------------------------------------

    function addUserBubble(text) {
        const node = document.getElementById('user-bubble').content.cloneNode(true);
        node.querySelector('[data-bind="who"]').textContent = userName;
        node.querySelector('[data-bind="time"]').textContent = nowHHMMSS();
        node.querySelector('[data-bind="text"]').textContent = text;
        append(node);
    }

    function addAssistantBubble(note, result, prompt) {
        const node = document.getElementById('assistant-bubble').content.cloneNode(true);
        const bind = (name) => node.querySelector('[data-bind="' + name + '"]');
        const card = node.querySelector('[data-bind-card]');
        const errorBox = node.querySelector('[data-bind-error]');
        const stepUpBox = node.querySelector('[data-bind-stepup]');

        bind('time').textContent = nowHHMMSS();
        bind('note').textContent = note || '';

        const hasTool = Boolean(result && result.tool);
        card.hidden = !hasTool;
        errorBox.hidden = !(hasTool && result.error);
        stepUpBox.hidden = !(hasTool && result.stepUpRequired);
        if (!hasTool) {
            append(node);
            return;
        }

        if (STEP_UP_TOOLS.has(result.tool)) card.classList.add('step-up');
        if (DESTRUCTIVE_TOOLS.has(result.tool)) card.classList.add('destructive');
        if (result.guard) card.classList.add('guarded');

        const [callType, resultType] = cardLabels(result.tool);
        bind('callType').textContent = callType;
        bind('resultType').textContent = resultType;
        bind('toolName').textContent = result.tool;
        bind('toolTier').textContent = result.guard ? 'guarded by ' + result.guard : tierOf(result.tool);
        bind('toolArgs').textContent = JSON.stringify(result.args || {}, null, 2);
        bind('toolResult').textContent = resultText(result);
        errorBox.textContent = result.error || '';

        if (result.stepUpRequired) {
            // Remember the prompt so it can be sent again once the popup upgrades the session.
            stepUpBox.querySelector('[data-stepup]').dataset.prompt = prompt || '';
        } else if (!result.error) {
            const sessions = collectSessions(result.result);
            if (sessions.length) card.before(buildSessionSummary(sessions));
        }
        append(node);
    }

    function resultText(result) {
        if (result.stepUpRequired) {
            return JSON.stringify({ error: 'insufficient_user_authentication' }, null, 2);
        }
        if (TURN_GUARDS.has(result.tool)) return 'blocked';
        if (result.result == null) return result.error ? '(no result)' : 'null';
        return JSON.stringify(result.result, null, 2);
    }

    function append(node) {
        transcript.appendChild(node);
        requestAnimationFrame(() => {
            transcript.scrollTop = transcript.scrollHeight;
        });
    }

    function tierOf(tool) {
        if (STEP_UP_TOOLS.has(tool)) return 'step-up';
        if (tool === 'my_session_feedback') return 'speaker';
        if (tool.startsWith('my_') || ATTENDEE_TOOLS.has(tool)) return 'attendee';
        return 'public';
    }

    function nowHHMMSS() {
        return new Date().toLocaleTimeString('en-GB', { hour12: false });
    }

    // --- Session summary ---------------------------------------------------

    // Sessions arrive embedded in several result shapes: bare SessionDto(s),
    // bookmarks ({session: ...}), and feedback ({session: ..., ratingCount: ...}).
    function collectSessions(result) {
        const items = Array.isArray(result) ? result : [result];
        const out = [];
        for (const it of items) {
            if (!it || typeof it !== 'object') continue;
            if (it.startsAt && it.title) {
                out.push({ s: it });
            } else if (it.session && it.session.startsAt) {
                let badge = null;
                if (typeof it.ratingCount === 'number') {
                    badge = it.ratingCount + ' rating' + (it.ratingCount === 1 ? '' : 's')
                        + (it.ratingCount ? ', avg ' + Number(it.averageStars).toFixed(1) + '★' : '');
                }
                out.push({ s: it.session, badge });
            }
        }
        return out;
    }

    function buildSessionSummary(entries) {
        const sorted = entries.slice().sort((a, b) =>
            (a.s.startsAt || '').localeCompare(b.s.startsAt || '')
            || (a.s.room || '').localeCompare(b.s.room || ''));
        const tracks = new Set(sorted.map(e => e.s.room).filter(Boolean));
        const wrap = el('div', 'session-summary');
        wrap.appendChild(el('div', 'session-summary-head',
            sorted.length + ' session' + (sorted.length === 1 ? '' : 's')
            + (tracks.size > 1 ? ' across ' + tracks.size + ' tracks' : '')));
        for (const e of sorted) wrap.appendChild(buildSessionRow(e));
        return wrap;
    }

    function buildSessionRow(e) {
        const s = e.s;
        const row = el('div', 'session-row' + (s.cancelled ? ' cancelled' : ''));

        const meta = el('div', 'session-meta');
        meta.appendChild(el('span', 'session-time', formatRange(s.startsAt, s.endsAt)));
        if (s.room) meta.appendChild(el('span', 'session-room', s.room));
        row.appendChild(meta);
        row.appendChild(el('div', 'session-title', s.title || '(untitled)'));

        const bits = [];
        if (s.speaker && s.speaker.name) bits.push(s.speaker.name);
        if (e.badge) bits.push(e.badge);
        if (s.cancelled) bits.push('cancelled' + (s.cancellationReason ? ': ' + s.cancellationReason : ''));
        if (bits.length) row.appendChild(el('div', 'session-sub', bits.join('  ·  ')));
        return row;
    }

    // Always render in the conference's local time so the presenter's laptop
    // timezone never misleads the audience.
    const VENUE_TZ = 'Europe/Amsterdam';
    const DAY_FMT = new Intl.DateTimeFormat('en-GB',
        { weekday: 'short', day: 'numeric', month: 'short', timeZone: VENUE_TZ });
    const TIME_FMT = new Intl.DateTimeFormat('en-GB',
        { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: VENUE_TZ });

    function formatRange(startIso, endIso) {
        if (!startIso) return '';
        const start = new Date(startIso);
        if (isNaN(start)) return startIso;
        const end = new Date(endIso);
        return DAY_FMT.format(start) + ' · ' + TIME_FMT.format(start)
            + (endIso && !isNaN(end) ? '–' + TIME_FMT.format(end) : '');
    }

    // --- Guardrail switches (demo) -----------------------------------------

    async function loadGuards() {
        try {
            const res = await fetch('/api/guardrails', { credentials: 'same-origin' });
            if (res.ok) renderGuards(await res.json());
        } catch (e) { /* the panel stays empty; the chat still works */ }
    }

    function renderGuards(list) {
        guardsEl.textContent = '';
        for (const g of list) {
            const box = document.createElement('input');
            box.type = 'checkbox';
            box.checked = g.enabled;
            box.dataset.guard = g.key;
            const text = el('span', 'guard-text');
            text.append(el('span', 'guard-name', g.label),
                el('span', 'guard-where', g.owasp + ' / ' + g.where));
            const label = document.createElement('label');
            label.append(box, text, el('span', 'guard-state', g.enabled ? 'on' : 'off'));
            const li = el('li', 'guard' + (g.enabled ? ' on' : ''));
            li.appendChild(label);
            guardsEl.appendChild(li);
        }
    }

    guardsEl.addEventListener('change', async (ev) => {
        const box = ev.target.closest('input[data-guard]');
        if (!box) return;
        try {
            const res = await fetch('/api/guardrails/' + encodeURIComponent(box.dataset.guard), {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: box.checked })
            });
            if (res.ok) {
                renderGuards(await res.json());
                return;
            }
        } catch (e) { /* fall through and show what the server has */ }
        loadGuards();
    });
    loadGuards();

    // --- MFA step-up -------------------------------------------------------
    // The chat is a single page, so it never navigates. The OIDC step-up runs in a popup:
    // once Keycloak completes the OTP, the callback rewrites the same-origin session cookie
    // to acr=urn:insights:mfa. The popup posts back, the sidebar identity is refreshed and
    // the prompt that needed MFA is sent again, with the transcript intact.
    let stepUp = null; // { win, btn, prompt, poll }

    transcript.addEventListener('click', (ev) => {
        const btn = ev.target.closest('[data-stepup]');
        if (btn) openStepUp(btn);
    });

    function openStepUp(btn) {
        if (stepUp && !stepUp.win.closed) {
            stepUp.win.focus();
            return;
        }
        if (stepUp) resetStepUp();
        const w = 480, h = 720;
        const left = window.screenX + Math.max(0, (window.outerWidth - w) / 2);
        const top = window.screenY + Math.max(0, (window.outerHeight - h) / 2);
        const win = window.open('/step-up', 'insights-step-up',
            'width=' + w + ',height=' + h + ',left=' + left + ',top=' + top);
        if (!win) {
            setStepUpStatus(btn.closest('.tool-stepup'),
                'Popup blocked. Allow popups for this site, then try again.');
            return;
        }
        btn.disabled = true;
        btn.textContent = 'Waiting for MFA…';
        // If the user closes the popup without finishing, restore the button.
        const poll = window.setInterval(() => {
            if (win.closed) resetStepUp();
        }, 500);
        stepUp = { win, btn, prompt: btn.dataset.prompt, poll };
    }

    window.addEventListener('message', (ev) => {
        if (ev.origin !== window.location.origin) return;
        if (ev.data && ev.data.type === 'step-up-complete') completeStepUp();
    });

    async function completeStepUp() {
        if (!stepUp) return;
        const { win, btn, prompt, poll } = stepUp;
        window.clearInterval(poll);
        stepUp = null;
        if (!win.closed) win.close();

        await refreshIdentity();

        const box = btn.closest('.tool-stepup');
        box.classList.add('done');
        btn.remove();
        setStepUpStatus(box, 'MFA complete (acr=urn:insights:mfa).'
            + (prompt ? ' Resending the prompt…' : ''));
        if (prompt) sendPrompt(prompt);
    }

    function resetStepUp() {
        window.clearInterval(stepUp.poll);
        stepUp.btn.disabled = false;
        stepUp.btn.textContent = 'Authenticate with MFA';
        stepUp = null;
    }

    function setStepUpStatus(box, text) {
        let status = box.querySelector('.stepup-status');
        if (!status) status = box.appendChild(el('p', 'stepup-status'));
        status.textContent = text;
    }

    async function refreshIdentity() {
        try {
            const res = await fetch('/api/me', { credentials: 'same-origin' });
            if (!res.ok) return;
            const identity = await res.json();
            for (const key of ['roles', 'acr', 'amr']) {
                document.querySelector('#identity [data-bind="' + key + '"]').textContent = identity[key];
            }
        } catch (e) { /* leave the stale values shown */ }
    }
})();
