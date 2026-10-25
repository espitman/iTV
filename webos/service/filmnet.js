"use strict";
const https = require('https');
const {merge} = require('./filimo');
function url(value) {
  let u; try { u = new URL(value); } catch { throw Error('لینک فیلم‌نت معتبر نیست'); }
  if (u.protocol !== 'https:' || !['filmnet.ir','www.filmnet.ir'].includes(u.hostname) || u.port || u.username || u.password ||
      !/^\/contents\/[a-zA-Z0-9]+(?:\/[^/]+)?\/?$/.test(u.pathname)) throw Error('لینک صفحهٔ فیلم یا سریال فیلم‌نت را وارد کنید');
  return 'https://filmnet.ir' + u.pathname.replace(/\/$/,'');
}
function get(value, redirects=0) {
  const target=url(value);
  return new Promise((resolve,reject)=>{
    const req=https.get(target,{headers:{Accept:'text/html','User-Agent':'iTV metadata importer'}},res=>{
      if ([301,302,307,308].includes(res.statusCode) && res.headers.location) {
        res.resume();
        if(redirects>=3)return reject(Error('تغییر مسیر فیلم‌نت بیش از حد مجاز است'));
        try { resolve(get(url(new URL(res.headers.location,target).href),redirects+1)); } catch(e) { reject(e); }
        return;
      }
      if(res.statusCode!==200){res.resume();return reject(Error('فیلم‌نت پاسخ معتبر نداد ('+res.statusCode+')'));}
      const chunks=[];let length=0;
      res.on('data',chunk=>{length+=chunk.length;if(length>16*1024*1024)res.destroy(Error('پاسخ فیلم‌نت بیش از حد بزرگ است'));else chunks.push(chunk);});
      res.on('error',reject);res.on('end',()=>resolve(Buffer.concat(chunks).toString('utf8')));
    });
    req.setTimeout(20000,()=>req.destroy(Error('فیلم‌نت پاسخ نمی‌دهد؛ دوباره تلاش کنید')));req.on('error',reject);
  });
}
function plain(value) {
  return String(value||'').replace(/<[^>]*>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/\s+/g,' ').trim();
}
function image(value) {
  try { const u=new URL(value?.path || '');return u.protocol==='https:' && !u.username && !u.password ? u.href : null; } catch { return null; }
}
function duration(value) {
  const parts=String(value||'').split(':');
  return parts.length===3 && parts.every(p=>/^\d+$/.test(p)) ? parts.reduce((a,b)=>a*60+Number(b),0) : 0;
}
async function preview(value, fetch=get) {
  const pageUrl=url(value),html=await fetch(pageUrl);
  const match=html.match(/<script\b(?=[^>]*\bid=["']__NEXT_DATA__["'])[^>]*>([\s\S]*?)<\/script>/i);
  let aggregate;
  try { aggregate=JSON.parse(match?.[1] || '').props.pageProps.aggregate; } catch { throw Error('ساختار اطلاعات فیلم‌نت قابل خواندن نیست'); }
  const v=aggregate?.video_content;
  if(!v?.id || !v.title || !['series','movie'].includes(v.type)) throw Error('اطلاعات فیلم یا سریال فیلم‌نت پیدا نشد');
  const episodes=[],ids=new Set(),numbers=new Set();
  function add(e,seasonNumber) {
    if(e.status && e.status!=='active')return;
    if(e.conditional_flag==='coming_soon' || e.conditional_flag==='comingsoon')return;
    const n=Number(e.episode || (v.type==='movie'?1:0));
    if(!e.id || !Number.isInteger(n) || n<1 || !Number.isInteger(seasonNumber) || seasonNumber<1)throw Error('شماره یا شناسهٔ قسمت فیلم‌نت معتبر نیست');
    if(ids.has(e.id) || numbers.has(seasonNumber+':'+n))throw Error('قسمت تکراری در اطلاعات فیلم‌نت');
    ids.add(e.id);numbers.add(seasonNumber+':'+n);
    episodes.push({metadataEpisodeId:String(e.id),seasonNumber,displayNumber:String(n),title:plain(e.title),description:plain(e.summary),imageUrl:image(e.cover_image),duration:duration(e.duration)});
  }
  if(v.type==='movie')add(v,1);
  else {
    const seasons=aggregate.series?.seasons || v.seasons;
    if(!Array.isArray(seasons) || !seasons.length)throw Error('فصل‌های سریال فیلم‌نت دریافت نشد');
    for(const season of seasons){
      if(!Array.isArray(season.episodes))throw Error('فهرست قسمت‌های فیلم‌نت ناقص است');
      for(const e of season.episodes)add(e,Number(season.season));
    }
  }
  if(!episodes.length || episodes.length>2000)throw Error('قسمت منتشرشدهٔ معتبری در فیلم‌نت پیدا نشد');
  return {metadataProvider:'filmnet',metadataUrl:pageUrl,metadataSourceId:String(v.id),sourceTitle:plain(v.title),isMovie:v.type==='movie',description:plain(v.summary),year:v.year,
    genres:(v.categories||[]).filter(c=>c.type==='genre').flatMap(c=>(c.items||[]).map(i=>plain(i.title))),
    automaticPosterUrl:image(v.poster_image),automaticBackdropUrl:image(v.cover_image),
    credits:(aggregate.artists||[]).map(a=>({name:plain(a.person?.name),role:(a.roles||[]).map(plain).join('، '),imageUrl:image(a.person?.avatar_image)})).filter(a=>a.name),
    episodes:episodes.sort((a,b)=>a.seasonNumber-b.seasonNumber||Number(a.displayNumber)-Number(b.displayNumber))};
}
module.exports={url,preview,merge};
