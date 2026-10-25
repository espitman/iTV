"use strict";
const http = require("http"), https = require("https"), crypto = require("crypto");
function mediaUrl(value) {
  let u;
  try { u = new URL(String(value || "").trim()); } catch { throw Error("لینک فایل WebDAV معتبر نیست"); }
  if (!["http:","https:"].includes(u.protocol) || u.username || u.password || u.hash)
    throw Error("لینک باید HTTP یا HTTPS و بدون رمز داخل نشانی باشد");
  if (/\.m3u8$/i.test(u.pathname)) throw Error("لینک مستقیم فایل ویدیو بدهید؛ فهرست HLS برای WebDAV پشتیبانی نمی‌شود");
  return u.href;
}
function normalize(body, old = {}) {
  const title = String(body.title || "").trim();
  if (!title || title.length > 300) throw Error("نام فیلم یا سریال را وارد کنید");
  if (typeof body.isMovie !== "boolean") throw Error("نوع عنوان را مشخص کنید");
  if (!Array.isArray(body.episodes) || !body.episodes.length || body.episodes.length > 2000)
    throw Error("بین ۱ تا ۲۰۰۰ فایل تعریف کنید");
  if (body.isMovie && body.episodes.length !== 1) throw Error("برای فیلم فقط یک فایل تعریف کنید");
  const keys = new Set(), ids = new Set();
  const episodes = body.episodes.map((e,index) => {
    const seasonNumber = body.isMovie ? 1 : Number(e.seasonNumber);
    const number = body.isMovie ? 1 : Number(e.displayNumber);
    const special = old.metadataProvider === "sheyda" && (old.episodes || []).some(x=>x.sourceEpisodeId===e.sourceEpisodeId && x.metadataSpecial) && number===0;
    if (!Number.isInteger(seasonNumber) || seasonNumber<1 || seasonNumber>10000 || !Number.isInteger(number) || number>10000 || (number<1 && !special))
      throw Error("شمارهٔ فصل و قسمت باید عدد مثبت باشد");
    const key = `${seasonNumber}:${number}`;
    if (keys.has(key)) throw Error("شمارهٔ قسمت در یک فصل تکراری است");
    keys.add(key);
    const previous = (old.episodes || []).find(x => x.sourceEpisodeId === e.sourceEpisodeId);
    const id = previous?.sourceEpisodeId || crypto.randomBytes(8).toString("hex");
    if (ids.has(id)) throw Error("شناسهٔ قسمت تکراری است");
    ids.add(id);
    return {...previous, sourceEpisodeId:id, seasonNumber, displayNumber:String(number),
      sourceOrder:index, title:String(e.title || `قسمت ${number}`).slice(0,300),
      mediaUrl:["filimo","filmnet","sheyda"].includes(old.metadataProvider) && previous && !(e.url || e.mediaUrl) ? "" : mediaUrl(e.url || e.mediaUrl), sourcePresent:true,
      position:previous?.position || 0, watched:!!previous?.watched};
  });
  const credentials = {username:String(body.username ?? old.webdav?.username ?? ""),
    password:body.clearCredentials ? "" : String(body.password || old.webdav?.password || "")};
  if (body.clearCredentials) credentials.username = "";
  if (credentials.username.length > 1000 || credentials.password.length > 4000) throw Error("اطلاعات ورود بیش از حد طولانی است");
  return {...old, kind:"webdav", sourceTitle:title, localTitle:["filimo","filmnet","sheyda"].includes(old.metadataProvider) ? title : null,
    description:String(body.description || "").slice(0,10000), isMovie:body.isMovie,
    episodes, webdav:credentials, importState:"READY", importError:null};
}
function open(url, credentials = {}, range, redirects = 0, method = "GET") {
  const u = new URL(mediaUrl(url));
  const headers = {Accept:"*/*"};
  if (range) {
    if (!/^bytes=\d*-\d*$/.test(range)) return Promise.reject(Error("بازهٔ فایل نامعتبر است"));
    headers.Range = range;
  }
  if (credentials.username || credentials.password)
    headers.Authorization = "Basic " + Buffer.from(credentials.username + ":" + credentials.password).toString("base64");
  return new Promise((resolve,reject) => {
    const request = (u.protocol === "https:" ? https : http).request(u,{method,headers}, response => {
      if ([301,302,303,307,308].includes(response.statusCode)) {
        response.resume();
        try {
          const next = new URL(response.headers.location,u);
          if (redirects >= 3 || next.origin !== u.origin) throw Error("تغییر مسیر WebDAV به سرور دیگر مجاز نیست");
          resolve(open(next.href,credentials,range,redirects+1,method));
        } catch(e) {reject(e);}
      } else resolve(response);
    });
    request.setTimeout(30000,() => request.destroy(Error("گوشی یا سرور WebDAV پاسخ نمی‌دهد")));
    request.on("error",reject);
    request.end();
  });
}
function errorMessage(status) {
  if (status === 401 || status === 403) return "نام کاربری، رمز یا دسترسی فایل WebDAV درست نیست";
  if (status === 404) return "فایل روی گوشی پیدا نشد؛ لینک یا مسیر فایل را بررسی کنید";
  return "سرور WebDAV پاسخ معتبر نداد (" + status + ")";
}
module.exports = {mediaUrl,normalize,open,errorMessage};
