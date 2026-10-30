"use strict";
const sheyda=require('./sheyda'),namava=require('./namava');
const filimo=require('./filimo'),filmnet=require('./filmnet'),catalog=require('./catalog');
function detect(value) {
  let u;try{u=new URL(String(value||'').trim());}catch{throw Error('لینک صفحهٔ فیلم یا سریال را وارد کنید');}
  if(['filimo.com','www.filimo.com'].includes(u.hostname))return {provider:'filimo',url:filimo.url(u.href)};
  if(['filmnet.ir','www.filmnet.ir'].includes(u.hostname))return {provider:'filmnet',url:filmnet.url(u.href)};
  if(['sheyda.com','www.sheyda.com'].includes(u.hostname))return {provider:'sheyda',url:sheyda.url(u.href)};
  if(['namava.ir','www.namava.ir'].includes(u.hostname))return {provider:'namava',url:namava.url(u.href)};
  if(['telewebion.net','www.telewebion.net','telewebion.ir','www.telewebion.ir'].includes(u.hostname))return {provider:'telewebion',url:catalog.source(u.href).pageUrl};
  throw Error('لینک فیلیمو، فیلم‌نت، شیدا، نماوا یا تلوبیون را وارد کنید');
}
module.exports={detect};
