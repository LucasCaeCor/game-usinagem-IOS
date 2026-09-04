#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : null;
};
const FORCE = flag('--force');
const CHECK = flag('--check');
const DRY = flag('--dry-run');

const REL = {
  studio: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt',
  store: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt',
  sim: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/domain/simulation/FactorySimulation.kt',
  ownerSim: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/domain/simulation/FactoryOwnerSimulation.kt',
  gameplay: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/AndroidV24Gameplay.kt',
  models: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/model/PersistentGameModels.kt',
};

const EXPECTED_PRE_V25 = {
  studio: 'a83b47fa3c1623e2d2fd1bab353c4149484c8bca753065723b8363b2665bbebc',
  store: '5f691f412542231d309b95294afc89bbf2495a0b213b2766f53b7ff931f1ebe2',
};

const BAD_CALL = 'val mastery = store.state.career.mastery(machine.machineType)';
const GOOD_CALL = 'val mastery = MachineMastery(machine.machineType, store.state.career.masteryXp[machine.machineType] ?: 0)';
const GOOD_IMPORT = 'import br.com.usinagemmaster.game.domain.MachineMastery';
const WRONG_IMPORT = 'import br.com.usinagemmaster.game.model.MachineMastery';
const IMPORT_ANCHOR = 'import br.com.usinagemmaster.game.domain.MinigameResult';

function sha(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}
function count(text, needle) {
  return text.split(needle).length - 1;
}
function existsRoot(root) {
  return fs.existsSync(path.join(root, REL.studio)) && fs.existsSync(path.join(root, REL.store));
}
function dirsOf(root) {
  try {
    return fs.readdirSync(root, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => path.join(root, e.name));
  } catch { return []; }
}
function findRoot(start) {
  const explicit = value('--root') || value('--ios-root');
  if (explicit) {
    const r = path.resolve(explicit);
    if (!existsRoot(r)) throw new Error(`Projeto iOS/KMP não encontrado em: ${r}`);
    return r;
  }
  const s = path.resolve(start);
  const parent = path.dirname(s);
  const grand = path.dirname(parent);
  const candidates = [
    s, path.join(s, 'game-usinagem-IOS'),
    parent, path.join(parent, 'game-usinagem-IOS'),
    grand, path.join(grand, 'game-usinagem-IOS'),
    ...dirsOf(s), ...dirsOf(parent),
  ];
  for (const c of [...new Set(candidates)]) if (existsRoot(c)) return c;
  throw new Error('Não encontrei game-usinagem-IOS. Rode dentro dele ou use --root "C:\\caminho\\game-usinagem-IOS".');
}
function requireTokens(file, tokens, label) {
  if (!fs.existsSync(file)) throw new Error(`${label} ausente: ${file}`);
  const text = fs.readFileSync(file, 'utf8');
  const missing = tokens.filter((t) => !text.includes(t));
  if (missing.length) throw new Error(`${label} incompatível. Faltando: ${missing.join(', ')}`);
}
function stamp() { return new Date().toISOString().replace(/[:.]/g, '-'); }
function backupFile(root, backupRoot, rel) {
  const from = path.join(root, rel);
  const to = path.join(backupRoot, rel);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
}
function gitInfo(root, rels) {
  if (!fs.existsSync(path.join(root, '.git'))) return null;
  try {
    const status = execFileSync('git', ['status', '--short', '--', ...rels], { cwd: root, encoding: 'utf8' }).trim();
    return status;
  } catch {
    return null;
  }
}

const root = findRoot(process.cwd());
const studio = path.join(root, REL.studio);
const store = path.join(root, REL.store);
const payloadStudio = path.join(SCRIPT_DIR, 'payload', 'FactoryStudio.kt');
const payloadStore = path.join(SCRIPT_DIR, 'payload', 'GameStore.kt');
if (!fs.existsSync(payloadStudio) || !fs.existsSync(payloadStore)) {
  throw new Error('Payload incompleto. Extraia o ZIP mantendo a pasta payload ao lado do .mjs.');
}

// Garante que a base KMP esperada existe e que MachineMastery realmente pertence a game.domain.
requireTokens(path.join(root, REL.sim), [
  'enum class WorkerActivity', 'FETCHING_MATERIAL', 'FETCHING_TOOLS', 'SETTING_UP',
  'CARRYING_PART', 'INSPECTING', 'PACKING', 'PHONE', 'class FactoryFloor', 'fun route(',
], 'FactorySimulation KMP');
requireTokens(path.join(root, REL.ownerSim), [
  'enum class OwnerActivity', 'COLLECTING', 'LOADING', 'DELIVERING', 'UNLOADING',
  'AWAITING_PAYMENT', 'RETURNING', 'class FactoryOwnerSimulation',
], 'FactoryOwnerSimulation KMP');
requireTokens(path.join(root, REL.gameplay), [
  'data class MachineMastery', 'enum class ProductionStage', 'MACHINED', 'WAITING_QC', 'QC',
  'APPROVED', 'REWORK', 'READY_TO_SHIP', 'object MachineMinigameCatalog',
], 'AndroidV24Gameplay / MachineMastery');
requireTokens(path.join(root, REL.models), [
  'data class OwnerWorkBatchSave', 'val schemaVersion: Int = 4', 'data class EmployeeSave',
], 'Modelos de save KMP V4');

let studioText = fs.readFileSync(studio, 'utf8');
let storeText = fs.readFileSync(store, 'utf8');
const preV25Studio = sha(studio) === EXPECTED_PRE_V25.studio;
const preV25Store = sha(store) === EXPECTED_PRE_V25.store;
const hasV25 = studioText.includes('factory_studio_v25');
const badCalls = count(studioText, BAD_CALL);
const goodCalls = count(studioText, GOOD_CALL);
const hasGoodImport = studioText.includes(GOOD_IMPORT);
const hasWrongImport = studioText.includes(WRONG_IMPORT);
const storeCompatible = storeText.includes('fun reprimandEmployee(id: String)') &&
  storeText.includes('fun assignEmployeeToMachine(employeeId: String, machineId: String)') &&
  storeText.includes('fun clearMachineOperator(machineId: String)');

console.log('\n[V25.2] Fábrica Viva iOS • consolidada + Kotlin/Native mastery');
console.log(`[V25.2] raiz: ${root}`);
console.log(`[V25.2] FactoryStudio V25: ${hasV25 ? 'sim' : 'não'}`);
console.log(`[V25.2] mastery antigo inválido: ${badCalls}`);
console.log(`[V25.2] mastery corrigido: ${goodCalls}`);
console.log(`[V25.2] import correto game.domain: ${hasGoodImport ? 'sim' : 'não'}`);
console.log(`[V25.2] import incorreto game.model: ${hasWrongImport ? 'SIM (será removido)' : 'não'}`);
console.log(`[V25.2] GameStore APIs V25: ${storeCompatible ? 'OK' : 'pendente'}`);

function finalValid(text) {
  return text.includes('factory_studio_v25') &&
    count(text, BAD_CALL) === 0 &&
    count(text, GOOD_CALL) >= 2 &&
    text.includes(GOOD_IMPORT) &&
    !text.includes(WRONG_IMPORT);
}

if (CHECK) {
  const ok = finalValid(studioText) && storeCompatible;
  if (ok) {
    console.log('[V25.2] CHECK OK: código local pronto para compilar.');
    const st = gitInfo(root, [REL.studio, REL.store]);
    if (st) {
      console.log('[V25.2] ATENÇÃO: há alterações locais ainda não commitadas:');
      console.log(st);
      console.log('[V25.2] O GitHub Actions só verá a fix depois de commit + push.');
    }
  } else {
    console.error('[V25.2] CHECK FALHOU: a correção ainda não está completa neste diretório.');
    process.exitCode = 2;
  }
  process.exit();
}

if (DRY) {
  if (preV25Studio) console.log('[V25.2] DRY-RUN: instalaria FactoryStudio V25.2 completo.');
  else if (hasV25) console.log('[V25.2] DRY-RUN: preservaria a V25 atual e corrigiria mastery/import in-place.');
  else console.log('[V25.2] DRY-RUN: FactoryStudio não reconhecido; aplicação real exigirá --force.');
  if (!storeCompatible) console.log('[V25.2] DRY-RUN: instalaria GameStore V25.');
  process.exit();
}

if (!preV25Studio && !hasV25 && !FORCE) {
  throw new Error('FactoryStudio.kt não é o original esperado nem uma V25 reconhecida. Nada foi alterado. Use --force somente conscientemente.');
}
if (!storeCompatible && !preV25Store && !FORCE) {
  throw new Error('GameStore.kt difere da base enviada e não possui as APIs V25. Nada foi alterado. Use --force somente conscientemente.');
}

const backupRoot = path.join(root, '.patch-backups', 'ios-fabrica-viva-paridade-v25-2', stamp());
backupFile(root, backupRoot, REL.studio);
if (!storeCompatible) backupFile(root, backupRoot, REL.store);

if (preV25Studio || (!hasV25 && FORCE)) {
  fs.copyFileSync(payloadStudio, studio);
} else {
  // V25 existente: corrige apenas o ponto que quebrou Kotlin/Native e preserva alterações posteriores.
  let t = fs.readFileSync(studio, 'utf8');
  t = t.split(WRONG_IMPORT + '\n').join('');
  t = t.split(BAD_CALL).join(GOOD_CALL);
  if (!t.includes(GOOD_IMPORT)) {
    if (!t.includes(IMPORT_ANCHOR) && !FORCE) {
      throw new Error('Não achei ponto seguro para import MachineMastery. Backup preservado em ' + backupRoot);
    }
    t = t.replace(IMPORT_ANCHOR, IMPORT_ANCHOR + '\n' + GOOD_IMPORT);
  }
  fs.writeFileSync(studio, t, 'utf8');
}

if (!storeCompatible) fs.copyFileSync(payloadStore, store);

studioText = fs.readFileSync(studio, 'utf8');
storeText = fs.readFileSync(store, 'utf8');
if (!finalValid(studioText)) {
  throw new Error('Validação final do FactoryStudio falhou. Backup preservado em: ' + backupRoot);
}
requireTokens(store, [
  'fun reprimandEmployee(id: String)',
  'fun assignEmployeeToMachine(employeeId: String, machineId: String)',
  'fun clearMachineOperator(machineId: String)',
], 'GameStore V25');

console.log('[V25.2] APLICAÇÃO OK.');
console.log(`[V25.2] backup: ${backupRoot}`);
console.log('[V25.2] 0 chamadas career.mastery(machineType) inválidas.');
console.log('[V25.2] MachineMastery importado de br.com.usinagemmaster.game.domain.');
console.log('[V25.2] save KMP V4, economia, engine, Firebase e contratos preservados.');

const st = gitInfo(root, [REL.studio, REL.store]);
if (st) {
  console.log('\n[V25.2] IMPORTANTE — arquivos modificados no Git:');
  console.log(st);
  console.log('[V25.2] Antes de rodar o Actions, faça commit e push dessas alterações.');
  console.log('[V25.2] Depois confirme com: node aplicar-ios-fabrica-viva-paridade-android-v25-2.mjs --check');
} else if (st === '') {
  console.log('[V25.2] Git não mostra diff nesses arquivos (eles podem já estar commitados).');
}
