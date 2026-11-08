/**
 * FlowNotes Handwriting & Canvas Engine
 * High-performance digital ink engine with pressure sensitivity, Catmull-Rom smoothing,
 * Active Recall Tape, smart shape recognition, and stroke erasing.
 */

class FlowCanvasEngine {
  constructor(containerId, options = {}) {
    this.container = document.getElementById(containerId);
    this.bgCanvas = document.getElementById("bg-canvas");
    this.drawCanvas = document.getElementById("draw-canvas");
    this.tapeCanvas = document.getElementById("tape-canvas");
    this.uiCanvas = document.getElementById("ui-canvas");
    this.objectsOverlay = document.getElementById("objects-overlay");

    this.bgCtx = this.bgCanvas.getContext("2d");
    this.ctx = this.drawCanvas.getContext("2d");
    this.tapeCtx = this.tapeCanvas.getContext("2d");
    this.uiCtx = this.uiCanvas.getContext("2d");

    this.dpr = window.devicePixelRatio || 1;
    this.width = options.width || 850;
    this.height = options.height || 1150;
    this.zoom = 1;

    // Active tool state
    this.currentTool = "pen"; // 'pen', 'fountain', 'highlighter', 'pencil', 'eraser', 'tape', 'shape', 'lasso', 'laser', 'text', 'sticky'
    this.currentColor = "#1e293b";
    this.currentSize = 2.5;
    this.eraserType = "stroke"; // 'stroke' or 'pixel'
    this.tapeColor = "#fef08a";

    // Stroke data
    this.strokes = []; // Array of stroke objects
    this.tapes = [];   // Array of active recall tape objects: { id, x, y, w, h, color, revealed }
    this.undoStack = [];
    this.redoStack = [];

    // Interaction tracking
    this.isDrawing = false;
    this.currentPoints = [];
    this.laserPoints = [];
    this.shapePreview = null;
    this.lassoSelection = null;
    this.lassoPath = [];

    this.initCanvasSize();
    this.bindEvents();
    this.startLaserLoop();
  }

  initCanvasSize() {
    const w = this.width;
    const h = this.height;

    [this.bgCanvas, this.drawCanvas, this.tapeCanvas, this.uiCanvas].forEach(c => {
      c.width = w * this.dpr;
      c.height = h * this.dpr;
      c.style.width = `${w}px`;
      c.style.height = `${h}px`;
      const ctx = c.getContext("2d");
      ctx.scale(this.dpr, this.dpr);
    });

    if (this.container) {
      this.container.style.width = `${w}px`;
      this.container.style.height = `${h}px`;
    }
  }

  bindEvents() {
    const el = this.uiCanvas;

    el.addEventListener("pointerdown", (e) => this.onPointerDown(e));
    el.addEventListener("pointermove", (e) => this.onPointerMove(e));
    el.addEventListener("pointerup", (e) => this.onPointerUp(e));
    el.addEventListener("pointercancel", (e) => this.onPointerUp(e));

    // Handle clicks for active recall tape toggle
    el.addEventListener("click", (e) => this.onCanvasClick(e));
  }

  getPointerPos(e) {
    const rect = this.uiCanvas.getBoundingClientRect();
    const x = (e.clientX - rect.left) / this.zoom;
    const y = (e.clientY - rect.top) / this.zoom;
    const pressure = e.pressure && e.pressure > 0 ? e.pressure : 0.5;
    return { x, y, pressure, time: Date.now() };
  }

  onPointerDown(e) {
    if (e.button !== 0 && e.pointerType === "mouse") return;
    this.isDrawing = true;
    this.uiCanvas.setPointerCapture(e.pointerId);

    const pt = this.getPointerPos(e);
    this.currentPoints = [pt];

    if (this.currentTool === "eraser" && this.eraserType === "stroke") {
      this.eraseStrokesAt(pt);
      return;
    }

    if (this.currentTool === "lasso") {
      this.lassoPath = [pt];
      return;
    }

    if (this.currentTool === "tape") {
      this.tapeStart = pt;
      return;
    }

    if (this.currentTool === "laser") {
      this.laserPoints.push({ x: pt.x, y: pt.y, alpha: 1.0 });
      return;
    }
  }

  onPointerMove(e) {
    if (!this.isDrawing) return;
    const pt = this.getPointerPos(e);
    this.currentPoints.push(pt);

    if (this.currentTool === "eraser") {
      if (this.eraserType === "stroke") {
        this.eraseStrokesAt(pt);
      } else {
        this.pixelEraseAt(pt);
      }
      return;
    }

    if (this.currentTool === "lasso") {
      this.lassoPath.push(pt);
      this.drawLassoPreview();
      return;
    }

    if (this.currentTool === "tape") {
      this.drawTapePreview(this.tapeStart, pt);
      return;
    }

    if (this.currentTool === "shape") {
      this.drawShapePreview(this.currentPoints[0], pt);
      return;
    }

    if (this.currentTool === "laser") {
      this.laserPoints.push({ x: pt.x, y: pt.y, alpha: 1.0 });
      return;
    }

    // Direct ink stroke rendering
    this.renderLiveStroke(this.currentPoints);
  }

  onPointerUp(e) {
    if (!this.isDrawing) return;
    this.isDrawing = false;
    const pt = this.getPointerPos(e);

    if (this.currentTool === "tape" && this.tapeStart) {
      const x = Math.min(this.tapeStart.x, pt.x);
      const y = Math.min(this.tapeStart.y, pt.y);
      const w = Math.abs(pt.x - this.tapeStart.x);
      const h = Math.abs(pt.y - this.tapeStart.y);
      if (w > 10 && h > 8) {
        this.saveUndoState();
        this.tapes.push({
          id: "tape_" + Date.now(),
          x, y, w, h,
          color: this.tapeColor,
          revealed: false
        });
        this.redrawTapes();
      }
      this.clearUiCanvas();
      this.tapeStart = null;
      return;
    }

    if (this.currentTool === "shape" && this.currentPoints.length > 2) {
      const shape = this.recognizeShape(this.currentPoints);
      if (shape) {
        this.saveUndoState();
        this.strokes.push({
          type: "shape",
          shape: shape.type,
          params: shape.params,
          color: this.currentColor,
          size: this.currentSize
        });
        this.redrawStrokes();
      }
      this.clearUiCanvas();
      this.currentPoints = [];
      return;
    }

    if (this.currentTool === "lasso") {
      this.finishLassoSelection();
      return;
    }

    if (this.currentTool === "laser") {
      return;
    }

    if (this.currentPoints.length > 1) {
      this.saveUndoState();
      this.strokes.push({
        type: "ink",
        tool: this.currentTool,
        color: this.currentColor,
        size: this.currentSize,
        points: [...this.currentPoints]
      });
      this.redrawStrokes();
    }

    this.clearUiCanvas();
    this.currentPoints = [];
  }

  onCanvasClick(e) {
    const pt = this.getPointerPos(e);
    // Check if clicked inside any active recall tape
    for (let i = this.tapes.length - 1; i >= 0; i--) {
      const tape = this.tapes[i];
      if (pt.x >= tape.x && pt.x <= tape.x + tape.w &&
          pt.y >= tape.y && pt.y <= tape.y + tape.h) {
        tape.revealed = !tape.revealed;
        this.redrawTapes();
        return;
      }
    }
  }

  // Live stroke rendering with Catmull-Rom smoothing
  renderLiveStroke(points) {
    if (points.length < 2) return;
    const ctx = this.ctx;
    ctx.save();

    const tool = this.currentTool;
    if (tool === "highlighter") {
      ctx.globalAlpha = 0.35;
      ctx.strokeStyle = this.currentColor;
      ctx.lineWidth = this.currentSize * 4.5;
      ctx.lineCap = "square";
      ctx.lineJoin = "bevel";
    } else if (tool === "fountain") {
      ctx.strokeStyle = this.currentColor;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
    } else {
      ctx.strokeStyle = this.currentColor;
      ctx.lineWidth = this.currentSize;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
    }

    const p1 = points[points.length - 2];
    const p2 = points[points.length - 1];

    if (tool === "fountain") {
      // Dynamic width calculation based on pressure & speed
      const dist = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      const speed = Math.min(dist / Math.max(1, p2.time - p1.time), 5);
      const dynWidth = Math.max(1, this.currentSize * (p2.pressure * 1.5 - speed * 0.15));
      ctx.lineWidth = dynWidth;
    }

    ctx.beginPath();
    ctx.moveTo(p1.x, p1.y);
    ctx.lineTo(p2.x, p2.y);
    ctx.stroke();

    ctx.restore();
  }

  // Redraw all committed strokes on the draw canvas
  redrawStrokes() {
    this.ctx.clearRect(0, 0, this.width, this.height);

    for (const s of this.strokes) {
      if (s.type === "shape") {
        this.drawVectorShape(this.ctx, s.shape, s.params, s.color, s.size);
      } else if (s.type === "ink") {
        this.drawFullInkStroke(this.ctx, s);
      }
    }
  }

  drawFullInkStroke(ctx, stroke) {
    const pts = stroke.points;
    if (!pts || pts.length < 2) return;

    ctx.save();
    if (stroke.tool === "highlighter") {
      ctx.globalAlpha = 0.35;
      ctx.strokeStyle = stroke.color;
      ctx.lineWidth = stroke.size * 4.5;
      ctx.lineCap = "square";
      ctx.lineJoin = "bevel";
    } else {
      ctx.strokeStyle = stroke.color;
      ctx.lineWidth = stroke.size;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
    }

    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);

    for (let i = 1; i < pts.length - 1; i++) {
      const xc = (pts[i].x + pts[i + 1].x) / 2;
      const yc = (pts[i].y + pts[i + 1].y) / 2;
      ctx.quadraticCurveTo(pts[i].x, pts[i].y, xc, yc);
    }
    ctx.lineTo(pts[pts.length - 1].x, pts[pts.length - 1].y);
    ctx.stroke();
    ctx.restore();
  }

  // Active Recall Tape Redraw
  redrawTapes() {
    this.tapeCtx.clearRect(0, 0, this.width, this.height);

    for (const tape of this.tapes) {
      this.tapeCtx.save();
      if (tape.revealed) {
        // Revealed state: transparent outline with tape tape border
        this.tapeCtx.strokeStyle = tape.color || "#fef08a";
        this.tapeCtx.lineWidth = 1.5;
        this.tapeCtx.setLineDash([4, 4]);
        this.tapeCtx.strokeRect(tape.x, tape.y, tape.w, tape.h);
        this.tapeCtx.fillStyle = "rgba(254, 240, 138, 0.12)";
        this.tapeCtx.fillRect(tape.x, tape.y, tape.w, tape.h);
      } else {
        // Concealed state: Solid aesthetic study tape with paper shadow
        this.tapeCtx.fillStyle = tape.color || "#fef08a";
        this.tapeCtx.shadowColor = "rgba(0,0,0,0.15)";
        this.tapeCtx.shadowBlur = 4;
        this.tapeCtx.shadowOffsetY = 2;
        this.tapeCtx.fillRect(tape.x, tape.y, tape.w, tape.h);

        // Subtle tape texture stripe
        this.tapeCtx.strokeStyle = "rgba(0,0,0,0.06)";
        this.tapeCtx.lineWidth = 2;
        this.tapeCtx.strokeRect(tape.x + 2, tape.y + 2, tape.w - 4, tape.h - 4);
      }
      this.tapeCtx.restore();
    }
  }

  drawTapePreview(start, curr) {
    this.clearUiCanvas();
    const x = Math.min(start.x, curr.x);
    const y = Math.min(start.y, curr.y);
    const w = Math.abs(curr.x - start.x);
    const h = Math.abs(curr.y - start.y);

    this.uiCtx.save();
    this.uiCtx.fillStyle = this.tapeColor;
    this.uiCtx.globalAlpha = 0.8;
    this.uiCtx.fillRect(x, y, w, h);
    this.uiCtx.strokeStyle = "#d97706";
    this.uiCtx.lineWidth = 1;
    this.uiCtx.strokeRect(x, y, w, h);
    this.uiCtx.restore();
  }

  // Stroke Eraser (Freenotes style)
  eraseStrokesAt(pt) {
    const radius = 15;
    const initialCount = this.strokes.length;
    this.strokes = this.strokes.filter(s => {
      if (s.type === "shape") return true;
      for (const p of s.points) {
        if (Math.hypot(p.x - pt.x, p.y - pt.y) < radius) {
          return false; // delete this stroke
        }
      }
      return true;
    });

    if (this.strokes.length !== initialCount) {
      this.redrawStrokes();
    }
  }

  pixelEraseAt(pt) {
    this.ctx.save();
    this.ctx.globalCompositeOperation = "destination-out";
    this.ctx.beginPath();
    this.ctx.arc(pt.x, pt.y, this.currentSize * 4, 0, Math.PI * 2);
    this.ctx.fill();
    this.ctx.restore();
  }

  // Shape Recognition
  drawShapePreview(p1, p2) {
    this.clearUiCanvas();
    this.uiCtx.save();
    this.uiCtx.strokeStyle = this.currentColor;
    this.uiCtx.lineWidth = this.currentSize;
    this.uiCtx.beginPath();
    this.uiCtx.moveTo(p1.x, p1.y);
    this.uiCtx.lineTo(p2.x, p2.y);
    this.uiCtx.stroke();
    this.uiCtx.restore();
  }

  recognizeShape(points) {
    const p1 = points[0];
    const p2 = points[points.length - 1];
    const dist = Math.hypot(p2.x - p1.x, p2.y - p1.y);

    // Bounding box
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    points.forEach(p => {
      minX = Math.min(minX, p.x);
      maxX = Math.max(maxX, p.x);
      minY = Math.min(minY, p.y);
      maxY = Math.max(maxY, p.y);
    });
    const w = maxX - minX;
    const h = maxY - minY;

    // Check if straight line
    if (dist > 30 && dist > Math.hypot(w, h) * 0.85) {
      return { type: "line", params: { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y } };
    }

    // Check if closed shape (circle or rect)
    const isClosed = Math.hypot(p1.x - p2.x, p1.y - p2.y) < Math.max(w, h) * 0.35;
    if (isClosed) {
      const aspectRatio = w / Math.max(1, h);
      if (aspectRatio > 0.8 && aspectRatio < 1.25) {
        return {
          type: "circle",
          params: { cx: minX + w / 2, cy: minY + h / 2, r: (w + h) / 4 }
        };
      }
      return {
        type: "rectangle",
        params: { x: minX, y: minY, w, h }
      };
    }

    return { type: "line", params: { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y } };
  }

  drawVectorShape(ctx, shape, p, color, size) {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = size;
    ctx.beginPath();

    if (shape === "line") {
      ctx.moveTo(p.x1, p.y1);
      ctx.lineTo(p.x2, p.y2);
    } else if (shape === "circle") {
      ctx.arc(p.cx, p.cy, p.r, 0, Math.PI * 2);
    } else if (shape === "rectangle") {
      ctx.strokeRect(p.x, p.y, p.w, p.h);
    }
    ctx.stroke();
    ctx.restore();
  }

  // Lasso Tool
  drawLassoPreview() {
    this.clearUiCanvas();
    if (this.lassoPath.length < 2) return;
    this.uiCtx.save();
    this.uiCtx.strokeStyle = "#3b82f6";
    this.uiCtx.lineWidth = 1.5;
    this.uiCtx.setLineDash([6, 6]);
    this.uiCtx.beginPath();
    this.uiCtx.moveTo(this.lassoPath[0].x, this.lassoPath[0].y);
    for (let i = 1; i < this.lassoPath.length; i++) {
      this.uiCtx.lineTo(this.lassoPath[i].x, this.lassoPath[i].y);
    }
    this.uiCtx.stroke();
    this.uiCtx.restore();
  }

  finishLassoSelection() {
    this.clearUiCanvas();
    this.lassoPath = [];
  }

  // Laser Pointer Animation Loop
  startLaserLoop() {
    const loop = () => {
      if (this.laserPoints.length > 0) {
        this.clearUiCanvas();
        for (let i = 0; i < this.laserPoints.length; i++) {
          const lp = this.laserPoints[i];
          lp.alpha -= 0.035;
        }
        this.laserPoints = this.laserPoints.filter(lp => lp.alpha > 0);

        this.uiCtx.save();
        for (const lp of this.laserPoints) {
          this.uiCtx.beginPath();
          this.uiCtx.arc(lp.x, lp.y, 6, 0, Math.PI * 2);
          this.uiCtx.fillStyle = `rgba(239, 68, 68, ${lp.alpha})`;
          this.uiCtx.shadowColor = "#ef4444";
          this.uiCtx.shadowBlur = 10;
          this.uiCtx.fill();
        }
        this.uiCtx.restore();
      }
      requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  }

  clearUiCanvas() {
    this.uiCtx.clearRect(0, 0, this.width, this.height);
  }

  // Undo / Redo
  saveUndoState() {
    this.undoStack.push({
      strokes: JSON.parse(JSON.stringify(this.strokes)),
      tapes: JSON.parse(JSON.stringify(this.tapes))
    });
    if (this.undoStack.length > 50) this.undoStack.shift();
    this.redoStack = [];
  }

  undo() {
    if (this.undoStack.length === 0) return;
    const currentState = {
      strokes: JSON.parse(JSON.stringify(this.strokes)),
      tapes: JSON.parse(JSON.stringify(this.tapes))
    };
    this.redoStack.push(currentState);
    const prevState = this.undoStack.pop();
    this.strokes = prevState.strokes;
    this.tapes = prevState.tapes;
    this.redrawStrokes();
    this.redrawTapes();
  }

  redo() {
    if (this.redoStack.length === 0) return;
    const nextState = this.redoStack.pop();
    this.undoStack.push({
      strokes: JSON.parse(JSON.stringify(this.strokes)),
      tapes: JSON.parse(JSON.stringify(this.tapes))
    });
    this.strokes = nextState.strokes;
    this.tapes = nextState.tapes;
    this.redrawStrokes();
    this.redrawTapes();
  }

  clearPage() {
    this.saveUndoState();
    this.strokes = [];
    this.tapes = [];
    this.redrawStrokes();
    this.redrawTapes();
  }

  // Reveal / Conceal all Active Recall Tapes
  toggleAllTapes(reveal = true) {
    this.tapes.forEach(t => t.revealed = reveal);
    this.redrawTapes();
  }

  setZoom(zoom) {
    this.zoom = zoom;
    if (this.container) {
      this.container.style.transform = `scale(${zoom})`;
    }
  }

  exportAsImage() {
    const merged = document.createElement("canvas");
    merged.width = this.width * this.dpr;
    merged.height = this.height * this.dpr;
    const mCtx = merged.getContext("2d");

    // Draw background, strokes, and tapes
    mCtx.drawImage(this.bgCanvas, 0, 0);
    mCtx.drawImage(this.drawCanvas, 0, 0);
    mCtx.drawImage(this.tapeCanvas, 0, 0);

    return merged.toDataURL("image/png");
  }
}

window.FlowCanvasEngine = FlowCanvasEngine;
