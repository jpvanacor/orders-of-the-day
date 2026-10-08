// Copies the web app (the same files GitHub Pages serves) into www/ for the Android build.
import { cpSync, mkdirSync, rmSync } from "node:fs";
const files = ["index.html", "manifest.webmanifest", "sw.js", "icons", "fonts"];
rmSync("www", { recursive: true, force: true });
mkdirSync("www");
for (const f of files) cpSync(f, `www/${f}`, { recursive: true });
console.log("www ready:", files.join(", "));
