const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/app.js'),'utf8');
const spatial=source.slice(source.indexOf('function focusSpatial(target) {'),source.indexOf('function commitTimelineSeek'));
function harness(){
 const document={},nodes=[];
 const context=vm.createContext({state:{route:"detail"},revealHomeRow:()=>{},document,modal:{open:false},root:{querySelectorAll:()=>nodes}});vm.runInContext(spatial,context);
 function node(x,y,row,width=100,height=100){const n={disabled:false,getClientRects:()=>[{}],getBoundingClientRect:()=>({x,y,width,height}),closest:()=>row,focus:()=>{document.activeElement=n;},scrollIntoView:()=>{}};nodes.push(n);return n;}
 return {node,move:(n,key)=>{document.activeElement=n;context.spatial(key);return document.activeElement;}};
}
test('horizontal navigation clamps both shelf edges while vertical navigation reaches other rows',()=>{
 const h=harness(),episodes={},credits={},a=h.node(300,100,episodes),b=h.node(100,100,episodes),c=h.node(450,250,credits),d=h.node(-50,250,credits);
 assert.equal(h.move(a,'ArrowLeft'),b);assert.equal(h.move(b,'ArrowRight'),a);
 assert.equal(h.move(a,'ArrowRight'),a);assert.equal(h.move(b,'ArrowLeft'),b);
 assert.equal(h.move(c,'ArrowRight'),c);assert.equal(h.move(d,'ArrowLeft'),d);
 assert.ok([c,d].includes(h.move(a,'ArrowDown')));
});
test('grid navigation stays on its visual row instead of wrapping to another row',()=>{
 const h=harness(),grid={},a=h.node(100,100,grid),b=h.node(300,100,grid),c=h.node(0,300,grid),d=h.node(450,300,grid);
 assert.equal(h.move(a,'ArrowLeft'),a);assert.equal(h.move(b,'ArrowRight'),b);
 assert.equal(h.move(a,'ArrowRight'),b);assert.ok([c,d].includes(h.move(a,'ArrowDown')));
});

test('home shelves snap to their heading while the hero and pinned shelves stay fixed',()=>{
 const helper=source.slice(source.indexOf('function revealHomeRow(target)'),source.indexOf('root.addEventListener("focusin"'));
 const calls=[],library={style:{},clientHeight:588,scrollTo:o=>calls.push(o.top)},section={offsetTop:352,offsetHeight:352};
 const state={route:'home'},context=vm.createContext({state});vm.runInContext(helper,context);
 context.revealHomeRow({closest:s=>s==='.home-library'?library:section});
 assert.deepEqual(calls,[352]);assert.equal(library.style.paddingBottom,'236px');
 section.offsetTop=0;context.revealHomeRow({closest:s=>s==='.home-library'?library:section});assert.deepEqual(calls,[352,0]);
 context.revealHomeRow({closest:()=>null});assert.equal(calls.length,2);
 state.route='detail';context.revealHomeRow({closest:()=>{throw Error('not home');}});
});

test('up from movies selects the clipped series shelf instead of hero controls',()=>{
 const document={},series={},movies={},nodes=[];
 const make=(x,y,row)=>{const n={getClientRects:()=>[{}],getBoundingClientRect:()=>({x,y,width:100,height:100}),closest:s=>s==='.home-library'?null:row,focus:()=>document.activeElement=n,scrollIntoView:()=>{}};nodes.push(n);return n;};
 const episode=make(100,-100,series),movie=make(100,500,movies),hero=make(100,350,null);
 const context=vm.createContext({state:{route:'home'},document,modal:{open:false},revealHomeRow:()=>{},root:{querySelectorAll:s=>s==='.home-library .shelf'?[series,movies]:nodes}});vm.runInContext(spatial,context);
 document.activeElement=movie;context.spatial('ArrowUp');assert.equal(document.activeElement,episode);
 document.activeElement=episode;context.spatial('ArrowDown');assert.equal(document.activeElement,movie);
});

test('home focus prevents instant browser scrolling and delegates vertical motion to smooth snap',()=>{
 const calls=[],shelf={getBoundingClientRect:()=>({left:0,right:500}),scrollBy:()=>{}},library={};
 const target={closest:s=>s==='.home-library'?library:shelf,getBoundingClientRect:()=>({left:100,right:200}),focus:options=>calls.push(options.preventScroll),scrollIntoView:()=>{throw Error('instant scroll');}};
 const context=vm.createContext({state:{route:'home'},revealHomeRow:()=>calls.push('smooth')});
 vm.runInContext(source.slice(source.indexOf('function focusSpatial(target)'),source.indexOf('function spatial(key)')),context);
 context.focusSpatial(target);assert.deepEqual(calls,[true,'smooth']);
});
