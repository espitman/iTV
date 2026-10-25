const {test}=require('node:test');
const assert=require('node:assert/strict');
const {preview,merge,url}=require('../service/filimo');
const {normalize}=require('../service/webdav');
test('Filimo URL accepts title pages only',()=>{
 assert.equal(url('https://filimo.com/n/abc?x=1'),'https://www.filimo.com/n/abc');
 assert.equal(url('https://www.filimo.com/m/h78m0'),'https://www.filimo.com/n/h78m0');
 for(const u of ['http://filimo.com/n/a','https://filimo.com.evil/n/a','https://x@filimo.com/n/a','https://filimo.com/api/foo'])assert.throws(()=>url(u));
});
test('Filimo imports all seasons and pages, excludes upcoming, refresh preserves local files and watch state',async()=>{
 const fetch=async u=>{
  if(u.includes('/one/'))return {data:{attributes:{General:{uid:'abc',id:10,title:'نام قسمت',serial:{enable:true,parent_id:'9',title:'سریال'},thumbnails:{movie_img_b:'poster'},descr:'خلاصه'}}}};
  if(u.includes('allseason'))return {data:{attributes:{data:[{episodes_link:'season1'},{episodes_link:'season2'}]}}};
  if(u.includes('moviedetail'))return {data:{attributes:{crewData:[{name_fa:'بازیگر',profile_image:'photo'}]}}};
  const e=(uid,n,soon=false)=>({type:'movies',attributes:{uid,thumbplay:uid==='a'?{thumbplay_img_b:'https://example.com/episode-a.jpg'}:undefined,serial:{season_id:u==='season2'?2:1,serial_part:n},watch_action:{type:soon?'commingsoon':'login'}}});
  return u==='season1'?{included:[e('a',1),e('future',3,true)],links:{paging:'page2'}}:u==='page2'?{included:[e('b',2)]}:{included:[e('c',1)]};
 };
 const meta=await preview('https://filimo.com/n/abc',fetch);
 assert.deepEqual(meta.episodes.map(e=>e.metadataEpisodeId),['a','b','c']);assert.equal(meta.credits[0].name,'بازیگر');assert.equal(meta.episodes[0].imageUrl,'https://example.com/episode-a.jpg');assert.equal(meta.episodes[1].imageUrl,null);
 const old=merge(meta);Object.assign(old.episodes[0],{mediaUrl:'http://phone/a.mp4',position:120,watched:true});old.customPosterUrl='custom';old.webdav={username:'u',password:'p'};
 const next=merge({...meta,episodes:[...meta.episodes,{metadataEpisodeId:'new',seasonNumber:2,displayNumber:'2'}]},old);
 assert.equal(next.episodes[0].sourceEpisodeId,old.episodes[0].sourceEpisodeId);assert.equal(next.episodes[0].mediaUrl,'http://phone/a.mp4');assert.equal(next.episodes[0].position,120);assert.equal(next.episodes[0].watched,true);assert.equal(next.customPosterUrl,'custom');assert.equal(next.webdav.password,'p');assert.equal(next.episodes[3].mediaUrl,'');
 const saved=normalize({title:'سریال',isMovie:false,episodes:next.episodes},next);assert.equal(saved.episodes[3].mediaUrl,'');assert.equal(saved.episodes[0].metadataEpisodeId,'a');
 assert.throws(()=>normalize({title:'دستی',isMovie:false,episodes:[{seasonNumber:1,displayNumber:1}]}));
});
test('Filimo failed or cyclic pagination never returns a partial import',async()=>{
 const fetch=async u=>u.includes('/one/')?{data:{attributes:{General:{uid:'a',title:'a',serial:{enable:true}}}}}:u.includes('allseason')?{data:{attributes:{data:[{episodes_link:'loop'}]}}}:{included:[],links:{paging:'loop'}};
 await assert.rejects(preview('https://filimo.com/n/a',fetch),/صفحه‌بندی/);
});
