const {test}=require('node:test'),assert=require('node:assert/strict');
const {detect}=require('../service/import-source');
test('import detects and validates each provider by exact hostname',()=>{
 assert.equal(detect(' https://filimo.com/m/abc ').provider,'filimo');
 assert.equal(detect('https://www.filmnet.ir/contents/abc/Title').provider,'filmnet');
 assert.equal(detect('https://telewebion.net/product/0xabc').provider,'telewebion');
 for(const u of ['https://filimo.com.evil/n/abc','https://evil.com/filmnet.ir/contents/a','http://filmnet.ir/contents/a','https://u:p@filimo.com/n/a','https://filmnet.ir/api/a','not a url'])assert.throws(()=>detect(u));
});
test('one import and preview API routes all three providers; unknown links leave library unchanged',async t=>{
 const fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os');
 const filimo=require('../service/filimo'),filmnet=require('../service/filmnet');
 const originals=[filimo.preview,filmnet.preview];
 for(const [name,p] of [['filimo',filimo],['filmnet',filmnet]])p.preview=async url=>({metadataProvider:name,metadataUrl:url,metadataSourceId:name+'-title',sourceTitle:name,isMovie:false,automaticPosterUrl:'https://example.com/poster.jpg',episodes:[{metadataEpisodeId:name+'-episode',seasonNumber:1,displayNumber:'1'}]});
 const catalogApi={preview:async url=>({kind:'product',sourceId:'0xabc',pageUrl:url,title:'telewebion',isMovie:false,seasons:[1]}),episodes:async()=>[{sourceEpisodeId:'0x1',seasonNumber:1}]};
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-auto-source-'));const runtime=require('../service/server').createServer({dataDir:dir,host:'127.0.0.1',port:0,catalogApi});const state=await runtime.start();
 t.after(async()=>{[filimo.preview,filmnet.preview]=originals;await new Promise(r=>runtime.server.close(r));await fs.rm(dir,{recursive:true,force:true});});
 const post=async(p,url)=>{const r=await fetch(`http://127.0.0.1:${state.port}${p}`,{method:'POST',headers:{'Content-Type':'application/json','x-itv-token':state.tvToken},body:JSON.stringify({url})});return {status:r.status,body:await r.json()};};
 for(const [name,url] of [['filimo','https://filimo.com/n/abc'],['filmnet','https://filmnet.ir/contents/abc'],['telewebion','https://telewebion.net/product/0xabc']]){
  const preview=await post('/api/preview',url);assert.equal(preview.status,200);assert.equal(preview.body.title,name);
  const first=await post('/api/series',url);assert.equal(first.status,200);const next=await post('/api/series',url);assert.equal(next.body.id,first.body.id);
 }
 assert.equal((await post('/api/series','https://example.com/movie')).status,400);
 const rows=await fetch(`http://127.0.0.1:${state.port}/api/series`,{headers:{'x-itv-token':state.tvToken}}).then(r=>r.json());assert.equal(rows.items.length,3);
});
