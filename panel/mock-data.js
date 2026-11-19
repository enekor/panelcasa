/* Datos de ejemplo para dev.html. Parámetros en la URL:
   ?mode=A|B   modo de presentación (A por defecto)
   &rt=0       sin tiempo real (horario teórico)
   &stale=1    sin conexión desde hace 10 min
   &alerts=1   con avisos
   &empty=1    sin datos todavía */
(function () {
  'use strict';
  var q = {};
  location.search.replace(/^\?/, '').split('&').forEach(function (kv) {
    if (!kv) return;
    var p = kv.split('=');
    q[p[0]] = decodeURIComponent(p[1] || '');
  });

  var now = Date.now();
  var MIN = 60000;
  var rt = q.rt !== '0';

  function train(id, line, color, text, dest, inMin, delay, opts) {
    opts = opts || {};
    var scheduled = now + inMin * MIN;
    return {
      tripId: id, line: line, color: color, textColor: text, destination: dest,
      scheduled: scheduled, predicted: scheduled + (rt ? delay : 0) * MIN,
      delayMin: rt ? delay : 0, realtime: rt && !opts.noRt, canceled: !!opts.canceled,
    };
  }

  var boardA = {
    station: 'Coslada', mode: 'A', realtimeOk: rt, scheduleOk: true,
    alerts: q.alerts === '1' ? ['Línea C2: trenes con retrasos de hasta 15 minutos por una avería en Vicálvaro.', 'Ascensor del andén 2 fuera de servicio.'] : [],
    blocks: [
      { title: 'Hacia Madrid-Chamartín-Clara Campoamor', show: 2, trains: [
        train('a1', 'C7', 'DE001B', 'FFFFFF', 'Madrid-Príncipe Pío', 2, 0),
        train('a2', 'C2', '00A54F', 'FFFFFF', 'Madrid-Chamartín-Clara Campoamor', 9, 3),
        train('a3', 'C7', 'DE001B', 'FFFFFF', 'Madrid-Príncipe Pío', 17, 0),
      ] },
      { title: 'Hacia Alcalá de Henares', show: 2, trains: [
        train('b1', 'C2', '00A54F', 'FFFFFF', 'Guadalajara', 5, 7),
        train('b2', 'C7', 'DE001B', 'FFFFFF', 'Alcalá de Henares', 12, 0, { canceled: true }),
        train('b3', 'C2', '00A54F', 'FFFFFF', 'Alcalá de Henares', 75, 0, { noRt: true }),
      ] },
    ],
  };

  var boardB = {
    station: 'Madrid-Atocha Cercanías', mode: 'B', realtimeOk: rt, scheduleOk: true,
    alerts: boardA.alerts,
    blocks: [{ title: 'Próximos trenes', show: 4, trains: [
      train('c1', 'C4b', '2C2A86', 'FFFFFF', 'Parla', 0, 0),
      train('c2', 'C5', 'F2B700', 'FFFFFF', 'Humanes', 3, 1),
      train('c3', 'C5', 'F2B700', 'FFFFFF', 'Móstoles-El Soto', 4, 0),
      train('c4', 'C3', '9E3EA0', 'FFFFFF', 'Madrid-Chamartín-Clara Campoamor', 6, 12),
      train('c5', 'C10', 'BCCF00', 'FFFFFF', 'Villalba', 8, 0),
    ] }],
  };

  function iso(offsetDays) {
    var d = new Date(now + offsetDays * 86400000);
    return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
  }
  var symbols = ['partlycloudy_day', 'fair_day', 'lightrainshowers_day', 'rain', 'cloudy', 'clearsky_night'];
  var hourStart = Math.ceil(now / 3600000) * 3600000;
  var meteo = {
    updatedAt: now - 30 * MIN,
    current: { temp: 13.4, symbol: 'partlycloudy_day' },
    hours: symbols.map(function (s, i) { return { time: hourStart + i * 3600000, temp: 13 - i * 0.8, symbol: s, precip: s.indexOf('rain') >= 0 ? 0.4 : 0 }; }),
    days: [
      { date: iso(0), min: 6.2, max: 14.1, symbol: 'partlycloudy_day', precip: 0 },
      { date: iso(1), min: 5.0, max: 12.3, symbol: 'rain', precip: 6.8 },
      { date: iso(2), min: 3.1, max: 10.9, symbol: 'lightsnowshowers_day', precip: 1.2 },
      { date: iso(3), min: 4.4, max: 15.0, symbol: 'clearsky_day', precip: 0 },
    ],
  };

  var data = q.empty === '1' ? {
    renfe: null, renfeStatus: { okAt: 0, startedAt: now, message: 'Descargando el horario de Renfe…' },
    meteo: null, meteoStatus: { okAt: 0, message: null },
  } : {
    renfe: q.mode === 'B' ? boardB : boardA,
    renfeStatus: { okAt: q.stale === '1' ? now - 10 * MIN : now, startedAt: now - 60 * MIN, message: null },
    meteo: meteo,
    meteoStatus: { okAt: now, message: null },
  };

  window.panel.update(data);
})();
