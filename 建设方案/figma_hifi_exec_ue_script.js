async function run() {
  const page = figma.createPage();
  page.name = 'Executive HiFi UE';
  figma.root.appendChild(page);
  await figma.setCurrentPageAsync(page);

  await figma.loadFontAsync({ family: 'Inter', style: 'Regular' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Medium' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Semi Bold' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Bold' });

  const COLORS = {
    bg0: { r: 0.02, g: 0.03, b: 0.09 },
    bg1: { r: 0.04, g: 0.07, b: 0.16 },
    bg2: { r: 0.08, g: 0.12, b: 0.24 },
    blue: { r: 0.35, g: 0.82, b: 1.0 },
    deepBlue: { r: 0.29, g: 0.49, b: 1.0 },
    purple: { r: 0.56, g: 0.49, b: 1.0 },
    green: { r: 0.2, g: 0.9, b: 0.72 },
    orange: { r: 1.0, g: 0.72, b: 0.3 },
    red: { r: 1.0, g: 0.42, b: 0.54 },
    white: { r: 0.96, g: 0.97, b: 1.0 },
    text2: { r: 0.67, g: 0.71, b: 0.82 },
    text3: { r: 0.48, g: 0.54, b: 0.68 },
    card: { r: 0.08, g: 0.12, b: 0.22 },
    stroke: { r: 0.38, g: 0.56, b: 0.9 },
  };

  function solid(color, opacity) {
    return opacity == null ? [{ type: 'SOLID', color }] : [{ type: 'SOLID', color, opacity }];
  }

  function setText(node, text, size, weight, color, x, y, width) {
    node.fontName = { family: 'Inter', style: weight };
    node.fontSize = size;
    node.characters = text;
    node.fills = solid(color);
    if (x != null) node.x = x;
    if (y != null) node.y = y;
    if (width != null) node.resize(width, node.height);
    return node;
  }

  function addText(parent, text, size, weight, color, x, y, width) {
    const t = figma.createText();
    setText(t, text, size, weight, color, x, y, width);
    parent.appendChild(t);
    return t;
  }

  function addRect(parent, x, y, w, h, color, opacity, radius) {
    const r = figma.createRectangle();
    r.x = x; r.y = y; r.resize(w, h);
    r.fills = solid(color, opacity);
    if (radius != null) r.cornerRadius = radius;
    parent.appendChild(r);
    return r;
  }

  function addEllipse(parent, x, y, w, h, color, opacity) {
    const e = figma.createEllipse();
    e.x = x; e.y = y; e.resize(w, h);
    e.fills = solid(color, opacity);
    parent.appendChild(e);
    return e;
  }

  function addGlowBlob(parent, x, y, w, h, color, opacity, blur) {
    const e = addEllipse(parent, x, y, w, h, color, opacity);
    e.effects = [{ type: 'LAYER_BLUR', radius: blur, visible: true }];
    return e;
  }

  function glassCard(parent, x, y, w, h, title, subtitle) {
    const f = figma.createFrame();
    f.x = x; f.y = y; f.resize(w, h);
    f.cornerRadius = 24;
    f.fills = [{
      type: 'GRADIENT_LINEAR',
      gradientTransform: [[1, 0, 0], [0, 1, 0]],
      gradientStops: [
        { position: 0, color: { ...COLORS.card, a: 0.54 } },
        { position: 1, color: { r: 0.05, g: 0.09, b: 0.18, a: 0.72 } }
      ]
    }];
    f.strokes = [{ type: 'SOLID', color: COLORS.stroke, opacity: 0.22 }];
    f.strokeWeight = 1;
    f.effects = [
      { type: 'BACKGROUND_BLUR', radius: 20, visible: true },
      { type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.34 }, offset: { x: 0, y: 24 }, radius: 42, spread: 0, visible: true, blendMode: 'NORMAL' }
    ];
    parent.appendChild(f);
    if (title) addText(f, title, 20, 'Semi Bold', COLORS.white, 24, 20, w - 48);
    if (subtitle) addText(f, subtitle, 12, 'Regular', COLORS.text2, 24, 48, w - 48);
    return f;
  }

  function chip(parent, x, y, w, h, text, color, strong) {
    const c = figma.createFrame();
    c.x = x; c.y = y; c.resize(w, h);
    c.cornerRadius = h / 2;
    c.fills = strong ? solid(color, 0.18) : solid({ r: 1, g: 1, b: 1 }, 0.05);
    c.strokes = [{ type: 'SOLID', color, opacity: strong ? 0.45 : 0.16 }];
    c.strokeWeight = 1;
    parent.appendChild(c);
    addText(c, text, 12, 'Medium', strong ? color : COLORS.text2, 14, 8, w - 28);
    return c;
  }

  function kpiCard(parent, x, y, w, h, label, value, accent) {
    const c = glassCard(parent, x, y, w, h);
    addText(c, label, 12, 'Medium', COLORS.text2, 18, 16, w - 36);
    addText(c, value, 32, 'Bold', COLORS.white, 18, 38, w - 36);
    const line = addRect(c, 18, h - 18, w - 36, 4, accent, 0.85, 99);
    line.effects = [{ type: 'LAYER_BLUR', radius: 3, visible: true }];
    return c;
  }

  function metricStrip(parent, x, y, data) {
    let cursor = x;
    for (const item of data) {
      const c = glassCard(parent, cursor, y, 220, 86);
      addText(c, item.label, 12, 'Medium', COLORS.text2, 18, 16, 180);
      addText(c, item.value, 30, 'Bold', COLORS.white, 18, 34, 160);
      addRect(c, 18, 68, 70, 4, item.color, 0.9, 99);
      cursor += 236;
    }
  }

  function topHeader(frame, title, subtitle, conclusion) {
    addText(frame, title, 42, 'Bold', COLORS.white, 72, 54, 760);
    addText(frame, subtitle, 18, 'Regular', COLORS.text2, 72, 108, 980);
    const note = glassCard(frame, 1360, 48, 488, 76);
    addText(note, '管理层结论', 12, 'Medium', COLORS.blue, 24, 14, 120);
    addText(note, conclusion, 16, 'Semi Bold', COLORS.white, 24, 32, 440);
    chip(frame, 72, 24, 132, 34, 'Executive Demo', COLORS.blue, true);
    chip(frame, 214, 24, 134, 34, 'AI Operation', COLORS.purple, false);
  }

  function createBaseScreen(index, title, subtitle, conclusion) {
    const frame = figma.createFrame();
    frame.name = title;
    frame.resize(1920, 1080);
    frame.x = index * 2040;
    frame.y = 120;
    frame.cornerRadius = 28;
    frame.clipsContent = true;
    frame.fills = [{
      type: 'GRADIENT_LINEAR',
      gradientTransform: [[0.86, -0.34, 0], [0.34, 0.86, 0]],
      gradientStops: [
        { position: 0, color: { ...COLORS.bg0, a: 1 } },
        { position: 0.55, color: { ...COLORS.bg1, a: 1 } },
        { position: 1, color: { ...COLORS.bg2, a: 1 } }
      ]
    }];
    page.appendChild(frame);
    addGlowBlob(frame, -180, -120, 500, 500, COLORS.deepBlue, 0.24, 120);
    addGlowBlob(frame, 1180, -80, 480, 480, COLORS.purple, 0.22, 130);
    addGlowBlob(frame, 980, 760, 620, 360, COLORS.green, 0.12, 110);
    addRect(frame, 0, 0, 1920, 1080, { r: 1, g: 1, b: 1 }, 0.015, 0);
    for (let i = 0; i < 14; i++) addRect(frame, 72 + i * 132, 160, 1, 820, { r: 1, g: 1, b: 1 }, 0.03, 0);
    for (let j = 0; j < 7; j++) addRect(frame, 72, 180 + j * 120, 1776, 1, { r: 1, g: 1, b: 1 }, 0.02, 0);
    topHeader(frame, title, subtitle, conclusion);
    return frame;
  }

  function linkLine(parent, x, y, w, h, color, rotation) {
    const r = addRect(parent, x, y, w, h, color, 0.75, 99);
    r.rotation = rotation || 0;
    r.effects = [{ type: 'LAYER_BLUR', radius: 1, visible: true }];
    return r;
  }

  function node(parent, x, y, size, title, color) {
    const outer = figma.createFrame();
    outer.x = x; outer.y = y; outer.resize(size, size);
    outer.cornerRadius = size / 2;
    outer.fills = solid({ r: 1, g: 1, b: 1 }, 0.04);
    outer.strokes = [{ type: 'SOLID', color, opacity: 0.45 }];
    outer.strokeWeight = 1;
    outer.effects = [
      { type: 'DROP_SHADOW', color: { ...color, a: 0.24 }, offset: { x: 0, y: 0 }, radius: 24, spread: 0, visible: true, blendMode: 'NORMAL' },
      { type: 'BACKGROUND_BLUR', radius: 8, visible: true }
    ];
    parent.appendChild(outer);
    addEllipse(outer, size / 2 - 10, size / 2 - 10, 20, 20, color, 0.95);
    const label = addText(parent, title, 12, 'Medium', COLORS.white, x - 10, y + size + 10, size + 20);
    label.textAlignHorizontal = 'CENTER';
    return outer;
  }

  // This script is prepared to generate six 1920x1080 high-fidelity executive screens.
  // Due Figma MCP rate limits in this session, the script was saved before execution completion.
  // Reuse the same structure and continue with the detailed screen-building blocks from the Copilot session.

  const s1 = createBaseScreen(0, 'Question Understanding Center', '问题理解与意图识别中枢', '系统不是关键词匹配，而是在理解意图、实体和业务上下文。');
  metricStrip(s1, 72, 164, [
    { label: '意图识别准确率', value: '96.8%', color: COLORS.blue },
    { label: '槽位补全率', value: '94.2%', color: COLORS.green },
    { label: '歧义澄清率', value: '81.5%', color: COLORS.purple },
    { label: '解析时延', value: '228ms', color: COLORS.orange }
  ]);

  return { pageName: page.name };
}

return await run();
