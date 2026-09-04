#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const PATCH_NAME = 'Fix conversão Android -> KMP V4';
const TARGET_REL = path.join('iosApp', 'UsinagemConverted', 'LocalSaveV23Adapter.swift');
const OLD_VERSION = 3;
const NEW_VERSION = 4;

function log(msg = '') {
  console.log(`[KMP-V4] ${msg}`);
}

function fail(msg) {
  console.error(`\n[KMP-V4] ERRO: ${msg}`);
  process.exit(1);
}

function parseArgs(argv) {
  const args = { root: null, check: false };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--check') {
      args.check = true;
      continue;
    }
    if (arg === '--root') {
      const value = argv[i + 1];
      if (!value || value.startsWith('--')) fail('Use --root "CAMINHO_DO_PROJETO_IOS".');
      args.root = path.resolve(value);
      i += 1;
      continue;
    }
    if (arg === '-h' || arg === '--help') {
      console.log(`\n${PATCH_NAME}\n\nUso:\n  node fix-conversao-backup-android-kmp-v4.mjs\n  node fix-conversao-backup-android-kmp-v4.mjs --root "C:\\caminho\\game-usinagem-IOS"\n  node fix-conversao-backup-android-kmp-v4.mjs --check\n`);
      process.exit(0);
    }
    fail(`Argumento desconhecido: ${arg}`);
  }
  return args;
}

function isFile(file) {
  try {
    return fs.statSync(file).isFile();
  } catch {
    return false;
  }
}

function unique(items) {
  return [...new Set(items.map((item) => path.resolve(item)))];
}

function candidateFiles(explicitRoot) {
  const cwd = process.cwd();
  const parent = path.dirname(cwd);
  const roots = explicitRoot
    ? [explicitRoot]
    : unique([
        cwd,
        parent,
        path.join(cwd, 'game-usinagem-IOS'),
        path.join(parent, 'game-usinagem-IOS'),
      ]);

  const candidates = [];
  for (const root of roots) {
    candidates.push(path.join(root, TARGET_REL));
    candidates.push(path.join(root, 'game-usinagem-IOS', TARGET_REL));
  }
  return unique(candidates);
}

function findTarget(explicitRoot) {
  const found = candidateFiles(explicitRoot).filter(isFile);
  if (found.length === 1) return found[0];
  if (found.length > 1) {
    fail(
      `Encontrei mais de um LocalSaveV23Adapter.swift. Rode novamente com --root apontando para o iOS correto:\n${found
        .map((f) => `  - ${f}`)
        .join('\n')}`,
    );
  }
  fail(
    `Não encontrei ${TARGET_REL}.\n` +
      'Esta correção precisa ser aplicada no projeto iOS/KMP, porque é o validador iOS que está rejeitando o save convertido.\n' +
      'Exemplo: node fix-conversao-backup-android-kmp-v4.mjs --root "C:\\Users\\PC-NOVO\\Desktop\\game-usinagem-IOS"',
  );
}

function countMatches(text, regex) {
  return [...text.matchAll(regex)].length;
}

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

const args = parseArgs(process.argv);
const target = findTarget(args.root);
let source = fs.readFileSync(target, 'utf8');

log(PATCH_NAME);
log(`arquivo: ${target}`);

const emitsV4 = /out\s*\+=\s*row\(\[\s*["']VERSION["']\s*,\s*4\s*\]\)/m.test(source);
const guardV3 = /newKmp\.contains\(\s*["']VERSION\|3["']\s*\)/g;
const guardV4 = /newKmp\.contains\(\s*["']VERSION\|4["']\s*\)/g;
const guardV3Count = countMatches(source, guardV3);
const guardV4Count = countMatches(source, guardV4);

if (!emitsV4) {
  fail(
    'O método androidToKmp() deste arquivo não está emitindo VERSION|4. ' +
      'Não vou alterar um arquivo de versão diferente automaticamente.',
  );
}

if (guardV3Count === 0 && guardV4Count >= 1) {
  log('já aplicado: o conversor emite VERSION|4 e o validador aceita VERSION|4.');
  process.exit(0);
}

if (guardV3Count !== 1) {
  fail(
    `Esperava encontrar exatamente 1 validação VERSION|${OLD_VERSION}, mas encontrei ${guardV3Count}. ` +
      'Nenhum arquivo foi alterado.',
  );
}

if (args.check) {
  log('CHECK: fix necessária. O conversor emite VERSION|4, mas o validador ainda exige VERSION|3.');
  process.exit(2);
}

const backup = `${target}.before-kmp-v4-fix-${timestamp()}.bak`;
fs.copyFileSync(target, backup);

source = source.replace(
  /newKmp\.contains\(\s*["']VERSION\|3["']\s*\)/,
  'newKmp.contains("VERSION|4")',
);

const postV3 = countMatches(source, /newKmp\.contains\(\s*["']VERSION\|3["']\s*\)/g);
const postV4 = countMatches(source, /newKmp\.contains\(\s*["']VERSION\|4["']\s*\)/g);

if (postV3 !== 0 || postV4 < 1) {
  fs.copyFileSync(backup, target);
  fail('A validação pós-patch falhou. O arquivo original foi restaurado automaticamente.');
}

fs.writeFileSync(target, source, 'utf8');

// Validação opcional do codec KMP, quando ele existe no mesmo projeto.
const iosRoot = path.resolve(target, '..', '..', '..');
const codec = path.join(
  iosRoot,
  'ios-converted',
  'composeApp',
  'src',
  'commonMain',
  'kotlin',
  'br',
  'com',
  'usinagemmaster',
  'game',
  'persistence',
  'GameSaveCodec.kt',
);

let codecStatus = 'não localizado (não impede a aplicação da fix)';
if (isFile(codec)) {
  const codecText = fs.readFileSync(codec, 'utf8');
  codecStatus = /row\(\s*["']VERSION["']\s*,\s*4\s*\)/m.test(codecText)
    ? 'OK: GameSaveCodec também grava VERSION 4'
    : 'ATENÇÃO: GameSaveCodec encontrado, mas não identifiquei row("VERSION", 4)';
}

log('APLICADO COM SUCESSO.');
log(`backup: ${backup}`);
log(`codec KMP: ${codecStatus}`);
log('alteração: validação do restore Android mudou de VERSION|3 para VERSION|4.');
log('nenhum arquivo do projeto Android foi alterado: o erro estava no validador da conversão no iOS.');
log('agora faça um novo build do iOS e teste novamente "Sincronizar/Restaurar" usando o backup Android.');
