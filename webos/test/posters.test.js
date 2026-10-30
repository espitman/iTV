const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/app.js'),'utf8');
const helpers=source.slice(source.indexOf('const posterCache ='),source.indexOf('function card(s, episode)'));
test('large episode image is reduced to its display size and original source is released',()=>{
 let replaced,removed,draw;
 const canvas={setAttribute:()=>{},getContext:()=>({drawImage:(...args)=>draw=args})};
 const context=vm.createContext({document:{createElement:()=>canvas}});vm.runInContext(helpers,context);
 const img={isConnected:true,naturalWidth:6720,naturalHeight:3780,clientWidth:320,clientHeight:180,className:'loaded',replaceWith:c=>replaced=c,removeAttribute:a=>removed=a};
 context.compactPoster(img);assert.equal(replaced,canvas);assert.equal(canvas.width,320);assert.equal(canvas.height,180);assert.equal(removed,'src');assert.ok(draw);
});
test('shelf images wait for intersection and old observers disconnect on render',()=>{
 let callback,disconnected=0,unobserved=0;
 const img={dataset:{src:'https://example.com/a.jpg'},removeAttribute:()=>{}};
 const context=vm.createContext({root:{querySelectorAll:()=>[img]},IntersectionObserver:class{constructor(cb){callback=cb;}observe(){}unobserve(){unobserved++;}disconnect(){disconnected++;}}});vm.runInContext(helpers,context);
 context.loadPosters();assert.equal(img.src,undefined);callback([{target:img,isIntersecting:false}]);assert.equal(img.src,undefined);callback([{target:img,isIntersecting:true}]);assert.equal(img.src,img.dataset.src);assert.equal(unobserved,1);context.loadPosters();assert.equal(disconnected,1);
});

test('season render reuses cached thumbnail pixels without requesting its source again',()=>{
 let loaded=0,replaced=0,draws=0;
 const document={createElement:()=>({setAttribute:()=>{},getContext:()=>({drawImage:()=>draws++})})};
 const image=()=>({dataset:{src:'https://example.com/episode.jpg'},currentSrc:'https://example.com/episode.jpg',clientWidth:320,clientHeight:180,naturalWidth:6720,naturalHeight:3780,isConnected:true,replaceWith:()=>replaced++,removeAttribute:()=>{},set src(v){loaded++;}});
 const first=image(),second=image(),context=vm.createContext({document,root:{querySelectorAll:()=>[second]}});vm.runInContext(helpers,context);
 context.compactPoster(first);context.loadPosters();assert.equal(replaced,2);assert.equal(loaded,0);assert.equal(draws,3);
 assert.ok(vm.runInContext('posterCacheBytes <= posterCacheLimit',context));
});
test('thumbnail cache evicts least recently used entries to bound memory',()=>{
 const document={createElement:()=>({setAttribute:()=>{},getContext:()=>({drawImage:()=>{}})})};
 const context=vm.createContext({document});vm.runInContext(helpers,context);
 for(let i=0;i<30;i++)context.compactPoster({src:'https://example.com/'+i,clientWidth:1024,clientHeight:1024,naturalWidth:2048,naturalHeight:2048,isConnected:true,replaceWith:()=>{},removeAttribute:()=>{}});
 assert.equal(vm.runInContext('posterCache.size',context),6);assert.ok(vm.runInContext('posterCacheBytes <= posterCacheLimit',context));assert.equal(vm.runInContext('posterCache.has("https://example.com/0|1024x1024")',context),false);
});
