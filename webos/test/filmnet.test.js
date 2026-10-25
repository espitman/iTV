const {test}=require('node:test');
const assert=require('node:assert/strict');
const filmnet=require('../service/filmnet');
const dav=require('../service/webdav');
const html=aggregate=>`<script id="__NEXT_DATA__" type="application/json">${JSON.stringify({props:{pageProps:{aggregate}}})}</script>`;
const episode=(id,episode,status='active')=>({id,episode,status,title:`قسمت ${episode}`,duration:'01:02:03',cover_image:{path:`https://static.filmnet.ir/${id}.jpg`}});
const aggregate=()=>({video_content:{id:'series-id',type:'series',title:'یل',summary:'<p>خلاصه&nbsp;سریال</p>',poster_image:{path:'https://static.filmnet.ir/poster.jpg'},cover_image:{path:'https://static.filmnet.ir/cover.jpg'}},series:{seasons:[{season:2,episodes:[episode('c',1)]},{season:1,episodes:[episode('b',2),episode('a',1),episode('future',3,'draft')]}]},artists:[{person:{name:'بازیگر',avatar_image:{path:'https://static.filmnet.ir/person.jpg'}},roles:['بازیگر']}]});
test('Filmnet URLs are restricted to HTTPS content pages on Filmnet',()=>{
 assert.equal(filmnet.url('https://www.filmnet.ir/contents/abc/Title?x=1'),'https://filmnet.ir/contents/abc/Title');
 for(const u of ['http://filmnet.ir/contents/a','https://filmnet.ir.evil/contents/a','https://u@filmnet.ir/contents/a','https://filmnet.ir/api/a','https://filmnet.ir:8443/contents/a'])assert.throws(()=>filmnet.url(u));
});
test('Filmnet imports seasons and episode images and preserves files, credentials and watch state on refresh',async()=>{
 const m=await filmnet.preview('https://filmnet.ir/contents/abc',async()=>html(aggregate()));
 assert.deepEqual(m.episodes.map(e=>e.metadataEpisodeId),['a','b','c']);assert.equal(m.episodes[0].duration,3723);assert.equal(m.episodes[0].imageUrl,'https://static.filmnet.ir/a.jpg');assert.equal(m.description,'خلاصه سریال');assert.equal(m.credits[0].role,'بازیگر');
 const old=filmnet.merge(m);Object.assign(old.episodes[0],{mediaUrl:'http://phone/a.mp4',position:123,watched:true});old.customPosterUrl='custom';old.webdav={username:'u',password:'p'};
 const a=aggregate();a.series.seasons[1].episodes.push(episode('new',4));const fresh=filmnet.merge(await filmnet.preview(m.metadataUrl,async()=>html(a)),old);
 assert.equal(fresh.episodes[0].sourceEpisodeId,old.episodes[0].sourceEpisodeId);assert.equal(fresh.episodes[0].mediaUrl,'http://phone/a.mp4');assert.equal(fresh.episodes[0].position,123);assert.equal(fresh.episodes[0].watched,true);assert.equal(fresh.customPosterUrl,'custom');assert.equal(fresh.webdav.password,'p');
 const saved=dav.normalize({title:'ویرایش نام',isMovie:false,episodes:fresh.episodes},fresh);assert.equal(saved.episodes.find(e=>e.metadataEpisodeId==='new').mediaUrl,'');assert.equal(saved.episodes[0].imageUrl,m.episodes[0].imageUrl);assert.equal(saved.localTitle,'ویرایش نام');
});
test('Filmnet rejects malformed or incomplete metadata without returning a partial library',async()=>{
 for(const a of [{}, {...aggregate(),series:{seasons:[{season:1}]}}, {...aggregate(),series:{seasons:[{season:1,episodes:[episode('a',1),episode('b',1)]}]}}])await assert.rejects(filmnet.preview('https://filmnet.ir/contents/a',async()=>html(a)));
 await assert.rejects(filmnet.preview('https://filmnet.ir/contents/a',async()=>'<html>Unavailable</html>'));
});
test('Filmnet movies import one file and allow missing episode images',async()=>{
 const v={id:'movie',type:'movie',title:'فیلم',status:'active',duration:'00:10:00'};
 const m=await filmnet.preview('https://filmnet.ir/contents/movie',async()=>html({video_content:v}));assert.equal(m.isMovie,true);assert.equal(m.episodes.length,1);assert.equal(m.episodes[0].imageUrl,null);
});
test('Filmnet API creates and refreshes imported titles without duplicating them',async t=>{
 const fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os');
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-filmnet-test-'));const original=filmnet.preview;
 filmnet.preview=u=>original(u,async()=>html(aggregate()));
 const runtime=require('../service/server').createServer({dataDir:dir,host:'127.0.0.1',port:0});const state=await runtime.start();
 t.after(async()=>{filmnet.preview=original;await new Promise(r=>runtime.server.close(r));await fs.rm(dir,{recursive:true,force:true});});
 const request=async(p,body)=>{const r=await fetch(`http://127.0.0.1:${state.port}${p}`,{method:body?'POST':'GET',headers:{'Content-Type':'application/json','x-itv-token':state.tvToken},...(body?{body:JSON.stringify(body)}:{})});assert.equal(r.status,200);return r.json();};
 const first=await request('/api/filmnet',{url:'https://filmnet.ir/contents/a'});assert.equal(first.episodes[0].imageUrl,'https://static.filmnet.ir/a.jpg');
 const second=await request('/api/filmnet',{url:'https://filmnet.ir/contents/a'});assert.equal(first.id,second.id);assert.equal((await request('/api/series')).items.length,1);
 await request(`/api/series/${first.id}/refresh`,{});
 for(let i=0;i<30;i++){const item=(await request('/api/series')).items[0];if(item.importState!=='RUNNING'){assert.equal(item.importState,'READY');return;}await new Promise(r=>setTimeout(r,10));}assert.fail('Refresh did not complete');
});
