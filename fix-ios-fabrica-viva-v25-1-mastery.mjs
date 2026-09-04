#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const value = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : null;
};

const CHECK = flag('--check');
const DRY = flag('--dry-run');
const FORCE = flag('--force');

const REL = 'ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt';

function targetExists(root) {
  return fs.existsSync(path.join(root, REL));
}

function childDirs(root) {
  try {
    return fs.readdirSync(root, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => path.join(root, e.name));
  } catch {
    return [];
  }
}

function findRoot(start) {
  const explicit = value('--root') || value('--ios-root');
  if (explicit) {
    const r = path.resolve(explicit);
    if (!targetExists(r)) {
      throw new Error(`Projeto iOS/KMP não encontrado em: ${r}`);
    }
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
    ...childDirs(s),
    ...childDirs(parent),
  ];

  for (const c of [...new Set(candidates)]) {
    if (targetExists(c)) return c;
  }

  throw new Error(
    'Não encontrei o projeto iOS/KMP. Rode dentro de game-usinagem-IOS ' +
    'ou use --root "C:\\caminho\\game-usinagem-IOS".'
  );
}

function stamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

function count(text, needle) {
  return text.split(needle).length - 1;
}

const root = findRoot(process.cwd());
const file = path.join(root, REL);

let text = fs.readFileSync(file, 'utf8');

const OLD = 'val mastery = store.state.career.mastery(machine.machineType)';
const FIXED = 'val mastery = MachineMastery(machine.machineType, store.state.career.masteryXp[machine.machineType] ?: 0)';
const IMPORT = 'import br.com.usinagemmaster.game.model.MachineMastery';
const IMPORT_ANCHOR = 'import br.com.usinagemmaster.game.model.MachineSave';

const oldCount = count(text, OLD);
const fixedCount = count(text, FIXED);
const hasImport = text.includes(IMPORT);
const isV25 = text.includes('factory_studio_v25');

console.log('\n[V25.1] Hotfix Kotlin/Native • mastery da Fábrica Viva');
console.log(`[V25.1] raiz: ${root}`);
console.log(`[V25.1] FactoryStudio V25: ${isV25 ? 'detectado' : 'não detectado'}`);
console.log(`[V25.1] chamadas antigas mastery(): ${oldCount}`);
console.log(`[V25.1] usos corrigidos MachineMastery: ${fixedCount}`);
console.log(`[V25.1] import MachineMastery: ${hasImport ? 'OK' : 'pendente'}`);

const applied = oldCount === 0 && fixedCount >= 2 && hasImport;

if (CHECK) {
  if (applied) {
    console.log('[V25.1] CHECK OK. Hotfix já aplicada.');
  } else {
    console.log('[V25.1] CHECK PENDENTE.');
    process.exitCode = 2;
  }
  process.exit();
}

if (applied) {
  console.log('[V25.1] Nenhuma alteração necessária.');
  process.exit();
}

if (!isV25 && !FORCE) {
  throw new Error(
    'Esta hotfix foi criada para a FactoryStudio V25. O marcador factory_studio_v25 não foi encontrado. ' +
    'Nada foi alterado. Use --force somente se souber que o arquivo corresponde à V25.'
  );
}

if (oldCount !== 2 && !FORCE) {
  throw new Error(
    `Esperava exatamente 2 ocorrências da chamada problemática mastery(), encontrei ${oldCount}. ` +
    'Nada foi alterado para evitar sobrescrever uma versão diferente.'
  );
}

if (!hasImport && !text.includes(IMPORT_ANCHOR) && !FORCE) {
  throw new Error(
    'Não encontrei o ponto seguro para inserir o import MachineMastery. Nada foi alterado.'
  );
}

if (DRY) {
  console.log('[V25.1] DRY-RUN: corrigiria as 2 referências mastery e adicionaria o import MachineMastery.');
  process.exit();
}

const backupDir = path.join(
  root,
  '.patch-backups',
  'ios-fabrica-viva-v25-1-mastery',
  stamp(),
  path.dirname(REL)
);
fs.mkdirSync(backupDir, { recursive: true });
const backupFile = path.join(backupDir, path.basename(REL));
fs.copyFileSync(file, backupFile);

if (!text.includes(IMPORT)) {
  text = text.replace(IMPORT_ANCHOR, `${IMPORT_ANCHOR}\n${IMPORT}`);
}

text = text.split(OLD).join(FIXED);

fs.writeFileSync(file, text, 'utf8');

const finalText = fs.readFileSync(file, 'utf8');
const finalOld = count(finalText, OLD);
const finalFixed = count(finalText, FIXED);

if (finalOld !== 0 || finalFixed < 2 || !finalText.includes(IMPORT)) {
  throw new Error(
    'Validação final falhou. O backup foi preservado em: ' + backupFile
  );
}

console.log('[V25.1] aplicado com sucesso.');
console.log(`[V25.1] backup: ${backupFile}`);
console.log('[V25.1] corrigido: unresolved reference mastery (linhas ~1196 e ~1304).');
console.log('[V25.1] consequência corrigida: unresolved reference times no cálculo de skillAssist.');
console.log('[V25.1] não altera: GameStore, engine, economia, contratos, save KMP V4 ou Firebase.');
console.log('[V25.1] faça uma nova build iOS/Kotlin Native.');
