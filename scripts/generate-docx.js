const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat,
  HeadingLevel, BorderStyle, WidthType, ShadingType,
  PageNumber, PageBreak, TableOfContents,
} = require("docx");

// ── Color Palette ──
const C = {
  primary: "1B4F72",    // Deep blue
  accent: "2E86C1",     // Medium blue
  light: "D4E6F1",      // Light blue
  lighter: "EBF5FB",    // Very light blue
  dark: "1A1A2E",       // Near black
  gray: "5D6D7E",       // Gray text
  white: "FFFFFF",
  headerBg: "1B4F72",
  rowAlt: "F2F8FC",
  border: "B0BEC5",
};

// ── Reusable helpers ──
const border = { style: BorderStyle.SINGLE, size: 1, color: C.border };
const borders = { top: border, bottom: border, left: border, right: border };
const noBorder = { style: BorderStyle.NONE, size: 0, color: C.white };
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };
const TABLE_WIDTH = 9026; // A4 content width with 1" margins

function headerCell(text, width) {
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: C.headerBg, type: ShadingType.CLEAR },
    margins: cellMargins,
    verticalAlign: "center",
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text, bold: true, font: "Microsoft YaHei", size: 20, color: C.white })] })],
  });
}

function dataCell(text, width, opts = {}) {
  const runs = Array.isArray(text)
    ? text
    : [new TextRun({ text, font: "Microsoft YaHei", size: 20, color: C.dark, ...opts })];
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading: opts.shaded ? { fill: C.rowAlt, type: ShadingType.CLEAR } : undefined,
    margins: cellMargins,
    children: [new Paragraph({ children: runs })],
  });
}

function makeTable(headers, rows, colWidths) {
  const totalW = colWidths.reduce((a, b) => a + b, 0);
  return new Table({
    width: { size: totalW, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [
      new TableRow({ children: headers.map((h, i) => headerCell(h, colWidths[i])) }),
      ...rows.map((row, ri) =>
        new TableRow({
          children: row.map((cell, ci) => {
            if (typeof cell === "object" && cell._cell) return cell._cell;
            return dataCell(String(cell), colWidths[ci], { shaded: ri % 2 === 1 });
          }),
        })
      ),
    ],
  });
}

function heading1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 200 }, children: [new TextRun({ text, font: "Microsoft YaHei" })] });
}
function heading2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 280, after: 160 }, children: [new TextRun({ text, font: "Microsoft YaHei" })] });
}
function heading3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, spacing: { before: 200, after: 120 }, children: [new TextRun({ text, font: "Microsoft YaHei" })] });
}
function para(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 360 },
    ...opts,
    children: Array.isArray(text)
      ? text
      : [new TextRun({ text, font: "Microsoft YaHei", size: 22, color: C.dark, ...opts })],
  });
}
function boldPara(label, value) {
  return new Paragraph({
    spacing: { after: 100, line: 360 },
    children: [
      new TextRun({ text: label, font: "Microsoft YaHei", size: 22, bold: true, color: C.primary }),
      new TextRun({ text: value, font: "Microsoft YaHei", size: 22, color: C.dark }),
    ],
  });
}
function codeBlock(text) {
  return new Paragraph({
    spacing: { after: 120 },
    shading: { fill: "F5F5F5", type: ShadingType.CLEAR },
    indent: { left: 360, right: 360 },
    children: [new TextRun({ text, font: "Consolas", size: 18, color: C.dark })],
  });
}

// ── Numbering Config ──
const numbering = {
  config: [
    {
      reference: "bullets",
      levels: [{
        level: 0, format: LevelFormat.BULLET, text: "\u2022", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } },
      }],
    },
    {
      reference: "bullets2",
      levels: [{
        level: 0, format: LevelFormat.BULLET, text: "\u25CB", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 1080, hanging: 360 } } },
      }],
    },
    {
      reference: "numbers",
      levels: [{
        level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } },
      }],
    },
  ],
};

function bullet(text, ref = "bullets") {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    spacing: { after: 80, line: 340 },
    children: Array.isArray(text)
      ? text
      : [new TextRun({ text, font: "Microsoft YaHei", size: 22, color: C.dark })],
  });
}

function numberedItem(text, ref = "numbers") {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    spacing: { after: 80, line: 340 },
    children: Array.isArray(text)
      ? text
      : [new TextRun({ text, font: "Microsoft YaHei", size: 22, color: C.dark })],
  });
}

// ── Cover Page ──
function coverPage() {
  return [
    new Paragraph({ spacing: { before: 3000 } }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 200 },
      children: [new TextRun({ text: "HHS", font: "Arial Black", size: 72, color: C.primary, bold: true })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 100 },
      children: [new TextRun({ text: "Health Hack System", font: "Arial", size: 36, color: C.accent })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 600 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: C.accent, space: 8 } },
      children: [new TextRun({ text: "\u667A\u80FD\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0", font: "Microsoft YaHei", size: 48, color: C.primary, bold: true })],
    }),
    new Paragraph({ spacing: { before: 1200 } }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 100 },
      children: [new TextRun({ text: "\u4F5C\u54C1\u4ECB\u7ECD\u6587\u6863", font: "Microsoft YaHei", size: 28, color: C.gray })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 100 },
      children: [new TextRun({ text: "\u2014\u2014 \u4F20\u667A\u676F\u7701\u8D5B\u4E00\u7B49\u5956 \u2014\u2014", font: "Microsoft YaHei", size: 24, color: C.accent, italics: true })],
    }),
    new Paragraph({ spacing: { before: 1600 } }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 60 },
      children: [new TextRun({ text: "\u7248\u672C\uFF1Av1.0", font: "Microsoft YaHei", size: 22, color: C.gray })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 60 },
      children: [new TextRun({ text: "\u66F4\u65B0\u65E5\u671F\uFF1A2026\u5E745\u670830\u65E5", font: "Microsoft YaHei", size: 22, color: C.gray })],
    }),
    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 1: 项目详情及使用方式 ──
function section1() {
  return [
    heading1("\u4E00\u3001\u9879\u76EE\u8BE6\u60C5\u53CA\u4F7F\u7528\u65B9\u5F0F"),
    heading2("1.1 \u9879\u76EE\u7B80\u4ECB"),
    para("HHS\uFF08Health Hack System\uFF09\u662F\u4E00\u6B3E\u9762\u5411\u4E2A\u4EBA\u7528\u6237\u7684\u667A\u80FD\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0\uFF0C\u96C6\u5065\u5EB7\u6570\u636E\u8FFD\u8E2A\u3001AI \u667A\u80FD\u5206\u6790\u3001\u5B9E\u65F6\u76D1\u63A7\u3001\u667A\u80FD\u9884\u8B66\u3001\u53EF\u7A7F\u6234\u8BBE\u5907\u8054\u52A8\u548C\u6E38\u620F\u5316\u6FC0\u52B1\u4E8E\u4E00\u4F53\uFF0C\u5E2E\u52A9\u7528\u6237\u5168\u9762\u638C\u63E1\u81EA\u8EAB\u5065\u5EB7\u72B6\u51B5\u5E76\u5F62\u6210\u5065\u5EB7\u751F\u6D3B\u4E60\u60EF\u3002"),
    para("\u7CFB\u7EDF\u91C7\u7528 Spring Boot 3 + Vue 3 \u524D\u540E\u7AEF\u5206\u79BB\u67B6\u6784\uFF0C\u90E8\u7F72\u4E8E\u963F\u91CC\u4E91 ECS\uFF0C\u652F\u6301 Docker \u5BB9\u5668\u5316\u4E00\u952E\u90E8\u7F72\uFF0C\u5177\u5907\u5B8C\u6574\u7684 CI/CD \u6D41\u6C34\u7EBF\u3002"),

    heading2("1.2 \u6838\u5FC3\u529F\u80FD\u6A21\u5757"),
    makeTable(
      ["\u529F\u80FD\u6A21\u5757", "\u8BF4\u660E", "\u7528\u6237\u4EF7\u503C"],
      [
        ["\u667A\u80FD\u6570\u636E\u5F55\u5165", "\u5FEB\u6377\u624B\u52A8\u3001AI \u8BED\u97F3/\u6587\u672C\u89E3\u6790\u3001OCR \u56FE\u7247\u8BC6\u522B\u3001\u7A7F\u6234\u8BBE\u5907\u81EA\u52A8\u540C\u6B65", "\u964D\u4F4E\u6570\u636E\u5F55\u5165\u95E8\u69DB\uFF0C\u63D0\u5347\u5F55\u5165\u6548\u7387"],
        ["\u5065\u5EB7\u6307\u6807\u7BA1\u7406", "\u8840\u7CD6\u3001\u8840\u538B\u3001\u5FC3\u7387\u3001BMI \u7B49\u591A\u7EF4\u5EA6\u6307\u6807", "\u5168\u9762\u638C\u63E1\u5065\u5EB7\u6570\u636E\u53D8\u5316"],
        ["\u5B9E\u65F6\u76D1\u63A7", "\u57FA\u4E8E WebSocket \u7684\u5B9E\u65F6\u6307\u6807\u91C7\u96C6\u4E0E\u52A8\u6001\u8D8B\u52BF\u56FE", "\u4F69\u6234\u8BBE\u5907\u65F6\u5B9E\u65F6\u67E5\u770B\u8EAB\u4F53\u72B6\u6001"],
        ["\u667A\u80FD\u9884\u8B66\u7CFB\u7EDF", "\u591A\u7EA7\u9608\u503C\u544A\u8B66\u3001\u53BB\u91CD\u5408\u5E76\u3001\u8D8B\u52BF\u9884\u6D4B\u3001AI \u5206\u6790", "\u53CA\u65F6\u53D1\u73B0\u5065\u5EB7\u98CE\u9669"],
        ["AI \u5065\u5EB7\u987E\u95EE", "\u57FA\u4E8E\u901A\u4E49\u5343\u95EE\u5927\u6A21\u578B\u7684\u667A\u80FD\u5065\u5EB7\u54A8\u8BE2", "\u968F\u65F6\u83B7\u53D6\u4E13\u4E1A\u5065\u5EB7\u5EFA\u8BAE"],
        ["\u5065\u5EB7\u8BC4\u5206", "\u591A\u7EF4\u5EA6\u7EFC\u5408\u8BC4\u5206 + AI \u5065\u5EB7\u62A5\u544A", "\u91CF\u5316\u5065\u5EB7\u72B6\u6001\uFF0C\u6307\u5BFC\u6539\u5584\u65B9\u5411"],
        ["\u9884\u9632\u4FDD\u5065", "\u5065\u5EB7\u6863\u6848\u3001\u98CE\u9669\u8BC4\u4F30\u3001\u7B5B\u67E5\u7BA1\u7406\u4E0E\u62A5\u544A\u5BF9\u6BD4", "\u4E3B\u52A8\u9884\u9632\uFF0C\u65E9\u671F\u53D1\u73B0"],
        ["\u4FDD\u5065\u8FFD\u8E2A", "\u7761\u7720\u3001\u8FD0\u52A8\u3001\u5FC3\u60C5\u3001\u76EE\u6807\u3001\u63D0\u9192", "\u57F9\u517B\u5065\u5EB7\u751F\u6D3B\u4E60\u60EF"],
        ["\u6E38\u620F\u5316\u6FC0\u52B1", "\u6210\u5C31\u7CFB\u7EDF\u3001\u79EF\u5206\u4F53\u7CFB\u3001\u8FDE\u7EED\u6253\u5361", "\u63D0\u5347\u7528\u6237\u7C98\u6027\u548C\u4F9D\u4ECE\u6027"],
        ["\u591A\u6E20\u9053\u63A8\u9001", "WebSocket\u3001\u90AE\u4EF6\u3001\u98DE\u4E66\u3001\u4F01\u4E1A\u5FAE\u4FE1", "\u786E\u4FDD\u91CD\u8981\u9884\u8B66\u4E0D\u9057\u6F0F"],
      ],
      [2000, 3800, 3226]
    ),

    heading2("1.3 \u4F7F\u7528\u65B9\u5F0F"),
    heading3("\u73AF\u5883\u8981\u6C42"),
    bullet([new TextRun({ text: "Java 17+", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u3001Node.js 20+\u3001MySQL 8.0+\u3001Redis 7.0+", font: "Microsoft YaHei", size: 22 })]),
    bullet("\u6216\u76F4\u63A5\u4F7F\u7528 Docker Compose \u4E00\u952E\u542F\u52A8"),

    heading3("\u5FEB\u901F\u542F\u52A8"),
    para([new TextRun({ text: "\u65B9\u5F0F\u4E00\uFF1ADocker Compose\uFF08\u63A8\u8350\uFF09", font: "Microsoft YaHei", size: 22, bold: true, color: C.primary })]),
    codeBlock("git clone https://github.com/Li3379/health-hack-system.git"),
    codeBlock("cd health-hack-system && cp .env.example .env"),
    codeBlock("docker compose up"),

    para([new TextRun({ text: "\u65B9\u5F0F\u4E8C\uFF1A\u672C\u5730\u5F00\u53D1", font: "Microsoft YaHei", size: 22, bold: true, color: C.primary })]),
    codeBlock("# \u7EC8\u7AEF1 - \u540E\u7AEF: cd hhs-backend && mvn spring-boot:run"),
    codeBlock("# \u7EC8\u7AEF2 - \u524D\u7AEF: cd hhs-frontend-v2 && npm ci && npm run dev"),

    para("\u542F\u52A8\u540E\u8BBF\u95EE http://localhost\uFF08Docker\uFF09\u6216 http://localhost:5173\uFF08\u672C\u5730\u5F00\u53D1\uFF09\uFF0C\u6CE8\u518C\u8D26\u6237\u5373\u53EF\u4F7F\u7528\u3002"),

    heading3("\u751F\u4EA7\u90E8\u7F72"),
    codeBlock("docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d"),
    para("\u751F\u4EA7\u73AF\u5883\u4EC5\u66B4\u9732 80 \u7AEF\u53E3\uFF0CNginx \u53CD\u5411\u4EE3\u7406\u81F3\u540E\u7AEF\uFF0CMySQL/Redis \u7AEF\u53E3\u4E0D\u5BF9\u5916\u66B4\u9732\u3002"),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 2: 项目亮点 ──
function section2() {
  return [
    heading1("\u4E8C\u3001\u9879\u76EE\u4EAE\u70B9"),

    heading2("\u4EAE\u70B9\u4E00\uFF1AAI \u6DF1\u5EA6\u878D\u5408\u7684\u5065\u5EB7\u7BA1\u7406"),
    para("\u7CFB\u7EDF\u4E0D\u662F\u7B80\u5355\u5730\u8C03\u7528\u5927\u6A21\u578B API\uFF0C\u800C\u662F\u5C06 AI \u80FD\u529B\u6DF1\u5EA6\u878D\u5165\u5065\u5EB7\u7BA1\u7406\u5168\u6D41\u7A0B\uFF1A"),
    bullet([new TextRun({ text: "AI \u667A\u80FD\u5F55\u5165\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u7528\u6237\u8F93\u5165\u201C\u6628\u5929\u5FC3\u7387 72\uFF0C\u8840\u538B 120/80\u201D\uFF0C\u7CFB\u7EDF\u81EA\u52A8\u89E3\u6790\u4E3A\u7ED3\u6784\u5316\u6307\u6807\u6570\u636E\u5E76\u5165\u5E93", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "AI \u5065\u5EB7\u987E\u95EE\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u57FA\u4E8E\u7528\u6237\u5065\u5EB7\u6863\u6848\u548C\u5386\u53F2\u6570\u636E\u8FDB\u884C\u4E2A\u6027\u5316\u54A8\u8BE2\uFF0C\u800C\u975E\u901A\u7528\u95EE\u7B54", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "AI \u9884\u8B66\u5206\u6790\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u5F53\u6307\u6807\u89E6\u53D1\u9884\u8B66\u65F6\uFF0CAI \u81EA\u52A8\u5206\u6790\u53EF\u80FD\u539F\u56E0\u5E76\u7ED9\u51FA\u5EFA\u8BAE", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "AI \u5065\u5EB7\u62A5\u544A\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u7EFC\u5408\u591A\u7EF4\u5EA6\u6570\u636E\u751F\u6210\u4E2A\u6027\u5316\u5065\u5EB7\u5206\u6790\u62A5\u544A", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u6D6E\u52A8 AI \u52A9\u624B\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u5168\u5C40\u60AC\u6D6E\u7403\u8BBE\u8BA1\uFF0C\u5728\u4EFB\u4F55\u9875\u9762\u90FD\u80FD\u5FEB\u901F\u54A8\u8BE2\uFF0C\u9644\u5E26\u5065\u5EB7\u5C0F\u8D34\u58EB\u8F6E\u64AD", font: "Microsoft YaHei", size: 22 })]),

    heading2("\u4EAE\u70B9\u4E8C\uFF1A\u667A\u80FD\u9884\u8B66\u5F15\u64CE"),
    para("\u533A\u522B\u4E8E\u7B80\u5355\u7684\u9608\u503C\u5224\u65AD\uFF0C\u7CFB\u7EDF\u6784\u5EFA\u4E86\u5B8C\u6574\u7684\u667A\u80FD\u9884\u8B66\u5B50\u7CFB\u7EDF\uFF1A"),
    makeTable(
      ["\u80FD\u529B", "\u8BF4\u660E"],
      [
        ["\u591A\u7EA7\u9608\u503C", "\u652F\u6301 Warning/Critical \u4E24\u7EA7\u9AD8/\u4F4E\u9608\u503C"],
        ["\u53BB\u91CD\u5408\u5E76", "\u76F8\u4F3C\u9884\u8B66\u81EA\u52A8\u5408\u5E76\uFF0C\u907F\u514D\u544A\u8B66\u98CE\u66B4"],
        ["\u8D8B\u52BF\u9884\u6D4B", "\u57FA\u4E8E\u5386\u53F2\u6570\u636E\u9884\u6D4B\u6307\u6807\u8D8B\u52BF\uFF0C\u63D0\u524D\u9884\u8B66"],
        ["\u9891\u7387\u7B56\u7565", "\u63A7\u5236\u544A\u8B66\u9891\u7387\uFF0C\u907F\u514D\u7528\u6237\u7584\u52B3"],
        ["\u6062\u590D\u901A\u77E5", "\u6307\u6807\u6062\u590D\u6B63\u5E38\u65F6\u4E3B\u52A8\u901A\u77E5\u7528\u6237"],
        ["\u4E2A\u6027\u5316\u9608\u503C", "\u7528\u6237\u53EF\u81EA\u5B9A\u4E49\u9884\u8B66\u9608\u503C\uFF0C\u9002\u5E94\u4E2A\u4EBA\u5DEE\u5F02"],
      ],
      [2200, 6826]
    ),

    heading2("\u4EAE\u70B9\u4E09\uFF1A\u56DB\u79CD\u6570\u636E\u5F55\u5165\u65B9\u5F0F"),
    para("\u4E3A\u89E3\u51B3\u5065\u5EB7\u6570\u636E\u201C\u5F55\u5165\u96BE\u201D\u7684\u75DB\u70B9\uFF0C\u7CFB\u7EDF\u63D0\u4F9B\u56DB\u79CD\u4E92\u8865\u7684\u5F55\u5165\u65B9\u5F0F\uFF1A"),
    makeTable(
      ["\u65B9\u5F0F", "\u573A\u666F", "\u6280\u672F\u5B9E\u73B0"],
      [
        ["\u5FEB\u6377\u624B\u52A8\u5F55\u5165", "\u65E5\u5E38\u5FEB\u901F\u8BB0\u5F55", "\u8868\u5355 + \u6307\u6807\u7C7B\u578B\u667A\u80FD\u5339\u914D"],
        ["AI \u6587\u672C\u89E3\u6790", "\u81EA\u7136\u8BED\u8A00\u63CF\u8FF0", "\u901A\u4E49\u5343\u95EE NLP \u89E3\u6790 + \u7ED3\u6784\u5316\u8F93\u51FA"],
        ["OCR \u56FE\u7247\u8BC6\u522B", "\u4F53\u68C0\u62A5\u544A\u3001\u5316\u9A8C\u5355", "\u767E\u5EA6 OCR + \u6A21\u677F\u5339\u914D + \u4EBA\u5DE5\u786E\u8BA4"],
        ["\u8BBE\u5907\u81EA\u52A8\u540C\u6B65", "\u534E\u4E3A/\u5C0F\u7C73\u624B\u73AF", "OAuth2 \u6388\u6743 + \u5B9A\u65F6\u540C\u6B65 + \u51B2\u7A81\u89E3\u51B3"],
      ],
      [2200, 2400, 4426]
    ),

    heading2("\u4EAE\u70B9\u56DB\uFF1A\u5168\u94FE\u8DEF\u5B9E\u65F6\u80FD\u529B"),
    bullet([new TextRun({ text: "WebSocket \u5B9E\u65F6\u63A8\u9001\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u6307\u6807\u53D8\u66F4\u3001\u9884\u8B66\u901A\u77E5\u5B9E\u65F6\u9001\u8FBE\u524D\u7AEF", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u5B9E\u65F6\u76D1\u63A7\u9762\u677F\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u52A8\u6001\u8D8B\u52BF\u56FE\u914D\u5408 WebSocket \u6570\u636E\u6D41", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u591A\u6E20\u9053\u63A8\u9001\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u9664 WebSocket \u5916\uFF0C\u8FD8\u652F\u6301\u90AE\u4EF6\u3001\u98DE\u4E66\u3001\u4F01\u4E1A\u5FAE\u4FE1\u79BB\u7EBF\u63A8\u9001", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u524D\u7AEF\u5B9E\u65F6\u72B6\u6001\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "Pinia store \u7BA1\u7406 WebSocket \u8FDE\u63A5\u72B6\u6001\uFF0C\u81EA\u52A8\u91CD\u8FDE", font: "Microsoft YaHei", size: 22 })]),

    heading2("\u4EAE\u70B9\u4E94\uFF1A\u7CBE\u81F4\u7684\u524D\u7AEF\u8BBE\u8BA1"),
    bullet([new TextRun({ text: "\u6BDB\u73BB\u7483\u8BBE\u8BA1\u7CFB\u7EDF\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u5168\u5C40 CSS \u81EA\u5B9A\u4E49\u5C5E\u6027 + backdrop-filter: blur() \u6BDB\u73BB\u7483\u6548\u679C", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u6DF1\u8272/\u6D45\u8272\u4E3B\u9898\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u5B8C\u6574\u7684\u8BBE\u8BA1 Token \u4F53\u7CFB\uFF0C\u4E00\u952E\u5207\u6362\uFF0C\u6240\u6709\u7EC4\u4EF6\u81EA\u52A8\u9002\u914D", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "GSAP \u5165\u573A\u52A8\u753B\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u9875\u9762\u5207\u6362\u548C\u7EC4\u4EF6\u5165\u573A\u4F7F\u7528 GSAP fromTo + clearProps \u52A8\u753B", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "3D \u89C6\u89C9\u589E\u5F3A\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "Three.js WebGL \u7403\u4F53\u30013D \u503E\u659C\u6548\u679C\u7B49\u4E30\u5BCC\u89C6\u89C9\u4EA4\u4E92", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "ECharts \u6570\u636E\u53EF\u89C6\u5316\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u5065\u5EB7\u8BC4\u5206\u96F7\u8FBE\u56FE\u3001\u4EEA\u8868\u76D8\u3001\u8FF7\u4F60\u8D8B\u52BF\u7EBF\u3001\u6D3B\u52A8\u73AF", font: "Microsoft YaHei", size: 22 })]),

    heading2("\u4EAE\u70B9\u516D\uFF1A\u4F01\u4E1A\u7EA7\u5DE5\u7A0B\u5B9E\u8DF5"),
    bullet([new TextRun({ text: "\u5B8C\u6574 CI/CD\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "5 \u4E2A GitHub Actions \u5DE5\u4F5C\u6D41\u8986\u76D6\u6D4B\u8BD5\u3001\u6784\u5EFA\u3001\u90E8\u7F72\u3001\u56DE\u6EDA", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u5BB9\u5668\u5316\u90E8\u7F72\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u591A\u9636\u6BB5 Docker \u6784\u5EFA\uFF0C\u975E root \u7528\u6237\u8FD0\u884C\uFF0C\u8D44\u6E90\u9650\u5236", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u5B89\u5168\u4F53\u7CFB\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "JWT \u65E0\u72B6\u6001\u8BA4\u8BC1\u3001AES-GCM \u4EE4\u724C\u52A0\u5BC6\u3001CORS \u5206\u73AF\u5883\u914D\u7F6E\u3001\u8DEF\u5F84\u904D\u5386\u9632\u62A4", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u6D4B\u8BD5\u8986\u76D6\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "46 \u4E2A\u540E\u7AEF\u6D4B\u8BD5\u6587\u4EF6\uFF0CTestcontainers \u96C6\u6210\u6D4B\u8BD5", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u4EE3\u7801\u8D28\u91CF\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "Google Checkstyle\u3001ESLint + Prettier\u3001JaCoCo \u8986\u76D6\u7387\u95E8\u7981", font: "Microsoft YaHei", size: 22 })]),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 3: Web 网页使用情况 ──
function section3() {
  return [
    heading1("\u4E09\u3001Web \u7F51\u9875\u4F7F\u7528\u60C5\u51B5\u6982\u8FF0"),
    heading2("3.1 \u9875\u9762\u7ED3\u6784"),
    para("\u7CFB\u7EDF\u91C7\u7528 Vue 3 \u5355\u9875\u5E94\u7528\uFF08SPA\uFF09\u67B6\u6784\uFF0C\u5171 22 \u4E2A\u8DEF\u7531\u9875\u9762\uFF0C\u901A\u8FC7 MainLayout \u7EC4\u4EF6\u5B9E\u73B0\u7EDF\u4E00\u7684\u4FA7\u8FB9\u680F\u5BFC\u822A + \u9876\u90E8\u680F + \u5185\u5BB9\u533A\u5E03\u5C40\u3002"),

    heading2("3.2 \u529F\u80FD\u9875\u9762\u4E00\u89C8"),
    makeTable(
      ["\u9875\u9762", "\u8DEF\u5F84", "\u529F\u80FD\u63CF\u8FF0"],
      [
        ["\u767B\u5F55/\u6CE8\u518C", "/login, /register", "\u7528\u6237\u8BA4\u8BC1\uFF0C\u652F\u6301 OAuth \u7B2C\u4E09\u65B9\u767B\u5F55"],
        ["\u4EEA\u8868\u76D8", "/dashboard", "\u5065\u5EB7\u6570\u636E\u7EFC\u5408\u6982\u89C8\uFF0C\u5FEB\u6377\u64CD\u4F5C\u5165\u53E3"],
        ["\u5065\u5EB7\u6307\u6807", "/health/metrics", "\u6307\u6807\u5386\u53F2\u8BB0\u5F55\u3001\u8D8B\u52BF\u56FE\u8868\u3001\u7B5B\u9009\u67E5\u8BE2"],
        ["\u5065\u5EB7\u8BC4\u5206", "/health/score", "\u591A\u7EF4\u5EA6\u7EFC\u5408\u8BC4\u5206\u3001\u96F7\u8FBE\u56FE\u3001AI \u62A5\u544A"],
        ["\u667A\u80FD\u9884\u8B66", "/health/alerts", "\u9884\u8B66\u5217\u8868\u3001\u7EA7\u522B\u7B5B\u9009\u3001AI \u5206\u6790"],
        ["\u9608\u503C\u8BBE\u7F6E", "/health/thresholds", "\u4E2A\u6027\u5316\u9884\u8B66\u9608\u503C\u914D\u7F6E"],
        ["\u6570\u636E\u5F55\u5165", "/data-input", "\u56DB\u79CD\u5F55\u5165\u65B9\u5F0F\u5207\u6362"],
        ["AI \u987E\u95EE", "/ai/chat", "\u5927\u6A21\u578B\u5065\u5EB7\u54A8\u8BE2\u5BF9\u8BDD"],
        ["\u5065\u5EB7\u6863\u6848", "/prevention/profile", "\u4E2A\u4EBA\u5065\u5EB7\u6863\u6848\u7BA1\u7406"],
        ["\u98CE\u9669\u8BC4\u4F30", "/prevention/risk", "\u75BE\u75C5\u98CE\u9669\u8BC4\u4F30\u4E0E\u5EFA\u8BAE"],
        ["\u4F53\u68C0\u62A5\u544A", "/screening/list", "\u4F53\u68C0\u62A5\u544A\u7BA1\u7406\u4E0E\u5F02\u5E38\u8FFD\u8E2A"],
        ["\u62A5\u544A\u5BF9\u6BD4", "/screening/compare/:idA/:idB", "\u4E24\u6B21\u4F53\u68C0\u7ED3\u679C\u5BF9\u6BD4\u5206\u6790"],
        ["\u5B9E\u65F6\u76D1\u63A7", "/realtime/monitor", "WebSocket \u5B9E\u65F6\u6307\u6807\u76D1\u63A7"],
        ["\u4FDD\u5065\u4EEA\u8868\u76D8", "/wellness/dashboard", "\u7761\u7720\u3001\u8FD0\u52A8\u3001\u5FC3\u60C5\u7B49\u4FDD\u5065\u6570\u636E"],
        ["\u76EE\u6807\u7BA1\u7406", "/goals", "\u5065\u5EB7\u76EE\u6807\u8BBE\u5B9A\u4E0E\u8FDB\u5EA6\u8FFD\u8E2A"],
        ["\u63D0\u9192\u7BA1\u7406", "/reminders", "\u81EA\u5B9A\u4E49\u5065\u5EB7\u63D0\u9192"],
        ["\u5FC3\u60C5\u8FFD\u8E2A", "/mood", "\u6BCF\u65E5\u5FC3\u60C5/\u7CBE\u529B/\u538B\u529B/\u7761\u7720\u8D28\u91CF\u8BB0\u5F55"],
        ["\u4E2A\u4EBA\u4E2D\u5FC3", "/user/profile", "\u4E2A\u4EBA\u8D44\u6599\u3001\u5934\u50CF\u4E0A\u4F20"],
        ["\u63A8\u9001\u8BBE\u7F6E", "/user/push-settings", "\u591A\u6E20\u9053\u63A8\u9001\u901A\u77E5\u914D\u7F6E"],
        ["\u8BBE\u5907\u7BA1\u7406", "/settings/device-platform", "\u7A7F\u6234\u8BBE\u5907\u5E73\u53F0\u914D\u7F6E\uFF08\u7BA1\u7406\u5458\uFF09"],
      ],
      [1600, 3200, 4226]
    ),

    heading2("3.3 \u4EA4\u4E92\u7279\u8272"),
    bullet([new TextRun({ text: "\u5168\u5C40\u6D6E\u52A8 AI \u52A9\u624B\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u9664 AI \u987E\u95EE\u9875\u9762\u5916\uFF0C\u6240\u6709\u9875\u9762\u53F3\u4E0B\u89D2\u5747\u6709\u53EF\u62D6\u62FD\u7684 AI \u6D6E\u52A8\u7403", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u4E3B\u9898\u5207\u6362\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u9876\u90E8\u680F\u652F\u6301\u6D45\u8272/\u6DF1\u8272/\u8DDF\u968F\u7CFB\u7EDF\u4E09\u79CD\u4E3B\u9898\u6A21\u5F0F", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "\u9875\u9762\u8FC7\u6E21\u52A8\u753B\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u8DEF\u7531\u5207\u6362\u4F7F\u7528 Vue Transition + GSAP \u52A8\u753B", font: "Microsoft YaHei", size: 22 })]),
    bullet([new TextRun({ text: "Markdown \u6E32\u67D3\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "AI \u5BF9\u8BDD\u652F\u6301 Markdown \u683C\u5F0F\uFF0C\u4F7F\u7528 DOMPurify \u9632 XSS", font: "Microsoft YaHei", size: 22 })]),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 4: 关键技术 ──
function section4() {
  return [
    heading1("\u56DB\u3001\u9879\u76EE\u4E2D\u6D89\u53CA\u7684\u5173\u952E\u6280\u672F"),

    heading2("4.1 \u540E\u7AEF\u6280\u672F\u6808"),
    makeTable(
      ["\u6280\u672F", "\u7248\u672C", "\u7528\u9014"],
      [
        ["Java", "17", "\u8FD0\u884C\u65F6\u8BED\u8A00"],
        ["Spring Boot", "3.2.0", "\u5E94\u7528\u6846\u67B6"],
        ["Spring Security", "6.x", "\u5B89\u5168\u8BA4\u8BC1\u6846\u67B6"],
        ["MyBatis-Plus", "3.5.5", "ORM \u6846\u67B6"],
        ["MySQL", "8.0", "\u5173\u7CFB\u578B\u6570\u636E\u5E93\uFF0C35 \u5F20\u8868"],
        ["Redis", "7.0", "\u7F13\u5B58\u3001\u9650\u6D41"],
        ["LangChain4j", "0.35.0", "AI/LLM \u96C6\u6210\u6846\u67B6"],
        ["\u901A\u4E49\u5343\u95EE", "qwen-flash", "\u5927\u8BED\u8A00\u6A21\u578B"],
        ["\u767E\u5EA6 OCR", "--", "\u6587\u5B57\u8BC6\u522B API"],
        ["JWT (jjwt)", "0.11.5", "\u65E0\u72B6\u6001\u8BA4\u8BC1\u4EE4\u724C"],
        ["WebSocket", "--", "\u5B9E\u65F6\u53CC\u5411\u901A\u4FE1"],
        ["Knife4j", "4.3.0", "API \u6587\u6863"],
        ["Apache POI", "5.2.5", "Excel \u5BFC\u51FA"],
        ["Testcontainers", "1.19.3", "\u96C6\u6210\u6D4B\u8BD5\u5BB9\u5668\u5316"],
      ],
      [2400, 1600, 5026]
    ),

    heading2("4.2 \u524D\u7AEF\u6280\u672F\u6808"),
    makeTable(
      ["\u6280\u672F", "\u7248\u672C", "\u7528\u9014"],
      [
        ["Vue 3", "3.4", "\u524D\u7AEF\u6846\u67B6\uFF08Composition API\uFF09"],
        ["TypeScript", "5.5", "\u7C7B\u578B\u5B89\u5168"],
        ["Vite", "5", "\u6784\u5EFA\u5DE5\u5177"],
        ["Element Plus", "2.5", "UI \u7EC4\u4EF6\u5E93"],
        ["Pinia", "2.1", "\u72B6\u6001\u7BA1\u7406"],
        ["ECharts", "5.4", "\u6570\u636E\u53EF\u89C6\u5316"],
        ["GSAP", "3.15", "\u9AD8\u6027\u80FD\u52A8\u753B"],
        ["Three.js", "0.184", "3D \u56FE\u5F62\u6E32\u67D3"],
        ["Vitest", "4.1", "\u5355\u5143\u6D4B\u8BD5"],
        ["Playwright", "1.58", "E2E \u6D4B\u8BD5"],
      ],
      [2400, 1600, 5026]
    ),

    heading2("4.3 \u67B6\u6784\u4E0E\u8BBE\u8BA1\u6A21\u5F0F"),
    makeTable(
      ["\u6A21\u5F0F", "\u5E94\u7528\u573A\u666F"],
      [
        ["\u5206\u5C42\u67B6\u6784", "Controller \u2192 Service \u2192 Mapper \u2192 Entity \u56DB\u5C42\u5206\u79BB"],
        ["\u9886\u57DF\u4E8B\u4EF6", "Spring ApplicationEventPublisher \u89E3\u8026\u6307\u6807\u5F55\u5165\u4E0E\u544A\u8B66/\u8BC4\u5206/\u79EF\u5206"],
        ["\u7B56\u7565\u6A21\u5F0F", "PushChannel \u63A5\u53E3 + 4 \u79CD\u63A8\u9001\u5B9E\u73B0"],
        ["\u6A21\u677F\u65B9\u6CD5", "\u544A\u8B66\u6D88\u606F\u6A21\u677F\uFF0C\u652F\u6301\u5360\u4F4D\u7B26\u66FF\u6362"],
        ["\u89C2\u5BDF\u8005\u6A21\u5F0F", "\u4E8B\u4EF6\u76D1\u542C\u5668\u5F02\u6B65\u5904\u7406\u8DE8\u5207\u9762\u5173\u6CE8\u70B9"],
        ["\u7EC4\u5408\u6A21\u5F0F", "\u5065\u5EB7\u8BC4\u5206\u7531\u591A\u4E2A\u7EF4\u5EA6\u8BC4\u5206\u5668\u7EC4\u5408\u8BA1\u7B97"],
      ],
      [2200, 6826]
    ),

    heading2("4.4 \u5173\u952E\u6280\u672F\u4EAE\u70B9"),
    numberedItem([new TextRun({ text: "Range \u5206\u533A\u8868\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "realtime_metric \u8868\u6309\u6708\u5206\u533A\uFF0C\u652F\u6301\u6D77\u91CF\u5B9E\u65F6\u6570\u636E\u9AD8\u6548\u67E5\u8BE2", font: "Microsoft YaHei", size: 22 })]),
    numberedItem([new TextRun({ text: "AES-GCM \u52A0\u5BC6\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "OAuth \u4EE4\u724C\u4F7F\u7528 AES-256-GCM \u52A0\u5BC6\u5B58\u50A8\uFF0C\u4FDD\u969C\u8BBE\u5907\u6388\u6743\u5B89\u5168", font: "Microsoft YaHei", size: 22 })]),
    numberedItem([new TextRun({ text: "\u591A\u5C42\u9650\u6D41\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "AI\u3001\u8BA4\u8BC1\u3001\u8BBE\u5907\u540C\u6B65\u63A5\u53E3\u5206\u522B\u72EC\u7ACB\u9650\u6D41\uFF0C\u9632\u6B62\u6EE5\u7528", font: "Microsoft YaHei", size: 22 })]),
    numberedItem([new TextRun({ text: "\u51B2\u7A81\u89E3\u51B3\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u8BBE\u5907\u540C\u6B65\u65F6\u4F7F\u7528 record_time \u65F6\u95F4\u6233\u8FDB\u884C\u6570\u636E\u51B2\u7A81\u89E3\u51B3", font: "Microsoft YaHei", size: 22 })]),
    numberedItem([new TextRun({ text: "\u5F02\u6B65\u4E8B\u4EF6\u5904\u7406\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u544A\u8B66\u751F\u6210\u3001\u8BC4\u5206\u66F4\u65B0\u3001\u79EF\u5206\u53D1\u653E\u5747\u901A\u8FC7\u5F02\u6B65\u4E8B\u4EF6\u76D1\u542C\u5668\u5904\u7406", font: "Microsoft YaHei", size: 22 })]),
    numberedItem([new TextRun({ text: "Testcontainers\uFF1A", font: "Microsoft YaHei", size: 22, bold: true }), new TextRun({ text: "\u4F7F\u7528\u771F\u5B9E MySQL \u5BB9\u5668\u8FDB\u884C\u96C6\u6210\u6D4B\u8BD5\uFF0C\u786E\u4FDD\u6D4B\u8BD5\u73AF\u5883\u4E0E\u751F\u4EA7\u4E00\u81F4", font: "Microsoft YaHei", size: 22 })]),

    heading2("4.5 \u90E8\u7F72\u4E0E\u8FD0\u7EF4\u6280\u672F"),
    makeTable(
      ["\u6280\u672F", "\u7528\u9014"],
      [
        ["Docker \u591A\u9636\u6BB5\u6784\u5EFA", "\u524D\u540E\u7AEF\u5206\u522B\u6784\u5EFA\u6700\u5C0F\u5316\u955C\u50CF"],
        ["Docker Compose", "4 \u670D\u52A1\u7F16\u6392\uFF08\u524D\u7AEF/Nginx + \u540E\u7AEF + MySQL + Redis\uFF09"],
        ["Nginx \u53CD\u5411\u4EE3\u7406", "SPA \u6258\u7BA1 + API \u4EE3\u7406 + WebSocket \u5347\u7EA7 + \u9759\u6001\u8D44\u6E90\u7F13\u5B58"],
        ["GitHub Actions", "5 \u4E2A CI/CD \u5DE5\u4F5C\u6D41"],
        ["GHCR", "GitHub Container Registry \u955C\u50CF\u6258\u7BA1"],
        ["\u963F\u91CC\u4E91 ECS", "\u751F\u4EA7\u670D\u52A1\u5668\u90E8\u7F72"],
      ],
      [2400, 6626]
    ),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 5: 项目图片 ──
function section5() {
  return [
    heading1("\u4E94\u3001\u9879\u76EE\u76F8\u5173\u56FE\u7247/\u7167\u7247"),
    para([new TextRun({ text: "\u6CE8\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: C.accent }), new TextRun({ text: "\u4EE5\u4E0B\u4E3A\u9879\u76EE\u5404\u529F\u80FD\u6A21\u5757\u7684\u9875\u9762\u622A\u56FE\u4F4D\u7F6E\uFF0C\u9700\u8865\u5145\u5B9E\u9645\u622A\u56FE\u3002", font: "Microsoft YaHei", size: 22, color: C.accent })]),

    heading2("5.1 \u7CFB\u7EDF\u67B6\u6784\u56FE"),
    para("\uFF08\u8BF7\u63D2\u5165\u7CFB\u7EDF\u67B6\u6784\u56FE\u7247\uFF09"),

    heading2("5.2 \u9875\u9762\u622A\u56FE"),
    makeTable(
      ["\u7F16\u53F7", "\u622A\u56FE", "\u8BF4\u660E"],
      [
        ["\u56FE1", "screenshots/dashboard.png", "\u4EEA\u8868\u76D8 \u2014 \u5065\u5EB7\u6570\u636E\u7EFC\u5408\u6982\u89C8"],
        ["\u56FE2", "screenshots/data-input.png", "\u667A\u80FD\u5F55\u5165 \u2014 \u56DB\u79CD\u5F55\u5165\u65B9\u5F0F"],
        ["\u56FE3", "screenshots/health-score.png", "\u5065\u5EB7\u8BC4\u5206 \u2014 \u96F7\u8FBE\u56FE + \u4EEA\u8868\u76D8"],
        ["\u56FE4", "screenshots/realtime-monitor.png", "\u5B9E\u65F6\u76D1\u63A7 \u2014 WebSocket \u52A8\u6001\u8D8B\u52BF"],
        ["\u56FE5", "screenshots/ai-chat.png", "AI \u987E\u95EE \u2014 \u5927\u6A21\u578B\u5065\u5EB7\u54A8\u8BE2"],
        ["\u56FE6", "screenshots/alerts.png", "\u667A\u80FD\u9884\u8B66 \u2014 \u591A\u7EA7\u544A\u8B66\u7BA1\u7406"],
        ["\u56FE7", "screenshots/wellness.png", "\u4FDD\u5065\u4EEA\u8868\u76D8 \u2014 \u7761\u7720/\u8FD0\u52A8/\u5FC3\u60C5"],
        ["\u56FE8", "screenshots/dark-theme.png", "\u6DF1\u8272\u4E3B\u9898 \u2014 \u6BDB\u73BB\u7483\u8BBE\u8BA1\u6548\u679C"],
        ["\u56FE9", "screenshots/floating-ai.png", "\u6D6E\u52A8 AI \u52A9\u624B \u2014 \u5168\u5C40\u60AC\u6D6E\u7403"],
        ["\u56FE10", "screenshots/api-docs.png", "API \u6587\u6863 \u2014 Knife4j Swagger"],
      ],
      [800, 3600, 4626]
    ),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 6: 获奖情况 ──
function section6() {
  return [
    heading1("\u516D\u3001\u9879\u76EE\u83B7\u5956\u60C5\u51B5"),
    para("\u672C\u9879\u76EE\u5728\u4F20\u667A\u676F\u7701\u8D5B\u4E2D\u8363\u83B7\u4E00\u7B49\u5956\u3002"),
    makeTable(
      ["\u8D5B\u4E8B", "\u5956\u9879", "\u65F6\u95F4"],
      [
        ["\u4F20\u667A\u676F\u7701\u8D5B", "\u4E00\u7B49\u5956", "\u2014\u2014"],
      ],
      [3000, 3013, 3013]
    ),
    para([new TextRun({ text: "\u6CE8\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: C.accent }), new TextRun({ text: "\u8BF7\u8865\u5145\u5177\u4F53\u53C2\u8D5B\u5C4A\u6B21\u548C\u83B7\u5956\u65F6\u95F4\u3002", font: "Microsoft YaHei", size: 22, color: C.accent })]),

    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ── Section 7: V2.0 优化说明 ──
function section7() {
  return [
    heading1("\u4E03\u3001V2.0 \u4F18\u5316\u8BF4\u660E"),
    para("\u672C\u9879\u76EE\u5728\u539F\u6709 V1.0 \u57FA\u7840\u4E0A\u8FDB\u884C\u4E86\u5168\u9762\u5347\u7EA7\u4F18\u5316\uFF0C\u4ECE\u201C\u529F\u80FD\u53EF\u7528\u7684\u539F\u578B\u201D\u5347\u7EA7\u4E3A\u201C\u8BBE\u8BA1\u7CBE\u81F4\u3001\u529F\u80FD\u5B8C\u6574\u7684\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0\u201D\u3002"),

    heading2("7.1 \u524D\u7AEF\u8BBE\u8BA1\u7EDF\u4E00"),
    para([new TextRun({ text: "\u4F18\u5316\u524D\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: "E74C3C" }), new TextRun({ text: "\u90E8\u5206\u9875\u9762\u4F7F\u7528\u786C\u7F16\u7801\u7684 Element Plus \u9ED8\u8BA4\u8272\uFF0C\u4E0E\u6574\u4F53\u6697\u8272\u8BBE\u8BA1\u7CFB\u7EDF\u5272\u88C2\u3002", font: "Microsoft YaHei", size: 22 })]),
    bullet("\u5168\u7AD9\u66FF\u6362\u786C\u7F16\u7801\u989C\u8272\u4E3A CSS \u81EA\u5B9A\u4E49\u5C5E\u6027\uFF08Design Tokens\uFF09"),
    bullet("\u7EDF\u4E00\u6BDB\u73BB\u7483\uFF08Glassmorphism\uFF09\u5361\u7247\u6548\u679C"),
    bullet("\u65B0\u589E GSAP \u9875\u9762\u5165\u573A\u52A8\u753B"),
    bullet("\u65B0\u589E 3D \u89C6\u89C9\u589E\u5F3A\u6548\u679C\uFF08Three.js WebGL + CSS 3D\uFF09"),

    heading2("7.2 \u6570\u636E\u53EF\u89C6\u5316\u5347\u7EA7"),
    para([new TextRun({ text: "\u4F18\u5316\u524D\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: "E74C3C" }), new TextRun({ text: "ECharts \u4EC5\u5728 3 \u4E2A\u9875\u9762\u4F7F\u7528\uFF0C\u56FE\u8868\u7C7B\u578B\u5355\u4E00\u3002", font: "Microsoft YaHei", size: 22 })]),
    bullet("\u521B\u5EFA useECharts \u5171\u4EAB Composable\uFF0C\u5C01\u88C5\u521D\u59CB\u5316/\u9500\u6BC1/\u4E3B\u9898/\u81EA\u9002\u5E94"),
    bullet("\u65B0\u589E\u5065\u5EB7\u8BC4\u5206\u96F7\u8FBE\u56FE\u3001\u4EEA\u8868\u76D8\u3001\u8FF7\u4F60\u8D8B\u52BF\u7EBF\u3001\u6D3B\u52A8\u73AF"),
    bullet("\u521B\u5EFA ECharts \u7EDF\u4E00\u6697\u8272\u4E3B\u9898\u914D\u7F6E"),

    heading2("7.3 \u540E\u7AEF\u6838\u5FC3\u529F\u80FD\u8865\u5168"),
    para([new TextRun({ text: "\u4F18\u5316\u524D\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: "E74C3C" }), new TextRun({ text: "\u5065\u5EB7\u5206\u6570\u5386\u53F2\u8FD4\u56DE\u5047\u6570\u636E\u3001\u7F3A\u5C11\u5BFC\u51FA\u529F\u80FD\u3001\u7F3A\u5C11\u63D0\u9192\u7CFB\u7EDF\u3002", font: "Microsoft YaHei", size: 22 })]),
    bullet("\u5B9E\u73B0 health_score_history \u8868\uFF0C\u5206\u6570\u5386\u53F2\u6301\u4E45\u5316 + \u5F02\u6B65\u8BB0\u5F55"),
    bullet("\u5B9E\u73B0 CSV/Excel/JSON \u6570\u636E\u5BFC\u51FA\uFF08\u5F02\u6B65\u751F\u6210 + \u4EFB\u52A1\u8FFD\u8E2A\uFF09"),
    bullet("\u5B9E\u73B0\u7528\u6237\u81EA\u5B9A\u4E49\u63D0\u9192\u7CFB\u7EDF\uFF08Cron \u8868\u8FBE\u5F0F\u8C03\u5EA6\uFF09"),
    bullet("\u5B9E\u73B0\u4F53\u68C0\u62A5\u544A\u5BF9\u6BD4\u529F\u80FD"),

    heading2("7.4 \u7528\u6237\u7C98\u6027\u529F\u80FD"),
    bullet("\u6E38\u620F\u5316\u7CFB\u7EDF\uFF1A\u6210\u5C31\u5B9A\u4E49 + \u79EF\u5206\u4F53\u7CFB + \u8FDE\u7EED\u6253\u5361\u8FFD\u8E2A"),
    bullet("\u5065\u5EB7\u76EE\u6807\u7BA1\u7406\uFF1A\u76EE\u6807\u8BBE\u5B9A + \u8FDB\u5EA6\u8BB0\u5F55 + \u5B8C\u6210\u7EDF\u8BA1"),
    bullet("\u5FC3\u60C5\u8FFD\u8E2A\uFF1A\u6BCF\u65E5\u5FC3\u60C5/\u7CBE\u529B/\u538B\u529B/\u7761\u7720\u8D28\u91CF\u8BB0\u5F55"),

    heading2("7.5 CI/CD \u4E0E\u5DE5\u7A0B\u5316"),
    para([new TextRun({ text: "\u4F18\u5316\u524D\uFF1A", font: "Microsoft YaHei", size: 22, bold: true, color: "E74C3C" }), new TextRun({ text: "\u7F3A\u5C11\u81EA\u52A8\u5316\u90E8\u7F72\u548C\u7248\u672C\u7BA1\u7406\u3002", font: "Microsoft YaHei", size: 22 })]),
    bullet("\u65B0\u589E 5 \u4E2A GitHub Actions \u5DE5\u4F5C\u6D41"),
    bullet("\u65B0\u589E GHCR \u955C\u50CF\u6258\u7BA1 + SHA/\u7248\u672C\u6807\u7B7E"),
    bullet("\u65B0\u589E ECS \u81EA\u52A8\u90E8\u7F72\u811A\u672C + \u5065\u5EB7\u68C0\u67E5"),
    bullet("\u65B0\u589E\u7248\u672C\u56DE\u6EDA\u673A\u5236\uFF08~2 \u5206\u949F\u5B8C\u6210\uFF09"),

    heading2("7.6 \u4F18\u5316\u6210\u679C\u603B\u7ED3"),
    makeTable(
      ["\u7EF4\u5EA6", "V1.0", "V2.0"],
      [
        ["\u6570\u636E\u5E93\u8868", "~20 \u5F20", "35 \u5F20 + 3 \u4E2A\u89C6\u56FE + 2 \u4E2A\u5B58\u50A8\u8FC7\u7A0B"],
        ["\u540E\u7AEF\u63A7\u5236\u5668", "~15 \u4E2A", "25 \u4E2A"],
        ["\u670D\u52A1\u5C42", "~20 \u4E2A\u63A5\u53E3", "30 \u4E2A\u63A5\u53E3 + 34 \u4E2A\u5B9E\u73B0"],
        ["\u524D\u7AEF\u9875\u9762", "~15 \u4E2A", "22 \u4E2A\u8DEF\u7531\u9875\u9762"],
        ["API \u6A21\u5757", "~12 \u4E2A", "20 \u4E2A"],
        ["Pinia Store", "~5 \u4E2A", "8 \u4E2A"],
        ["\u6D4B\u8BD5\u6587\u4EF6", "\u57FA\u7840\u6D4B\u8BD5", "46 \u4E2A\uFF0827 \u5355\u5143 + 9 \u96C6\u6210 + 3 \u5B89\u5168 + 1 \u6027\u80FD\uFF09"],
        ["CI/CD", "\u65E0", "5 \u4E2A\u5DE5\u4F5C\u6D41"],
        ["\u63A8\u9001\u6E20\u9053", "WebSocket", "WebSocket + \u90AE\u4EF6 + \u98DE\u4E66 + \u4F01\u4E1A\u5FAE\u4FE1"],
        ["\u6570\u636E\u5F55\u5165", "\u624B\u52A8 + OCR", "\u5FEB\u6377 + AI + OCR + \u8BBE\u5907\u540C\u6B65"],
        ["\u4E3B\u9898\u7CFB\u7EDF", "\u65E0", "\u6DF1\u8272/\u6D45\u8272 + Design Tokens"],
        ["\u52A8\u753B\u6548\u679C", "\u65E0", "GSAP + Three.js + CSS 3D"],
      ],
      [1800, 3613, 3613]
    ),
  ];
}

// ── Build Document ──
const doc = new Document({
  creator: "HHS Team",
  title: "HHS \u667A\u80FD\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0 \u2014 \u4F5C\u54C1\u4ECB\u7ECD\u6587\u6863",
  description: "\u4F20\u667A\u676F\u7701\u8D5B\u4E00\u7B49\u5956\u83B7\u5956\u4F5C\u54C1\u4ECB\u7ECD",
  styles: {
    default: {
      document: {
        run: { font: "Microsoft YaHei", size: 22, color: C.dark },
        paragraph: { spacing: { line: 360 } },
      },
    },
    paragraphStyles: [
      {
        id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 36, bold: true, font: "Microsoft YaHei", color: C.primary },
        paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: C.accent, space: 4 } },
        },
      },
      {
        id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: "Microsoft YaHei", color: C.accent },
        paragraph: { spacing: { before: 280, after: 160 }, outlineLevel: 1 },
      },
      {
        id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 24, bold: true, font: "Microsoft YaHei", color: C.dark },
        paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 2 },
      },
    ],
  },
  numbering,
  sections: [
    // ── Cover Page ──
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
        },
      },
      children: coverPage(),
    },
    // ── TOC Page ──
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
        },
      },
      headers: {
        default: new Header({
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [new TextRun({ text: "HHS \u667A\u80FD\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0", font: "Microsoft YaHei", size: 18, color: C.gray, italics: true })],
          })],
        }),
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            border: { top: { style: BorderStyle.SINGLE, size: 1, color: C.border, space: 4 } },
            children: [
              new TextRun({ text: "\u7B2C ", font: "Microsoft YaHei", size: 18, color: C.gray }),
              new TextRun({ children: [PageNumber.CURRENT], font: "Microsoft YaHei", size: 18, color: C.gray }),
              new TextRun({ text: " \u9875", font: "Microsoft YaHei", size: 18, color: C.gray }),
            ],
          })],
        }),
      },
      children: [
        heading1("\u76EE\u5F55"),
        new TableOfContents("\u76EE\u5F55", { hyperlink: true, headingStyleRange: "1-3" }),
        new Paragraph({ children: [new PageBreak()] }),
      ],
    },
    // ── Main Content ──
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
        },
      },
      headers: {
        default: new Header({
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [new TextRun({ text: "HHS \u667A\u80FD\u5065\u5EB7\u76D1\u6D4B\u5E73\u53F0 \u2014 \u4F5C\u54C1\u4ECB\u7ECD", font: "Microsoft YaHei", size: 18, color: C.gray, italics: true })],
          })],
        }),
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            border: { top: { style: BorderStyle.SINGLE, size: 1, color: C.border, space: 4 } },
            children: [
              new TextRun({ text: "\u7B2C ", font: "Microsoft YaHei", size: 18, color: C.gray }),
              new TextRun({ children: [PageNumber.CURRENT], font: "Microsoft YaHei", size: 18, color: C.gray }),
              new TextRun({ text: " \u9875", font: "Microsoft YaHei", size: 18, color: C.gray }),
            ],
          })],
        }),
      },
      children: [
        ...section1(),
        ...section2(),
        ...section3(),
        ...section4(),
        ...section5(),
        ...section6(),
        ...section7(),
        // ── Footer note ──
        new Paragraph({ spacing: { before: 600 } }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          border: { top: { style: BorderStyle.SINGLE, size: 2, color: C.accent, space: 8 } },
          spacing: { before: 200 },
          children: [
            new TextRun({ text: "\u6587\u6863\u7248\u672C\uFF1Av1.0 | \u66F4\u65B0\u65E5\u671F\uFF1A2026\u5E745\u670830\u65E5", font: "Microsoft YaHei", size: 20, color: C.gray, italics: true }),
          ],
        }),
      ],
    },
  ],
});

// ── Generate ──
const outPath = "D:/Programming File/IDEA/health-hack-system/docs/\u4F5C\u54C1\u4ECB\u7ECD\u6587\u6863.docx";
Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(outPath, buffer);
  console.log("Generated: " + outPath);
  console.log("Size: " + (buffer.length / 1024).toFixed(1) + " KB");
});
