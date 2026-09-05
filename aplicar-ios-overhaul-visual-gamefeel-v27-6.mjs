#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const CHECK = flag('--check');
const DRY = flag('--dry-run');
const FORCE = flag('--force');

const REL = {
  parity: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt',
  studio: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt',
};

const BAD_STEEL = 'color=Steel300,';
const GOOD_STEEL = 'color=Steel400,';
const BAD_BOX = 'BoxWithConstraints(Modifier.matchParentSize()) {';
const GOOD_BOX = 'BoxWithConstraints(Modifier.fillMaxSize()) {';

function existsRoot(root) {
  return fs.existsSync(path.join(root, REL.parity)) && fs.existsSync(path.join(root, REL.studio));
}
function dirs(root) {
  try { return fs.readdirSync(root, {withFileTypes:true}).filter(e => e.isDirectory()).map(e => path.join(root,e.name)); }
  catch { return []; }
}
function findRoot(start) {
  const explicit = value('--root') || value('--ios-root');
  if (explicit) {
    const r = path.resolve(explicit);
    if (!existsRoot(r)) throw new Error(`Projeto iOS/KMP não encontrado em: ${r}`);
    return r;
  }
  const s = path.resolve(start), p = path.dirname(s), g = path.dirname(p);
  const candidates = [s, path.join(s,'game-usinagem-IOS'), p, path.join(p,'game-usinagem-IOS'), g, path.join(g,'game-usinagem-IOS'), ...dirs(s), ...dirs(p)];
  for (const c of [...new Set(candidates)]) if (existsRoot(c)) return c;
  throw new Error('Não encontrei game-usinagem-IOS. Rode dentro dele ou use --root "C:\\caminho\\game-usinagem-IOS".');
}
function stamp(){ return new Date().toISOString().replace(/[:.]/g,'-'); }
function count(text, needle){ return text.split(needle).length - 1; }
function backup(root,broot,rel){
  const src=path.join(root,rel), dst=path.join(broot,rel);
  fs.mkdirSync(path.dirname(dst),{recursive:true}); fs.copyFileSync(src,dst);
}
function inspect(root){
  const parity = fs.readFileSync(path.join(root,REL.parity),'utf8');
  const studio = fs.readFileSync(path.join(root,REL.studio),'utf8');
  return {
    v27_5: parity.includes('landing_v27_5') && studio.includes('factory_studio_v27_5'),
    steel300Bad: count(parity,BAD_STEEL),
    steel400Good: count(parity,GOOD_STEEL),
    boxBad: count(studio,BAD_BOX),
    boxGood: count(studio,GOOD_BOX),
    interactionLayer: studio.includes('StudioInteractionLayerV27_5') && studio.includes('StudioTapTargetV27_5'),
    schema4Untouched: true,
  };
}
function printState(s){
  console.log(`[V27.6] base V27.5: ${s.v27_5 ? 'OK' : 'não detectada'}`);
  console.log(`[V27.6] Steel300 problemático: ${s.steel300Bad}`);
  console.log(`[V27.6] Steel400 corrigido: ${s.steel400Good}`);
  console.log(`[V27.6] matchParentSize problemático: ${s.boxBad}`);
  console.log(`[V27.6] fillMaxSize corrigido: ${s.boxGood}`);
  console.log(`[V27.6] camada de interação V27.5: ${s.interactionLayer ? 'OK' : 'pendente'}`);
}
function valid(s){
  return s.v27_5 && s.steel300Bad === 0 && s.steel400Good >= 1 && s.boxBad === 0 && s.boxGood >= 1 && s.interactionLayer;
}

const root=findRoot(process.cwd());
console.log('\n[V27.6] Kotlin/Native Compose compatibility hotfix');
console.log(`[V27.6] raiz: ${root}`);
let state=inspect(root); printState(state);

if(CHECK){
  if(valid(state)) { console.log('[V27.6] CHECK OK: os 2 erros do Actions foram removidos e a interação V27.5 foi preservada.'); process.exit(); }
  console.error('[V27.6] CHECK FALHOU: hotfix ainda não está completa neste diretório.'); process.exit(2);
}
if(DRY){
  console.log('[V27.6] DRY-RUN: corrigiria apenas AndroidV24ParityUi.kt e FactoryStudio.kt.');
  process.exit();
}
if(valid(state) && !FORCE){ console.log('[V27.6] Já aplicada. Use --check para validar.'); process.exit(); }
if(!state.v27_5 && !FORCE) throw new Error('V27.5 não detectada. Nada foi alterado. Use --force somente se souber exatamente o estado do projeto.');

const broot=path.join(root,'.patch-backups','ios-overhaul-visual-gamefeel-v27-6',stamp());
backup(root,broot,REL.parity); backup(root,broot,REL.studio);

let parity=fs.readFileSync(path.join(root,REL.parity),'utf8');
let studio=fs.readFileSync(path.join(root,REL.studio),'utf8');

if(count(parity,BAD_STEEL) > 0) parity=parity.split(BAD_STEEL).join(GOOD_STEEL);
if(count(studio,BAD_BOX) > 0) studio=studio.split(BAD_BOX).join(GOOD_BOX);

fs.writeFileSync(path.join(root,REL.parity),parity,'utf8');
fs.writeFileSync(path.join(root,REL.studio),studio,'utf8');

state=inspect(root); printState(state);
if(!valid(state)) throw new Error(`Validação final falhou. Backup preservado em: ${broot}`);

console.log('\n[V27.6] APLICAÇÃO OK.');
console.log(`[V27.6] backup: ${broot}`);
console.log('[V27.6] Corrigido: Steel300 inexistente -> Steel400.');
console.log('[V27.6] Corrigido: BoxWithConstraints(matchParentSize) -> fillMaxSize.');
console.log('[V27.6] Interação V27.5, landing, salários, missões, áudio e save não foram alterados.');
