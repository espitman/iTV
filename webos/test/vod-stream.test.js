const {test}=require('node:test'),assert=require('node:assert/strict'),{resolve}=require('../service/catalog');
function mock(content,source){const calls=[];return {calls,fetch:async(url,...args)=>{calls.push({url,args});return url.includes('get-content')?{body:{content:[content]}}:{body:{source}};}};}
test('free VOD without legacy stream uses the public player endpoint and metadata token',async()=>{
 const m=mock({enable:true,content_status:'FREE',content_token:'public-content-token',stream:{telewebion:''}},[{type:'application/x-mpegURL',src:'https://vod-khayyam1210.telewebion.ir/show/master.m3u8'}]);
 const r=await resolve('product','0xc211a06',m.fetch);assert.equal(new URL(r.url).hostname,'vod-khayyam1210.telewebion.ir');assert.equal(m.calls.length,2);assert.deepEqual(m.calls[1].args,[false,0,{content:'public-content-token'}]);assert.ok(m.calls[1].url.endsWith('/stream/0xc211a06'));
});
test('legacy VOD keeps its existing direct stream',async()=>{
 const m=mock({stream:{telewebion:'https://vod.telewebion.net/master.m3u8'}});await resolve('product','0x1',m.fetch);assert.equal(m.calls.length,1);
});
test('public VOD fallback rejects paid, disabled, missing and foreign sources',async()=>{
 for(const content of [{content_status:'PAID',content_token:'x'},{content_status:'FREE'},{content_status:'FREE',content_token:'x',enable:false}]){
  const m=mock(content,[]);await assert.rejects(resolve('product','0x1',m.fetch));assert.equal(m.calls.length,1);
 }
 for(const source of [[],[{type:'application/x-mpegURL',src:'https://evil.com/master.m3u8'}]]){const m=mock({content_status:'FREE',content_token:'x'},source);await assert.rejects(resolve('product','0x1',m.fetch));}
});
