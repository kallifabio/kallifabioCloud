const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const cwd = process.cwd();
const srcDir = path.join(cwd, 'branding', 'spigotmc');
const outDir = path.join(srcDir, 'png');
fs.mkdirSync(outDir, { recursive: true });

const jobs = [
  { input: 'kallicloud-logo-icon.svg', outputs: [
      { name: 'kallicloud-logo-icon-512.png', w: 512, h: 512 },
      { name: 'kallicloud-logo-icon-256.png', w: 256, h: 256 },
      { name: 'kallicloud-logo-icon-128.png', w: 128, h: 128 }
    ]
  },
  { input: 'kallicloud-logo-horizontal.svg', outputs: [
      { name: 'kallicloud-logo-horizontal-1600x520.png', w: 1600, h: 520 },
      { name: 'kallicloud-logo-horizontal-1200x390.png', w: 1200, h: 390 },
      { name: 'kallicloud-logo-horizontal-800x260.png', w: 800, h: 260 }
    ]
  },
  { input: 'kallicloud-spigotmc-banner.svg', outputs: [
      { name: 'kallicloud-spigotmc-banner-1280x720.png', w: 1280, h: 720 },
      { name: 'kallicloud-spigotmc-banner-1920x1080.png', w: 1920, h: 1080 },
      { name: 'kallicloud-spigotmc-banner-960x540.png', w: 960, h: 540 }
    ]
  }
];

for (const job of jobs) {
  const svgPath = path.join(srcDir, job.input);
  const svg = fs.readFileSync(svgPath);
  for (const o of job.outputs) {
    const resvg = new Resvg(svg, { fitTo: { mode: 'width', value: o.w } });
    const pngData = resvg.render();
    const outPath = path.join(outDir, o.name);
    fs.writeFileSync(outPath, pngData.asPng());
    console.log(outPath);
  }
}
