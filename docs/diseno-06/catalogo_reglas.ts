// MiVisor TV / referencia de normalización. Código de diseño propuesto, no motor de emisión.
export type SourceState = 'unknown'|'untested'|'checking'|'ready'|'failed'|'offline';
export type Availability = 'unknown'|'available'|'unavailable'|'no_sources';
export interface RawSource { sourceId:string; providerId?:string; providerName?:string; logicalChannelId?:string; rawChannelId:string; displayName:string; family?:string; categories?:string[]; quality?:string; status?:SourceState; url?:string }
export interface SafeLogicalChannel {channelId:string;providerId:string|null;displayName:string;family:string;categories:string[];sources:RawSource[];availability:Availability;aliases:string[]}
// Approval table must contain only exact, audited source-channel identities.
export type ApprovedAliases = Record<string,string>; // `${providerId}/${rawChannelId}` -> stable channelId
const collator=new Intl.Collator('es',{numeric:true,sensitivity:'base'});
export function groupVerifiedSources(rows:RawSource[], approved:ApprovedAliases={}):SafeLogicalChannel[]{
  const groups=new Map<string,SafeLogicalChannel>();
  for(const r of rows){
    if(!r.sourceId||!r.rawChannelId)throw new Error('Source needs stable identifiers');
    const provider=r.providerId || 'unidentified';
    const explicit=r.logicalChannelId || approved[`${provider}/${r.rawChannelId}`];
    // When no audited identity is available do NOT coalesce by fuzzy name.
    const logical=explicit || `unverified:${r.rawChannelId}:${r.sourceId}`;
    const key=`${provider}/${logical}`;
    let g=groups.get(key);
    if(!g){g={channelId:key,providerId:r.providerId||null,displayName:r.displayName,family:r.family||'Sin clasificar',categories:r.categories||[],sources:[],availability:'unknown',aliases:[]};groups.set(key,g)}
    if(!g.sources.some(s=>s.sourceId===r.sourceId))g.sources.push(r);
    if(!g.aliases.includes(r.displayName))g.aliases.push(r.displayName);
  }
  for(const g of groups.values()){
    const states=g.sources.map(s=>s.status||'unknown');
    g.availability=!g.sources.length?'no_sources':states.includes('ready')?'available':states.every(s=>s==='failed'||s==='offline')?'unavailable':'unknown';
  }
  return [...groups.values()].sort((a,b)=>collator.compare(a.displayName,b.displayName));
}
export function displayFamilies(channels:SafeLogicalChannel[]){return [...new Set(channels.map(c=>c.family))].sort(collator.compare)}
export function byProvider(channels:SafeLogicalChannel[],providerId:string|null){return channels.filter(c=>c.providerId===providerId)}
