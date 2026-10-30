const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path'),{create,mime}=require('../service/artwork-cache');
const jpeg=Buffer.from([255,216,255,224,0,0,1,2]);
test('artwork files survive service restart and avoid a second download',async t=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-artwork-'));t.after(()=>fs.rm(dir,{recursive:true,force:true}));let downloads=0;
 const first=create(dir,{fetch:async()=>{downloads++;return jpeg;}}),id=await first.ensure('https://example.com/a.jpg');
 const second=create(dir,{fetch:async()=>{throw Error('offline');}});assert.equal(await second.ensure('https://example.com/a.jpg'),id);assert.deepEqual((await second.read(id)).body,jpeg);assert.equal(downloads,1);await assert.rejects(second.read('../library.json'));
});
test('disk cache bounds storage and evicts older artwork',async t=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-artwork-limit-'));t.after(()=>fs.rm(dir,{recursive:true,force:true}));const cache=create(dir,{limit:jpeg.length,fetch:async()=>jpeg});
 const old=await cache.ensure('https://example.com/old.jpg');await fs.utimes(path.join(dir,old),new Date(0),new Date(0));const recent=await cache.ensure('https://example.com/new.jpg');await assert.rejects(cache.read(old));assert.equal((await cache.read(recent)).type,'image/jpeg');
});
test('invalid image responses are never persisted and concurrent requests share a download',async t=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'itv-artwork-safe-'));t.after(()=>fs.rm(dir,{recursive:true,force:true}));let calls=0;const cache=create(dir,{fetch:async()=>{calls++;return jpeg;}});
 const [a,b]=await Promise.all([cache.ensure('https://example.com/a.jpg'),cache.ensure('https://example.com/a.jpg')]);assert.equal(a,b);assert.equal(calls,1);assert.throws(()=>mime(Buffer.from('<html>bad</html>')));
 await assert.rejects(create(dir,{fetch:async()=>Buffer.from('<html>error</html>')}).ensure('https://example.com/bad.jpg'));assert.equal((await fs.readdir(dir)).length,1);
});
