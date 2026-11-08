/**
 * FlowNotes Paper Templates Engine
 * Renders high-resolution procedural backgrounds (Cornell, Grid, Dotted, Ruled, Planner)
 */

const PaperTemplates = {
  render(canvas, type = "ruled", options = {}) {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const width = canvas.width;
    const height = canvas.height;
    const dpr = options.dpr || 1;

    ctx.save();
    ctx.clearRect(0, 0, width, height);

    switch (type) {
      case "blank":
        this.renderBlank(ctx, width, height, options);
        break;
      case "lined":
      case "ruled":
        this.renderRuled(ctx, width, height, { ...options, lineSpacing: 32 * dpr, margin: true });
        break;
      case "college":
        this.renderRuled(ctx, width, height, { ...options, lineSpacing: 24 * dpr, margin: true });
        break;
      case "grid":
        this.renderGrid(ctx, width, height, { ...options, size: 24 * dpr });
        break;
      case "dotted":
        this.renderDotted(ctx, width, height, { ...options, spacing: 24 * dpr });
        break;
      case "cornell":
        this.renderCornell(ctx, width, height, options);
        break;
      case "planner":
        this.renderPlanner(ctx, width, height, options);
        break;
      case "dark_grid":
        this.renderDarkGrid(ctx, width, height, options);
        break;
      case "sepia":
        this.renderSepia(ctx, width, height, options);
        break;
      default:
        this.renderRuled(ctx, width, height, options);
    }

    ctx.restore();
  },

  renderBlank(ctx, width, height, options) {
    ctx.fillStyle = options.dark ? "#1a202c" : "#ffffff";
    ctx.fillRect(0, 0, width, height);
  },

  renderRuled(ctx, width, height, options) {
    const isDark = options.dark;
    const bg = isDark ? "#1a202c" : "#ffffff";
    const lineColor = isDark ? "#2d3748" : "#e2e8f0";
    const marginColor = isDark ? "#e53e3e" : "#fca5a5";

    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, width, height);

    const lineSpacing = options.lineSpacing || 30;
    const startY = 80;

    // Draw horizontal lines
    ctx.beginPath();
    ctx.strokeStyle = lineColor;
    ctx.lineWidth = 1;

    for (let y = startY; y < height - 40; y += lineSpacing) {
      ctx.moveTo(40, y);
      ctx.lineTo(width - 40, y);
    }
    ctx.stroke();

    // Red left margin line
    if (options.margin) {
      ctx.beginPath();
      ctx.strokeStyle = marginColor;
      ctx.lineWidth = 1.5;
      const marginX = 120;
      ctx.moveTo(marginX, 40);
      ctx.lineTo(marginX, height - 40);
      ctx.stroke();
    }
  },

  renderGrid(ctx, width, height, options) {
    const isDark = options.dark;
    ctx.fillStyle = isDark ? "#1a202c" : "#ffffff";
    ctx.fillRect(0, 0, width, height);

    const size = options.size || 24;
    ctx.beginPath();
    ctx.strokeStyle = isDark ? "#2d3748" : "#edf2f7";
    ctx.lineWidth = 1;

    // Vertical lines
    for (let x = 40; x < width - 40; x += size) {
      ctx.moveTo(x, 40);
      ctx.lineTo(x, height - 40);
    }
    // Horizontal lines
    for (let y = 40; y < height - 40; y += size) {
      ctx.moveTo(40, y);
      ctx.lineTo(width - 40, y);
    }
    ctx.stroke();
  },

  renderDotted(ctx, width, height, options) {
    const isDark = options.dark;
    ctx.fillStyle = isDark ? "#1a202c" : "#ffffff";
    ctx.fillRect(0, 0, width, height);

    const spacing = options.spacing || 24;
    ctx.fillStyle = isDark ? "#4a5568" : "#cbd5e1";

    for (let x = 40; x < width - 40; x += spacing) {
      for (let y = 40; y < height - 40; y += spacing) {
        ctx.beginPath();
        ctx.arc(x, y, 1.2, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  },

  renderCornell(ctx, width, height, options) {
    const isDark = options.dark;
    ctx.fillStyle = isDark ? "#1a202c" : "#ffffff";
    ctx.fillRect(0, 0, width, height);

    const lineColor = isDark ? "#2d3748" : "#e2e8f0";
    const dividerColor = isDark ? "#4a5568" : "#94a3b8";

    const topHeaderY = 90;
    const summaryY = height - 160;
    const cueColumnX = 220;

    // Top Header section
    ctx.beginPath();
    ctx.strokeStyle = dividerColor;
    ctx.lineWidth = 2;
    ctx.moveTo(40, topHeaderY);
    ctx.lineTo(width - 40, topHeaderY);
    // Vertical Cue Divider
    ctx.moveTo(cueColumnX, topHeaderY);
    ctx.lineTo(cueColumnX, summaryY);
    // Bottom Summary Divider
    ctx.moveTo(40, summaryY);
    ctx.lineTo(width - 40, summaryY);
    ctx.stroke();

    // Section Labels
    ctx.fillStyle = isDark ? "#718096" : "#94a3b8";
    ctx.font = "bold 13px -apple-system, sans-serif";
    ctx.fillText("TITLE / DATE", 45, 60);
    ctx.fillText("CUES / QUESTIONS / KEYWORDS", 45, topHeaderY + 28);
    ctx.fillText("NOTES & LECTURE OUTLINE", cueColumnX + 16, topHeaderY + 28);
    ctx.fillText("SUMMARY & KEY TAKEAWAYS", 45, summaryY + 30);

    // Ruled lines in Notes area
    ctx.beginPath();
    ctx.strokeStyle = lineColor;
    ctx.lineWidth = 1;
    for (let y = topHeaderY + 50; y < summaryY - 20; y += 28) {
      ctx.moveTo(cueColumnX + 10, y);
      ctx.lineTo(width - 40, y);
    }
    // Lines in Summary area
    for (let y = summaryY + 55; y < height - 40; y += 26) {
      ctx.moveTo(40, y);
      ctx.lineTo(width - 40, y);
    }
    ctx.stroke();
  },

  renderPlanner(ctx, width, height, options) {
    const isDark = options.dark;
    ctx.fillStyle = isDark ? "#1a202c" : "#ffffff";
    ctx.fillRect(0, 0, width, height);

    const borderCol = isDark ? "#4a5568" : "#cbd5e1";
    ctx.fillStyle = isDark ? "#f7fafc" : "#1a202c";
    ctx.font = "bold 18px -apple-system, sans-serif";
    ctx.fillText("WEEKLY STUDY PLANNER", 50, 60);

    // Days grid
    const days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "WEEKEND / TO-DO"];
    const colWidth = (width - 100) / 2;
    const rowHeight = (height - 130) / 3;

    ctx.strokeStyle = borderCol;
    ctx.lineWidth = 1.5;

    days.forEach((day, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const x = 50 + col * colWidth;
      const y = 85 + row * rowHeight;

      ctx.strokeRect(x, y, colWidth - 10, rowHeight - 12);

      ctx.fillStyle = isDark ? "#a0aec0" : "#64748b";
      ctx.font = "bold 12px -apple-system, sans-serif";
      ctx.fillText(day, x + 12, y + 24);

      // checklist lines inside day
      ctx.beginPath();
      ctx.strokeStyle = isDark ? "#2d3748" : "#edf2f7";
      ctx.lineWidth = 1;
      for (let ly = y + 48; ly < y + rowHeight - 20; ly += 24) {
        // small checkbox
        ctx.strokeRect(x + 12, ly - 10, 10, 10);
        ctx.moveTo(x + 30, ly);
        ctx.lineTo(x + colWidth - 25, ly);
      }
      ctx.stroke();
      ctx.strokeStyle = borderCol;
      ctx.lineWidth = 1.5;
    });
  },

  renderDarkGrid(ctx, width, height, options) {
    this.renderGrid(ctx, width, height, { ...options, dark: true, size: 28 });
  },

  renderSepia(ctx, width, height, options) {
    ctx.fillStyle = "#fdf6e2"; // Warm vintage paper
    ctx.fillRect(0, 0, width, height);
    this.renderRuled(ctx, width, height, {
      dark: false,
      lineSpacing: 30,
      margin: true,
      lineColor: "#e5dec9",
      marginColor: "#d97706"
    });
  }
};

window.PaperTemplates = PaperTemplates;
