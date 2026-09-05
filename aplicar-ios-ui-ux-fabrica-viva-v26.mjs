#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : null;
};
const CHECK = flag('--check');
const DRY = flag('--dry-run');
const FORCE = flag('--force');

const REL = {
  studio: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt',
  store: 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt',
};

function existsRoot(root) {
  return fs.existsSync(path.join(root, REL.studio)) && fs.existsSync(path.join(root, REL.store));
}
function dirsOf(root) {
  try {
    return fs.readdirSync(root, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(root, e.name));
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
  const candidates = [s, path.join(s, 'game-usinagem-IOS'), parent, path.join(parent, 'game-usinagem-IOS'), grand, path.join(grand, 'game-usinagem-IOS'), ...dirsOf(s), ...dirsOf(parent)];
  for (const c of [...new Set(candidates)]) if (existsRoot(c)) return c;
  throw new Error('Não encontrei game-usinagem-IOS. Rode dentro dele ou use --root "C:\\caminho\\game-usinagem-IOS".');
}
function stamp() { return new Date().toISOString().replace(/[:.]/g, '-'); }
function backup(root, rel, backupRoot) {
  const src = path.join(root, rel);
  const dst = path.join(backupRoot, rel);
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  fs.copyFileSync(src, dst);
}
function walkKt(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walkKt(full, out);
    else if (entry.isFile() && entry.name.endsWith('.kt')) out.push(full);
  }
  return out;
}
function patchGenericLabels(root) {
  const srcRoot = path.join(root, 'ios-converted/composeApp/src');
  let changed = 0;
  for (const file of walkKt(srcRoot)) {
    const before = fs.readFileSync(file, 'utf8');
    let after = before.replaceAll('Rouleta', 'Roleta').replaceAll('Contratar lendário', 'Lendários pela Roleta');
    if (after !== before) {
      fs.writeFileSync(file, after, 'utf8');
      changed++;
    }
  }
  return changed;
}
function validateFile(file, tokens, label) {
  const text = fs.readFileSync(file, 'utf8');
  const missing = tokens.filter((t) => !text.includes(t));
  if (missing.length) throw new Error(`${label} inválido. Faltando: ${missing.join(', ')}`);
}

const root = findRoot(process.cwd());
const studio = path.join(root, REL.studio);
const store = path.join(root, REL.store);
const payloadStudio = path.join(SCRIPT_DIR, 'payload', 'FactoryStudio.kt');
const payloadStore = path.join(SCRIPT_DIR, 'payload', 'GameStore.kt');
if (!fs.existsSync(payloadStudio) || !fs.existsSync(payloadStore)) {
  throw new Error('Payload incompleto. Extraia o ZIP mantendo a pasta payload.');
}

console.log('\n[V26] Fábrica Viva + UI/UX visual');
console.log(`[V26] raiz: ${root}`);

if (CHECK) {
  try {
    validateFile(studio, ['factory_studio_v26', 'AÇÕES DO TURNO', 'Melhor operador', 'Auto distribuir', '🎟'], 'FactoryStudio V26');
    validateFile(store, ['fun autoDistributeOperators()', 'fun assignBestOperator(machineId: String)', 'fun operatorFitScore(employeeId: String, machineId: String): Int', 'Equipe lendária não é contratada direto', 'grouped'], 'GameStore V26');
    console.log('[V26] CHECK OK.');
    process.exit();
  } catch (e) {
    console.error('[V26] CHECK FALHOU:', e.message);
    process.exit(2);
  }
}

if (DRY) {
  console.log('[V26] DRY-RUN: substituiria FactoryStudio.kt e GameStore.kt pela V26 e corrigiria rótulos genéricos (Rouleta -> Roleta).');
  process.exit();
}

const backupRoot = path.join(root, '.patch-backups', 'ios-ui-ux-fabrica-viva-v26', stamp());
backup(root, REL.studio, backupRoot);
backup(root, REL.store, backupRoot);

fs.copyFileSync(payloadStudio, studio);
fs.copyFileSync(payloadStore, store);
const genericChanges = patchGenericLabels(root);

validateFile(studio, ['factory_studio_v26', 'AÇÕES DO TURNO', 'Melhor operador', 'Auto distribuir', '🎟'], 'FactoryStudio V26');
validateFile(store, ['fun autoDistributeOperators()', 'fun assignBestOperator(machineId: String)', 'fun operatorFitScore(employeeId: String, machineId: String): Int', 'Equipe lendária não é contratada direto', 'grouped'], 'GameStore V26');

console.log('[V26] APLICAÇÃO OK.');
console.log(`[V26] backup: ${backupRoot}`);
console.log(`[V26] rótulos genéricos corrigidos: ${genericChanges}`);
console.log('[V26] principais entregas:');
console.log(' - fábrica viva menos inclinada e com área útil maior;');
console.log(' - HUD compacta, timers de ficha/bônus/modo foco;');
console.log(' - ações do turno reescritas de forma clara;');
console.log(' - operador selecionado agora exibe frase/interação;');
console.log(' - lista técnica com score por operador;');
console.log(' - botão melhor operador e auto distribuir equipe;');
console.log(' - lendários somente por roleta/missões;');
console.log(' - dinheiro com separador de milhar.');
console.log('[V26] depois rode sua build iOS novamente.');
