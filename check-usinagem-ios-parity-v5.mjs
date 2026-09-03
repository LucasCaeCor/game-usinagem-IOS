#!/usr/bin/env node
/**
 * Usinagem Master iOS — Parity Gate V5
 *
 * Este patch NÃO declara a migração concluída.
 * Ele cria um gate objetivo de paridade para impedir que a versão iOS
 * seja tratada como "pronta" enquanto módulos do Android ainda forem placeholders.
 *
 * Rode na raiz do game-usinagem-IOS.
 */
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

const manifest = {
  version: 1,
  sourceOfTruth: "Android atual do repositório + README do Usinagem Master",
  systems: [
    { id: "dashboard", label: "Dashboard", tokens: ["DashboardStatus", "Produção", "Contratos"] },
    { id: "factory", label: "Fábrica Viva", tokens: ["Fábrica", "máquina", "operador"] },
    { id: "machines", label: "Máquinas", tokens: ["Machine", "condition", "operating"] },
    { id: "employees", label: "Funcionários", tokens: ["Employee", "exaust", "descanso"] },
    { id: "contracts", label: "Contratos", tokens: ["Contract", "reward", "penalty"] },
    { id: "production", label: "Produção/economia", tokens: ["ProductionEngine", "EconomyBalance"] },
    { id: "worklife", label: "Turnos/exaustão", tokens: ["WorkLife", "factoryOpen", "efficiency"] },
    { id: "finance", label: "Finanças", tokens: ["finance", "transaction", "cash"] },
    { id: "facility", label: "Expansão do galpão", tokens: ["warehouse", "upgrade", "facility"] },
    { id: "goals", label: "Metas/progressão", tokens: ["Goal", "reputation", "level"] },
    { id: "skills", label: "Pesquisa/especializações", tokens: ["Skill", "specialty", "specialization"] },
    { id: "tools", label: "Ferramentas", tokens: ["Tool", "qualityBonus", "speedMultiplier"] },
    { id: "gacha", label: "Roleta Industrial", tokens: ["Gacha", "pity", "ticket"] },
    { id: "character", label: "Personagem/skins", tokens: ["Skin", "Character", "Player"] },
    { id: "community", label: "Comunidade/social", tokens: ["Social", "Community", "Firebase"] },
    { id: "persistence", label: "Persistência multiplataforma", tokens: ["Repository", "save", "persist"] }
  ]
};

const targetRoot = path.join(root, "ios-converted", "composeApp", "src");
if (!fs.existsSync(targetRoot)) {
  console.error("[parity-v5] Execute na raiz do game-usinagem-IOS.");
  process.exit(1);
}

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else if (/\.(kt|kts|swift|plist|md)$/i.test(entry.name)) out.push(p);
  }
  return out;
}

const files = walk(targetRoot);
const aggregate = files
  .map(file => {
    try { return fs.readFileSync(file, "utf8"); }
    catch { return ""; }
  })
  .join("\n");

const appPath = path.join(targetRoot, "commonMain", "kotlin", "App.kt");
const app = fs.existsSync(appPath) ? fs.readFileSync(appPath, "utf8") : "";

const placeholderPatterns = [
  "Droid2iOS conversion ready",
  "será migrado",
  "será migrada",
  "nas próximas etapas",
  "ainda não migrado",
  "ainda não migrada",
  "MigrationSection("
];

const placeholders = placeholderPatterns.filter(p => app.toLowerCase().includes(p.toLowerCase()));

const rows = manifest.systems.map(system => {
  const hits = system.tokens.filter(token => aggregate.toLowerCase().includes(token.toLowerCase()));
  return {
    ...system,
    hits,
    status: hits.length >= Math.min(2, system.tokens.length) ? "FOUND" : "MISSING"
  };
});

const report = [
  "# iOS Feature Parity — Usinagem Master",
  "",
  `Gerado em: ${new Date().toISOString()}`,
  "",
  "Fonte de verdade: implementação Android atual + documentação funcional do projeto.",
  "",
  "## Sistemas",
  "",
  "| Sistema | Evidência no KMP/iOS | Status |",
  "|---|---|---|",
  ...rows.map(r => `| ${r.label} | ${r.hits.length ? r.hits.join(", ") : "—"} | ${r.status === "FOUND" ? "🟡 detectado" : "❌ pendente"} |`),
  "",
  "## Placeholders detectados em App.kt",
  "",
  ...(placeholders.length ? placeholders.map(p => `- ❌ ${p}`) : ["- ✅ Nenhum placeholder conhecido detectado"]),
  "",
  "## Critério de conclusão",
  "",
  "A migração NÃO está concluída enquanto:",
  "- houver placeholders funcionais;",
  "- algum sistema Android atual estiver ausente no commonMain/iosMain;",
  "- persistência/save não sobreviver ao fechamento do app;",
  "- produção/contratos/turnos/exaustão divergirem das regras Android;",
  "- o GitHub Actions não gerar IPA com sucesso.",
  ""
].join("\n");

const reportPath = path.join(root, "IOS_FEATURE_PARITY.md");
const manifestPath = path.join(root, "ios-parity-manifest.json");

fs.writeFileSync(reportPath, report, "utf8");
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n", "utf8");

console.log(report);
console.log("");
console.log("[parity-v5] Criados:");
console.log("  IOS_FEATURE_PARITY.md");
console.log("  ios-parity-manifest.json");
console.log("");
console.log("Este gate é diagnóstico: ele não substitui build/testes funcionais.");
