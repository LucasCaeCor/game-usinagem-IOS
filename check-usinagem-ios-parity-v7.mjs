#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const common = path.join(root, "ios-converted/composeApp/src/commonMain/kotlin");
const ios = path.join(root, "ios-converted/composeApp/src/iosMain/kotlin");

function fail(message) {
  console.error(`[PARITY V7] ERRO: ${message}`);
  process.exitCode = 1;
}

if (!fs.existsSync(common) || !fs.existsSync(ios)) {
  console.error("[PARITY V7] Execute na raiz do game-usinagem-IOS.");
  process.exit(1);
}

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const p = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(p) : [p];
  });
}

const files = [...walk(common), ...walk(ios)].filter(p => /\.(kt|kts)$/i.test(p));
const text = files.map(p => fs.readFileSync(p, "utf8")).join("\n");

const gates = [
  ["Save iOS", ["PlatformSaveStorage", "NSUserDefaults", "GameSaveCodec"]],
  ["Schema V6→V7", ["schemaVersion", "PROFILE3", "EXP3"]],
  ["Produção", ["ProductionEngine", "boostedProfit", "CYCLE_MILLIS"]],
  ["Fábrica operacional", ["FactorySimulation", "FactoryOwnerSimulation", "WorkerActivity", "FactoryMachineState"]],
  ["Carga/entrega", ["cargoInTransit", "AWAITING_PAYMENT", "settleCargoDelivery"]],
  ["Máquinas", ["buyMachine", "repairMachine", "sellMachine", "moveMachineNext"]],
  ["Funcionários", ["hireEmployee", "assignEmployeeNext", "restEmployee", "fireEmployee"]],
  ["Disciplina", ["idleEmployeeId", "reprimandIdleEmployee", "SNACK_IMMUNITY_MILLIS"]],
  ["Turnos/exaustão", ["ShiftMode", "advanceFatigue", "CONTINUOUS_24H"]],
  ["Contratos", ["acceptContract", "cancelContract", "special", "contractLockReason"]],
  ["Ferramentas", ["contractTools", "bindTool", "consumeBoundTool"]],
  ["Metas late-game", ["thirty_machines", "reputation_500", "warehouse_500"]],
  ["Especializações", ["cnc_torno", "cnc_fresagem", "gemeo_digital"]],
  ["Roleta/pity", ["pityEpic", "pityLegendary", "forcedLegendary", "gachaTickets"]],
  ["Personagem/skins", ["PRINCESA", "PINOQUIO", "TATUZAO", "MAGRAO", "KENDAO_KIMONO"]],
  ["Minigame", ["MINIGAME_COOLDOWN_MILLIS", "settlePrecisionMinigame"]],
  ["UI industrial", ["UsinagemMasterTheme", "NavigationBar", "FactoryStudio"]],
  ["Zoom/pan", ["detectTransformGestures", "onDoubleTap"]],
  ["Comunidade", ["Multiplayer assíncrono", "FIREBASE iOS NÃO CONFIGURADO"]],
];

console.log("# Usinagem Master iOS — Parity Gate V7\n");
let failed = 0;
for (const [label, tokens] of gates) {
  const missing = tokens.filter(token => !text.includes(token));
  if (missing.length) {
    failed++;
    console.log(`❌ ${label}: faltou ${missing.join(", ")}`);
  } else {
    console.log(`✅ ${label}`);
  }
}

const forbidden = [
  "Droid2iOS conversion ready",
  "MigrationSection(",
  "será migrado",
  "nas próximas etapas",
  "use o agente de estúdio",
];
for (const token of forbidden) {
  if (text.toLowerCase().includes(token.toLowerCase())) {
    failed++;
    console.log(`❌ Placeholder encontrado: ${token}`);
  }
}

const explicitWeight = text.includes("import androidx.compose.foundation.layout.weight");
if (explicitWeight) {
  failed++;
  console.log("❌ Import explícito incompatível de layout.weight reapareceu.");
}

console.log("");
if (failed === 0) {
  console.log("✅ PARIDADE LOCAL V7: todos os gates estruturais passaram.");
  console.log("ℹ️ Firebase online continua um gate externo: requer configuração/credenciais iOS reais.");
} else {
  fail(`${failed} gate(s) não passaram.`);
}
