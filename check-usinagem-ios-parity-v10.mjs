#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
const root = process.cwd();

const checks = [
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/model/PersistentGameModels.kt", "schemaVersion: Int = 4", "schema 4"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/persistence/GameSaveCodec.kt", 'row("VERSION", 4)', "codec schema 4"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/persistence/GameSaveCodec.kt", '"CAREER4"', "carreira persistente"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/persistence/GameSaveCodec.kt", '"LM4"', "missões persistentes"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt", "schemaVersion = 4", "persist schema 4"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt", "fun operateMachine", "operação manual"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt", "fun inspectOwnerBatch", "qualidade"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt", "fun shipOwnerBatch", "expedição"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt", "fun hireLegendaryEmployee", "lendários"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/AndroidV24Gameplay.kt", "object IndustrialSkillCatalog", "skills industriais"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/AndroidV24Gameplay.kt", "object LegendaryEmployeeCatalog", "catálogo lendário"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/AndroidV24Gameplay.kt", "object LegendaryMissionCatalog", "missões lendárias"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt", "fun AndroidV24MainMenu", "menu"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt", "fun OwnerCareerPanel", "jornada UI"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/AndroidV24ParityUi.kt", "fun IndustrialCareerTree", "árvore UI"],
  ["ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/IndustrialRoulette.kt", "rouletteTargetIndex", "roleta alinhada"],
  ["iosApp/UsinagemConverted/LocalSaveV23Adapter.swift", '"CAREER4"', "cloud carreira"],
  ["iosApp/UsinagemConverted/LocalSaveV23Adapter.swift", '"LM4"', "cloud missões"],
  ["iosApp/UsinagemConverted/CloudSyncGateViewController.swift", "isNetworkFailureMode", "gate V9.1"],
];

let failed = 0;
for (const [rel, token, label] of checks) {
  const file = path.join(root, rel);
  if (!fs.existsSync(file) || !fs.readFileSync(file, "utf8").includes(token)) {
    console.error(`❌ ${label}`);
    failed++;
  } else console.log(`✅ ${label}`);
}

const gameplay = fs.readFileSync(
  path.join(root, "ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/AndroidV24Gameplay.kt"),
  "utf8"
);
const skillCount = (gameplay.match(/IndustrialSkillDefinition\("/g) || []).length;
const legendaryBlock = gameplay.split("object LegendaryEmployeeCatalog")[1]?.split("private val workQuotes")[0] || "";
const legendaryCount = (legendaryBlock.match(/LegendaryEmployeeDefinition\("/g) || []).length;

if (skillCount !== 31) { console.error(`❌ skills: ${skillCount}/31`); failed++; }
else console.log("✅ skills: 31/31");
if (legendaryCount !== 11) { console.error(`❌ lendários: ${legendaryCount}/11`); failed++; }
else console.log("✅ lendários: 11/11");

if (failed) {
  console.error(`\n❌ V10: ${failed} gate(s) falharam.`);
  process.exit(1);
}
console.log("\n✅ PARIDADE V10: todos os gates estruturais passaram.");
