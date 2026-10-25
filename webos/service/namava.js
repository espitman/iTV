"use strict";
const https=require('https');
const {merge}=require('./filimo');
function url(value){
 let u;try{u=new URL(value);}catch{throw Error('لینک نماوا معتبر نیست');}
 if(u.protocol!=='https:'||!['namava.ir','www.namava.ir'].includes(u.hostname)||u.port||u.username||u.password||!/^\/series\/[1-9]\d*(?:-[^/]+)?\/?$/.test(u.pathname))throw Error('لینک صفحهٔ سریال نماوا را وارد کنید');
 return 'https://www.namava.ir'+u.pathname.replace(/\/$/,'');
}
function get(path){return new Promise((resolve,reject)=>{
 const req=https.get('https://www.namava.ir/api'+path,{headers:{Accept:'application/json','User-Agent':'iTV metadata importer'}},res=>{
  if(res.statusCode!==200){res.resume();return reject(Error('نماوا پاسخ معتبر نداد ('+res.statusCode+')'));}
  const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size>8*1024*1024)res.destroy(Error('پاسخ نماوا بیش از حد بزرگ است'));else chunks.push(b);});res.on('error',reject);res.on('end',()=>{try{resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));}catch{reject(Error('پاسخ نماوا قابل خواندن نیست'));}});
 });req.setTimeout(20000,()=>req.destroy(Error('نماوا پاسخ نمی‌دهد؛ دوباره تلاش کنید')));req.on('error',reject);
});}
const digits=s=>String(s||'').replace(/[۰-۹]/g,c=>'۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).replace(/[٠-٩]/g,c=>'٠١٢٣٤٥٦٧٨٩'.indexOf(c));
function image(value){if(!value)return null;try{const u=new URL(value,'https://static.namava.ir');return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch{return null;}}
function result(d){if(d?.succeeded!==true||d.result==null)throw Error('اطلاعات نماوا ناقص است');return d.result;}
async function preview(value,fetch=get){
 const pageUrl=url(value),id=pageUrl.match(/\/series\/(\d+)/)[1],p=result(await fetch('/v2.0/medias/'+id+'/single-series'));
 if(String(p.id)!==id||!p.caption||p.type!=='Series'||!Array.isArray(p.seasons)||!p.seasons.length||p.seasons.length>100)throw Error('اطلاعات سریال نماوا معتبر نیست');
 const episodes=[],ids=new Set(),seasons=new Set(),numbers=new Set();
 for(const s of p.seasons){
  if(s.publishInFuture)continue;
  const season=Number(digits(s.seasonOrderId));
  if(!Number.isSafeInteger(s.seasonId)||s.seasonId<1||!Number.isInteger(season)||season<1||seasons.has(season))throw Error('فصل‌های نماوا معتبر نیست');seasons.add(season);
  const rows=result(await fetch('/v2.0/medias/seasons/'+s.seasonId+'/episodes'));if(!Array.isArray(rows))throw Error('فهرست قسمت‌های نماوا ناقص است');
  for(const e of rows){
   if(e.publishInFuture||e.unknownDatePublish)continue;
   const match=digits(e.episodeCaption).match(/^قسمت\s+(\d+)$/),special=!match,number=match?Number(match[1]):0,key=season+':'+number;
   if(!Number.isSafeInteger(e.id)||e.id<1||ids.has(e.id)||numbers.has(key)||(!special&&number<1))throw Error('شماره یا شناسهٔ قسمت نماوا معتبر نیست');ids.add(e.id);numbers.add(key);
   episodes.push({metadataEpisodeId:String(e.id),seasonNumber:season,displayNumber:String(number),...(special?{metadataSpecial:true,displayLabel:e.caption}:{}),title:e.caption,description:e.shortDescription||'',imageUrl:image(e.imageUrl),duration:(Number(e.mediaDuration)||0)*60});
  }
 }
 if(!episodes.length||episodes.length>2000)throw Error('قسمت منتشرشدهٔ معتبری در نماوا پیدا نشد');
 const roles={Actor:'بازیگر',Director:'کارگردان',Writer:'نویسنده',Producer:'تهیه‌کننده'};
 return {metadataProvider:'namava',metadataUrl:pageUrl,metadataSourceId:id,sourceTitle:p.caption,isMovie:false,description:p.story||p.shortDescription||'',year:p.year,genres:(p.categories||[]).map(c=>c.categoryName),automaticPosterUrl:image(p.imageUrl),automaticBackdropUrl:image(p.coverLandscape),credits:(p.casts||[]).map(c=>({name:c.castName,role:roles[c.castRole]||c.castRole,imageUrl:image(c.castImageUrl)})).filter(c=>c.name),episodes:episodes.sort((a,b)=>a.seasonNumber-b.seasonNumber||(Number(a.displayNumber)||Infinity)-(Number(b.displayNumber)||Infinity))};
}
module.exports={url,preview,merge};
