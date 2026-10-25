const {test}=require('node:test'),assert=require('node:assert/strict');
const n=require('../service/namava'),{detect}=require('../service/import-source'),webdav=require('../service/webdav');
const page='https://www.namava.ir/series/105412-test';
const program={id:105412,caption:'سریال',type:'Series',seasons:[{seasonId:10,seasonOrderId:'۱'}],imageUrl:'/poster.jpg',casts:[{castName:'نام',castRole:'Actor'}]};
const episode={id:20,caption:'قسمت یک',episodeCaption:'قسمت ۱',imageUrl:'/episode.jpg',mediaDuration:50};
const fetch=async path=>({succeeded:true,result:path.endsWith('single-series')?program:[episode,{...episode,id:21,caption:'آنچه گذشت',episodeCaption:'۲۰ قسمت'},{...episode,id:22,publishInFuture:true}]});
test('Namava validates exact series links and automatic detection',()=>{
 assert.equal(detect(page).provider,'namava');
 for(const u of ['https://namava.ir.evil/series/1-a','http://namava.ir/series/1-a','https://u:p@namava.ir/series/1-a','https://namava.ir/api/1'])assert.throws(()=>n.url(u));
});
test('Namava keeps episode images, special labels and local playback state',async()=>{
 const meta=await n.preview(page,fetch);assert.equal(meta.episodes.length,2);assert.equal(meta.episodes[0].duration,3000);assert.equal(meta.episodes[0].imageUrl,'https://static.namava.ir/episode.jpg');assert.equal(meta.episodes[1].displayLabel,'آنچه گذشت');
 const item=n.merge(meta);item.episodes[0].mediaUrl='https://example.com/file.mp4';item.episodes[0].position=123;
 const updated=n.merge(meta,item);assert.equal(updated.episodes[0].position,123);assert.equal(updated.episodes[0].mediaUrl,item.episodes[0].mediaUrl);
 const saved=webdav.normalize({title:'سریال',isMovie:false,episodes:updated.episodes.map(e=>({...e,url:e.mediaUrl}))},updated);assert.equal(saved.episodes[1].displayNumber,'0');
});
test('Namava rejects incomplete seasons and duplicate episode numbers',async()=>{
 await assert.rejects(n.preview(page,async path=>path.endsWith('single-series')?{succeeded:true,result:program}:{succeeded:false}),/ناقص/);
 await assert.rejects(n.preview(page,async path=>({succeeded:true,result:path.endsWith('single-series')?program:[episode,{...episode,id:22}]})),/معتبر/);
});
