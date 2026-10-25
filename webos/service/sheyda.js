"use strict";
const https=require('https');
const {merge}=require('./filimo');
function url(value){
 let u;try{u=new URL(value);}catch{throw Error('لینک شیدا معتبر نیست');}
 if(u.protocol!=='https:' || !['sheyda.com','www.sheyda.com'].includes(u.hostname) || u.port || u.username || u.password || !/^\/p\/[a-zA-Z0-9]+\/?$/.test(u.pathname))throw Error('لینک صفحهٔ فیلم یا سریال شیدا را وارد کنید');
 return 'https://www.sheyda.com/p/'+u.pathname.split('/')[2];
}
const programQuery=`query getProgramByUid($uid:String!){getProgramByUid(uid:$uid){status{statusCode} data{program{id uid title description summary isSolitary productionYear portraitImagePath landscapeImagePath bannerImagePath} seasons{id title} solitaryEpisode{id uid title order duration landscapeImagePath releaseStatus status} genres{name} cast{name artists{id fullName imagePath}}}}}`;
const seasonQuery=`query getSeasonEpisodes($seasonID:String!){getSeasonEpisodes(seasonID:$seasonID){status{statusCode} data{id uid title order duration landscapeImagePath releaseStatus status seasonID summary}}}`;
function post(endpoint,query,operationName,variables,did){
 const body=JSON.stringify({query,operationName,variables});
 return new Promise((resolve,reject)=>{
  const req=https.request(endpoint,{method:'POST',headers:{'Content-Type':'application/json','User-Agent':'iTV/0.44 metadata','Content-Length':Buffer.byteLength(body),'x-source-p':'202314',...(did?{did}:{})}},res=>{
   if(res.statusCode!==200){res.resume();return reject(Error('شیدا پاسخ معتبر نداد ('+res.statusCode+')'));}
   const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size>8*1024*1024)res.destroy(Error('پاسخ شیدا بیش از حد بزرگ است'));else chunks.push(b);});res.on('error',reject);
   res.on('end',()=>{try{const d=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(d.errors?.length)throw Error('دریافت اطلاعات عمومی شیدا انجام نشد');resolve(d);}catch(e){reject(e);}});
  });req.setTimeout(20000,()=>req.destroy(Error('شیدا پاسخ نمی‌دهد؛ دوباره تلاش کنید')));req.on('error',reject);req.end(body);
 });
}
// Public anonymous device session, created through the site's normal getDid query.
// It never uses the user's browser session, login or subscription credentials.
async function client(){
 const response=await post('https://mikasa.sheyda.com/graphql','query getDid { getDid { statusCode message data { did } } }','getDid',{});
 const did=response.data?.getDid?.data?.did;if(!did)throw Error('اتصال عمومی شیدا برقرار نشد');
 return (operation,variables)=>post('https://api.sheyda.com/query',operation==='getProgramByUid'?programQuery:seasonQuery,operation,variables,did);
}
const digits=s=>String(s||'').replace(/[۰-۹]/g,c=>'۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).replace(/[٠-٩]/g,c=>'٠١٢٣٤٥٦٧٨٩'.indexOf(c));
function seasonNumber(title,singleSeason=false){
 const text=digits(title).replace(/ي/g,'ی').trim(),n=text.match(/\d+/);
 if(n && Number(n[0])>0)return Number(n[0]);
 const ordinals=['اول','دوم','سوم','چهارم','پنجم','ششم','هفتم','هشتم','نهم','دهم','یازدهم','دوازدهم','سیزدهم','چهاردهم','پانزدهم','شانزدهم','هفدهم','هجدهم','نوزدهم','بیستم'];
 const i=ordinals.indexOf(text.replace(/^فصل\s+/,''));if(i<0){if(singleSeason)return 1;throw Error('شمارهٔ فصل شیدا قابل تشخیص نیست');}return i+1;
}
function image(value){try{const u=new URL(value);return u.protocol==='https:' && !u.username && !u.password?u.href:null;}catch{return null;}}
function data(response,operation){const r=response?.data?.[operation];if(!r || r.status?.statusCode!==200 || r.data==null)throw Error('اطلاعات شیدا ناقص است');return r.data;}
async function preview(value,fetch){
 const pageUrl=url(value),uid=pageUrl.split('/').pop(),query=fetch||await client();
 const d=data(await query('getProgramByUid',{uid}),'getProgramByUid'),p=d.program;
 if(!p?.id || !p.title || typeof p.isSolitary!=='boolean')throw Error('اطلاعات عنوان شیدا معتبر نیست');
 const episodes=[],ids=new Set(),numbers=new Set();
 function add(e,season){
  if(e.releaseStatus!=='RELEASED' || e.status!=='ENABLED')return;
  const n=digits(e.title).match(/قسمت\s*(\d+)/),special=!p.isSolitary && !n;
  const number=p.isSolitary?1:n?Number(n[1]):0;
  if(!e.id || !Number.isInteger(number) || (!special && number<1) || number>10000 || ids.has(e.id) || numbers.has(season+':'+number))throw Error('شماره یا شناسهٔ قسمت شیدا معتبر نیست');
  ids.add(e.id);numbers.add(season+':'+number);
  episodes.push({metadataEpisodeId:String(e.id),seasonNumber:season,displayNumber:String(number),...(special?{metadataSpecial:true,displayLabel:e.title}:{}),title:e.title,description:e.summary||'',duration:Number(e.duration)||0,imageUrl:image(e.landscapeImagePath)});
 }
 if(p.isSolitary){if(!d.solitaryEpisode)throw Error('قسمت فیلم شیدا دریافت نشد');add(d.solitaryEpisode,1);}
 else{
  if(!Array.isArray(d.seasons)||!d.seasons.length||d.seasons.length>100)throw Error('فصل‌های شیدا دریافت نشد');
  const seenSeasons=new Set();
  for(const season of d.seasons){const n=seasonNumber(season.title,d.seasons.length===1);if(!season.id||seenSeasons.has(n))throw Error('فهرست فصل‌های شیدا معتبر نیست');seenSeasons.add(n);
   const rows=data(await query('getSeasonEpisodes',{seasonID:season.id}),'getSeasonEpisodes');if(!Array.isArray(rows))throw Error('قسمت‌های فصل شیدا ناقص است');
   for(const e of rows){if(e.seasonID && e.seasonID!==season.id)throw Error('فصل قسمت شیدا معتبر نیست');add(e,n);}
  }
 }
 if(!episodes.length||episodes.length>2000)throw Error('قسمت منتشرشدهٔ معتبری در شیدا پیدا نشد');
 return {metadataProvider:'sheyda',metadataUrl:pageUrl,metadataSourceId:String(p.id),sourceTitle:p.title,isMovie:p.isSolitary,description:p.summary||p.description||'',year:p.productionYear,genres:(d.genres||[]).map(g=>g.name),automaticPosterUrl:image(p.portraitImagePath),automaticBackdropUrl:image(p.bannerImagePath)||image(p.landscapeImagePath),credits:(d.cast||[]).flatMap(r=>(r.artists||[]).map(a=>({name:a.fullName,role:r.name,imageUrl:image(a.imagePath)}))).filter(a=>a.name),episodes:episodes.sort((a,b)=>a.seasonNumber-b.seasonNumber||(Number(a.displayNumber)||Infinity)-(Number(b.displayNumber)||Infinity))};
}
module.exports={url,preview,merge};
