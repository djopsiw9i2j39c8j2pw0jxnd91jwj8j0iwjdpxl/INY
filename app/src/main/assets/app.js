'use strict';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={menu:'M4 6h16M4 12h16M4 18h16',home:'m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',search:'m21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',plus:'M12 5v14M5 12h14',close:'m6 6 12 12M6 18 18 6',back:'m14 5-7 7 7 7M7 12h14',short:'m15 3-9 5a4 4 0 0 0 0 7l2 1-3 2a2 2 0 0 0 2 3l11-6a4 4 0 0 0 0-7l-2-1 2-1a2 2 0 0 0-3-3Zm-5 6 5 3-5 3Z',subscriptions:'M5 3h14M3 7h18M3 11h18v10H3ZM10 14l5 2-5 2Z',library:'M4 3v17h17M8 3h13v13H8Zm4 3 5 3-5 3Z',history:'M3 10a9 9 0 1 1 1 7M3 4v6h6M12 7v5l3 2',bookmark:'M6 3h12v18l-6-4-6 4Z',list:'M4 5h16M4 10h16M4 15h9M4 20h9m4-6 5 3-5 3Z',like:'M8 10V21H3V10ZM8 10l5-8c3 0 2 5 1 7h5a2 2 0 0 1 2 2l-2 9a2 2 0 0 1-2 1H8',download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',settings:'m9 3-1 3-3 1-2 3 2 2v3l3 1 1 3h5l1-3 3-1v-3l2-2-2-3-3-1-1-3Zm6 9a3 3 0 1 1-6 0 3 3 0 0 1 6 0',external:'M14 3h7v7M10 14 21 3M11 3H3v18h18v-8',share:'m14 4 7 6-7 6v-4C8 12 5 14 3 20c0-10 4-14 11-14Z',copy:'M9 9h12v12H9ZM5 15H3V3h12v2',check:'m4 12 5 5L20 6',user:'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21v-3a8 8 0 0 1 16 0v3',comment:'M21 3H3v14h5l4 4 4-4h5Z',refresh:'M20 8a8 8 0 1 0 0 9M20 3v5h-5',send:'M22 2 11 13M22 2l-7 20-4-9-9-4Z',sun:'M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8M12 1v3M12 20v3M1 12h3M20 12h3M4 4l2 2M18 18l2 2M4 20l2-2M18 6l2-2'};
const icon=n=>`<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[n]||paths.external}"></path></svg>`;
function hydrate(root=document){root.querySelectorAll('[data-icon]').forEach(e=>e.outerHTML=icon(e.dataset.icon));}
const pending=new Map();let requestId=0;
function native(op,args={}){
  return new Promise((resolve,reject)=>{
    if(!window.Native?.postMessage){reject(Error('Bản xem trước: chức năng này chạy trong ứng dụng Android.'));return;}
    const id=String(++requestId),timer=setTimeout(()=>{pending.delete(id);reject(Error('Hết thời gian chờ. Kiểm tra kết nối và thử lại.'));},op==='upload'?3600000:(op==='authorize'||op==='webLogin')?300000:90000);
    pending.set(id,{resolve,reject,timer});Native.postMessage(JSON.stringify({id,op,args}));
  });
}
if(window.Native)Native.onmessage=e=>{let r;try{r=JSON.parse(e.data);}catch{return;}const p=pending.get(r.id);if(!p)return;clearTimeout(p.timer);pending.delete(r.id);r.error?p.reject(Error(r.error)):p.resolve(r.data);};
function readStore(key,def=[]){try{return JSON.parse(localStorage.getItem('iny_'+key))??def;}catch{return def;}}
function writeStore(key,data){localStorage.setItem('iny_'+key,JSON.stringify(data));}
let shortFeed=null, shortLoading=false, watchQualities=[], activePanel='', commentRun=0, suggestionRun=0, suggestionTimer;
let config={},route={type:'home'},epoch=0,watchEpoch=0,currentVideo=null,commentToken='',commentOrder='relevance',myChannel=null,subscriptionId=null,toastTimer;
const videos=new Map();
const count=n=>new Intl.NumberFormat('vi',{notation:'compact',maximumFractionDigits:1}).format(Number(n)||0);
const age=s=>{const d=Math.max(0,(Date.now()-new Date(s).getTime())/86400000);return !Number.isFinite(d)?'':d<1?'Hôm nay':d<30?`${Math.floor(d)} ngày trước`:d<365?`${Math.floor(d/30)} tháng trước`:`${Math.floor(d/365)} năm trước`;};
function displayDate(value=''){const n=new Date(value);if(/^\d{4}-/.test(value)&&Number.isFinite(+n))return new Intl.DateTimeFormat('vi-VN').format(n);return value.replace(/(\d+) (second|minute|hour|day|week|month|year)s? ago/g,(_,num,unit)=>`${num} ${{second:'giây',minute:'phút',hour:'giờ',day:'ngày',week:'tuần',month:'tháng',year:'năm'}[unit]} trước`);}
function duration(s){const m=(s||'').match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/);if(!m)return '';const h=Number(m[1]||0),min=Number(m[2]||0),sec=Number(m[3]||0);return h?`${h}:${String(min).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${min}:${String(sec).padStart(2,'0')}`;}
function fmtSec(t){t=Number(t)||0;if(t<=0)return '';const h=Math.floor(t/3600),m=Math.floor(t%3600/60),sec=t%60;return h?`${h}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${m}:${String(sec).padStart(2,'0')}`;}
const initial=s=>esc(String(s||'?').trim().slice(0,1).toUpperCase());
function avatar(url,name,cls='channel-dot'){return `<span class="${cls}">${initial(name)}${url?`<img src="${esc(url)}" alt="">`:''}</span>`;}
document.addEventListener('error',e=>{if(e.target.tagName==='IMG'&&e.target.closest('.channel-dot,.avatar'))e.target.remove();},true);
const seconds=t=>t.split(':').reduce((a,p)=>a*60+Number(p),0);
const richRe=/(https?:\/\/[^\s<]+[^\s<.,;:!?)\]'"])|(?<![\w:])((?:\d{1,2}:)?\d{1,2}:\d{2})(?![\w:])|(?<![^\s(])(#[\p{L}\p{N}_]{2,})/gu;
function rich(text){return esc(text).replace(richRe,(m,url,ts,tag)=>url?`<button class="lk" data-action="openLink" data-url="${url}">${url}</button>`:ts?`<button class="lk ts" data-action="seek" data-t="${seconds(ts)}">${ts}</button>`:`<button class="lk" data-action="hashtag" data-q="${tag}">${tag}</button>`);}
function videoNp(i){const v={id:i.id,title:i.title||'Video',channel:i.channel||'',channelId:i.channelId||'',avatar:i.avatar||'',subscribers:i.subscribers,thumb:i.thumb||'',published:i.publishedText||'',publishedText:displayDate(i.publishedText),durationSec:i.duration,duration:fmtSec(i.duration),views:i.views,likes:i.likes,description:i.description||'',live:!!i.live,short:!!i.short};videos.set(v.id,v);return v;}
function videoFrom(item){if(item.kind==='np')return videoNp(item);const s=item.snippet||{},id=typeof item.id==='object'?item.id.videoId:(s.resourceId?.videoId||item.contentDetails?.videoId||item.id);if(!/^[\w-]{11}$/.test(id||''))return null;const v={id,title:s.title||'Video',channel:s.channelTitle||s.videoOwnerChannelTitle||'',channelId:s.channelId||s.videoOwnerChannelId||'',thumb:s.thumbnails?.medium?.url||s.thumbnails?.high?.url||'',published:s.publishedAt||'',duration:duration(item.contentDetails?.duration),views:item.statistics?.viewCount,likes:item.statistics?.likeCount,description:s.description||'',live:s.liveBroadcastContent==='live'};videos.set(id,v);return v;}
function toast(message){$('#toast').textContent=message;$('#toast').classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('show'),4200);}
function modal(title,body){$('#dialogTitle').textContent=title;$('#dialogBody').innerHTML=body;if(!$('#dialog').open)$('#dialog').showModal();hydrate($('#dialog'));placePlayer();}
function closeModal(){$('#dialog').close();placePlayer();}
const isShort=()=>$('#watch').classList.contains('short-watch');
function placeSheet(){const sheet=$('#sheet');if(sheet.hidden)return;sheet.style.top=(isShort()?Math.round(innerHeight*.4):Math.ceil($('#playerBox').getBoundingClientRect().bottom))+'px';}
function openSheet(panel,title,body,foot=''){commentRun++;activePanel=panel;$('#sheetTitle').textContent=title;$('#sheetBody').innerHTML=body;$('#sheetFoot').innerHTML=foot;$('#sheet').hidden=false;$('#sheetBody').scrollTop=0;placePlayer();}
function closeSheet(){if(!$('#sheet').hidden){$('#sheet').hidden=true;activePanel='';commentRun++;}placePlayer();}
function btn(action,label,ico='',extra='',cls='secondary'){return `<button class="${cls}" data-action="${esc(action)}" ${extra}>${ico?icon(ico):''}${esc(label)}</button>`;}
function head(title,subtitle='',extra=''){return title||subtitle||extra?`<div class="page-head"><div>${title?`<h1>${esc(title)}</h1>`:''}${subtitle?`<p class="muted small">${esc(subtitle)}</p>`:''}</div>${extra}</div>`:'';}
function empty(title,message,action='',label=''){return `<div class="empty">${icon('library')}<h2>${esc(title)}</h2><p>${esc(message)}</p>${action?btn(action,label,'settings','','primary'):''}</div>`;}
function errorBlock(e){const auth=e.message.includes('AUTH_REQUIRED');return empty(auth?'Cần quyền truy cập Google':'Không tải được nội dung',e.message.replace(/^AUTH_REQUIRED:\s*/,''),auth?'login':'reload',auth?'Cấp quyền Google':'Thử lại');}
function skeleton(){return '<div class="grid section-pad">'+Array(6).fill('<div class="skeleton"></div>').join('')+'</div>';}
function setActive(type){document.querySelectorAll('aside>button,.bottom button').forEach(b=>b.classList.toggle('active',b.dataset.action===type||(type==='search'&&b.dataset.action==='home')));}
function setChips(type){const list=[['home','Tất cả'],['topic','Âm nhạc','âm nhạc'],['topic','Trò chơi','gaming'],['topic','Tin tức','tin tức'],['topic','Công nghệ','công nghệ']];$('#chips').innerHTML=['home','topic'].includes(type)?list.map(([act,label,q])=>`<button class="chip ${act===type&&(!q||route.q===q)?'active':''}" data-action="${act}" ${q?`data-q="${q}"`:''}>${label}</button>`).join(''):'';}
function card(v){return `<button class="card" data-action="${v.short?'watchShort':'watch'}" data-id="${esc(v.id)}"><div class="thumb"><img src="${esc(v.thumb)}" alt="${esc(v.title)}" loading="lazy" referrerpolicy="strict-origin-when-cross-origin">${v.duration||v.live?`<span class="duration">${v.live?'TRỰC TIẾP':esc(v.duration)}</span>`:''}</div><div class="v-info">${avatar(v.avatar,v.channel||'I')}<div class="v-copy"><h3>${esc(v.title)}</h3><p>${esc(v.channel)}</p><p>${v.views?`${count(v.views)} lượt xem · `:''}${esc(v.publishedText||age(v.published))}</p></div></div></button>`;}
function moreButton(token,action='more',extra=''){return token?`<div class="load-more">${btn(action,'Xem thêm','plus',`${extra} data-token="${esc(token)}"`)}</div>`:'';}
function showCards(items,token,{append=false,compact=false,shorts=false}={}){
 const html=items.map(i=>i.kind==='npChannel'?channelCard(i):cardOrEmpty(i,shorts)).join('');
 if(append){$('#videoGrid')?.insertAdjacentHTML('beforeend',html);$('#moreBox')?.remove();$('#content').insertAdjacentHTML('beforeend',`<div id="moreBox">${moreButton(token)}</div>`);}
 else $('#content').insertAdjacentHTML('beforeend',html||token?`<div id="videoGrid" class="grid ${compact?'compact':''} ${shorts?'shorts-grid':''}">${html}</div><div id="moreBox">${moreButton(token)}</div>`:empty('Không có kết quả','Thử từ khóa khác hoặc quay lại sau.','',''));
}
function shortCard(v){return `<button class="short-card" data-action="watchShort" data-id="${esc(v.id)}"><img src="${esc(v.thumb)}" alt="" loading="lazy" referrerpolicy="strict-origin-when-cross-origin"><span class="sc-title">${esc(v.title)}</span></button>`;}
function cardOrEmpty(item,shorts=false){const v=videoFrom(item);return v?(shorts?shortCard(v):card(v)):'';}
function channelCard(c){return `<button class="card channel-result" data-action="channel" data-id="${esc(c.id)}">${c.thumb?`<img src="${esc(c.thumb)}" alt="" loading="lazy">`:icon('user')}<span><b>${esc(c.title)}</b><small>Kênh${c.subscribers>=0?' · '+count(c.subscribers)+' người đăng ký':''}</small></span></button>`;}
function hideSuggestions(){++suggestionRun;clearTimeout(suggestionTimer);$('#suggestions').hidden=true;}
function searchInput(){const q=$('#search').value.trim();hideSuggestions();if(q.length<2)return;const run=suggestionRun;suggestionTimer=setTimeout(async()=>{try{const r=await native('suggest',{q});if(run!==suggestionRun||document.activeElement!==$('#search'))return;const items=(r.items||[]).filter(x=>typeof x==='string');$('#suggestions').innerHTML=items.map(x=>btn('suggestion',x,'search',`data-q="${esc(x)}"`,'suggestion')).join('');$('#suggestions').hidden=!items.length;}catch{}},280);}
function searchFilters(){return `<nav class="result-filters" aria-label="Loại kết quả">${[["","Tất cả"],["videos","Video"],["channels","Kênh"]].map(([mode,label])=>btn("searchFilter",label,"",`data-mode="${mode}" aria-pressed="${(route.duration||"")===mode}"`,`chip ${(route.duration||"")===mode?"active":""}`)).join("")}</nav>`;}
function go(type,args={}){route={type,...args};shortFeed=null;hideSuggestions();document.body.classList.remove('search-open');$('#search').blur();renderRoute();}
async function renderRoute(token='',append=false){
 const run=++epoch,type=route.type;setActive(type);setChips(type);if(!append){$('#content').innerHTML=skeleton();window.scrollTo(0,0);}
 try{
  if(['library','account','settings','uploadForm','downloadForm','privacy'].includes(type)){if(type==='library')return renderLibrary();return;}
  if(type==='history'||type==='saved'){return await renderLocal(type,run);}
  if(type==='subscriptions'){const res=await native('subscriptions',{pageToken:token});if(run!==epoch)return;const html=(res.items||[]).map(it=>{const s=it.snippet||{};return `<button class="list-row" data-action="channel" data-id="${esc(s.resourceId?.channelId)}"><img src="${esc(s.thumbnails?.default?.url||'')}" alt=""><span class="grow"><strong>${esc(s.title)}</strong><small class="muted">${esc(s.description?.slice(0,100))}</small></span>${icon('external')}</button>`;}).join('');if(!append)$('#content').innerHTML=head('Kênh đăng ký','Danh sách từ tài khoản YouTube đã cấp quyền.');$('#content').insertAdjacentHTML('beforeend',html||empty('Chưa có kênh đăng ký','Theo dõi một kênh để kênh đó xuất hiện tại đây.','',''));$('#moreBox')?.remove();$('#content').insertAdjacentHTML('beforeend',`<div id="moreBox">${moreButton(res.nextPageToken)}</div>`);return;}
  if(type==='playlists'){return await renderPlaylists(run,token,append);}
  if(type==='channel'){return await renderChannel(run,token,append);}
  let op='popular',args={pageToken:token},title='',subtitle='',compact=false;
  if(type==='search'||type==='topic'){op='search';args={...args,q:route.q,order:route.order||'relevance',duration:route.duration||''};title=type==='search'?`Kết quả cho “${route.q}”`:route.q;subtitle='';}
  if(type==='short'){op='shorts';args={...args,channel:route.id||''};title='Shorts';compact=true;}
  if(type==='liked'){op='liked';title='Video đã thích';subtitle='Từ tài khoản YouTube của bạn';}
  if(type==='playlist'){op='playlistVideos';args={...args,id:route.id};title=route.title||'Playlist';subtitle='Playlist YouTube';}
  const res=await native(op,args);if(run!==epoch)return;
  if(!append){$('#content').innerHTML=head(title,subtitle)+(type==='search'?searchFilters():'');}
  if(type==='short'){
   if(!append||!shortFeed)shortFeed={ids:[],token:'',channel:route.id||''};
   for(const raw of res.items||[])if(raw.short&&!shortFeed.ids.includes(raw.id))shortFeed.ids.push(raw.id);
   shortFeed.token=res.nextPageToken||'';
  }
  showCards(res.items||[],res.nextPageToken,{append,compact,shorts:type==='short'});
  if(type==='home'&&!append)homeShelf(run);
 }catch(e){if(run===epoch){if(append)toast(e.message);else $('#content').innerHTML=errorBlock(e);}}
}
function homeShelf(run){native('shorts',{channel:'',pageToken:''}).then(res=>{
 if(run!==epoch||route.type!=='home')return;const items=(res.items||[]).filter(i=>i.short).map(videoFrom).filter(Boolean).slice(0,10);if(items.length<3)return;
 shortFeed={ids:items.map(v=>v.id),token:res.nextPageToken||'',channel:''};
 const grid=$('#videoGrid');if(!grid||$('#homeShelf'))return;
 const html=`<section id="homeShelf" class="shorts-shelf"><h2>${icon('short')}Shorts</h2><div class="shelf-row">${items.map(shortCard).join('')}</div></section>`;
 const at=grid.querySelectorAll(':scope>.card')[3];at?at.insertAdjacentHTML('beforebegin',html):grid.insertAdjacentHTML('beforeend',html);
}).catch(()=>{});}
async function renderLocal(type,run){const store=readStore(type),data=store.slice(0,200);$('#content').innerHTML=head(type==='history'?'Đã xem trên INY':'Xem sau trên INY','Lưu trên thiết bị, không đồng bộ với lịch sử / Xem sau của YouTube.',btn(type==='history'?'clearHistory':'clearSaved','Xóa danh sách','close'));if(!data.length){$('#content').insertAdjacentHTML('beforeend',empty('Danh sách còn trống','Mở một video hoặc nhấn Lưu để thêm video.','',''));return;}
 const todo=data.filter(v=>!v.title).slice(0,10);if(todo.length){await Promise.allSettled(todo.map(async m=>{Object.assign(m,metaOf(videoFrom(await native('video',{id:m.id}))));}));writeStore(type,store);}
 if(run!==epoch)return;const shown=data.filter(v=>v.title);if(!shown.length){$('#content').insertAdjacentHTML('beforeend',empty('Chưa tải được thông tin','Mở lại video để lưu đầy đủ thông tin.','',''));return;}
 showCards(shown.slice(0,50).map(v=>({kind:'np',id:v.id,title:v.title,channel:v.channel,channelId:v.channelId,avatar:v.avatar,thumb:v.thumb,duration:v.durationSec,views:v.views,publishedText:v.publishedText})),null);if(shown.length>50)$('#content').insertAdjacentHTML('beforeend','<div class="notice">Hiện 50 mục gần nhất. Các mục còn lại vẫn được lưu trên thiết bị.</div>');}
function renderLibrary(){const saved=readStore('saved').length,history=readStore('history').length;$('#content').innerHTML=head('Bạn','',btn('account','Tài khoản','user'))+`<div class="stats"><div class="stat"><b>${history}</b><small>Đã xem</small></div><div class="stat"><b>${saved}</b><small>Xem sau</small></div><div class="stat"><b>10.00</b><small>Phiên bản .03</small></div></div><div class="section-pad">${[['history','Lịch sử xem trên INY','history'],['saved','Xem sau trên INY','bookmark'],['playlists','Playlist YouTube','list'],['liked','Video YouTube đã thích','like'],['downloadForm','Tải tệp có quyền sử dụng','download'],['settings','Cài đặt & cập nhật','settings']].map(([a,l,i])=>`<button class="list-row" data-action="${a}">${icon(i)}<span class="grow">${l}</span>${icon('external')}</button>`).join('')}</div>`;}
async function renderPlaylists(run,token,append){const res=await native('playlists',{pageToken:token});if(run!==epoch)return;if(!append)$('#content').innerHTML=head('Playlist YouTube','Playlist trong tài khoản đã cấp quyền.',btn('createPlaylist','Tạo playlist','plus'));const html=(res.items||[]).map(it=>`<button class="list-row" data-action="playlist" data-id="${esc(it.id)}" data-title="${esc(it.snippet.title)}"><span class="channel-dot">${icon('list')}</span><span class="grow"><strong>${esc(it.snippet.title)}</strong><small class="muted">${it.contentDetails?.itemCount||0} video</small></span></button>`).join('');$('#moreBox')?.remove();$('#content').insertAdjacentHTML('beforeend',html||empty('Chưa có playlist','Tạo playlist đầu tiên của bạn.','',''));$('#content').insertAdjacentHTML('beforeend',`<div id="moreBox">${moreButton(res.nextPageToken)}</div>`);}
async function renderChannel(run,token,append){const id=route.id;let res;
 if(!append){res=await native('channel',{id});if(run!==epoch)return;$('#content').innerHTML=`<div class="channel-header"><div class="row">${avatar(res.avatar,res.title,'channel-dot lg')}<div><h1>${esc(res.title||'Kênh')}</h1><span class="muted">${res.subscribers>=0?`${count(res.subscribers)} người đăng ký`:'Ẩn số người đăng ký'}</span></div></div>${res.hasShorts?btn('channelShorts','Shorts','short',`data-id="${esc(id)}"`):''}</div>`;if(res.error){$('#content').insertAdjacentHTML('beforeend',empty('Chưa tải được video của kênh',res.error,'reload','Thử lại'));return;}}
 else{res=await native('channelVideos',{pageToken:token});if(run!==epoch)return;}
 showCards(res.items||[],res.nextPageToken,{append});}
function metaOf(v){return{title:v.title,channel:v.channel,channelId:v.channelId,avatar:v.avatar,thumb:v.thumb,durationSec:v.durationSec,views:v.views,publishedText:v.publishedText};}
function recordView(v){let h=readStore('history').filter(x=>x.id!==v.id);h.unshift({id:v.id,at:Date.now(),...metaOf(v)});writeStore('history',h.slice(0,200));}
function isSaved(id){return readStore('saved').some(v=>v.id===id);}
function toggleSaved(v){let list=readStore('saved'),exists=list.some(x=>x.id===v.id);list=exists?list.filter(x=>x.id!==v.id):[{id:v.id,at:Date.now(),...metaOf(v)},...list].slice(0,200);writeStore('saved',list);const b=$('#saveButton');if(b){b.classList.toggle('selected',!exists);b.innerHTML=icon('bookmark')+(exists?'Lưu':'Đã lưu');}toast(exists?'Đã bỏ khỏi Xem sau trên INY':'Đã lưu trên thiết bị');}
let embedMode=false;
function playerRect(){const box=$('#playerBox');if($('#dialog').open||(activePanel&&isShort()))return{w:0,h:0};const r=box.getBoundingClientRect();return{x:r.left,y:r.top,w:r.width,h:r.height};}
function placePlayer(){placeSheet();if(!embedMode&&!$('#watch').hidden)native('place',{rect:playerRect()}).catch(()=>{});}
function embed(id){embedMode=true;native('stop').catch(()=>{});$('#playerBox').innerHTML=`<iframe id="ytplayer" title="Video" src="https://www.youtube.com/embed/${id}?playsinline=1&enablejsapi=1&origin=https%3A%2F%2Fio.github.iny.app" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>`;}
function shortInfo(){const v=currentVideo||{};return{channel:v.channel||'',title:v.title||'',likes:v.likes?count(v.likes):'Thích',subscribed:!!subscriptionId,liked:$('#likeButton')?.getAttribute('aria-pressed')==='true',saved:!!(v.id&&isSaved(v.id))};}
function shortSync(){if($('#watch').classList.contains('short-watch'))native('shortInfo',shortInfo()).catch(()=>{});}
window.shortAct=n=>{const q=sel=>document.querySelector(sel)?.click();
 if(n==='back')closeWatch();else if(n==='like')q('#likeButton');else if(n==='comments')q('[data-action="commentsPanel"]');else if(n==='share')q('[data-action="shareVideo"]');else if(n==='save')q('#saveButton');else if(n==='subscribe')q('.js-sub');else if(n==='channel')q('.watch-channel [data-action="channel"]');
 setTimeout(shortSync,400);};
async function startNative(id,run,height){embedMode=false;$('#playerBox').innerHTML='';try{await native('play',{id,height:height||720,qualities:watchQualities,speed:Number(readStore('speed',1)),short:$('#watch').classList.contains('short-watch'),info:shortInfo(),rect:playerRect()});if(run===watchEpoch)placePlayer();}catch(e){if(run===watchEpoch)embed(id);}}
function displayRating(rating){setTimeout(shortSync,0);const b=$('#likeButton'),d=$('#dislikeButton');if(b){b.classList.toggle('selected',rating==='like');b.setAttribute('aria-pressed',String(rating==='like'));}if(d){d.classList.toggle('selected',rating==='dislike');d.setAttribute('aria-pressed',String(rating==='dislike'));}}
function syncActions(v,run=watchEpoch){if(!config.authorized)return;if(v.channelId)native('subscription',{id:v.channelId,videoId:v.id}).then(r=>{if(run!==watchEpoch)return;subscriptionId=r.items?.[0]?.id||null;updateSubscribeButton();}).catch(()=>{});native('rating',{id:v.id}).then(r=>{if(run===watchEpoch)displayRating(r.items?.[0]?.rating);}).catch(()=>{});}
async function watch(id,short=false){if(!/^[\w-]{11}$/.test(id))return;closeModal();const run=++watchEpoch,known=videos.get(id);currentVideo=known||{id,title:'Đang tải…'};subscriptionId=null;commentToken='';commentCache=null;embedMode=false;watchQualities=[];closeSheet();
 const w=$('#watch');w.classList.toggle('short-watch',short);$('#watchLabel').textContent=short?'Shorts':'Đang phát';w.hidden=false;document.body.style.overflow='hidden';$('#playerBox').innerHTML='';$('#watchBody').scrollTop=0;$('#watchBody').innerHTML=skeleton();native('stop').catch(()=>{});native('immersive',{on:short}).catch(()=>{});
 try{const res=await native('video',{id});if(run!==watchEpoch)return;const v=videoFrom({...res,short});currentVideo=v;recordView(v);watchQualities=(res.qualities||[]).map(Number).filter(h=>h>0);renderWatch(v,res.related||[]);loadCommentPreview(v,run);const want=Number(readStore('quality',720))||720;startNative(id,run,watchQualities.find(h=>h<=want)||watchQualities.at(-1)||want);syncActions(v,run);}
 catch{if(run===watchEpoch){if(known)recordView(known);renderWatch(currentVideo,[]);embed(id);}}
}
async function stepShort(direction){if(shortLoading||!shortFeed||!currentVideo||!$('#watch').classList.contains('short-watch'))return;const feed=shortFeed,run=watchEpoch;const i=feed.ids.indexOf(currentVideo.id);if(i<0)return;const target=i+direction;if(target<0)return;
 if(target>=feed.ids.length&&feed.token){shortLoading=true;try{for(let pages=0;pages<3&&feed.token&&target>=feed.ids.length;pages++){const res=await native('shorts',{channel:feed.channel,pageToken:feed.token});if(feed!==shortFeed||run!==watchEpoch)return;feed.token=res.nextPageToken||'';for(const item of res.items||[])if(item.short&&!feed.ids.includes(item.id)){videoFrom(item);feed.ids.push(item.id);}}}catch{toast('Chưa tải được Shorts tiếp theo.');return;}finally{shortLoading=false;}}
 if(feed===shortFeed&&run===watchEpoch&&feed.ids[target])watch(feed.ids[target],true);else if(direction>0)toast('Đã hết Shorts trong danh sách này.');
}
function channelRow(v){return `<div class="row space watch-channel"><button class="row" data-action="channel" data-id="${esc(v.channelId)}">${avatar(v.avatar,v.channel)}<span class="ch-text"><b>${esc(v.channel)}</b>${v.subscribers>0?`<small class="muted">${count(v.subscribers)} người đăng ký</small>`:''}</span></button>${v.channelId?btn('subscribe','Đăng ký','plus','','primary js-sub'):''}</div>`;}
function renderWatch(v,related){const short=$('#watch').classList.contains('short-watch');
 $('#watchBody').innerHTML=`<div class="watch-summary"><h1>${esc(v.title||'Video')}</h1>${channelRow(v)}<div class="actions watch-actions">${btn('like',v.likes?count(v.likes):'Thích','like','id="likeButton" aria-label="Thích" aria-pressed="false"')}${btn('dislike','Không thích','like','id="dislikeButton" aria-label="Không thích" aria-pressed="false"')}${btn('commentsPanel','Bình luận','comment')}${btn('shareVideo','Chia sẻ','share')}${btn('saveVideo',isSaved(v.id)?'Đã lưu':'Lưu','bookmark','id="saveButton"',isSaved(v.id)?'secondary selected':'secondary')}${btn('saveToPlaylist','Playlist','list')}</div><button class="description" data-action="descriptionPanel"><b>${v.views?count(v.views)+' lượt xem':''}${v.views&&v.publishedText?' · ':''}${esc(v.publishedText||'')}</b><span>Xem mô tả ›</span></button>${short?'':'<button class="comment-preview" id="commentPreview" data-action="commentsPanel" hidden></button>'}</div>${short?`<div class="short-nav" aria-label="Điều hướng Shorts">${btn('shortPrev','‹ Trước')}${btn('shortNext','Tiếp ›')}</div>`:`<div id="recommendations" class="grid"></div>`}`;
 const rec=$('#recommendations');if(rec)rec.innerHTML=related.map(videoFrom).filter(x=>x&&x.id!==v.id&&!x.short).slice(0,12).map(card).join('');updateSubscribeButton();
}
function descriptionHtml(v){
 const stat=(n,label)=>`<div><b>${n}</b><small class="muted">${label}</small></div>`;
 return `<h3 class="d-title">${esc(v.title)}</h3><div class="d-stats">${stat(v.likes!=null?count(v.likes):'–','Lượt thích')}${stat(v.views!=null?count(v.views):'–','Lượt xem')}${stat(esc(v.publishedText||'–'),'Ngày đăng')}</div><div class="d-text">${v.description?rich(v.description):'<span class="muted">Video này chưa có mô tả.</span>'}</div><div class="d-channel">${channelRow(v)}</div>`;
}
function showWatchPanel(panel){if(!currentVideo)return;
 if(panel==='description'){openSheet(panel,'Mô tả',descriptionHtml(currentVideo));updateSubscribeButton();return;}
 const me=myChannel?.snippet;
 openSheet(panel,'Bình luận','<div id="comments"></div>',`<div class="comment-bar">${avatar(me?.thumbnails?.default?.url,me?.title||'Bạn')}<input class="field comment-input" id="newComment" maxlength="5000" placeholder="Bình luận…" aria-label="Nội dung bình luận" autocomplete="off"><button class="icon" data-action="postComment" aria-label="Gửi bình luận">${icon('send')}</button></div>`);
 loadComments();}
function updateSubscribeButton(){shortSync();document.querySelectorAll('.js-sub').forEach(b=>{b.className='js-sub '+(subscriptionId?'secondary':'primary');b.innerHTML=icon(subscriptionId?'check':'plus')+(subscriptionId?'Đã đăng ký':'Đăng ký');});}
function leaveWatch(){if(!$('#watch').hidden)closeWatch();}
function seekTo(sec){if(!embedMode)return native('seek',{sec});$('#ytplayer')?.contentWindow?.postMessage(JSON.stringify({event:'command',func:'seekTo',args:[sec,true]}),'https://www.youtube.com');}
function closeWatch(){closeModal();closeSheet();native('stop').catch(()=>{});native('immersive',{on:false}).catch(()=>{});pausePlayer();watchEpoch++;$('#watch').hidden=true;$('#playerBox').innerHTML='';document.body.style.overflow='';currentVideo=null;}
function pausePlayer(){const player=$('#ytplayer');player?.contentWindow?.postMessage(JSON.stringify({event:'command',func:'pauseVideo',args:[]}),'https://www.youtube.com');}
window.pausePlayer=pausePlayer;

let commentCache=null;
async function fetchComments(id,token=''){if(!token&&commentCache?.id===id)return commentCache.res;const res=await native('comments',{id,pageToken:token});if(!token)commentCache={id,res};return res;}
async function loadCommentPreview(v,run){try{const c=(await fetchComments(v.id)).items?.[0],box=$('#commentPreview');if(!c||!box||run!==watchEpoch)return;box.innerHTML=`<b>Bình luận</b><div class="row">${avatar(c.avatar,c.author)}<span class="clamp">${esc(c.text)}</span></div>`;box.hidden=false;}catch{}}
function commentHtml(c,reply=false){return `<article class="comment${reply?' reply':''}">${avatar(c.avatar,c.author)}<div class="body">${c.pinned?'<div class="pin muted small">Đã ghim</div>':''}<div class="meta"><b class="${c.owner?'owner':''}">${esc(c.author)}</b> · ${esc(displayDate(c.publishedText))}</div><p>${rich(c.text)}</p><div class="buttons"><span class="like-count">${icon('like')}${c.likes?count(c.likes):''}</span>${c.hearted?'<span class="heart">♥</span>':''}${c.replyCount?btn('replies',`${c.replyCount} phản hồi`,'',`data-id="${esc(c.id)}"`,'link'):''}</div><div class="replies" id="reply-${esc(c.id)}"></div></div></article>`;}
async function loadComments(append=false,run=watchEpoch){if(!currentVideo||!$('#comments'))return;const id=currentVideo.id,panel=commentRun;if(!append){commentToken='';$('#comments').innerHTML='<p class="muted">Đang tải bình luận…</p>';}
 try{const res=await fetchComments(id,append?commentToken:'');if(run!==watchEpoch||panel!==commentRun||!$('#comments'))return;const html=(res.items||[]).map(c=>commentHtml(c)).join('');if(!append)$('#comments').innerHTML=html||'<p class="muted">Chưa có bình luận công khai.</p>';else{$('#moreComments')?.remove();$('#comments').insertAdjacentHTML('beforeend',html);}commentToken=res.nextPageToken||'';if(commentToken)$('#comments').insertAdjacentHTML('beforeend',`<div id="moreComments" class="load-more">${btn('moreComments','Xem thêm bình luận','plus')}</div>`);}
 catch(e){if(run===watchEpoch&&panel===commentRun&&$('#comments')){if(append)toast(e.message);else $('#comments').innerHTML=`<p class="muted">${esc(e.message)}</p>`;}}
}
async function settings(){config=await native('config');modal('Cài đặt',`<p class="muted">I'm not youtube · ${esc(config.versionName)}<br>Ứng dụng độc lập. Không phải YouTube chính thức.</p><label>Giao diện</label>${btn('theme','Đổi sáng / tối','sun')}<hr><label>Định danh cập nhật</label><p><code>${esc(config.package)}</code></p><p class="small muted">Version code ${config.versionCode}. Android kiểm tra package + chứng chỉ ký khi cài bản cập nhật. Không tự tải / tự cài APK.</p><label>SHA-1 chứng chỉ đang cài</label><p><code>${esc(config.sha1)}</code></p>${btn('copySha','Sao chép SHA-1','copy')}<hr>${btn('checkUpdate','Kiểm tra GitHub Release','refresh','','primary')}<div id="updateResult"></div><hr>${btn('privacy','Quyền riêng tư','external')}`);}
async function loadMyChannel(){
 myChannel=null;
 if(config.authorized)myChannel=(await native('myChannel')).items?.[0]||null;
 const me=myChannel?.snippet;
 $('header .avatar').innerHTML=me?.thumbnails?.default?.url?`<img src="${esc(me.thumbnails.default.url)}" alt="">`:initial(me?.title||'I');
}
async function account(){config=await native('config');modal('Tài khoản Google',`<div id="accountInfo"><p>${config.authorized?'Đã đăng nhập.':'Chưa đăng nhập.'}</p></div><p class="muted small">Đăng nhập để thích, bình luận và xem các kênh đã đăng ký. Bạn vẫn có thể xem video mà không cần đăng nhập.</p><div class="row">${btn('login',config.authorized?'Đăng nhập lại':'Đăng nhập YouTube','user','','primary')}${config.authorized?btn('logout','Đăng xuất','close'):''}</div><hr>${btn('manageGoogle','Quản lý / thu hồi quyền Google','external')}`);
 if(!config.authorized)return;
 try{await loadMyChannel();}catch(e){if($('#accountInfo'))$('#accountInfo').textContent=e.message;return;}
 const me=myChannel?.snippet;if(me&&$('#accountInfo'))$('#accountInfo').innerHTML=`<div class="row">${avatar(me.thumbnails?.default?.url,me.title)}<b>${esc(me.title)}</b></div>`;}
function uploadForm(){modal('Tạo nội dung',`<div class="notice upload-notice">Tải video lên bằng API chính thức. Cần OAuth đã cấu hình và tài khoản có quyền tải video lên; chế độ hiển thị còn phụ thuộc cài đặt / hạn chế tài khoản. Quá trình tải chỉ chạy khi app còn hoạt động, chưa hỗ trợ phục hồi sau khi app bị đóng.</div><label for="uploadTitle">Tiêu đề</label><input class="field" id="uploadTitle" maxlength="100" placeholder="Tiêu đề video của bạn"><label for="uploadDescription">Mô tả</label><textarea id="uploadDescription" maxlength="5000" placeholder="Giới thiệu video"></textarea><label for="uploadPrivacy">Chế độ hiển thị</label><select id="uploadPrivacy"><option value="private">Riêng tư</option><option value="unlisted">Không công khai</option><option value="public">Công khai</option></select><label><input type="checkbox" id="madeForKids"> Video dành cho trẻ em</label><p class="small muted">Bạn chịu trách nhiệm về quyền đăng tải và khai báo nội dung dành cho trẻ em.</p>${btn('selectUpload','Chọn video & tải lên','plus','id="uploadButton"','primary')}<p id="uploadStatus" role="status"></p><progress id="uploadProgress" max="100" value="0" hidden></progress><hr><p class="small muted">Community post chưa có luồng API công khai được triển khai trong bản này. Mở YouTube để đăng bài / quản lý nội dung.</p>${btn('studio','Mở YouTube Studio','external')}`);}
function downloadForm(){modal('Tải tệp bạn có quyền',`<p class="muted">Tải liên kết tệp HTTPS trực tiếp do bạn sở hữu hoặc được cho phép. Không phải công cụ tải video YouTube, không tách âm thanh và không vượt hạn chế của trình phát.</p><label for="downloadUrl">Liên kết .mp4 / .webm / .mov / .m4v / .mp3 / .m4a</label><input class="field" id="downloadUrl" type="url" placeholder="https://your-domain.example/video.mp4">${btn('startDownload','Tải về Downloads','download','','primary')}<p class="small muted">Android Download Manager xử lý tải xuống. Xem tiến độ / hủy trong thông báo hệ thống. Kết nối chuyển hướng của máy chủ cần được bạn tin cậy.</p>`);}
function privacy(){modal('Quyền riêng tư & điều khoản',`<p>I'm not youtube là ứng dụng độc lập, không liên kết hoặc được bảo trợ bởi Google / YouTube.</p><h3>Dữ liệu trên thiết bị</h3><p>Token Google chỉ giữ trong RAM và hết hiệu lực cục bộ sau khoảng 50 phút. Lịch sử và Xem sau của INY chỉ lưu ID video + thời điểm trên thiết bị, không đồng bộ sang lịch sử / Xem sau YouTube. Xóa dữ liệu ứng dụng để xóa tất cả.</p><h3>Dữ liệu gửi đi</h3><p>Tìm kiếm, gợi ý từ khóa, thông tin video, bình luận và luồng phát được lấy trực tiếp từ YouTube bằng NewPipe Extractor, không dùng YouTube API key và không qua máy chủ trung gian của INY. Khi cấp quyền Google, thao tác thích / đăng ký / bình luận / playlist / tải lên dùng API chính thức của Google. Ảnh thu nhỏ tải từ máy chủ ảnh của YouTube. Kiểm tra cập nhật kết nối GitHub. Không có máy chủ phân tích của INY.</p><h3>Quyền Google</h3><p>Quyền đọc tài khoản, quản lý tương tác và tải video lên được xin qua Google Play services. Thích, đăng ký, bình luận và tải lên sẽ thay đổi tài khoản YouTube thật của bạn. Đăng xuất cục bộ không thu hồi quyền đã cấp trên Google; dùng liên kết quản lý quyền dưới đây.</p><h3>Giới hạn</h3><p>Trình phát riêng của app không hiển thị quảng cáo YouTube; khi NewPipe Extractor lỗi, app chuyển sang trình phát nhúng của YouTube. Không phát nền, không tải video YouTube. Shorts được lấy từ tab Shorts của các kênh; danh sách này không cá nhân hóa. Trang chủ là gợi ý chung, không phải đề xuất cá nhân hóa. Không bán Premium.</p><div class="row">${btn('ytTerms','Điều khoản YouTube','external')}${btn('googlePrivacy','Riêng tư Google','external')}${btn('manageGoogle','Thu hồi quyền Google','external')}</div>`);}
async function saveToPlaylist(){const r=await native('playlists');modal('Thêm vào playlist YouTube',(r.items||[]).map(it=>`<button class="list-row" data-action="addPlaylistVideo" data-id="${esc(it.id)}">${icon('list')}<span class="grow">${esc(it.snippet.title)}</span></button>`).join('')||'<p>Chưa có playlist. Tạo playlist trong Thư viện trước.</p>');}
function requireConfirm(title,text,action,extra=''){modal(title,`<p>${esc(text)}</p><div class="row">${btn(action,'Xác nhận','check',extra,'primary')}${btn('closeDialog','Hủy','close')}</div>`);}
async function handle(action,el){
 switch(action){
 case 'home':go('home');break;case 'topic':go('topic',{q:el.dataset.q});break;case 'short':go('short');break;case 'long':go('long');break;
 case 'history':case 'saved':case 'subscriptions':case 'playlists':case 'liked':case 'library':go(action);break;
 case 'reload':renderRoute();break;case 'more':await renderRoute(el.dataset.token,true);break;
 case 'searchFocus':document.body.classList.add('search-open');$('#search').focus();break;
 case 'sidebar':document.body.classList.toggle('side-hidden');break;
 case 'settings':await settings();break;case 'account':await account();break;case 'uploadForm':uploadForm();break;case 'downloadForm':downloadForm();break;case 'privacy':privacy();break;
 case 'closeDialog':closeModal();break;case 'closeSheet':closeSheet();break;case 'closeWatch':closeWatch();break;
 case 'watch':await watch(el.dataset.id);break;
 case 'watchShort':if(!shortFeed?.ids.includes(el.dataset.id))shortFeed={ids:[el.dataset.id],token:'',channel:''};await watch(el.dataset.id,true);break;
 case 'channelShorts':go('short',{id:el.dataset.id});break;
 case 'shortPrev':case 'shortNext':await stepShort(action==='shortNext'?1:-1);break;
 case 'commentsPanel':showWatchPanel('comments');break;
 case 'descriptionPanel':showWatchPanel('description');break;
 case 'suggestion':$('#search').value=el.dataset.q;go('search',{q:el.dataset.q});break;
 case 'searchFilter':go('search',{q:route.q,duration:el.dataset.mode});break;
 case 'channel':if(!el.dataset.id)return;leaveWatch();go('channel',{id:el.dataset.id});break;
 case 'hashtag':leaveWatch();$('#search').value=el.dataset.q;go('search',{q:el.dataset.q});break;
 case 'seek':await seekTo(Number(el.dataset.t));break;
 case 'playlist':go('playlist',{id:el.dataset.id,title:el.dataset.title});break;
 case 'theme':{const isLight=!document.body.classList.contains('light');document.body.classList.toggle('light',isLight);writeStore('theme',isLight?'light':'dark');break;}
 case 'copySha':await native('copy',{text:config.sha1});toast('Đã sao chép SHA-1');break;
 case 'login':{await native('webLogin');config=await native('config');closeModal();loadMyChannel().catch(()=>{});toast('Đã kết nối với YouTube');if(currentVideo)syncActions(currentVideo);else if(['subscriptions','liked','playlists','playlist'].includes(route.type))await renderRoute();else await account();break;}
 case 'logout':await native('logout');config=await native('config');await loadMyChannel();closeModal();toast('Đã đăng xuất khỏi YouTube trên app này.');break;
 case 'checkUpdate':{const r=await native('update');$('#updateResult').innerHTML=`<p>${esc(r.message)}</p>${r.available?btn('openRelease','Mở bản phát hành','external',`data-url="${esc(r.url)}"`):''}`;break;}
 case 'openRelease':case 'openLink':await native('open',{url:el.dataset.url});break;
 case 'shareVideo':if(currentVideo)await native('share',{text:`${currentVideo.title}\nhttps://www.youtube.com/watch?v=${currentVideo.id}`});break;
 case 'copyVideoLink':if(currentVideo){await native('copy',{text:`https://www.youtube.com/watch?v=${currentVideo.id}`});toast('Đã sao chép liên kết');}break;
 case 'copyDescription':if(currentVideo){await native('copy',{text:currentVideo.description});toast('Đã sao chép mô tả');}break;
 case 'saveVideo':if(currentVideo)toggleSaved(currentVideo);break;
 case 'like':case 'dislike':if(currentVideo){const run=watchEpoch,requested=action==='like'?'like':'dislike';const rating=el.classList.contains('selected')?'none':requested;await native('rate',{id:currentVideo.id,rating});if(run===watchEpoch)displayRating(rating);}break;
 case 'subscribe':if(!currentVideo?.channelId)return;if(subscriptionId){requireConfirm('Hủy đăng ký kênh','Thao tác này sẽ hủy đăng ký trên tài khoản YouTube thật.','confirmUnsubscribe',`data-id="${esc(subscriptionId)}"`);}else{const r=await native('subscribe',{id:currentVideo.channelId});subscriptionId=r.id;updateSubscribeButton();toast('Đã đăng ký kênh trên YouTube');}break;
 case 'confirmUnsubscribe':await native('unsubscribe',{id:el.dataset.id});subscriptionId=null;closeModal();updateSubscribeButton();toast('Đã hủy đăng ký');break;
  case 'replies':{const id=el.dataset.id,run=watchEpoch,r=await native('replies',{id,pageToken:el.dataset.token||''});if(run!==watchEpoch)return;const box=document.getElementById('reply-'+id);if(!box)return;el.remove();box.insertAdjacentHTML('beforeend',(r.items||[]).map(c=>commentHtml(c,true)).join('')+(r.nextPageToken?btn('replies','Phản hồi khác','',`data-id="${esc(id)}" data-token="${esc(r.nextPageToken)}"`,'link'):''));break;}
 case 'moreComments':await loadComments(true);break;
 case 'embedPlayer':if(currentVideo)embed(currentVideo.id);break;
 case 'postComment':{const input=$('#newComment');if(!currentVideo||!input)return;const text=input.value.trim(),run=watchEpoch,panel=commentRun;if(!text)return;await native('postComment',{id:currentVideo.id,text});toast('Đã đăng bình luận');if(run===watchEpoch&&panel===commentRun&&input.isConnected){input.value='';commentCache=null;await loadComments();}break;}
 case 'saveToPlaylist':await saveToPlaylist();break;
 case 'addPlaylistVideo':if(currentVideo){await native('savePlaylist',{playlistId:el.dataset.id,videoId:currentVideo.id});closeModal();toast('Đã thêm vào playlist YouTube');}break;
 case 'createPlaylist':modal('Tạo playlist riêng tư',`<label for="playlistTitle">Tên playlist</label><input class="field" id="playlistTitle" maxlength="150">${btn('confirmCreatePlaylist','Tạo playlist','plus','','primary')}`);break;
 case 'confirmCreatePlaylist':{const title=$('#playlistTitle').value.trim();if(!title)return;await native('createPlaylist',{title});closeModal();go('playlists');toast('Đã tạo playlist trên YouTube');break;}
 case 'selectUpload':{const title=$('#uploadTitle').value.trim();if(!title){toast('Nhập tiêu đề trước');return;}const args={title,description:$('#uploadDescription').value,privacy:$('#uploadPrivacy').value,madeForKids:$('#madeForKids').checked};$('#uploadStatus').textContent='Chọn video, sau đó giữ ứng dụng mở trong khi tải lên…';$('#uploadProgress').hidden=false;try{await native('authorize',{upload:true});config=await native('config');const r=await native('upload',args);if($('#uploadStatus'))$('#uploadStatus').textContent=`Đã tải lên: ${r.id}. YouTube cần thời gian xử lý video.`;toast('Video đã được tải lên YouTube');}catch(e){if($('#uploadStatus'))$('#uploadStatus').textContent=e.message;throw e;}break;}
 case 'startDownload':{const url=$('#downloadUrl').value.trim(),r=await native('download',{url});toast(`Đã gửi tải ${r.filename} tới Android`);closeModal();break;}
 case 'clearHistory':requireConfirm('Xóa lịch sử INY','Chỉ xóa lịch sử trên thiết bị, không xóa lịch sử YouTube.','confirmClearHistory');break;
 case 'clearSaved':requireConfirm('Xóa Xem sau INY','Xóa toàn bộ mục Xem sau lưu trên thiết bị này.','confirmClearSaved');break;
 case 'confirmClearHistory':writeStore('history',[]);closeModal();go('history');break;
 case 'confirmClearSaved':writeStore('saved',[]);closeModal();go('saved');break;
 default:{const links={studio:'https://studio.youtube.com/',manageGoogle:'https://myaccount.google.com/connections',ytTerms:'https://www.youtube.com/t/terms',googlePrivacy:'https://policies.google.com/privacy'};if(links[action])await native('open',{url:links[action]});}
 }
}
document.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b||b.disabled)return;const action=b.dataset.action;b.disabled=true;try{await handle(action,b);}catch(err){if(err.message.includes('AUTH_REQUIRED')){config.authorized=false;modal('Cần quyền truy cập Google',`<p>${esc(err.message.replace('AUTH_REQUIRED:',''))}</p>${btn('login','Cấp quyền Google','user','','primary')}`);}else toast(err.message);}finally{b.disabled=false;}});
$('#searchForm').addEventListener('submit',e=>{e.preventDefault();const q=$('#search').value.trim();if(q)go('search',{q});});
$('#search').addEventListener('input',searchInput);
$('#search').addEventListener('focus',searchInput);
$('#search').addEventListener('keydown',e=>{if(e.key==='Escape'){hideSuggestions();$('#search').blur();}});
document.addEventListener('pointerdown',e=>{if(!e.target.closest('#searchForm,#suggestions'))hideSuggestions();});
$('#dialog').addEventListener('close',placePlayer);
let swipeY=0,swipeX=0;
$('#watchBody').addEventListener('touchstart',e=>{swipeY=e.changedTouches[0].clientY;swipeX=e.changedTouches[0].clientX;},{passive:true});
$('#watchBody').addEventListener('touchend',e=>{const t=e.changedTouches[0],dy=t.clientY-swipeY;if(Math.abs(dy)>64&&Math.abs(dy)>Math.abs(t.clientX-swipeX)*1.4)stepShort(dy<0?1:-1);},{passive:true});
$('#dialog').addEventListener('click',e=>{if(e.target===$('#dialog')){const r=$('#dialog').getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)closeModal();}});
window.nativeEvent=(name,args)=>{
 if(name==='uploadProgress'&&$('#uploadProgress')){$('#uploadProgress').value=args.percent;$('#uploadStatus').textContent=`Đang tải ${args.percent}%`;}
 if(name==='playerError'&&currentVideo&&!$('#watch').hidden&&!embedMode)embed(currentVideo.id);
 if(name==='relayout')placePlayer();
 if(name==='shortSwipe')stepShort(Number(args.direction));
 if(name==='speedChanged')writeStore('speed',Number(args.value));
 if(name==='qualityChanged'&&currentVideo&&!embedMode){const value=Number(args.value);writeStore('quality',value);startNative(currentVideo.id,watchEpoch,value);}
};
window.addEventListener('resize',placePlayer);
window.onNativeBack=()=>{if($('#dialog').open)closeModal();else if(!$('#sheet').hidden)closeSheet();else if(!$('#watch').hidden)closeWatch();else if(document.body.classList.contains('search-open'))document.body.classList.remove('search-open');else if(route.type!=='home')go('home');else toast('Dùng nút Home của Android để rời ứng dụng.');};
document.addEventListener('visibilitychange',()=>{if(document.hidden)pausePlayer();});
(async()=>{hydrate();document.body.classList.toggle('light',readStore('theme','dark')==='light');try{config=await native('config');}catch{}loadMyChannel().catch(()=>{});await renderRoute();})();
