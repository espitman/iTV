const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises'), os = require('node:os'), path = require('node:path'), http = require('node:http');
const {createServer} = require('../service/server');
const dav = require('../service/webdav');
test('manual validation rejects duplicate numbering and non-HTTP media URLs', () => {
  const e={seasonNumber:1,displayNumber:1,url:'http://192.168.1.2/video.mp4'};
  assert.throws(()=>dav.normalize({title:'x',isMovie:false,episodes:[e,e]}));
  for(const url of ['file:///tmp/a','ftp://phone/a','http://u:p@phone/a','http://phone/a.m3u8'])
    assert.throws(()=>dav.mediaUrl(url));
  assert.equal(dav.mediaUrl('http://phone:8080/نام فایل.mp4').includes('%'),true);
});
test('manual library: auth, secret redaction, range streaming, edit preserves progress, delete and restart',async t=>{
  const bytes=Buffer.from('0123456789abcdef');
  let lastRange, auth;
  const phone=http.createServer((req,res)=>{
    auth=req.headers.authorization;lastRange=req.headers.range;
    if(auth!=='Basic '+Buffer.from('phone:secret').toString('base64')) {res.writeHead(401);res.end();return;}
    if(req.url==='/missing.mp4'){res.writeHead(404);res.end();return;}
    if(req.url==='/redirect.mp4'){res.writeHead(302,{Location:'http://example.com/file.mp4'});res.end();return;}
    const match=req.headers.range?.match(/^bytes=(\d+)-(\d*)$/);
    const start=match?Number(match[1]):0,end=match&&match[2]?Number(match[2]):bytes.length-1;
    res.writeHead(match?206:200,{'Content-Type':'video/mp4','Accept-Ranges':'bytes','Content-Length':end-start+1,...(match?{'Content-Range':`bytes ${start}-${end}/${bytes.length}`}:{})});
    res.end(bytes.subarray(start,end+1));
  });
  await new Promise(r=>phone.listen(0,'127.0.0.1',r));
  const file=`http://127.0.0.1:${phone.address().port}/video.mp4`;
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-dav-test-'));
  let runtime=createServer({dataDir:dir,host:'127.0.0.1',port:0});
  let session=await runtime.start(), base=`http://127.0.0.1:${session.port}`;
  t.after(async()=>{await new Promise(r=>runtime.server.close(r));await new Promise(r=>phone.close(r));await fs.rm(dir,{recursive:true,force:true});});
  const request=async(url,body,method='POST',token=session.tvToken)=>{
    const r=await fetch(base+url,{method,headers:{'Content-Type':'application/json','X-ITV-Token':token},body:body===undefined?undefined:JSON.stringify(body)});
    return {status:r.status,data:await r.json()};
  };
  const body={title:'سریال شخصی',description:'توضیحات',isMovie:false,username:'phone',password:'secret',episodes:[{seasonNumber:1,displayNumber:1,url:file},{seasonNumber:2,displayNumber:1,url:file}]};
  assert.equal((await request('/api/manual',body,'POST','invalid')).status,401);
  const created=await request('/api/manual',body);assert.equal(created.status,200);
  const id=created.data.id;
  let list=(await request('/api/series',undefined,'GET')).data;
  assert.equal(JSON.stringify(list).includes('secret'),false);
  let item=list.items[0];assert.equal(item.title,body.title);assert.equal(item.seasonCount,2);assert.equal(item.credentialsConfigured,true);
  assert.equal(item.webdav,undefined);
  const episodeId=item.episodes[0].sourceEpisodeId;
  const checked=await request('/api/webdav/check',{id,url:file});assert.equal(checked.data.rangeSupported,true);
  assert.equal((await request('/api/webdav/check',{id,url:file.replace('video.mp4','missing.mp4')})).status,400);
  assert.equal((await request('/api/webdav/check',{id,url:file,password:'wrong'})).status,400);
  const resolved=await request(`/api/series/${id}/resolve`,{episodeId});assert.equal(resolved.data.type,'file');
  assert.equal(resolved.data.url.includes('secret'),false);
  const played=await fetch(resolved.data.url,{headers:{Range:'bytes=3-7'}});
  assert.equal(played.status,206);assert.equal(await played.text(),'34567');assert.equal(lastRange,'bytes=3-7');
  assert.equal(auth,'Basic '+Buffer.from('phone:secret').toString('base64'));
  assert.equal((await fetch(resolved.data.url.replace(/ticket=.*/,'ticket=bad'))).status,401);
  await request(`/api/series/${id}/progress`,{episodeId,position:35,duration:1000});
  const edited={...body,title:'نام جدید',username:undefined,password:'',episodes:item.episodes.map(e=>({...e,url:file}))};
  assert.equal((await request('/api/manual/'+id,edited,'PATCH')).status,200);
  item=(await request('/api/series',undefined,'GET')).data.items[0];
  assert.equal(item.episodes[0].position,35);assert.equal(item.episodes[0].sourceEpisodeId,episodeId);
  assert.equal((await request(`/api/series/${id}/resolve`,{episodeId})).status,200);
  await new Promise(r=>runtime.server.close(r));
  runtime=createServer({dataDir:dir,host:'127.0.0.1',port:0});session=await runtime.start();base=`http://127.0.0.1:${session.port}`;
  assert.equal((await request('/api/series',undefined,'GET')).data.items[0].title,'نام جدید');
  assert.equal((await request('/api/series/'+id,undefined,'DELETE')).status,200);
  assert.equal((await request('/api/series',undefined,'GET')).data.items.length,0);
  await assert.rejects(dav.open(file.replace('video.mp4','redirect.mp4'),{username:'phone',password:'secret'}));
});
