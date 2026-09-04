#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
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

const EXPECTED_ORIGINAL = {
  studio: 'a83b47fa3c1623e2d2fd1bab353c4149484c8bca753065723b8363b2665bbebc',
  store: '5f691f412542231d309b95294afc89bbf2495a0b213b2766f53b7ff931f1ebe2',
};

function sha(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
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
    s,
    path.join(s, 'game-usinagem-IOS'),
    parent,
    path.join(parent, 'game-usinagem-IOS'),
    grand,
    path.join(grand, 'game-usinagem-IOS'),
    ...dirsOf(s),
    ...dirsOf(parent),
  ];
  for (const c of [...new Set(candidates)]) if (existsRoot(c)) return c;
  throw new Error(
    'Não encontrei o game-usinagem-IOS. Rode dentro dele ou use --root "C:\\caminho\\game-usinagem-IOS".'
  );
}
function requireTokens(file, tokens, label) {
  if (!fs.existsSync(file)) throw new Error(`${label} ausente: ${file}`);
  const text = fs.readFileSync(file, 'utf8');
  const missing = tokens.filter((t) => !text.includes(t));
  if (missing.length) throw new Error(`${label} incompatível. Faltando: ${missing.join(', ')}`);
}
function stamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}
function copyBackup(root, backup, rel) {
  const from = path.join(root, rel);
  const to = path.join(backup, rel);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
}

const root = findRoot(process.cwd());
const studio = path.join(root, REL.studio);
const store = path.join(root, REL.store);
const payloadStudio = path.join(SCRIPT_DIR, 'payload', 'FactoryStudio.kt');
const payloadStore = path.join(SCRIPT_DIR, 'payload', 'GameStore.kt');
if (!fs.existsSync(payloadStudio) || !fs.existsSync(payloadStore)) {
  throw new Error('Payload da fix incompleto. Extraia o ZIP mantendo a pasta payload ao lado do .mjs.');
}

// Gate de engine: a fix de UI depende destas regras já presentes no KMP.
requireTokens(path.join(root, REL.sim), [
  'enum class WorkerActivity', 'FETCHING_MATERIAL', 'FETCHING_TOOLS', 'SETTING_UP',
  'CARRYING_PART', 'INSPECTING', 'PACKING', 'PHONE', 'class FactoryFloor', 'fun route(',
], 'FactorySimulation KMP');
requireTokens(path.join(root, REL.ownerSim), [
  'enum class OwnerActivity', 'COLLECTING', 'LOADING', 'DELIVERING', 'UNLOADING',
  'AWAITING_PAYMENT', 'RETURNING', 'class FactoryOwnerSimulation',
], 'FactoryOwnerSimulation KMP');
requireTokens(path.join(root, REL.gameplay), [
  'enum class ProductionStage', 'MACHINED', 'WAITING_QC', 'QC', 'APPROVED',
  'REWORK', 'READY_TO_SHIP', 'object MachineMinigameCatalog',
], 'Gameplay KMP');
requireTokens(path.join(root, REL.models), [
  'data class OwnerWorkBatchSave', 'val schemaVersion: Int = 4', 'data class EmployeeSave',
], 'Modelos de save KMP V4');

const currentStudioHash = sha(studio);
const currentStoreHash = sha(store);
const payloadStudioHash = sha(payloadStudio);
const payloadStoreHash = sha(payloadStore);
const studioText = fs.readFileSync(studio, 'utf8');
const storeText = fs.readFileSync(store, 'utf8');
const studioApplied = currentStudioHash === payloadStudioHash || studioText.includes('factory_studio_v25');
const storeCompatible = currentStoreHash === payloadStoreHash || (
  storeText.includes('fun reprimandEmployee(id: String)') &&
  storeText.includes('fun assignEmployeeToMachine(employeeId: String, machineId: String)') &&
  storeText.includes('fun clearMachineOperator(machineId: String)')
);

console.log('\n[V25] Fábrica Viva iOS ← paridade Android');
console.log(`[V25] raiz: ${root}`);
console.log(`[V25] engine KMP: OK (rotas, máquinas, funcionários, dono e save V4)`);
console.log(`[V25] FactoryStudio: ${studioApplied ? 'já aplicado/compatível' : 'pendente'}`);
console.log(`[V25] GameStore: ${storeCompatible ? 'já aplicado/compatível' : 'pendente'}`);

if (CHECK) {
  if (!studioApplied || !storeCompatible) process.exitCode = 2;
  else console.log('[V25] CHECK OK.');
  process.exit();
}

if (!studioApplied && currentStudioHash !== EXPECTED_ORIGINAL.studio && !FORCE) {
  throw new Error(
    'FactoryStudio.kt difere do projeto enviado. Nada foi sobrescrito. ' +
    'Revise suas alterações locais ou use --force conscientemente.'
  );
}
if (!storeCompatible && currentStoreHash !== EXPECTED_ORIGINAL.store && !FORCE) {
  throw new Error(
    'GameStore.kt difere do projeto enviado e ainda não possui as APIs V25. Nada foi sobrescrito. ' +
    'Revise suas alterações locais ou use --force conscientemente.'
  );
}

const willStudio = !studioApplied;
const willStore = !storeCompatible;
if (!willStudio && !willStore) {
  console.log('[V25] Nenhuma alteração necessária.');
  process.exit();
}
if (DRY) {
  console.log(`[V25] DRY-RUN: substituiria ${[willStudio && 'FactoryStudio.kt', willStore && 'GameStore.kt'].filter(Boolean).join(' + ')}`);
  process.exit();
}

const backup = path.join(root, '.patch-backups', 'ios-fabrica-viva-paridade-v25', stamp());
if (willStudio) copyBackup(root, backup, REL.studio);
if (willStore) copyBackup(root, backup, REL.store);

if (willStudio) fs.copyFileSync(payloadStudio, studio);
if (willStore) fs.copyFileSync(payloadStore, store);

const finalStudio = fs.readFileSync(studio, 'utf8');
const finalStore = fs.readFileSync(store, 'utf8');
requireTokens(studio, [
  'factory_studio_v25', 'StudioMachineManagementDialog', 'StudioMachineMinigameDialog',
  'StudioQualityInspectionDialog', 'studioEmployeeAvatar', 'reprimandTargetId',
  'store.startCargoDelivery()', 'store.moveOwnerBatchToQuality()', 'store.packOwnerBatch()',
  'store.shipOwnerBatch()',
], 'FactoryStudio V25');
requireTokens(store, [
  'fun reprimandEmployee(id: String)',
  'fun assignEmployeeToMachine(employeeId: String, machineId: String)',
  'fun clearMachineOperator(machineId: String)',
], 'GameStore V25');

console.log(`[V25] aplicado: ${[willStudio && 'FactoryStudio.kt', willStore && 'GameStore.kt'].filter(Boolean).join(' + ')}`);
console.log(`[V25] backup: ${backup}`);
console.log('[V25] mantém: economia, produção automática, save KMP V4 e engine de simulação.');
console.log('[V25] adiciona: toque em máquina/funcionário, bronca com rota do dono, Q→P→E, operação/retrabalho, QC, operador direto e visual avançado.');
console.log('[V25] pronto. Agora faça uma nova build do iOS.');
