"use strict";
const https = require('https'), crypto = require('crypto');
const base = 'https://www.filimo.com/api/fa/v1';
function url(value) {
  let u; try { u=new URL(value); } catch { throw Error('لینک فیلیمو معتبر نیست'); }
  if(u.protocol!=='https:' || !['www.filimo.com','filimo.com'].includes(u.hostname) || u.port || u.username || u.password || !/^\/[nm]\/[a-zA-Z0-9]+\/?$/.test(u.pathname)) throw Error('لینک صفحهٔ فیلم یا سریال فیلیمو را وارد کنید');
  return 'https://www.filimo.com/n/'+u.pathname.split('/')[2];
}
function get(value) {
  const u=new URL(value);
  if(u.origin!=='https://www.filimo.com' || !/^\/api\/fa\/v1\/(movie\/|web\/review\/moviedetail\/uid\/)/.test(u.pathname)) throw Error('نشانی اطلاعات فیلیمو معتبر نیست');
  return new Promise((resolve,reject)=>{
    const req=https.get(u,{headers:{Accept:'application/json'}},res=>{
      if(res.statusCode!==200){res.resume();return reject(Error('فیلیمو پاسخ معتبر نداد ('+res.statusCode+')'));}
      let data='';res.setEncoding('utf8');res.on('data',chunk=>{data+=chunk;if(data.length>8e6)res.destroy(Error('پاسخ فیلیمو بیش از حد بزرگ است'));});
      res.on('error',reject);res.on('end',()=>{try{resolve(JSON.parse(data));}catch{reject(Error('اطلاعات دریافتی فیلیمو معتبر نیست'));}});
    });req.setTimeout(20000,()=>req.destroy(Error('فیلیمو پاسخ نمی‌دهد؛ دوباره تلاش کنید')));req.on('error',reject);
  });
}
async function preview(value, fetch=get) {
  const pageUrl=url(value),uid=pageUrl.split('/').pop();
  const g=(await fetch(base+'/movie/movie/one/uid/'+uid))?.data?.attributes?.General;
  if(!g?.uid || !g.title) throw Error('اطلاعات این عنوان در فیلیمو پیدا نشد');
  const serial=!!g.serial?.enable, episodes=[], seen=new Set();
  if(serial){
    const seasons=(await fetch(base+'/movie/serial/allseason/uid/'+uid))?.data?.attributes?.data;
    if(!Array.isArray(seasons) || !seasons.length) throw Error('فصل‌های سریال دریافت نشد');
    for(const season of seasons){
      let next=season.episodes_link, pages=0;
      while(next){
        if(seen.has(next) || ++pages>100) throw Error('صفحه‌بندی قسمت‌های فیلیمو معتبر نیست');seen.add(next);
        const data=await fetch(next);
        if(!Array.isArray(data.included))throw Error('فهرست قسمت‌های فیلیمو معتبر نیست');
        for(const item of data.included){const a=item.attributes;if(item.type!=='movies' || !a?.uid)continue;
          if(a.watch_action?.type==='commingsoon' || a.badge?.commingsoon)continue;
          episodes.push({metadataEpisodeId:String(a.uid),seasonNumber:Number(a.serial?.season_id || season.serial_season_part),displayNumber:String(a.serial?.serial_part),title:a.movie_title || a.full_title,imageUrl:a.thumbplay?.thumbplay_img_b || a.thumbplay?.thumbplay_img_m || a.pic?.movie_img_b || null,duration:Number(a.duration)||0});
        }
        next=data.links?.paging || null;
      }
    }
  }else episodes.push({metadataEpisodeId:g.uid,seasonNumber:1,displayNumber:'1',title:g.title,duration:Number(g.duration?.value)||0});
  if(!episodes.length || episodes.length>2000 || episodes.some(e=>!Number.isInteger(e.seasonNumber)||e.seasonNumber<1||!/^\d+$/.test(e.displayNumber)))throw Error('قسمت منتشرشدهٔ معتبری پیدا نشد');
  let credits;
  try { const details=(await fetch(base+'/web/review/moviedetail/uid/'+uid))?.data?.attributes; credits=(details?.crewData || details?.ActorCrewData?.profile || []).map(p=>({name:p.name_fa||p.name,role:p.role_name_fa||p.post_info?.title_fa||'بازیگر',imageUrl:p.profile_image})).filter(p=>p.name); } catch { /* Optional cast metadata must not block episode updates. */ }
  const unique=new Map(episodes.map(e=>[e.metadataEpisodeId,e]));
  return {metadataProvider:'filimo',metadataUrl:pageUrl,metadataSourceId:String(serial?g.serial.parent_id:g.id),sourceTitle:serial?g.serial.title:g.movie_name||g.title,isMovie:!serial,description:g.descr||'',...(credits ? {credits} : {}),year:g.pro_year,genres:(g.categories||[]).map(c=>c.title),automaticPosterUrl:g.thumbnails?.movie_img_b||null,automaticBackdropUrl:g.cover_data?.horizontal||g.cover||null,episodes:[...unique.values()].sort((a,b)=>a.seasonNumber-b.seasonNumber||Number(a.displayNumber)-Number(b.displayNumber))};
}
function merge(meta,old={}){
 const previous=new Map((old.episodes||[]).map(e=>[e.metadataEpisodeId,e]));
 const episodes=meta.episodes.map((e,i)=>({...previous.get(e.metadataEpisodeId),...e,sourceEpisodeId:previous.get(e.metadataEpisodeId)?.sourceEpisodeId||crypto.randomBytes(8).toString('hex'),sourceOrder:i,sourcePresent:true,mediaUrl:previous.get(e.metadataEpisodeId)?.mediaUrl||'',position:previous.get(e.metadataEpisodeId)?.position||0,watched:!!previous.get(e.metadataEpisodeId)?.watched}));
 const ids=new Set(episodes.map(e=>e.metadataEpisodeId));
 return {...old,...meta,kind:'webdav',episodes:[...episodes,...(old.episodes||[]).filter(e=>!ids.has(e.metadataEpisodeId))],webdav:old.webdav||{username:'',password:''},lastMetadataRefresh:Date.now(),importState:'READY',importError:null};
}
module.exports={url,preview,merge};
