/* Panel de meteo y Renfe. Sin peticiones de red: la app nativa llama a window.panel.update(datos).
   Compatible con Chrome 80 o superior; sin dependencias ni paso de compilación. */
(function () {
  'use strict';

  var STALE_MS = 3 * 60 * 1000;
  var LEAVING_GRACE_MS = 60 * 1000;
  var DAYS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
  var MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

  var state = { data: null, receivedAt: 0 };
  var $ = function (id) { return document.getElementById(id); };

  // ---------- utilidades DOM: solo se toca lo que cambia ----------

  function setText(el, text) {
    if (el.textContent !== text) el.textContent = text;
  }
  function setHtmlOnce(el, key, html) {
    if (el.getAttribute('data-key') !== key) {
      el.innerHTML = html;
      el.setAttribute('data-key', key);
    }
  }
  function setClass(el, cls) {
    if (el.className !== cls) el.className = cls;
  }
  function setHidden(el, hidden) {
    if (el.hidden !== hidden) el.hidden = hidden;
  }
  function setStyle(el, prop, value) {
    if (el.style[prop] !== value) el.style[prop] = value;
  }
  function make(tag, cls, parent) {
    var el = document.createElement(tag);
    if (cls) el.className = cls;
    if (parent) parent.appendChild(el);
    return el;
  }
  function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function hhmm(ms) { var d = new Date(ms); return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function deg(t) { return Math.round(t) + '°'; }
  function mm(v) { return v > 0 ? String(Math.round(v * 10) / 10).replace('.', ',') + ' mm' : ''; }

  // ---------- reloj ----------

  function renderClock() {
    var d = new Date();
    setText($('clock-time'), pad(d.getHours()) + ':' + pad(d.getMinutes()));
    setText($('clock-date'), capitalize(DAYS[d.getDay()]) + ', ' + d.getDate() + ' de ' + MONTHS[d.getMonth()]);
  }

  function scheduleClock() {
    renderClock();
    var now = new Date();
    var wait = 60000 - (now.getSeconds() * 1000 + now.getMilliseconds()) + 50;
    setTimeout(scheduleClock, wait);
  }

  // ---------- meteo ----------

  var hourNodes = [];
  var dayNodes = [];

  function buildMeteo() {
    var hours = $('hours');
    for (var i = 0; i < 6; i++) {
      var h = make('div', 'hour', hours);
      hourNodes.push({ time: make('div', 'h-time', h), icon: make('div', 'h-icon', h), temp: make('div', 'h-temp', h), rain: make('div', 'h-rain', h), root: h });
    }
    var days = $('days');
    for (var j = 0; j < 4; j++) {
      var d = make('div', 'day', days);
      dayNodes.push({ name: make('div', 'd-name', d), icon: make('div', 'd-icon', d), max: make('div', 'd-max', d), min: make('div', 'd-min', d), rain: make('div', 'd-rain', d), root: d });
    }
  }

  function dayName(iso, index) {
    if (index === 0) return 'hoy';
    if (index === 1) return 'mañana';
    var p = iso.split('-');
    var d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
    return DAYS[d.getDay()];
  }

  function renderMeteo(meteo, status) {
    var msg = $('meteo-msg');
    var none = !meteo;
    setHidden($('hours'), none);
    setHidden($('days'), none);
    setHidden($('now-temp'), none);
    if (none) {
      setText(msg, (status && status.message) || 'Cargando el tiempo…');
      setHidden(msg, false);
      return;
    }
    setText(msg, (status && status.message) || '');
    setHidden(msg, !(status && status.message));
    var cur = meteo.current;
    setText($('now-temp'), cur ? deg(cur.temp) : '--°');
    setHtmlOnce($('now-icon'), cur ? cur.symbol : '', cur ? window.weatherIcon(cur.symbol) : '');

    for (var i = 0; i < hourNodes.length; i++) {
      var n = hourNodes[i];
      var h = meteo.hours[i];
      setHidden(n.root, !h);
      if (!h) continue;
      setText(n.time, hhmm(h.time).slice(0, 2) + 'h');
      setHtmlOnce(n.icon, h.symbol, window.weatherIcon(h.symbol));
      setText(n.temp, deg(h.temp));
      setText(n.rain, mm(h.precip));
    }
    for (var j = 0; j < dayNodes.length; j++) {
      var dn = dayNodes[j];
      var d = meteo.days[j];
      setHidden(dn.root, !d);
      if (!d) continue;
      setText(dn.name, dayName(d.date, j));
      setHtmlOnce(dn.icon, d.symbol, window.weatherIcon(d.symbol));
      setText(dn.max, deg(d.max));
      setText(dn.min, deg(d.min));
      setText(dn.rain, mm(d.precip));
    }
  }

  // ---------- Renfe ----------

  var blockNodes = [];
  var blockShape = '';

  function buildBlocks(board) {
    var shape = board.mode + '|' + board.blocks.map(function (b) { return b.title + ':' + b.show; }).join('|');
    if (shape === blockShape) return;
    blockShape = shape;
    var root = $('blocks');
    root.innerHTML = '';
    blockNodes = board.blocks.map(function (b) {
      var el = make('div', 'block', root);
      var title = make('div', 'b-title', el);
      title.textContent = b.title;
      setHidden(title, board.mode === 'B');
      var rows = make('div', 'b-rows', el);
      var rowNodes = [];
      for (var i = 0; i < b.show; i++) {
        var r = make('div', 'train', rows);
        var min = make('div', 't-min', r);
        var line = make('div', 't-line', r);
        var info = make('div', 't-info', r);
        rowNodes.push({ root: r, min: min, line: line, dest: make('div', 't-dest', info), stateEl: make('div', 't-state', info) });
      }
      return { rows: rowNodes };
    });
  }

  function minutesHtml(train, now) {
    var diff = train.predicted - now;
    if (diff <= 30000) return { key: 'now', html: '<span class="now-word">ahora</span>' };
    var m = Math.floor(diff / 60000);
    if (m < 1) return { key: 'now', html: '<span class="now-word">ahora</span>' };
    if (m >= 60) return { key: 'h' + hhmm(train.predicted), html: hhmm(train.predicted) };
    return { key: 'm' + m, html: m + '<small>min</small>' };
  }

  function trainState(train, realtimeOk) {
    if (train.canceled) return { text: 'Suprimido', cls: '' };
    if (!realtimeOk || !train.realtime) return { text: 'Horario teórico · ' + hhmm(train.scheduled), cls: 'theory' };
    if (train.delayMin <= 0) return { text: 'Puntual · ' + hhmm(train.scheduled), cls: 'ok' };
    var cls = train.delayMin >= 5 ? 'verylate' : 'late';
    return { text: '+' + train.delayMin + ' min · prevista ' + hhmm(train.scheduled), cls: cls };
  }

  function renderRenfe(board, status, now) {
    var msg = $('renfe-msg');
    setHidden($('blocks'), !board);
    if (!board) {
      setText($('station'), 'Cercanías');
      $('blocks').innerHTML = '';
      blockShape = '';
      setText(msg, (status && status.message) || 'Cargando trenes…');
      setHidden(msg, false);
      setHidden($('alerts'), true);
      return;
    }
    setText($('station'), board.station);
    buildBlocks(board);

    for (var b = 0; b < board.blocks.length; b++) {
      var block = board.blocks[b];
      var visible = block.trains.filter(function (t) { return t.predicted >= now - LEAVING_GRACE_MS; });
      var rows = blockNodes[b].rows;
      for (var i = 0; i < rows.length; i++) {
        var n = rows[i];
        var t = visible[i];
        if (!t) {
          setClass(n.root, 'train empty');
          setHtmlOnce(n.min, 'none', '');
          setText(n.line, '');
          setStyle(n.line, 'background', 'transparent');
          setText(n.dest, i === 0 ? 'Sin trenes próximos' : '');
          setText(n.stateEl, '');
          continue;
        }
        setClass(n.root, t.canceled ? 'train canceled' : 'train');
        var m = minutesHtml(t, now);
        setHtmlOnce(n.min, m.key, m.html);
        setText(n.line, t.line);
        setStyle(n.line, 'background', '#' + t.color);
        setStyle(n.line, 'color', '#' + t.textColor);
        setText(n.dest, t.destination);
        var st = trainState(t, board.realtimeOk);
        setText(n.stateEl, st.text);
        setClass(n.stateEl, 't-state ' + st.cls);
      }
    }

    var message = '';
    if (!board.scheduleOk) message = 'El horario descargado no tiene trenes para hoy. Se reintentará la descarga.';
    else if (status && status.message) message = status.message;
    setText(msg, message);
    setHidden(msg, !message);

    var alerts = $('alerts');
    var text = (board.alerts || []).join('  ·  ');
    setText(alerts, text);
    setHidden(alerts, !text);
  }

  // ---------- caducidad ----------

  /** Aviso si no llega nada de Renfe en 3 minutos, o si la app deja de mandar datos al panel. */
  function renderStale(now) {
    var d = state.data;
    var el = $('stale');
    if (!d || state.receivedAt === 0) {
      setHidden(el, true);
      return;
    }
    var st = d.renfeStatus || {};
    var okAt = st.okAt || 0;
    // Antes del primer dato se cuenta desde el arranque, para no avisar nada más encender.
    var since = Math.max(okAt, st.startedAt || 0);
    var stale = now - state.receivedAt > STALE_MS || (since > 0 && now - since > STALE_MS);
    if (stale) {
      setText(el, okAt > 0 ? 'Sin conexión desde ' + hhmm(okAt) : 'Sin conexión');
      setHidden(el, false);
    } else {
      setHidden(el, true);
    }
  }

  // ---------- ciclo ----------

  function renderAll() {
    var now = Date.now();
    var d = state.data || {};
    renderMeteo(d.meteo || null, d.meteoStatus);
    renderRenfe(d.renfe || null, d.renfeStatus, now);
    renderStale(now);
  }

  window.panel = {
    /** Recibe el estado completo desde la app. Acepta objeto o cadena JSON. */
    update: function (data) {
      state.data = typeof data === 'string' ? JSON.parse(data) : data;
      state.receivedAt = Date.now();
      renderAll();
    },
  };

  buildMeteo();
  scheduleClock();
  renderAll();
  // La cuenta atrás se recalcula en local cada 30 s, sin tráfico de red.
  setInterval(renderAll, 30000);
})();
