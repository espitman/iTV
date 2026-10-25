const {test}=require('node:test'),assert=require('node:assert/strict');
const sheyda=require('../service/sheyda'),dav=require('../service/webdav'),{detect}=require('../service/import-source');
const response=(name,data)=>({data:{[name]:{status:{statusCode:200},data}}});
const ep=(id,title,order,seasonID='s1')=>({id,title,order,seasonID,status:'ENABLED',releaseStatus:'RELEASED',duration:123,landscapeImagePath:'https://static-s.sheyda.com/'+id+'.jpg'});
const query=async(name,vars)=>name==='getProgramByUid'?response(name,{program:{id:'p',title:'بامداد خمار',isSolitary:false,portraitImagePath:'https://static-s.sheyda.com/poster.jpg'},seasons:[{id:'s2',title:'فصل دوم'},{id:'s1',title:'فصل اول'}],cast:[{name:'بازیگر',artists:[{fullName:'نام',imagePath:'https://static-s.sheyda.com/actor.jpg'}]}]}):response(name,vars.seasonID==='s1'?[ep('a','قسمت ۱',2),ep('b','قسمت ۲',3),{...ep('future','قسمت ۳',4),releaseStatus:'COMING_SOON'}]:[ep('special','قسمت اسپشیال',1,'s2'),ep('c','قسمت ۱',2,'s2')]);
test('Sheyda uses validated program links and automatic provider detection',()=>{
 assert.equal(detect('https://sheyda.com/p/abc').provider,'sheyda');assert.equal(sheyda.url('https://sheyda.com/p/abc?x=1'),'https://www.sheyda.com/p/abc');
 for(const u of ['http://sheyda.com/p/a','https://sheyda.com.evil/p/a','https://u@sheyda.com/p/a','https://sheyda.com/e/a'])assert.throws(()=>sheyda.url(u));
});
test('Sheyda imports both seasons, keeps true episode numbers and distinguishes specials',async()=>{
 const m=await sheyda.preview('https://sheyda.com/p/abc',query);assert.equal(m.episodes.length,4);assert.deepEqual(m.episodes.map(e=>[e.seasonNumber,e.displayNumber]),[[1,'1'],[1,'2'],[2,'1'],[2,'0']]);assert.equal(m.episodes.at(-1).displayLabel,'قسمت اسپشیال');assert.equal(m.episodes[0].imageUrl,'https://static-s.sheyda.com/a.jpg');assert.equal(m.credits[0].name,'نام');
 const old=sheyda.merge(m);Object.assign(old.episodes[0],{mediaUrl:'http://phone/a.mp4',position:65,watched:true});old.webdav={username:'u',password:'p'};const fresh=sheyda.merge(m,old);assert.equal(fresh.episodes[0].mediaUrl,'http://phone/a.mp4');assert.equal(fresh.episodes[0].position,65);assert.equal(fresh.episodes[0].sourceEpisodeId,old.episodes[0].sourceEpisodeId);assert.equal(fresh.webdav.password,'p');
 const edited=dav.normalize({title:'عنوان',isMovie:false,episodes:fresh.episodes},fresh);assert.equal(edited.episodes.at(-1).displayLabel,'قسمت اسپشیال');assert.equal(edited.episodes.at(-1).displayNumber,'0');
 assert.throws(()=>dav.normalize({title:'دستی',isMovie:false,episodes:[{seasonNumber:1,displayNumber:0,url:'http://phone/a.mp4'}]}));
});
test('Sheyda does not return partial metadata when a season fails',async()=>{
 await assert.rejects(sheyda.preview('https://sheyda.com/p/a',async(name,v)=>name==='getProgramByUid'?query(name,v):({errors:[{message:'failed'}]})),/ناقص/);
});
test('Sheyda movies use the released solitary episode',async()=>{
 const m=await sheyda.preview('https://sheyda.com/p/a',async name=>response(name,{program:{id:'movie',title:'فیلم',isSolitary:true},solitaryEpisode:ep('movie-ep','فیلم',1)}));assert.equal(m.isMovie,true);assert.equal(m.episodes[0].displayNumber,'1');
});

test('single named season uses season one when Sheyda supplies no season number',async()=>{
 const fetch=async(name,v)=>name==='getProgramByUid'?response(name,{program:{id:'taxi',title:'تاکسی',isSolitary:false},seasons:[{id:'taxi-season',title:'تاکسی '}]}):response(name,[ep('taxi-1','قسمت ۱',1,'taxi-season')]);
 const m=await sheyda.preview('https://www.sheyda.com/p/27ff1af0bfb',fetch);assert.equal(m.episodes[0].seasonNumber,1);assert.equal(m.episodes[0].displayNumber,'1');
});
test('multiple unnamed seasons are rejected instead of guessing their order',async()=>{
 await assert.rejects(sheyda.preview('https://sheyda.com/p/a',async name=>response(name,{program:{id:'p',title:'عنوان',isSolitary:false},seasons:[{id:'a',title:'بخش الف'},{id:'b',title:'بخش ب'}]})),/شمارهٔ فصل/);
});
