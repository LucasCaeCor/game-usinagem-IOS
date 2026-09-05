#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => { const i=args.indexOf(name); return i>=0 ? args[i+1] : null; };
const CHECK=flag('--check'), DRY=flag('--dry-run'), FORCE=flag('--force');

const rel={
  gameApp:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/GameApp.kt',
  parity:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt',
  studio:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt',
  roulette:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/IndustrialRoulette.kt',
  avatar:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/PlayerAvatarVisual.kt',
  machineArt:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/MachineArtworkV27.kt',
  visual:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/VisualExperienceV27.kt',
  feedback:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/GameFeedback.kt',
  feedbackIos:'ios-converted/composeApp/src/iosMain/kotlin/br/com/usinagemmaster/game/ui/GameFeedback.ios.kt',
  store:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt',
  models:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/model/PersistentGameModels.kt',
  codec:'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/persistence/GameSaveCodec.kt',
  audioDir:'iosApp/UsinagemConverted',
};
const files=[
 ['payload/common/ui/GameApp.kt',rel.gameApp],['payload/common/ui/AndroidV24ParityUi.kt',rel.parity],['payload/common/ui/FactoryStudio.kt',rel.studio],
 ['payload/common/ui/IndustrialRoulette.kt',rel.roulette],['payload/common/ui/PlayerAvatarVisual.kt',rel.avatar],['payload/common/ui/MachineArtworkV27.kt',rel.machineArt],
 ['payload/common/ui/VisualExperienceV27.kt',rel.visual],['payload/common/ui/GameFeedback.kt',rel.feedback],['payload/ios/ui/GameFeedback.ios.kt',rel.feedbackIos],
 ['payload/common/domain/GameStore.kt',rel.store],['payload/common/model/PersistentGameModels.kt',rel.models],['payload/common/persistence/GameSaveCodec.kt',rel.codec],
];
const audio=['factory_ambient.wav','machine_tick.wav','weld_spark.wav','ui_click.wav','machine_start.wav','reward_sting.wav','quality_pass.wav'];
function existsRoot(root){return fs.existsSync(path.join(root,rel.gameApp))&&fs.existsSync(path.join(root,rel.store))&&fs.existsSync(path.join(root,'iosApp/project.yml'));}
function dirs(root){try{return fs.readdirSync(root,{withFileTypes:true}).filter(x=>x.isDirectory()).map(x=>path.join(root,x.name));}catch{return[];}}
function findRoot(start){const explicit=value('--root')||value('--ios-root');if(explicit){const r=path.resolve(explicit);if(!existsRoot(r))throw new Error(`Projeto iOS/KMP não encontrado em ${r}`);return r;}const s=path.resolve(start),p=path.dirname(s),g=path.dirname(p);for(const c of [...new Set([s,path.join(s,'game-usinagem-IOS'),p,path.join(p,'game-usinagem-IOS'),g,path.join(g,'game-usinagem-IOS'),...dirs(s),...dirs(p)])])if(existsRoot(c))return c;throw new Error('Não encontrei game-usinagem-IOS. Use --root "C:\\caminho\\game-usinagem-IOS".');}
function read(f){return fs.readFileSync(f,'utf8');}
function has(f,tokens){if(!fs.existsSync(f))return false;const t=read(f);return tokens.every(x=>t.includes(x));}
function stamp(){return new Date().toISOString().replace(/[:.]/g,'-');}
function backup(root,broot,target){const src=path.join(root,target);if(!fs.existsSync(src))return;const dst=path.join(broot,target);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(src,dst);}
function gitStatus(root,paths){if(!fs.existsSync(path.join(root,'.git')))return null;try{return execFileSync('git',['status','--short','--',...paths],{cwd:root,encoding:'utf8'}).trim();}catch{return null;}}
function state(root){
 const studio=path.join(root,rel.studio), parity=path.join(root,rel.parity); const st=fs.existsSync(studio)?read(studio):''; const pa=fs.existsSync(parity)?read(parity):'';
 return {
   landing: pa.includes('landing_clean_v27_5') && !pa.includes('Operadores ao fundo: a landing') && !pa.includes('MachineArtworkV27("Torno Mecânico"'),
   factoryInteraction: st.includes('factory_studio_v27_5') && st.includes('StudioInteractionLayerV27_5') && st.includes('StudioTapTargetV27_5') && st.includes('workerDialogId = id') && st.includes('machineDialogId = id'),
   cameraIsolation: st.includes('var cameraMode by remember') && st.includes('if (!cameraMode)') && st.includes('MODO CÂMERA'),
   noCanvasTapPicking: !st.includes('detectTapGestures(') && !st.includes('touchedMachine') && !st.includes('touchedWorker'),
   stations: st.includes('drawStationMarkerV27_2') && st.includes('"Q", "QUALIDADE"') && st.includes('"E", "EXPEDIÇÃO"'),
   payroll: has(path.join(root,rel.store),['val monthlyPayrollCents','private fun processMonthlyPayroll','PAYROLL_MONTH_MILLIS']),
   daily: has(path.join(root,rel.store),['fun claimDailyMission(id: String)','ensureDailyMissions','dailyMissionResetRemainingMillis']),
   boost: has(path.join(root,rel.visual),['ADIANTAR 10 MIN','boost10Minutes']),
   schema4: has(path.join(root,rel.models),['val schemaVersion: Int = 4']) && has(path.join(root,rel.codec),['row("VERSION", 4)']),
   audio: audio.every(f=>fs.existsSync(path.join(root,rel.audioDir,f))),
 };
}
function printState(s){for(const [k,v] of Object.entries(s))console.log(`[V27.5] ${k}: ${v?'OK':'PENDENTE'}`);}
const root=findRoot(process.cwd());
console.log('\n[V27.5] INTERAÇÃO REAL + LANDING CLEAN • Usinagem Master iOS/KMP');
console.log(`[V27.5] raiz: ${root}`);
const before=state(root);
if(CHECK){printState(before);if(!Object.values(before).every(Boolean)){console.error('[V27.5] CHECK FALHOU.');process.exit(2);}console.log('[V27.5] CHECK OK: hit-test Compose, câmera isolada, landing limpa e schema 4 preservados.');const gs=gitStatus(root,files.map(x=>x[1]));if(gs){console.log('\n[V27.5] Alterações locais ainda não enviadas:');console.log(gs);}process.exit();}
if(DRY){console.log('[V27.5] DRY-RUN: instalaria V27.5 consolidada sobre V27.4/V27.3/V27.2.');printState(before);process.exit();}
if(Object.values(before).every(Boolean)&&!FORCE){console.log('[V27.5] Já aplicada. Use --check para validar.');process.exit();}
if(!has(path.join(root,rel.models),['data class GameSave','val schemaVersion: Int = 4']))throw new Error('Base incompatível: GameSave schema 4 não encontrado.');
for(const [payload] of files)if(!fs.existsSync(path.join(SCRIPT_DIR,payload)))throw new Error(`Payload ausente: ${payload}`);
for(const f of audio)if(!fs.existsSync(path.join(SCRIPT_DIR,'payload/audio',f)))throw new Error(`Áudio ausente: ${f}`);
const broot=path.join(root,'.patch-backups','ios-overhaul-visual-gamefeel-v27-5',stamp());
for(const [,target] of files)backup(root,broot,target);for(const f of audio)backup(root,broot,path.join(rel.audioDir,f));
for(const [payload,target] of files){const src=path.join(SCRIPT_DIR,payload),dst=path.join(root,target);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(src,dst);}for(const f of audio){const dst=path.join(root,rel.audioDir,f);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(path.join(SCRIPT_DIR,'payload/audio',f),dst);}
const after=state(root);printState(after);if(!Object.values(after).every(Boolean))throw new Error(`Validação final falhou. Backup: ${broot}`);
console.log('\n[V27.5] APLICAÇÃO OK.');
console.log(`[V27.5] backup: ${broot}`);
console.log('[V27.5] Canvas agora só desenha; clique usa hit targets Compose reais sobre máquinas, operadores e estações.');
console.log('[V27.5] INTERAGIR é o modo padrão; CÂMERA é separado para não disputar gestos.');
console.log('[V27.5] Landing sem máquinas/operadores no fundo e botões com tipografia/altura reduzidas.');
console.log('[V27.5] Salários, missões, adiantar 10 min, áudio, economia e save schema 4 preservados.');
const gs=gitStatus(root,files.map(x=>x[1]).concat(audio.map(f=>path.join(rel.audioDir,f))));if(gs){console.log('\n[V27.5] Faça commit + push antes do GitHub Actions:');console.log(gs);}
