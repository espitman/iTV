const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/app.js'),'utf8');
const spatial=source.slice(source.indexOf('function spatial(key) {'),source.indexOf('function commitTimelineSeek'));
function harness(){
 const document={},nodes=[];
 const context=vm.createContext({document,modal:{open:false},root:{querySelectorAll:()=>nodes}});vm.runInContext(spatial,context);
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
