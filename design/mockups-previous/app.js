(function () {
  "use strict";

  var SCREENS = {
    home: "خانه",
    "series-single": "سریال تک‌فصل",
    "series-multi": "سریال چندفصل",
    "player-controls": "کنترل پخش",
    "player-resume": "ادامه پخش",
    "player-skip": "رد تیتراژ",
    "player-next": "قسمت بعد",
    "tv-settings": "تنظیمات تلویزیون",
    "web-dashboard": "داشبورد وب",
    "home-empty": "خانه خالی"
  };

  var PAIR_URL = "http://192.168.1.42:8191";
  var params = new URLSearchParams(window.location.search);
  var name = params.get("screen") || "home";
  if (!SCREENS[name]) name = "home";

  document.documentElement.dataset.screen = name;
  document.title = "iTV · " + SCREENS[name];

  document.querySelectorAll(".screen").forEach(function (el) {
    el.hidden = el.getAttribute("data-screen") !== name;
  });

  if (name === "tv-settings" && window.ITVQR) {
    var host = document.getElementById("qr-host");
    if (host) host.innerHTML = window.ITVQR.toSvg(PAIR_URL);
  }
})();
