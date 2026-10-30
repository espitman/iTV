"use strict";
const fs=require('fs').promises,path=require('path'),crypto=require('crypto'),https=require('https');
function mime(b){
 if(b.length>3&&b[0]===255&&b[1]===216&&b[2]===255)return 'image/jpeg';
 if(b.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))return 'image/png';
 if(b.toString('ascii',0,4)==='RIFF'&&b.toString('ascii',8,12)==='WEBP')return 'image/webp';
 if(/^GIF8[79]a/.test(b.toString('ascii',0,6)))return 'image/gif';
 if(b.toString('ascii',4,8)==='ftyp'&&/avif|avis/.test(b.toString('ascii',8,32)))return 'image/avif';
 throw Error('قالب عکس معتبر نیست');
}
function download(value,redirects=0){
 const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.port)throw Error('نشانی عکس معتبر نیست');
 return new Promise((resolve,reject)=>{
  const req=https.get(u,{headers:{'User-Agent':'iTV artwork cache',Accept:'image/*'}},res=>{
   if([301,302,303,307,308].includes(res.statusCode)&&res.headers.location){res.resume();if(redirects>=4)return reject(Error('تغییر مسیر عکس بیش از حد مجاز است'));
    try{const next=new URL(res.headers.location,u);const tw=h=>h==='telewebion.net'||h.endsWith('.telewebion.net')||h==='telewebion.ir'||h.endsWith('.telewebion.ir');if(next.hostname!==u.hostname&&!(tw(next.hostname)&&tw(u.hostname)))throw Error('تغییر مسیر عکس معتبر نیست');resolve(download(next.href,redirects+1));}catch(e){reject(e);}return;
   }
   if(res.statusCode!==200){res.resume();return reject(Error('دریافت عکس ناموفق بود'));}
   let size=0;const chunks=[];res.on('data',b=>{size+=b.length;if(size>20*1024*1024)res.destroy(Error('عکس بیش از حد بزرگ است'));else chunks.push(b);});res.on('error',reject);res.on('end',()=>{try{const b=Buffer.concat(chunks);mime(b);resolve(b);}catch(e){reject(e);}});
  });req.setTimeout(15000,()=>req.destroy(Error('مهلت دریافت عکس تمام شد')));req.on('error',reject);
 });
}
function create(dir,{fetch=download,limit=128*1024*1024}={}){
 const pending=new Map();
 async function read(id){if(!/^[a-f\d]{64}$/.test(id))throw Error('شناسه عکس نامعتبر است');const file=path.join(dir,id),body=await fs.readFile(file);await fs.utimes(file,new Date(),new Date()).catch(()=>{});return {body,type:mime(body)};}
 async function trim(){const rows=await Promise.all((await fs.readdir(dir)).filter(n=>/^[a-f\d]{64}$/.test(n)).map(async n=>{const s=await fs.stat(path.join(dir,n)).catch(()=>null);return s?{n,size:s.size,time:s.mtimeMs}:null;}));let size=rows.filter(Boolean).reduce((a,r)=>a+r.size,0);for(const r of rows.filter(Boolean).sort((a,b)=>a.time-b.time)){if(size<=limit)break;await fs.unlink(path.join(dir,r.n)).catch(()=>{});size-=r.size;}}
 async function ensure(url){const id=crypto.createHash('sha256').update(url).digest('hex');try{await read(id);return id;}catch{}
  if(pending.has(id))return pending.get(id);
  const task=(async()=>{const body=await fetch(url);mime(body);if(body.length>Math.min(limit,20*1024*1024))throw Error('عکس بیش از حد بزرگ است');await fs.mkdir(dir,{recursive:true});const tmp=path.join(dir,id+'.tmp');await fs.writeFile(tmp,body);await fs.rename(tmp,path.join(dir,id));await trim();return id;})();pending.set(id,task);try{return await task;}finally{pending.delete(id);}
 }
 return {read,ensure};
}
module.exports={create,mime};
