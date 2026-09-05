#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const CHECK = flag('--check');
const DRY = flag('--dry-run');
const FORCE = flag('--force');

const rel = {
  gameApp: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/GameApp.kt',
  parity: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt',
  studio: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt',
  roulette: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/IndustrialRoulette.kt',
  avatar: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/PlayerAvatarVisual.kt',
  machineArt: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/MachineArtworkV27.kt',
  visual: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/VisualExperienceV27.kt',
  feedback: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/GameFeedback.kt',
  feedbackIos: 'ios-converted/composeApp/src/iosMain/kotlin/br/com/usinagemmaster/game/ui/GameFeedback.ios.kt',
  store: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt',
  models: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/model/PersistentGameModels.kt',
  codec: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/persistence/GameSaveCodec.kt',
  audioDir: 'iosApp/UsinagemConverted',
};

const files = [
  ['payload/common/ui/GameApp.kt', rel.gameApp],
  ['payload/common/ui/AndroidV24ParityUi.kt', rel.parity],
  ['payload/common/ui/FactoryStudio.kt', rel.studio],
  ['payload/common/ui/IndustrialRoulette.kt', rel.roulette],
  ['payload/common/ui/PlayerAvatarVisual.kt', rel.avatar],
  ['payload/common/ui/MachineArtworkV27.kt', rel.machineArt],
  ['payload/common/ui/VisualExperienceV27.kt', rel.visual],
  ['payload/common/ui/GameFeedback.kt', rel.feedback],
  ['payload/ios/ui/GameFeedback.ios.kt', rel.feedbackIos],
  ['payload/common/domain/GameStore.kt', rel.store],
  ['payload/common/model/PersistentGameModels.kt', rel.models],
  ['payload/common/persistence/GameSaveCodec.kt', rel.codec],
];
const audio = ['factory_ambient.wav','machine_tick.wav','weld_spark.wav','ui_click.wav','machine_start.wav','reward_sting.wav','quality_pass.wav'];

function existsRoot(root) {
  return fs.existsSync(path.join(root, rel.gameApp)) &&
    fs.existsSync(path.join(root, rel.store)) &&
    fs.existsSync(path.join(root, rel.models)) &&
    fs.existsSync(path.join(root, rel.codec)) &&
    fs.existsSync(path.join(root, 'iosApp/project.yml'));
}
function dirs(root) {
  try { return fs.readdirSync(root,{withFileTypes:true}).filter(x=>x.isDirectory()).map(x=>path.join(root,x.name)); }
  catch { return []; }
}
function findRoot(start) {
  const explicit = value('--root') || value('--ios-root');
  if (explicit) {
    const r=path.resolve(explicit);
    if (!existsRoot(r)) throw new Error(`Projeto iOS/KMP não encontrado em ${r}`);
    return r;
  }
  const s=path.resolve(start), p=path.dirname(s), g=path.dirname(p);
  const candidates=[s,path.join(s,'game-usinagem-IOS'),p,path.join(p,'game-usinagem-IOS'),g,path.join(g,'game-usinagem-IOS'),...dirs(s),...dirs(p)];
  for (const c of [...new Set(candidates)]) if (existsRoot(c)) return c;
  throw new Error('Não encontrei game-usinagem-IOS. Rode dentro dele ou use --root "C:\\caminho\\game-usinagem-IOS".');
}
function stamp(){return new Date().toISOString().replace(/[:.]/g,'-');}
function read(file){return fs.readFileSync(file,'utf8');}
function has(file,tokens){if(!fs.existsSync(file))return false;const t=read(file);return tokens.every(x=>t.includes(x));}
function backup(root,backupRoot,targetRel){const src=path.join(root,targetRel);if(!fs.existsSync(src))return;const dst=path.join(backupRoot,targetRel);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(src,dst);}
function gitStatus(root, paths){
  if(!fs.existsSync(path.join(root,'.git')))return null;
  try{return execFileSync('git',['status','--short','--',...paths],{cwd:root,encoding:'utf8'}).trim();}catch{return null;}
}
function state(root){
  const studioText = fs.existsSync(path.join(root,rel.studio)) ? read(path.join(root,rel.studio)) : '';
  const checks = {
    app: has(path.join(root,rel.gameApp),['game_app_visual_v27_4','DailyMissionsV27_2','ShiftCommandDeckV27','FOLHA MENSAL']),
    factoryClick: has(path.join(root,rel.studio),['factory_studio_v27_4','detectTapGestures','workerDialogId = touchedWorker.id','machineDialogId = touchedMachine.id','Offset(0f, -base * 1.90f)']),
    factoryStations: has(path.join(root,rel.studio),['drawStationMarkerV27_2','"Q", "QUALIDADE"','"P", "PREPARAÇÃO"','"E", "EXPEDIÇÃO"']),
    workerSpeech: has(path.join(root,rel.studio),['legendarySpeechEnabled','speechInterval = 60_000L + configured * 7_500L','activeWorkers[(slot % activeWorkers.size).toInt()]','studioWorkerQuoteV27(employee, worker.activity, phraseIndex)']),
    compilerFixSpeechClock: has(path.join(root,rel.studio),['import br.com.usinagemmaster.game.domain.currentTimeMillis','val nowSpeech = currentTimeMillis()']),
    roulette: has(path.join(root,rel.roulette),['roulette_visual_v27','Equipe lendária','drawRouletteGlyphV27']),
    avatar: has(path.join(root,rel.avatar),['avatar_humanized_v27','Rosto V27']),
    store: has(path.join(root,rel.store),['autoDistributeOperators','fun claimDailyMission(id: String)','fun dailyMissionProgress','ensureDailyMissions','dailyMissionResetRemainingMillis']),
    visual: has(path.join(root,rel.visual),['visual_experience_v27_4','DailyMissionsV27_2','CareerHowToProgressV27_2','TurnMetricCompactV27_2','ADIANTAR 10 MIN']),
    dailyModel: has(path.join(root,rel.models),['data class DailyMissionSave','data class DailyMissionStateSave','val dailyMissions: DailyMissionStateSave','val schemaVersion: Int = 4']),
    dailyCodec: has(path.join(root,rel.codec),['row("DAILY4"','"DM4"','dailyMissions = DailyMissionStateSave']),
    payroll: has(path.join(root,rel.store),['val monthlyPayrollCents','private fun processMonthlyPayroll','PAYROLL_MONTH_MILLIS']) && has(path.join(root,rel.models),['val lastPayrollCycle: Long = -1L']) && has(path.join(root,rel.codec),['row("PAYROLL4"','"PAYROLL4" -> lastPayrollCycle']),
    machineArt: has(path.join(root,rel.machineArt),['machine_art_v27','MachineArtworkV27']),
    feedback: has(path.join(root,rel.feedback),['GameSoundEffect','expect object GameFeedback']),
    feedbackIos: has(path.join(root,rel.feedbackIos),['AVAudioPlayer','actual object GameFeedback']),
    compilerFixBorderStroke: has(path.join(root,rel.parity),['import androidx.compose.foundation.BorderStroke']),
    compilerFixSpeechDrawText: studioText.includes('drawText(layout, topLeft') && !studioText.includes('drawText(measurer, layout'),
    schema4: has(path.join(root,rel.models),['val schemaVersion: Int = 4']) && has(path.join(root,rel.codec),['row("VERSION", 4)']),
    audio: audio.every(f=>fs.existsSync(path.join(root,rel.audioDir,f))),
  };
  return checks;
}
function printState(s){Object.entries(s).forEach(([k,v])=>console.log(`[V27.4] ${k}: ${v?'OK':'PENDENTE'}`));}

const root=findRoot(process.cwd());
console.log('\n[V27.4] FÁBRICA VIVA + MISSÕES DIÁRIAS + REFINO UI/UX • Usinagem Master iOS/KMP');
console.log(`[V27.4] raiz: ${root}`);
const before=state(root);
if(CHECK){
  printState(before);
  const ok=Object.values(before).every(Boolean);
  if(!ok){console.error('[V27.4] CHECK FALHOU.');process.exit(2);}
  console.log('[V27.4] CHECK OK: toque, estações, falas, missões diárias, carreira e schema 4 validados.');
  const st=gitStatus(root,files.map(x=>x[1]));
  if(st){console.log('\n[V27.4] Alterações locais ainda não enviadas ao GitHub:');console.log(st);console.log('[V27.4] Faça commit + push antes do Actions remoto.');}
  process.exit();
}
if(DRY){console.log('[V27.4] DRY-RUN: instalaria V27.4 consolidada (12 Kotlin + 7 WAVs), com backup e schema 4 preservado.');printState(before);process.exit();}
if(Object.values(before).every(Boolean) && !FORCE){console.log('[V27.4] Já aplicada. Use --check para validar ou --force para reinstalar.');process.exit();}

const modelText=read(path.join(root,rel.models));
if(!modelText.includes('data class GameSave') || !modelText.includes('val schemaVersion: Int = 4')) throw new Error('Base KMP incompatível: GameSave schema 4 não encontrado. Nada foi alterado.');
const storeText=read(path.join(root,rel.store));
if(!storeText.includes('class GameStore') || !storeText.includes('fun spinGacha()')) throw new Error('GameStore incompatível. Nada foi alterado.');
for(const [payload] of files){if(!fs.existsSync(path.join(SCRIPT_DIR,payload)))throw new Error(`Payload ausente: ${payload}. Extraia o ZIP inteiro.`);}
for(const f of audio){if(!fs.existsSync(path.join(SCRIPT_DIR,'payload/audio',f)))throw new Error(`Áudio ausente: ${f}`);}

const backupRoot=path.join(root,'.patch-backups','ios-overhaul-visual-gamefeel-v27-4',stamp());
for(const [,target] of files) backup(root,backupRoot,target);
for(const f of audio) backup(root,backupRoot,path.join(rel.audioDir,f));

for(const [payload,target] of files){const src=path.join(SCRIPT_DIR,payload);const dst=path.join(root,target);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(src,dst);}
for(const f of audio){const dst=path.join(root,rel.audioDir,f);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.copyFileSync(path.join(SCRIPT_DIR,'payload/audio',f),dst);}

function walk(dir,out=[]){if(!fs.existsSync(dir))return out;for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory()&&!e.name.startsWith('.patch-backups'))walk(p,out);else if(e.isFile()&&e.name.endsWith('.kt'))out.push(p);}return out;}
let typoCount=0;
for(const f of walk(path.join(root,'ios-converted/composeApp/src'))){const old=read(f);const next=old.replaceAll('Rouleta','Roleta').replaceAll('ROULETA','ROLETA');if(next!==old){fs.writeFileSync(f,next,'utf8');typoCount++;}}

const after=state(root);printState(after);
if(!Object.values(after).every(Boolean))throw new Error(`Validação final falhou. Backup preservado em ${backupRoot}`);
console.log('\n[V27.4] APLICAÇÃO OK.');
console.log(`[V27.4] backup: ${backupRoot}`);
console.log(`[V27.4] arquivos com typo Roleta corrigido: ${typoCount}`);
console.log('[V27.4] Corrigido: toque em máquinas/operadores com gesto unificado.');
console.log('[V27.4] Corrigido: estações A/F/Q/P/C/E identificadas no galpão.');
console.log('[V27.4] Corrigido: falas rotativas, uma por vez, com intervalo de 1 a 2,5 min.');
console.log('[V27.4] Corrigido: lista técnica com ações compactas.');
console.log('[V27.4] Dashboard: Precisão + Lucro + Foco como métricas principais + Adiantar 10 min restaurado.');
console.log('[V27.4] Novo: 3 missões diárias persistentes com dinheiro, ferramenta e XP do personagem.');
console.log('[V27.4] Carreira: painel explica exatamente como ganhar pontos industriais.');
console.log('[V27.4] Novo: folha salarial mensal persistente, com total e próximo pagamento.');
console.log('[V27.4] GameSave permanece schema 4.');
const st=gitStatus(root,files.map(x=>x[1]).concat(audio.map(f=>path.join(rel.audioDir,f))));
if(st){console.log('\n[V27.4] IMPORTANTE — faça commit + push antes do GitHub Actions:');console.log(st);}
