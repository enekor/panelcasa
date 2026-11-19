/* Iconos de tiempo en SVG en línea, sin fuentes ni ficheros externos.
   Agrupa los symbol_code de Met.no en unas pocas familias. */
(function () {
  'use strict';

  var SUN = '<circle cx="32" cy="32" r="12" fill="#ffc83d"/>' +
    '<g stroke="#ffc83d" stroke-width="4" stroke-linecap="round">' +
    '<line x1="32" y1="6" x2="32" y2="13"/><line x1="32" y1="51" x2="32" y2="58"/>' +
    '<line x1="6" y1="32" x2="13" y2="32"/><line x1="51" y1="32" x2="58" y2="32"/>' +
    '<line x1="13.6" y1="13.6" x2="18.5" y2="18.5"/><line x1="45.5" y1="45.5" x2="50.4" y2="50.4"/>' +
    '<line x1="13.6" y1="50.4" x2="18.5" y2="45.5"/><line x1="45.5" y1="18.5" x2="50.4" y2="13.6"/></g>';
  var MOON = '<path d="M40 10a22 22 0 1 0 14 34A18 18 0 0 1 40 10z" fill="#d7dde3"/>';
  var SMALL_SUN = '<g transform="translate(-6,-8) scale(0.75)">' + SUN + '</g>';
  var SMALL_MOON = '<g transform="translate(-4,-6) scale(0.7)">' + MOON + '</g>';
  var CLOUD = '<path d="M18 50h30a11 11 0 0 0 0-22 15 15 0 0 0-28-2 12 12 0 0 0-2 24z" fill="#c9d1d8"/>';
  var DARK_CLOUD = '<path d="M18 46h30a11 11 0 0 0 0-22 15 15 0 0 0-28-2 12 12 0 0 0-2 24z" fill="#8d99a4"/>';

  function drops(n) {
    var xs = n === 1 ? [32] : n === 2 ? [26, 38] : [20, 32, 44];
    var s = '<g stroke="#4ea3ff" stroke-width="4" stroke-linecap="round">';
    for (var i = 0; i < xs.length; i++) s += '<line x1="' + xs[i] + '" y1="51" x2="' + (xs[i] - 3) + '" y2="59"/>';
    return s + '</g>';
  }
  function flakes(n) {
    var xs = n === 1 ? [32] : n === 2 ? [25, 39] : [20, 32, 44];
    var s = '';
    for (var i = 0; i < xs.length; i++) s += '<circle cx="' + xs[i] + '" cy="55" r="3.5" fill="#ffffff"/>';
    return s;
  }
  var SLEET = '<g stroke="#4ea3ff" stroke-width="4" stroke-linecap="round"><line x1="24" y1="51" x2="21" y2="59"/><line x1="44" y1="51" x2="41" y2="59"/></g>' +
    '<circle cx="33" cy="55" r="3.5" fill="#ffffff"/>';
  var BOLT = '<path d="M34 44l-8 12h7l-3 8 10-13h-7l3-7z" fill="#ffc83d"/>';
  var FOG = '<g stroke="#c9d1d8" stroke-width="4" stroke-linecap="round"><line x1="10" y1="24" x2="54" y2="24"/>' +
    '<line x1="14" y1="34" x2="50" y2="34"/><line x1="10" y1="44" x2="54" y2="44"/></g>';

  function svg(body) {
    return '<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' + body + '</svg>';
  }

  function intensity(base) {
    if (base.indexOf('heavy') === 0) return 3;
    if (base.indexOf('light') === 0) return 1;
    return 2;
  }

  /** Devuelve el SVG para un symbol_code de Met.no, p. ej. «lightrainshowers_day». */
  function weatherIcon(symbol) {
    if (!symbol) return '';
    var parts = String(symbol).split('_');
    var base = parts[0];
    var night = parts[1] === 'night' || parts[1] === 'polartwilight';
    var orb = night ? SMALL_MOON : SMALL_SUN;
    var showers = base.indexOf('showers') >= 0;
    var thunder = base.indexOf('thunder') >= 0;
    var extra = thunder ? BOLT : '';

    if (base === 'clearsky') return svg(night ? MOON : SUN);
    if (base === 'fair') return svg(orb + '<g transform="translate(8,10) scale(0.8)">' + CLOUD + '</g>');
    if (base === 'partlycloudy') return svg(orb + CLOUD);
    if (base === 'cloudy') return svg(CLOUD);
    if (base === 'fog') return svg(FOG);
    if (base.indexOf('snow') >= 0) return svg((showers ? orb : '') + DARK_CLOUD + flakes(intensity(base)) + extra);
    if (base.indexOf('sleet') >= 0) return svg((showers ? orb : '') + DARK_CLOUD + SLEET + extra);
    if (base.indexOf('rain') >= 0) return svg((showers ? orb : '') + DARK_CLOUD + drops(intensity(base)) + extra);
    if (thunder) return svg(DARK_CLOUD + BOLT);
    return svg(CLOUD);
  }

  window.weatherIcon = weatherIcon;
})();
